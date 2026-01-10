package io.github.ehlyzov.branchline.integration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.Compiler
import io.github.ehlyzov.branchline.vm.VM
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.system.measureTimeMillis

@Disabled("Load/perf test — run manually; generates heavy workload")
class TreeReconstructionLoadTest {

    data class Edge(val treeId: String, val fromId: String, val toId: String)

    @Test
    fun `measure SharedStore throughput in VM for tree reconstruction`() {
        val totalTrees = System.getProperty("LOAD_TREES")?.toIntOrNull() ?: 1000
        val minDepth = System.getProperty("LOAD_MIN_DEPTH")?.toIntOrNull() ?: 5
        val maxDepth = System.getProperty("LOAD_MAX_DEPTH")?.toIntOrNull() ?: 10
        val minBranch = System.getProperty("LOAD_MIN_BRANCH")?.toIntOrNull() ?: 5
        val maxBranch = System.getProperty("LOAD_MAX_BRANCH")?.toIntOrNull() ?: 10
        val capEdges = System.getProperty("LOAD_CAP_EDGES")?.toIntOrNull() ?: 250_000
        val parallelConfigs = intArrayOf(1, 4, 16, 64)

        // Compile tiny DSL to bytecode; VM executes per edge
        val bytecodeVm = compileToVM()

        // Generate workload once (shuffled), then reuse for each run on a fresh store
        val workload = generateWorkload(
            totalTrees = totalTrees,
            minDepth = minDepth,
            maxDepth = maxDepth,
            minBranch = minBranch,
            maxBranch = maxBranch,
            capEdges = capEdges,
            seed = 42
        )

        println("LoadTest - trees=$totalTrees depth=${minDepth}..${maxDepth} branch=${minBranch}..${maxBranch} edges=${workload.size}")

        for (p in parallelConfigs) {
            val shared = DefaultSharedStore().apply { addResource("nodeParents", SharedResourceKind.SINGLE) }
            val elapsedMs = measureTimeMillis {
                runPipelineWithParallelism(
                    edges = workload,
                    parallelism = p,
                    vm = bytecodeVm.vm,
                    bytecode = bytecodeVm.bytecode,
                    shared = shared
                )
            }
            val eps = if (elapsedMs > 0) (workload.size * 1000L) / elapsedMs else Long.MAX_VALUE
            println("parallel=$p took=${elapsedMs}ms throughput=${eps} edges/sec")
            assertTrue(elapsedMs < 60_000, "Single config should finish within 60s in CI env")
        }
    }

    private data class VMProgram(val vm: VM, val bytecode: io.github.ehlyzov.branchline.vm.Bytecode)

    private fun compileToVM(): VMProgram {
        val program = """
            TRANSFORM T { LET msg = row;
                // Keep VM work non-trivial
                LET a = msg.treeId;
                OUTPUT { ok: true };
            }
        """.trimIndent()

        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = StdLib.fns
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)
        val vm = VM(hostFns, funcs)
        return VMProgram(vm, bytecode)
    }

    private fun runPipelineWithParallelism(
        edges: List<Edge>,
        parallelism: Int,
        vm: VM,
        bytecode: io.github.ehlyzov.branchline.vm.Bytecode,
        shared: DefaultSharedStore,
    ) = runBlocking {
        val channel = Channel<Edge>(capacity = 4096)
        val producer = launch(Dispatchers.Default) {
            for (e in edges) channel.send(e)
            channel.close()
        }
        val workers = mutableListOf<Job>()
        var idx = 0
        while (idx < parallelism) {
            val job = launch(Dispatchers.Default) {
                for (e in channel) processEdge(e, vm, bytecode, shared)
            }
            workers.add(job)
            idx += 1
        }
        producer.join()
        workers.forEach { it.join() }
    }

    private fun processEdge(
        e: Edge,
        vm: VM,
        bytecode: io.github.ehlyzov.branchline.vm.Bytecode,
        shared: DefaultSharedStore,
    ) {
        val level: Int = if (e.fromId == "root") {
            1
        } else {
            val parentKey = keyOf(e.treeId, e.fromId)
            val parentInfo = runBlocking { shared.await("nodeParents", parentKey) } as Map<*, *>
            val parentLevel = parentInfo["level"] as Int
            parentLevel + 1
        }

        val env = mutableMapOf<String, Any?>(
            "row" to mapOf(
                "treeId" to e.treeId,
                "fromId" to e.fromId,
                "toId" to e.toId
            )
        )
        vm.execute(bytecode, env)

        val nodeKey = keyOf(e.treeId, e.toId)
        val nodeInfo = mapOf(
            "nodeId" to e.toId,
            "level" to level,
            "treeId" to e.treeId,
            "parentId" to e.fromId
        )
        runBlocking { shared.setOnce("nodeParents", nodeKey, nodeInfo) }
    }

    private fun keyOf(treeId: String, nodeId: String): String {
        return StringBuilder(treeId.length + 1 + nodeId.length)
            .append(treeId).append(':').append(nodeId).toString()
    }

    private fun generateWorkload(
        totalTrees: Int,
        minDepth: Int,
        maxDepth: Int,
        minBranch: Int,
        maxBranch: Int,
        capEdges: Int,
        seed: Int,
    ): List<Edge> {
        val rnd = Random(seed)
        val out = ArrayList<Edge>(min(capEdges, totalTrees * 32))
        var produced = 0
        var t = 0
        while (t < totalTrees && produced < capEdges) {
            val treeId = UUID.randomUUID().toString()
            val depth = clamp(minDepth + rnd.nextInt(max(1, maxDepth - minDepth + 1)), 1, 32)
            val branch = clamp(minBranch + rnd.nextInt(max(1, maxBranch - minBranch + 1)), 1, 32)
            produced += generateTreeEdges(treeId, depth, branch, capEdges - produced, out, rnd)
            t += 1
        }
        out.shuffle(rnd)
        return out
    }

    private fun clamp(v: Int, lo: Int, hi: Int): Int {
        return max(lo, min(v, hi))
    }

    private fun generateTreeEdges(
        treeId: String,
        maxDepth: Int,
        branching: Int,
        remainingBudget: Int,
        out: MutableList<Edge>,
        rnd: Random,
    ): Int {
        if (remainingBudget <= 0) return 0
        var produced = 0
        var nextNodeIdx = 1

        val queueIds = ArrayDeque<String>()
        val queueDepth = ArrayDeque<Int>()

        val rootId = "n${nextNodeIdx++}"
        out.add(Edge(treeId, "root", rootId))
        produced += 1
        if (produced >= remainingBudget) return produced

        queueIds.add(rootId)
        queueDepth.add(1)

        while (queueIds.isNotEmpty() && produced < remainingBudget) {
            val parentId = queueIds.removeFirst()
            val depth = queueDepth.removeFirst()
            if (depth >= maxDepth) continue
            val children = 1 + rnd.nextInt(max(1, branching))
            var i = 0
            while (i < children && produced < remainingBudget) {
                val childId = "n${nextNodeIdx++}"
                out.add(Edge(treeId, parentId, childId))
                produced += 1
                queueIds.add(childId)
                queueDepth.add(depth + 1)
                i += 1
            }
        }
        return produced
    }
}

