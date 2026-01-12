package io.github.ehlyzov.branchline.examples

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.StdLib
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/* =========================
 * Models
 * ========================= */

internal data class Edge(val src: Int, val dst: Int)

internal data class WeightedEdge(val src: Int, val dst: Int, val weight: Int)

internal data class GraphSpec(
    val nodeCount: Int,
    val start: Int,
    val target: Int,
    val edges: List<Edge>,
    val levels: IntArray,
)

internal data class WeightedTreeSpec(
    val nodeCount: Int,
    val rootId: Int,
    val nodes: List<Int>,
    val rootEdge: WeightedEdge,
    val edges: List<WeightedEdge>,
    val childrenByParent: Map<Int, List<WeightedEdge>>,
    val depthByNode: IntArray,
    val leafWeights: IntArray,
)

internal data class MetricsSnapshot(
    val maxRunning: Int,
    val maxAwaiting: Int,
)

/* =========================
 * DSL programs
 * ========================= */

internal object ExamplePrograms {
    val SHORTEST_PATH_DSL = """
        SHARED distances SINGLE;
        SHARED parents SINGLE;

        FUNC NodeKey(id) {
            RETURN "node:" + id;
        }

        FUNC BuildPath(start, node) {
            IF node == start THEN {
                RETURN [node];
            } ELSE {
                LET parent = AWAIT_SHARED("parents", NodeKey(node));
                LET prefix = BuildPath(start, parent);
                RETURN prefix ++ [node];
            }
        }

        TRANSFORM EdgeIngest { LET edge = row;
            LET fromId = edge.src;
            LET toId = edge.dst;
            LET parentDist = AWAIT_SHARED("distances", NodeKey(fromId));
            LET newDist = parentDist + 1;
            OUTPUT {
                src: fromId,
                dst: toId,
                distance: newDist,
                parent: fromId
            };
        }

        TRANSFORM ShortestPath { LET query = row;
            LET start = query.start;
            LET goal = query.goal;
            LET distance = AWAIT_SHARED("distances", NodeKey(goal));
            LET path = BuildPath(start, goal);
            OUTPUT {
                start: start,
                goal: goal,
                distance: distance,
                path: path
            };
        }
    """.trimIndent()

    val WEIGHTED_TREE_DSL = """
        SHARED rootDistance SINGLE;
        SHARED leafDistance SINGLE;

        FUNC NodeKey(id) {
            RETURN "node:" + id;
        }

        TRANSFORM RootDistanceIngest { LET edge = row;
            LET fromId = edge.src;
            LET toId = edge.dst;
            LET weight = edge.weight;
            LET rootSum = IF fromId == 0 THEN weight ELSE (AWAIT_SHARED("rootDistance", NodeKey(fromId)) + weight);
            OUTPUT {
                src: fromId,
                dst: toId,
                rootSum: rootSum
            };
        }

        TRANSFORM LeafDistanceNode { LET node = row;
            LET nodeId = node.nodeId;
            LET children = node.children;
            LET options = [
                IF c.id == 0 THEN c.weight ELSE (c.weight + AWAIT_SHARED("leafDistance", NodeKey(c.id)))
                FOR EACH c IN children
            ];
            LET leafSum = MIN(options);
            OUTPUT {
                nodeId: nodeId,
                leafSum: leafSum
            };
        }

        TRANSFORM EdgeMetrics { LET edge = row;
            LET fromId = edge.src;
            LET toId = edge.dst;
            LET rootSum = AWAIT_SHARED("rootDistance", NodeKey(toId));
            LET leafSum = AWAIT_SHARED("leafDistance", NodeKey(fromId));
            OUTPUT {
                src: fromId,
                dst: toId,
                rootSum: rootSum,
                leafSum: leafSum
            };
        }
    """.trimIndent()

