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
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ComplexGraphTransformsTest {
    @Test
    public fun `shortest path uses shared single state`() = runBlocking(Dispatchers.Default) {
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
            val source = 1
            val target = 6

            sharedStore.setOnce("distances", nodeKey(source), 0)

            val edges = listOf(
                Edge(src = 2, dst = 4),
                Edge(src = 4, dst = 6),
                Edge(src = 1, dst = 2),
                Edge(src = 1, dst = 3),
                Edge(src = 3, dst = 5),
            ).shuffled(Random(0))

            val jobs = edges.map { edge ->
                async {
                    val output = edgeExec.run(edgeEnv(edge)) as Map<*, *>
                    val toId = requireInt(output["dst"], "dst")
                    val distance = requireInt(output["distance"], "distance")
                    val parent = requireInt(output["parent"], "parent")
                    val distanceWritten = sharedStore.setOnce("distances", nodeKey(toId), distance)
                    val parentWritten = sharedStore.setOnce("parents", nodeKey(toId), parent)
                    assertTrue(distanceWritten, "distance already set for node $toId")
                    assertTrue(parentWritten, "parent already set for node $toId")
                }
            }
            jobs.awaitAll()

            val queryOutput = queryExec.run(queryEnv(source, target)) as Map<*, *>
            val distance = requireInt(queryOutput["distance"], "distance")
            val path = requireIntList(queryOutput["path"], "path")
            assertEquals(3, distance)
            assertEquals(listOf(1, 2, 4, 6), path)
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }

    @Test
    public fun `weighted tree metrics combine root and leaf passes`() = runBlocking(Dispatchers.Default) {
        val sharedStore = DefaultSharedStore().apply {
            addResource("rootDistance", SharedResourceKind.SINGLE)
            addResource("leafDistance", SharedResourceKind.SINGLE)
        }
        val previousStore = SharedStoreProvider.store
        SharedStoreProvider.store = sharedStore
        try {
            val context = buildProgramContext(WEIGHTED_TREE_DSL)
            val rootExec = buildExec(context, "RootDistanceIngest", sharedStore)
            val leafExec = buildExec(context, "LeafDistanceIngest", sharedStore)
            val metricsExec = buildExec(context, "EdgeMetrics", sharedStore)

            val edges = listOf(
                WeightedEdge(src = 0, dst = 1, weight = 0),
                WeightedEdge(src = 1, dst = 2, weight = 5),
                WeightedEdge(src = 2, dst = 3, weight = 7),
                WeightedEdge(src = 3, dst = 0, weight = 11),
            )

            val rootJobs = edges.shuffled(Random(1)).map { edge ->
                async {
                    val output = rootExec.run(edgeEnv(edge)) as Map<*, *>
                    val toId = requireInt(output["dst"], "dst")
                    val rootSum = requireInt(output["rootSum"], "rootSum")
                    val stored = sharedStore.setOnce("rootDistance", nodeKey(toId), rootSum)
                    assertTrue(stored, "rootDistance already set for node $toId")
                }
            }
            rootJobs.awaitAll()

            val leafJobs = edges.shuffled(Random(2)).map { edge ->
                async {
                    val output = leafExec.run(edgeEnv(edge)) as Map<*, *>
                    val fromId = requireInt(output["src"], "src")
                    val leafSum = requireInt(output["leafSum"], "leafSum")
                    val stored = sharedStore.setOnce("leafDistance", nodeKey(fromId), leafSum)
                    assertTrue(stored, "leafDistance already set for node $fromId")
                }
            }
            leafJobs.awaitAll()

            val expected = mapOf(
                EdgeKey(src = 0, dst = 1) to ExpectedMetrics(rootSum = 0, leafSum = 23),
                EdgeKey(src = 1, dst = 2) to ExpectedMetrics(rootSum = 5, leafSum = 23),
                EdgeKey(src = 2, dst = 3) to ExpectedMetrics(rootSum = 12, leafSum = 18),
                EdgeKey(src = 3, dst = 0) to ExpectedMetrics(rootSum = 23, leafSum = 11),
            )

            edges.forEach { edge ->
                val output = metricsExec.run(edgeEnv(edge)) as Map<*, *>
                val fromId = requireInt(output["src"], "src")
                val toId = requireInt(output["dst"], "dst")
                val rootSum = requireInt(output["rootSum"], "rootSum")
                val leafSum = requireInt(output["leafSum"], "leafSum")
                val expectedMetrics = expected[EdgeKey(src = fromId, dst = toId)]
                    ?: error("Missing expected metrics for edge ($fromId, $toId)")
                assertEquals(expectedMetrics.rootSum, rootSum)
                assertEquals(expectedMetrics.leafSum, leafSum)
            }
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }

    @Test
    public fun `rebuilds graph from streamed edges`() = runBlocking(Dispatchers.Default) {
        val sharedStore = DefaultSharedStore().apply {
            addResource("graph", SharedResourceKind.SINGLE)
        }
        val previousStore = SharedStoreProvider.store
        SharedStoreProvider.store = sharedStore
        try {
            val context = buildProgramContext(GRAPH_REBUILD_DSL)
            val rebuildExec = buildExec(context, "GraphRebuild", sharedStore)
            val snapshotExec = buildExec(context, "GraphSnapshot", sharedStore)

            val edges = listOf(
                WeightedEdge(src = 0, dst = 1, weight = 0),
                WeightedEdge(src = 1, dst = 2, weight = 5),
                WeightedEdge(src = 2, dst = 3, weight = 7),
                WeightedEdge(src = 3, dst = 0, weight = 11),
            )

            edges.forEach { edge ->
                val output = rebuildExec.run(edgeEnv(edge)) as Map<*, *>
                val fromId = requireInt(output["src"], "src")
                val toId = requireInt(output["dst"], "dst")
                val weight = requireInt(output["weight"], "weight")
                val stored = sharedStore.setOnce(
                    "graph",
                    nodeKey(fromId),
                    mapOf(
                        "to" to toId,
                        "weight" to weight,
                    ),
                )
                assertTrue(stored, "graph already set for node $fromId")
            }

            val snapshot = snapshotExec.run(nodesEnv(listOf(0, 1, 2, 3))) as Map<*, *>
            val graph = requireList(snapshot["graph"], "graph")
            val actual = LinkedHashMap<Int, WeightedEdgeInfo>(graph.size)
            for (entry in graph) {
                val entryMap = requireMap(entry, "graph entry")
                val fromId = requireInt(entryMap["src"], "src")
                val edgeMap = requireMap(entryMap["edge"], "edge")
                val toId = requireInt(edgeMap["to"], "edge.to")
                val weight = requireInt(edgeMap["weight"], "edge.weight")
                actual[fromId] = WeightedEdgeInfo(to = toId, weight = weight)
            }

            val expected = linkedMapOf(
                0 to WeightedEdgeInfo(to = 1, weight = 0),
                1 to WeightedEdgeInfo(to = 2, weight = 5),
                2 to WeightedEdgeInfo(to = 3, weight = 7),
                3 to WeightedEdgeInfo(to = 0, weight = 11),
            )
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

private data class EdgeKey(
    val src: Int,
    val dst: Int,
)

private data class ExpectedMetrics(
    val rootSum: Int,
    val leafSum: Int,
)

private data class WeightedEdgeInfo(
    val to: Int,
    val weight: Int,
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

private fun queryEnv(source: Int, target: Int): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "start" to source,
            "goal" to target,
        ),
    )
}

private fun nodesEnv(nodes: List<Int>): MutableMap<String, Any?> {
    return mutableMapOf(
        "row" to mapOf(
            "nodes" to nodes,
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

    TRANSFORM LeafDistanceIngest { LET edge = row;
        LET fromId = edge.src;
        LET toId = edge.dst;
        LET weight = edge.weight;
        LET leafSum = IF toId == 0 THEN weight ELSE (AWAIT_SHARED("leafDistance", NodeKey(toId)) + weight);
        OUTPUT {
            src: fromId,
            dst: toId,
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

private val GRAPH_REBUILD_DSL = """
    SHARED graph SINGLE;

    FUNC NodeKey(id) {
        RETURN "node:" + id;
    }

    TRANSFORM GraphRebuild { LET edge = row;
        LET fromId = edge.src;
        LET toId = edge.dst;
        LET weight = edge.weight;
        OUTPUT {
            src: fromId,
            dst: toId,
            weight: weight
        };
    }

    TRANSFORM GraphSnapshot { LET query = row;
        LET nodes = query.nodes;
        LET entries = [ { src: n, edge: AWAIT_SHARED("graph", NodeKey(n)) } FOR EACH n IN nodes ];
        OUTPUT { graph: entries };
    }
""".trimIndent()
