package io.github.ehlyzov.branchline.examples

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.ir.TransformRegistry
import io.github.ehlyzov.branchline.ir.buildTransformDescriptors
import io.github.ehlyzov.branchline.ir.makeEval
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.StdLib
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class Edge(
    val src: Int,
    val dst: Int,
)

internal data class WeightedEdge(
    val src: Int,
    val dst: Int,
    val weight: Int,
)

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

internal class TransformMetrics {
    private val running = AtomicInteger(0)
    private val awaiting = AtomicInteger(0)
    private val maxRunning = AtomicInteger(0)
    private val maxAwaiting = AtomicInteger(0)

    internal fun onTransformStart() {
        val current = running.incrementAndGet()
        updateMax(maxRunning, current)
    }

    internal fun onTransformEnd() {
        running.decrementAndGet()
    }

    internal fun onAwaitStart() {
        val current = awaiting.incrementAndGet()
        updateMax(maxAwaiting, current)
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
) : SharedStore {
    override suspend fun get(resource: String, key: String): Any? =
        delegate.get(resource, key)

    override suspend fun setOnce(resource: String, key: String, value: Any?): Boolean =
        delegate.setOnce(resource, key, value)

    override suspend fun put(resource: String, key: String, value: Any?) {
        delegate.put(resource, key, value)
    }

    override fun snapshot(): Map<String, Map<String, Any?>> = delegate.snapshot()

    override suspend fun commit(
        snapshot: Map<String, Map<String, Any?>>,
        delta: Map<String, Map<String, Any?>>,
    ) {
        delegate.commit(snapshot, delta)
    }

    override suspend fun await(resource: String, key: String): Any? {
        val existing = delegate.get(resource, key)
        if (existing != null) {
            return existing
        }
        metrics.onAwaitStart()
        return try {
            delegate.await(resource, key)
        } finally {
            metrics.onAwaitEnd()
        }
    }

    override fun hasResource(resource: String): Boolean = delegate.hasResource(resource)

    override fun addResource(resource: String, kind: SharedResourceKind) {
        delegate.addResource(resource, kind)
    }
}

internal data class ProgramContext(
    val funcs: Map<String, FuncDecl>,
    val transforms: Map<String, TransformDecl>,
    val hostFns: Map<String, (List<Any?>) -> Any?>,
    val registry: TransformRegistry,
)

internal fun buildProgramContext(source: String): ProgramContext {
    val tokens = Lexer(source).lex()
    val program = Parser(tokens, source).parse()
    val funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    val transforms = program.decls.filterIsInstance<TransformDecl>().associateBy { transform ->
        transform.name ?: error("Transform name required for example program")
    }
    val typeDecls = program.decls.filterIsInstance<TypeDecl>()
    val hostFns = StdLib.fns
    val descriptors = buildTransformDescriptors(transforms.values.toList(), typeDecls, hostFns.keys)
    val registry = TransformRegistry(funcs, hostFns, descriptors)
    return ProgramContext(
        funcs = funcs,
        transforms = transforms,
        hostFns = hostFns,
        registry = registry,
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
    val eval = makeEval(
        hostFns = context.hostFns,
        funcs = context.funcs,
        reg = context.registry,
        sharedStore = sharedStore,
    )
    return Exec(ir, eval)
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

internal suspend fun runPhase(
    label: String,
    metrics: TransformMetrics,
    block: suspend () -> Unit,
) {
    metrics.resetMax()
    val start = System.nanoTime()
    block()
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    val snapshot = metrics.snapshot()
    println("$label: ${elapsedMs}ms | running=${snapshot.maxRunning} waiting=${snapshot.maxAwaiting}")
}

internal fun buildShortestPathGraph(
    random: Random,
    nodeCount: Int,
    extraEdges: Int,
): GraphSpec {
    val start = random.nextInt(nodeCount)
    val levels = IntArray(nodeCount) { -1 }
    val childrenByParent = mutableMapOf<Int, MutableList<Int>>()
    val remainingNodes = (0 until nodeCount).filter { it != start }.toMutableList()
    val queue = ArrayDeque<Int>()
    levels[start] = 0
    queue.add(start)
    var ensuredBranch = false

    while (remainingNodes.isNotEmpty()) {
        val parent = queue.removeFirst()
        val remaining = remainingNodes.size
        val maxChildren = minOf(3, remaining)
        val minChildren = if (!ensuredBranch && parent == start && remaining >= 2) 2 else 1
        val childCount = minChildren + random.nextInt(maxChildren - minChildren + 1)
        repeat(childCount) {
            val index = random.nextInt(remainingNodes.size)
            val child = remainingNodes.removeAt(index)
            levels[child] = levels[parent] + 1
            childrenByParent.getOrPut(parent) { mutableListOf() }.add(child)
            queue.add(child)
        }
        ensuredBranch = true
    }

    val edges = mutableListOf<Edge>()
    childrenByParent.forEach { (parent, childList) ->
        childList.forEach { child ->
            edges.add(Edge(src = parent, dst = child))
        }
    }

    val edgeSet = edges.toMutableSet()
    val targetEdges = edges.size + extraEdges
    var attempts = 0
    while (edgeSet.size < targetEdges && attempts < extraEdges * 10) {
        val src = random.nextInt(nodeCount)
        val dst = random.nextInt(nodeCount)
        if (src == dst) {
            attempts += 1
            continue
        }
        if (levels[dst] > levels[src]) {
            attempts += 1
            continue
        }
        val edge = Edge(src = src, dst = dst)
        if (edgeSet.add(edge)) {
            edges.add(edge)
        }
        attempts += 1
    }

    val target = if (nodeCount > 1) {
        val pick = random.nextInt(nodeCount - 1)
        if (pick >= start) pick + 1 else pick
    } else {
        start
    }

    return GraphSpec(
        nodeCount = nodeCount,
        start = start,
        target = target,
        edges = edges,
        levels = levels,
    )
}

internal fun buildWeightedTree(
    random: Random,
    nodeCount: Int,
): WeightedTreeSpec {
    val rootId = 1
    val nodes = (1..nodeCount).toList()
    val depthByNode = IntArray(nodeCount + 1) { -1 }
    val childrenByParent = mutableMapOf<Int, MutableList<WeightedEdge>>()
    val edges = mutableListOf<WeightedEdge>()
    val queue = ArrayDeque<Int>()
    depthByNode[rootId] = 0
    queue.add(rootId)
    var nextNode = rootId + 1
    var ensuredBranch = false

    while (nextNode <= nodeCount) {
        val parent = queue.removeFirst()
        val remaining = nodeCount - nextNode + 1
        if (remaining <= 0) continue
        val maxChildren = minOf(3, remaining)
        val minChildren = if (!ensuredBranch && parent == rootId && remaining >= 2) 2 else 1
        val childCount = minChildren + random.nextInt(maxChildren - minChildren + 1)
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
        ensuredBranch = true
    }

    val leafWeights = IntArray(nodeCount + 1)
    nodes.forEach { node ->
        val children = childrenByParent[node].orEmpty()
        if (children.isEmpty()) {
            leafWeights[node] = random.nextInt(1, 9)
        }
    }

    val rootEdge = WeightedEdge(src = 0, dst = rootId, weight = 0)
    return WeightedTreeSpec(
        nodeCount = nodeCount,
        rootId = rootId,
        nodes = nodes,
        rootEdge = rootEdge,
        edges = edges,
        childrenByParent = childrenByParent,
        depthByNode = depthByNode,
        leafWeights = leafWeights,
    )
}

internal suspend fun runRootDistancePass(
    tree: WeightedTreeSpec,
    exec: Exec,
    sharedStore: SharedStore,
    metrics: TransformMetrics,
) {
    val rootOutput = runTransform(exec, edgeEnv(tree.rootEdge), metrics)
    val rootId = requireInt(rootOutput["dst"], "dst")
    val rootSum = requireInt(rootOutput["rootSum"], "rootSum")
    sharedStore.setOnce("rootDistance", nodeKey(rootId), rootSum)

    val edgesByDepth = tree.edges.groupBy { tree.depthByNode[it.src] }.toSortedMap()
    for ((_, edges) in edgesByDepth) {
        coroutineScope {
            val jobs = edges.map { edge ->
                async {
                    val output = runTransform(exec, edgeEnv(edge), metrics)
                    val toId = requireInt(output["dst"], "dst")
                    val rootSumEdge = requireInt(output["rootSum"], "rootSum")
                    sharedStore.setOnce("rootDistance", nodeKey(toId), rootSumEdge)
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
    val depthKeys = nodesByDepth.keys.sortedDescending()
    for (depth in depthKeys) {
        val nodesAtDepth = nodesByDepth[depth].orEmpty()
        coroutineScope {
            val jobs = nodesAtDepth.map { nodeId ->
                async {
                    val children = tree.childrenByParent[nodeId].orEmpty().map { edge ->
                        mapOf(
                            "id" to edge.dst,
                            "weight" to edge.weight,
                        )
                    }
                    val childEntries = if (children.isEmpty()) {
                        listOf(
                            mapOf(
                                "id" to 0,
                                "weight" to tree.leafWeights[nodeId],
                            )
                        )
                    } else {
                        children
                    }
                    val output = runTransform(exec, nodeEnv(nodeId, childEntries), metrics)
                    val outNode = requireInt(output["nodeId"], "nodeId")
                    val leafSum = requireInt(output["leafSum"], "leafSum")
                    sharedStore.setOnce("leafDistance", nodeKey(outNode), leafSum)
                }
            }
            jobs.awaitAll()
        }
    }
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

internal fun edgeEnv(edge: Edge): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "src" to edge.src,
            "dst" to edge.dst,
        ),
    )
}

internal fun edgeEnv(edge: WeightedEdge): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "src" to edge.src,
            "dst" to edge.dst,
            "weight" to edge.weight,
        ),
    )
}

internal fun nodeEnv(nodeId: Int, children: List<Map<String, Any?>>): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "nodeId" to nodeId,
            "children" to children,
        ),
    )
}