    val TREE_SNAPSHOT_DSL = """
        SHARED rootDistance SINGLE;
        SHARED leafDistance SINGLE;
        SHARED children SINGLE;

        FUNC NodeKey(id) {
            RETURN "node:" + id;
        }

        FUNC BuildNode(id) {
            LET rootSum = AWAIT_SHARED("rootDistance", NodeKey(id));
            LET leafSum = AWAIT_SHARED("leafDistance", NodeKey(id));
            LET kids = AWAIT_SHARED("children", NodeKey(id));
            LET built = [
                { weight: c.weight, node: BuildNode(c.id) }
                FOR EACH c IN kids
            ];
            RETURN {
                id: id,
                rootDistance: rootSum,
                leafDistance: leafSum,
                children: built
            };
        }

        TRANSFORM TreeSnapshot { LET query = row;
            LET rootId = query.root;
            LET tree = BuildNode(rootId);
            OUTPUT { tree: tree };
        }
    """.trimIndent()
}

/* =========================
 * Runtime build / Exec
 * ========================= */

internal data class ProgramContext(
    val funcs: Map<String, FuncDecl>,
    val transforms: Map<String, TransformDecl>,
    val hostFns: Map<String, (List<Any?>) -> Any?>,
)

internal fun buildProgramContext(source: String): ProgramContext {
    val tokens = Lexer(source).lex()
    val program = Parser(tokens, source).parse()

    val funcs = program.decls
        .filterIsInstance<FuncDecl>()
        .associateBy { it.name }

    val transforms = program.decls
        .filterIsInstance<TransformDecl>()
        .associateBy { t -> t.name ?: error("Transform name required for example program") }

    return ProgramContext(
        funcs = funcs,
        transforms = transforms,
        hostFns = StdLib.fns,
    )
}

internal fun buildExec(
    context: ProgramContext,
    transformName: String,
    sharedStore: SharedStore?,
): Exec {
    val transform = context.transforms[transformName]
        ?: error("Missing transform '$transformName'")

    val ir = ToIR(context.funcs, context.hostFns).compile(transform.body.statements)

    return Exec(
        ir = ir,
        hostFns = context.hostFns,
        hostFnMeta = StdLib.meta,
        funcs = context.funcs,
        sharedStore = sharedStore,
    )
}

/* =========================
 * Instrumentation
 * ========================= */

internal class TransformMetrics {
    private val running = AtomicInteger(0)
    private val awaiting = AtomicInteger(0)
    private val maxRunning = AtomicInteger(0)
    private val maxAwaiting = AtomicInteger(0)

    internal fun onTransformStart() {
        updateMax(maxRunning, running.incrementAndGet())
    }

    internal fun onTransformEnd() {
        running.decrementAndGet()
    }

    internal fun onAwaitStart() {
        updateMax(maxAwaiting, awaiting.incrementAndGet())
    }

    internal fun onAwaitEnd() {
        awaiting.decrementAndGet()
    }

    internal fun resetMax() {
        maxRunning.set(running.get())
        maxAwaiting.set(awaiting.get())
    }

    internal fun snapshot(): MetricsSnapshot = MetricsSnapshot(
        maxRunning = maxRunning.get(),
        maxAwaiting = maxAwaiting.get(),
    )
}

internal class InstrumentedSharedStore(
    private val delegate: SharedStore,
    private val metrics: TransformMetrics,
) : SharedStore by delegate {

    override suspend fun await(resource: String, key: String): Any? {
        delegate.lookup(resource, key)?.let { return it }

        metrics.onAwaitStart()
        return try {
            delegate.await(resource, key)
        } finally {
            metrics.onAwaitEnd()
        }
    }
}

internal suspend fun runTransform(
    exec: Exec,
    env: MutableMap<String, Any?>,
    metrics: TransformMetrics,
): Map<*, *> {
    metrics.onTransformStart()
    return try {
        exec.run(env) as Map<*, *>
    } finally {
        metrics.onTransformEnd()
    }
}

