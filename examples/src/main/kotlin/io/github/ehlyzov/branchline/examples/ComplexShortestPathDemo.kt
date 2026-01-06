package io.github.ehlyzov.branchline.examples

import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

public fun main() {
    runBlocking(Dispatchers.Default) {
        val graph = buildShortestPathGraph(Random(7), nodeCount = 2000, extraEdges = 4000)
        val metrics = TransformMetrics()
        val sharedStore = InstrumentedSharedStore(DefaultSharedStore(), metrics).apply {
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

            println("Task 1: Shortest path")
            println(
                "Nodes: ${graph.nodeCount} | Edges: ${graph.edges.size} | " +
                    "Start: ${graph.start} | Goal: ${graph.target}"
            )

            val totalStart = System.nanoTime()
            runPhase("Phase 1: Edge ingest", metrics) {
                val edgesByLevel = graph.edges.groupBy { graph.levels[it.src] }.toSortedMap()
                for ((_, edges) in edgesByLevel) {
                    coroutineScope {
                        val jobs = edges.map { edge ->
                            async {
                                val output = runTransform(edgeExec, edgeEnv(edge), metrics)
                                val toId = requireInt(output["dst"], "dst")
                                val distance = requireInt(output["distance"], "distance")
                                val parent = requireInt(output["parent"], "parent")
                                val distanceWritten = sharedStore.setOnce("distances", nodeKey(toId), distance)
                                if (distanceWritten) {
                                    sharedStore.setOnce("parents", nodeKey(toId), parent)
                                }
                            }
                        }
                        jobs.awaitAll()
                    }
                }
            }

            runPhase("Phase 2: Shortest path query", metrics) {
                val output = runTransform(queryExec, queryEnv(graph.start, graph.target), metrics)
                val distance = requireInt(output["distance"], "distance")
                val path = requireIntList(output["path"], "path")
                println("Distance: $distance | Path length: ${path.size}")
            }

            val totalMs = (System.nanoTime() - totalStart) / 1_000_000
            println("Total time: ${totalMs}ms")
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }
}
