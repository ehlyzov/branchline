package io.github.ehlyzov.branchline.integration

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
import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import io.github.ehlyzov.branchline.std.StdLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ComplexGraphTransformsTest {
    @Test
    public fun `shortest path uses shared single state`() = runBlocking(Dispatchers.Default) {
        val graph = buildShortestPathGraph(Random(0), nodeCount = 128, extraEdges = 256)
        val sharedStore = DefaultSharedStore().apply {
            addResource("distances", SharedResourceKind.SINGLE)
            addResource("parents", SharedResourceKind.SINGLE)
        }
        val previousStore = SharedStoreProvider.store
        SharedStoreProvider.store = sharedStore
        try {
            val context = buildProgramContext(SHORTEST_PATH_DSL)
            val edgeExec = buildExec(context, "EdgeIngest", sharedStore)
            val queryExec = buildExec(context, "ShortestPath", sharedStore)

            sharedStore.setOnce("distances", nodeKey(graph.start), 0)

            val edgesByLevel = graph.edges.groupBy { graph.levels[it.src] }.toSortedMap()
            for ((_, edges) in edgesByLevel) {
                val jobs = edges.map { edge ->
                    async {
                        val output = edgeExec.run(edgeEnv(edge)) as Map<*, *>
                        val toId = requireInt(output["dst"], "dst")
                        val distance = requireInt(output["distance"], "distance")
                        val parent = requireInt(output["parent"], "parent")
                        val distanceWritten = sharedStore.setOnce("distances", nodeKey(toId), distance)
                        if (distanceWritten) {
                            val parentWritten = sharedStore.setOnce("parents", nodeKey(toId), parent)
                            assertTrue(parentWritten, "parent already set for node $toId")
                        }
                    }
                }
                jobs.awaitAll()
            }

            val queryOutput = queryExec.run(queryEnv(graph.start, graph.target)) as Map<*, *>
            val distance = requireInt(queryOutput["distance"], "distance")
            val path = requireIntList(queryOutput["path"], "path")
            val expectedPath = buildPath(graph.start, graph.target, graph.parents)
            assertEquals(graph.levels[graph.target], distance)
            assertEquals(expectedPath, path)

            val distancesSnapshot = sharedStore.snapshot()["distances"].orEmpty()
            graph.levels.forEachIndexed { node, level ->
                val stored = distancesSnapshot[nodeKey(node)]
                    ?: error("Missing distance for node $node")
                assertEquals(level, requireInt(stored, "distances[$node]"))
            }
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }

    @Test
    public fun `weighted tree metrics combine root and leaf passes`() = runBlocking(Dispatchers.Default) {
        val tree = buildWeightedTree(Random(1), nodeCount = 12)
        val sharedStore = DefaultSharedStore().apply {
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

            runRootDistancePass(tree, rootExec, sharedStore)
            runLeafDistancePass(tree, leafExec, sharedStore)

            tree.edges.forEach { edge ->
                val output = metricsExec.run(edgeEnv(edge)) as Map<*, *>
                val fromId = requireInt(output["src"], "src")
                val toId = requireInt(output["dst"], "dst")
                val rootSum = requireInt(output["rootSum"], "rootSum")
                val leafSum = requireInt(output["leafSum"], "leafSum")
                assertEquals(tree.rootDistances[toId], rootSum)
                assertEquals(tree.leafDistances[fromId], leafSum)
            }

            val rootSnapshot = sharedStore.snapshot()["rootDistance"].orEmpty()
            tree.nodes.forEach { node ->
                val stored = rootSnapshot[nodeKey(node)]
                    ?: error("Missing rootDistance for node $node")
                assertEquals(tree.rootDistances[node], requireInt(stored, "rootDistance[$node]"))
            }

            val leafSnapshot = sharedStore.snapshot()["leafDistance"].orEmpty()
            tree.nodes.forEach { node ->
                val stored = leafSnapshot[nodeKey(node)]
                    ?: error("Missing leafDistance for node $node")
                assertEquals(tree.leafDistances[node], requireInt(stored, "leafDistance[$node]"))
            }
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }

    @Test
    public fun `reconstructs full tree from task 2 shared state`() = runBlocking(Dispatchers.Default) {
        val tree = buildWeightedTree(Random(2), nodeCount = 10)
        val sharedStore = DefaultSharedStore().apply {
            addResource("rootDistance", SharedResourceKind.SINGLE)
            addResource("leafDistance", SharedResourceKind.SINGLE)
            addResource("children", SharedResourceKind.SINGLE)
        }
        val previousStore = SharedStoreProvider.store
        SharedStoreProvider.store = sharedStore
        try {
            val weightedContext = buildProgramContext(WEIGHTED_TREE_DSL)
            val rootExec = buildExec(weightedContext, "RootDistanceIngest", sharedStore)
            val leafExec = buildExec(weightedContext, "LeafDistanceNode", sharedStore)

            runRootDistancePass(tree, rootExec, sharedStore)
            runLeafDistancePass(tree, leafExec, sharedStore)

            tree.nodes.forEach { nodeId ->
                val children = tree.childrenByParent[nodeId].orEmpty().map { edge ->
                    mapOf(
                        "id" to edge.dst,
                        "weight" to edge.weight,
                    )
                }
                val stored = sharedStore.setOnce("children", nodeKey(nodeId), children)
                assertTrue(stored, "children already set for node $nodeId")
            }

            val snapshotContext = buildProgramContext(TREE_SNAPSHOT_DSL)
            val snapshotExec = buildExec(snapshotContext, "TreeSnapshot", sharedStore)

            val snapshot = snapshotExec.run(rootEnv(tree.rootId)) as Map<*, *>
            val actual = parseTreeNode(snapshot["tree"])
            val expected = buildExpectedTree(tree, tree.rootId)
            assertEquals(expected, actual)
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }
}

private data class Edge(
    val src: Int,
    val dst: Int,
)

private data class WeightedEdge(
    val src: Int,
    val dst: Int,
    val weight: Int,
)

private data class GraphSpec(
    val start: Int,
    val target: Int,
    val edges: List<Edge>,
    val levels: IntArray,
    val parents: IntArray,
)

private data class WeightedTreeSpec(
    val rootId: Int,
    val nodes: List<Int>,
    val rootEdge: WeightedEdge,
    val edges: List<WeightedEdge>,
    val childrenByParent: Map<Int, List<WeightedEdge>>,
    val depthByNode: IntArray,
    val rootDistances: IntArray,
    val leafDistances: IntArray,
    val leafWeights: IntArray,
)

private data class ExpectedTreeEdge(
    val weight: Int,
    val node: ExpectedTreeNode,
)

private data class ExpectedTreeNode(
    val id: Int,
    val rootDistance: Int,
    val leafDistance: Int,
    val children: List<ExpectedTreeEdge>,
)

private data class ProgramContext(
    val funcs: Map<String, FuncDecl>,
    val transforms: Map<String, TransformDecl>,
    val hostFns: Map<String, (List<Any?>) -> Any?>,
    val registry: TransformRegistry,
)

private fun buildProgramContext(source: String): ProgramContext {
    val tokens = Lexer(source).lex()
    val program = Parser(tokens, source).parse()
    val funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    val transforms = program.decls.filterIsInstance<TransformDecl>().associateBy { transform ->
        transform.name ?: error("Transform name required for test program")
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

private fun buildExec(
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

private fun buildShortestPathGraph(
    random: Random,
    nodeCount: Int,
    extraEdges: Int,
): GraphSpec {
    val start = random.nextInt(nodeCount)
    val parents = IntArray(nodeCount) { -1 }
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
            parents[child] = parent
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
        start = start,
        target = target,
        edges = edges,
        levels = levels,
        parents = parents,
    )
}

private fun buildWeightedTree(
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
            val weight = random.nextInt(1, 9)
            leafWeights[node] = weight
        }
    }

    val rootEdge = WeightedEdge(src = 0, dst = rootId, weight = 0)
    val rootDistances = IntArray(nodeCount + 1) { -1 }
    rootDistances[rootId] = rootEdge.weight
    val rootQueue = ArrayDeque<Int>()
    rootQueue.add(rootId)
    while (rootQueue.isNotEmpty()) {
        val node = rootQueue.removeFirst()
        val base = rootDistances[node]
        childrenByParent[node].orEmpty().forEach { edge ->
            rootDistances[edge.dst] = base + edge.weight
            rootQueue.add(edge.dst)
        }
    }

    val leafDistances = IntArray(nodeCount + 1) { -1 }
    val nodesByDepth = nodes.sortedByDescending { depthByNode[it] }
    nodesByDepth.forEach { node ->
        val children = childrenByParent[node].orEmpty()
        if (children.isEmpty()) {
            leafDistances[node] = leafWeights[node]
        } else {
            var best = Int.MAX_VALUE
            children.forEach { edge ->
                val candidate = edge.weight + leafDistances[edge.dst]
                if (candidate < best) {
                    best = candidate
                }
            }
            leafDistances[node] = best
        }
    }

    return WeightedTreeSpec(
        rootId = rootId,
        nodes = nodes,
        rootEdge = rootEdge,
        edges = edges,
        childrenByParent = childrenByParent,
        depthByNode = depthByNode,
        rootDistances = rootDistances,
        leafDistances = leafDistances,
        leafWeights = leafWeights,
    )
}

private suspend fun runRootDistancePass(
    tree: WeightedTreeSpec,
    exec: Exec,
    sharedStore: SharedStore,
) {
    val rootOutput = exec.run(edgeEnv(tree.rootEdge)) as Map<*, *>
    val rootId = requireInt(rootOutput["dst"], "dst")
    val rootSum = requireInt(rootOutput["rootSum"], "rootSum")
    val rootStored = sharedStore.setOnce("rootDistance", nodeKey(rootId), rootSum)
    assertTrue(rootStored, "rootDistance already set for node $rootId")

    val edgesByDepth = tree.edges.groupBy { tree.depthByNode[it.src] }.toSortedMap()
    for ((_, edges) in edgesByDepth) {
        coroutineScope {
            val jobs = edges.map { edge ->
                async {
                    val output = exec.run(edgeEnv(edge)) as Map<*, *>
                    val toId = requireInt(output["dst"], "dst")
                    val rootSumEdge = requireInt(output["rootSum"], "rootSum")
                    val stored = sharedStore.setOnce("rootDistance", nodeKey(toId), rootSumEdge)
                    assertTrue(stored, "rootDistance already set for node $toId")
                }
            }
            jobs.awaitAll()
        }
    }
}

private suspend fun runLeafDistancePass(
    tree: WeightedTreeSpec,
    exec: Exec,
    sharedStore: SharedStore,
) {
    val nodesByDepth = tree.nodes.sortedByDescending { tree.depthByNode[it] }
    nodesByDepth.forEach { nodeId ->
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
        val output = exec.run(nodeEnv(nodeId, childEntries)) as Map<*, *>
        val outNode = requireInt(output["nodeId"], "nodeId")
        val leafSum = requireInt(output["leafSum"], "leafSum")
        val stored = sharedStore.setOnce("leafDistance", nodeKey(outNode), leafSum)
        assertTrue(stored, "leafDistance already set for node $outNode")
    }
}

private fun buildPath(start: Int, target: Int, parents: IntArray): List<Int> {
    val path = ArrayDeque<Int>()
    var node = target
    while (node != start) {
        path.addFirst(node)
        val parent = parents[node]
        require(parent >= 0) { "Missing parent for node $node" }
        node = parent
    }
    path.addFirst(start)
    return path.toList()
}

private fun buildExpectedTree(tree: WeightedTreeSpec, nodeId: Int): ExpectedTreeNode {
    val childrenEdges = tree.childrenByParent[nodeId].orEmpty()
    val children = childrenEdges.map { edge ->
        ExpectedTreeEdge(
            weight = edge.weight,
            node = buildExpectedTree(tree, edge.dst),
        )
    }
    return ExpectedTreeNode(
        id = nodeId,
        rootDistance = tree.rootDistances[nodeId],
        leafDistance = tree.leafDistances[nodeId],
        children = children,
    )
}

private fun parseTreeNode(value: Any?): ExpectedTreeNode {
    val nodeMap = requireMap(value, "tree")
    val id = requireInt(nodeMap["id"], "id")
    val rootDistance = requireInt(nodeMap["rootDistance"], "rootDistance")
    val leafDistance = requireInt(nodeMap["leafDistance"], "leafDistance")
    val childrenList = requireList(nodeMap["children"], "children")
    val children = childrenList.mapIndexed { index, entry ->
        val childMap = requireMap(entry, "children[$index]")
        val weight = requireInt(childMap["weight"], "children[$index].weight")
        val node = parseTreeNode(childMap["node"])
        ExpectedTreeEdge(weight = weight, node = node)
    }
    return ExpectedTreeNode(
        id = id,
        rootDistance = rootDistance,
        leafDistance = leafDistance,
        children = children,
    )
}

private fun edgeEnv(edge: Edge): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "src" to edge.src,
            "dst" to edge.dst,
        ),
    )
}