internal inline suspend fun <T> runPhase(
    label: String,
    metrics: TransformMetrics,
    crossinline block: suspend () -> T,
): T {
    metrics.resetMax()
    val started = System.nanoTime()

    val result = block()

    val elapsedMs = (System.nanoTime() - started) / 1_000_000
    val snapshot = metrics.snapshot()
    println("$label: ${elapsedMs}ms | running=${snapshot.maxRunning} waiting=${snapshot.maxAwaiting}")
    return result
}

/* =========================
 * Generators
 * ========================= */

internal fun buildShortestPathGraph(
    random: Random,
    nodeCount: Int,
    extraEdges: Int,
): GraphSpec {
    require(nodeCount > 0) { "nodeCount must be > 0" }
    require(extraEdges >= 0) { "extraEdges must be >= 0" }

    val start = random.nextInt(nodeCount)
    val levels = IntArray(nodeCount) { -1 }
    val remaining = buildRemainingNodes(nodeCount, start)

    val edges = ArrayList<Edge>(nodeCount - 1 + extraEdges)
    val edgeSet = HashSet<Edge>(nodeCount - 1 + extraEdges)

    buildBfsTree(
        random = random,
        start = start,
        levels = levels,
        remaining = remaining,
        onTreeEdge = { src, dst -> addEdge(edges, edgeSet, src, dst) },
    )

    addExtraEdges(
        random = random,
        nodeCount = nodeCount,
        extraEdges = extraEdges,
        levels = levels,
        edges = edges,
        edgeSet = edgeSet,
    )

    return GraphSpec(
        nodeCount = nodeCount,
        start = start,
        target = pickTarget(random, nodeCount, start),
        edges = edges,
        levels = levels,
    )
}

private fun buildBfsTree(
    random: Random,
    start: Int,
    levels: IntArray,
    remaining: MutableList<Int>,
    onTreeEdge: (src: Int, dst: Int) -> Unit,
) {
    val queue = ArrayDeque<Int>()
    levels[start] = 0
    queue.add(start)

    var firstIteration = true

    while (remaining.isNotEmpty()) {
        val parent = queue.removeFirst()

        val childCount = pickChildCount(
            random = random,
            isStart = (parent == start),
            remainingCount = remaining.size,
            ensureBranchThisIteration = firstIteration,
        )

        repeat(childCount) {
            val child = remaining.removeRandom(random)
            levels[child] = levels[parent] + 1
            queue.add(child)
            onTreeEdge(parent, child)
        }

        firstIteration = false
    }
}

private fun pickChildCount(
    random: Random,
    isStart: Boolean,
    remainingCount: Int,
    ensureBranchThisIteration: Boolean,
): Int {
    val maxChildren = minOf(3, remainingCount)
    val minChildren = if (ensureBranchThisIteration && isStart && remainingCount >= 2) 2 else 1
    return random.nextIntInclusive(minChildren, maxChildren)
}

private fun addExtraEdges(
    random: Random,
    nodeCount: Int,
    extraEdges: Int,
    levels: IntArray,
    edges: MutableList<Edge>,
    edgeSet: MutableSet<Edge>,
) {
    if (extraEdges == 0) return

    val targetSize = edgeSet.size + extraEdges
    val maxAttempts = extraEdges * 10
    var attempts = 0

    while (edgeSet.size < targetSize && attempts < maxAttempts) {
        attempts++

        val src = random.nextInt(nodeCount)
        val dst = random.nextInt(nodeCount)
        if (!isExtraEdgeAllowed(src, dst, levels)) continue

        val e = Edge(src, dst)
        if (edgeSet.add(e)) edges.add(e)
    }
}

private fun isExtraEdgeAllowed(src: Int, dst: Int, levels: IntArray): Boolean {
    if (src == dst) return false
    if (levels[dst] > levels[src]) return false // disallow edge "down" the BFS levels
    return true
}

private fun buildRemainingNodes(nodeCount: Int, start: Int): MutableList<Int> =
    ArrayList<Int>(nodeCount - 1).apply {
        for (i in 0 until nodeCount) if (i != start) add(i)
    }

