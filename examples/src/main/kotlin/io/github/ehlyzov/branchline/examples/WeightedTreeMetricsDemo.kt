package io.github.ehlyzov.branchline.examples

import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

public fun main() {
    runBlocking(Dispatchers.Default) {
        val tree = buildWeightedTree(Random(11), nodeCount = 512)
        val metrics = TransformMetrics()
        val sharedStore = InstrumentedSharedStore(DefaultSharedStore(), metrics).apply {
            addResource("rootDistance", SharedResourceKind.SINGLE)
            addResource("leafDistance", SharedResourceKind.SINGLE)
        }
        val previousStore = SharedStoreProvider.store
        SharedStoreProvider.store = sharedStore
        try {
            val context = buildProgramContext(WEIGHTED_TREE_DSL)
            val rootExec = buildExec(context, "RootDistanceIngest", sharedStore)
            val leafExec = buildExec(context, "LeafDistanceNode", sharedStore)
            val metricsExec = buildExec(context, "EdgeMetrics", sharedStore)

            println("Task 2: Weighted tree metrics")
            println("Nodes: ${tree.nodeCount} | Edges: ${tree.edges.size} | Root: ${tree.rootId}")

            val totalStart = System.nanoTime()
            runPhase("Phase 1: Root distance pass", metrics) {
                runRootDistancePass(tree, rootExec, sharedStore, metrics)
            }

            runPhase("Phase 2: Leaf distance pass", metrics) {
                runLeafDistancePass(tree, leafExec, sharedStore, metrics)
            }

            val samples = Collections.synchronizedList(mutableListOf<Map<*, *>>())
            runPhase("Phase 3: Edge metrics snapshot", metrics) {
                coroutineScope {
                    val jobs = tree.edges.map { edge ->
                        async {
                            val output = runTransform(metricsExec, edgeEnv(edge), metrics)
                            synchronized(samples) {
                                if (samples.size < 3) {
                                    samples.add(output)
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            if (samples.isNotEmpty()) {
                println("Sample metrics:")
                samples.forEach { output ->
                    val src = requireInt(output["src"], "src")
                    val dst = requireInt(output["dst"], "dst")
                    val rootSum = requireInt(output["rootSum"], "rootSum")
                    val leafSum = requireInt(output["leafSum"], "leafSum")
                    println("edge $src -> $dst | rootSum=$rootSum | leafSum=$leafSum")
                }
            }

            val totalMs = (System.nanoTime() - totalStart) / 1_000_000
            println("Total time: ${totalMs}ms")
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }
}