private fun edgeEnv(edge: WeightedEdge): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "src" to edge.src,
            "dst" to edge.dst,
            "weight" to edge.weight,
        ),
    )
}

private fun nodeEnv(nodeId: Int, children: List<Map<String, Any?>>): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "nodeId" to nodeId,
            "children" to children,
        ),
    )
}

private fun queryEnv(source: Int, target: Int): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "start" to source,
            "goal" to target,
        ),
    )
}

private fun rootEnv(rootId: Int): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "root" to rootId,
        ),
    )
}

private fun nodeKey(id: Int): String = "node:$id"

private fun requireInt(value: Any?, label: String): Int {
    return when (value) {
        is Int -> value
        is Long -> value.toInt()
        is BLBigInt -> value.toInt()
        else -> error("$label expected integer, got ${value?.let { it::class.simpleName } ?: "null"}")
    }
}

private fun requireIntList(value: Any?, label: String): List<Int> {
    val list = value as? List<*> ?: error("$label expected list")
    return list.mapIndexed { index, item ->
        requireInt(item, "$label[$index]")
    }
}

private fun requireList(value: Any?, label: String): List<*> =
    value as? List<*> ?: error("$label expected list")

private fun requireMap(value: Any?, label: String): Map<*, *> =
    value as? Map<*, *> ?: error("$label expected map")

private val SHORTEST_PATH_DSL = """
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

private val WEIGHTED_TREE_DSL = """
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

private val TREE_SNAPSHOT_DSL = """
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