private fun pickTarget(random: Random, nodeCount: Int, start: Int): Int {
    if (nodeCount == 1) return start
    val pick = random.nextInt(nodeCount - 1)
    return if (pick >= start) pick + 1 else pick
}

private fun MutableList<Int>.removeRandom(random: Random): Int =
    removeAt(random.nextInt(size))

private fun addEdge(
    edges: MutableList<Edge>,
    edgeSet: MutableSet<Edge>,
    src: Int,
    dst: Int,
) {
    val e = Edge(src = src, dst = dst)
    edges.add(e)
    edgeSet.add(e)
}

internal fun buildWeightedTree(
    random: Random,
    nodeCount: Int,
): WeightedTreeSpec {
    require(nodeCount >= 1) { "nodeCount must be >= 1" }

    val rootId = 1
    val nodes = (1..nodeCount).toList()

    val depthByNode = IntArray(nodeCount + 1) { -1 }
    val childrenByParent = mutableMapOf<Int, MutableList<WeightedEdge>>()
    val edges = mutableListOf<WeightedEdge>()
    val queue = ArrayDeque<Int>()

    depthByNode[rootId] = 0
    queue.add(rootId)

    var nextNode = rootId + 1
    var firstIteration = true

    while (nextNode <= nodeCount) {
        val parent = queue.removeFirst()

        val remainingCount = nodeCount - nextNode + 1
        if (remainingCount <= 0) continue

        val childCount = pickChildCount(
            random = random,
            isStart = (parent == rootId),
            remainingCount = remainingCount,
            ensureBranchThisIteration = firstIteration,
        )

        repeat(childCount) {
            if (nextNode > nodeCount) return@repeat
            val child = nextNode++
            val weight = random.nextInt(1, 9)

            val edge = WeightedEdge(src = parent, dst = child, weight = weight)
            edges.add(edge)
            childrenByParent.getOrPut(parent) { mutableListOf() }.add(edge)

            depthByNode[child] = depthByNode[parent] + 1
            queue.add(child)
        }

        firstIteration = false
    }

    val leafWeights = IntArray(nodeCount + 1)
    for (node in nodes) {
        val kids = childrenByParent[node].orEmpty()
        if (kids.isEmpty()) {
            leafWeights[node] = random.nextInt(1, 9)
        }
    }

    return WeightedTreeSpec(
        nodeCount = nodeCount,
        rootId = rootId,
        nodes = nodes,
        rootEdge = WeightedEdge(src = 0, dst = rootId, weight = 0),
        edges = edges,
        childrenByParent = childrenByParent,
        depthByNode = depthByNode,
        leafWeights = leafWeights,
    )
}

/* =========================
 * Passes / orchestration
 * ========================= */

internal suspend fun runRootDistancePass(
    tree: WeightedTreeSpec,
    exec: Exec,
    sharedStore: SharedStore,
    metrics: TransformMetrics,
) {
    val rootOutput = runTransform(exec, edgeEnv(tree.rootEdge), metrics)
    val rootId = requireInt(rootOutput["dst"], "dst")
    val rootSum = requireInt(rootOutput["rootSum"], "rootSum")
    sharedStore.setOnce("rootDistance", rootId.nodeKey(), rootSum)

    val edgesByDepth = tree.edges.groupBy { tree.depthByNode[it.src] }.toSortedMap()
    for ((_, edgesAtDepth) in edgesByDepth) {
        coroutineScope {
            val jobs = edgesAtDepth.map { edge ->
                async {
                    val output = runTransform(exec, edgeEnv(edge), metrics)
                    val toId = requireInt(output["dst"], "dst")
                    val sum = requireInt(output["rootSum"], "rootSum")
                    sharedStore.setOnce("rootDistance", toId.nodeKey(), sum)
                }
            }
            jobs.awaitAll()
        }
    }
}