internal fun queryEnv(source: Int, target: Int): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "start" to source,
            "goal" to target,
        ),
    )
}

internal fun rootEnv(rootId: Int): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "root" to rootId,
        ),
    )
}

internal fun nodeKey(id: Int): String = "node:$id"

internal fun requireInt(value: Any?, label: String): Int {
    return when (value) {
        is Int -> value
        is Long -> value.toInt()
        is BLBigInt -> value.toInt()
        else -> error("$label expected integer, got ${value?.let { it::class.simpleName } ?: "null"}")
    }
}

internal fun requireIntList(value: Any?, label: String): List<Int> {
    val list = value as? List<*> ?: error("$label expected list")
    return list.mapIndexed { index, item ->
        requireInt(item, "$label[$index]")
    }
}

internal fun requireList(value: Any?, label: String): List<*> =
    value as? List<*> ?: error("$label expected list")

internal fun requireMap(value: Any?, label: String): Map<*, *> =
    value as? Map<*, *> ?: error("$label expected map")

internal val SHORTEST_PATH_DSL = """
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

internal val WEIGHTED_TREE_DSL = """
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

internal val TREE_SNAPSHOT_DSL = """
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

private fun updateMax(target: AtomicInteger, value: Int) {
    while (true) {
        val current = target.get()
        if (value <= current) return
        if (target.compareAndSet(current, value)) return
    }
}