internal suspend fun runLeafDistancePass(
    tree: WeightedTreeSpec,
    exec: Exec,
    sharedStore: SharedStore,
    metrics: TransformMetrics,
) {
    val nodesByDepth = tree.nodes.groupBy { tree.depthByNode[it] }
    val depthsDescending = nodesByDepth.keys.sortedDescending()

    for (depth in depthsDescending) {
        val nodesAtDepth = nodesByDepth[depth].orEmpty()
        coroutineScope {
            val jobs = nodesAtDepth.map { nodeId ->
                async {
                    val childEntries = buildChildEntries(tree, nodeId)
                    val output = runTransform(exec, nodeEnv(nodeId, childEntries), metrics)

                    val outNode = requireInt(output["nodeId"], "nodeId")
                    val leafSum = requireInt(output["leafSum"], "leafSum")
                    sharedStore.setOnce("leafDistance", outNode.nodeKey(), leafSum)
                }
            }
            jobs.awaitAll()
        }
    }
}

private fun buildChildEntries(tree: WeightedTreeSpec, nodeId: Int): List<Map<String, Any?>> {
    val children = tree.childrenByParent[nodeId].orEmpty()
    if (children.isNotEmpty()) {
        return children.map { edge ->
            mapOf(
                "id" to edge.dst,
                "weight" to edge.weight,
            )
        }
    }

    return listOf(
        mapOf(
            "id" to 0,
            "weight" to tree.leafWeights[nodeId],
        )
    )
}

internal fun countTreeNodes(value: Any?): Int {
    val nodeMap = requireMap(value, "tree")
    val children = requireList(nodeMap["children"], "children")

    var count = 1
    children.forEachIndexed { index, entry ->
        val childMap = requireMap(entry, "children[$index]")
        count += countTreeNodes(childMap["node"])
    }
    return count
}

/* =========================
 * Env builders / keys
 * ========================= */

private fun rowEnv(row: Map<String, Any?>): MutableMap<String, Any?> =
    mutableMapOf("row" to row)

internal fun edgeEnv(edge: Edge): MutableMap<String, Any?> =
    rowEnv(mapOf("src" to edge.src, "dst" to edge.dst))

internal fun edgeEnv(edge: WeightedEdge): MutableMap<String, Any?> =
    rowEnv(mapOf("src" to edge.src, "dst" to edge.dst, "weight" to edge.weight))

internal fun nodeEnv(nodeId: Int, children: List<Map<String, Any?>>): MutableMap<String, Any?> =
    rowEnv(mapOf("nodeId" to nodeId, "children" to children))

internal fun queryEnv(source: Int, target: Int): MutableMap<String, Any?> =
    rowEnv(mapOf("start" to source, "goal" to target))

internal fun rootEnv(rootId: Int): MutableMap<String, Any?> =
    rowEnv(mapOf("root" to rootId))

internal fun Int.nodeKey(): String = "node:$this"

/* =========================
 * Value coercions
 * ========================= */

private inline fun <reified T> requireType(value: Any?, label: String): T =
    value as? T ?: error("$label expected ${T::class.simpleName}, got ${value?.let { it::class.simpleName } ?: "null"}")

internal fun requireList(value: Any?, label: String): List<*> = requireType(value, label)
internal fun requireMap(value: Any?, label: String): Map<*, *> = requireType(value, label)

internal fun requireInt(value: Any?, label: String): Int = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is BLBigInt -> value.toInt()
    else -> error("$label expected integer, got ${value?.let { it::class.simpleName } ?: "null"}")
}

internal fun requireIntList(value: Any?, label: String): List<Int> {
    val list = requireList(value, label)
    return list.mapIndexed { index, item -> requireInt(item, "$label[$index]") }
}

/* =========================
 * Low-level helpers
 * ========================= */

private fun updateMax(target: AtomicInteger, value: Int) {
    while (true) {
        val current = target.get()
        if (value <= current) return
        if (target.compareAndSet(current, value)) return
    }
}

private fun Random.nextIntInclusive(from: Int, to: Int): Int {
    require(from <= to) { "Invalid range: [$from..$to]" }
    return from + nextInt(to - from + 1)
}
