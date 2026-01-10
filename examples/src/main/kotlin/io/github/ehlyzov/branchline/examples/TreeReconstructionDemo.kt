package io.github.ehlyzov.branchline.examples

import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

public fun main() {
    runBlocking(Dispatchers.Default) {
        val tree = buildWeightedTree(Random(23), nodeCount = 256)
        val metrics = TransformMetrics()
        val sharedStore = InstrumentedSharedStore(DefaultSharedStore(), metrics).apply {
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
            val snapshotContext = buildProgramContext(TREE_SNAPSHOT_DSL)
            val snapshotExec = buildExec(snapshotContext, "TreeSnapshot", sharedStore)

            println("Task 3: Full tree reconstruction")
            println("Nodes: ${tree.nodeCount} | Edges: ${tree.edges.size} | Root: ${tree.rootId}")

            val totalStart = System.nanoTime()
            runPhase("Phase 1: Root distance pass", metrics) {
                runRootDistancePass(tree, rootExec, sharedStore, metrics)
            }

            runPhase("Phase 2: Leaf distance pass", metrics) {
                runLeafDistancePass(tree, leafExec, sharedStore, metrics)
            }

            runPhase("Phase 3: Store children adjacency", metrics) {
                tree.nodes.forEach { nodeId ->
                    val children = tree.childrenByParent[nodeId].orEmpty().map { edge ->
                        mapOf(
                            "id" to edge.dst,
                            "weight" to edge.weight,
                        )
                    }
                    sharedStore.setOnce("children", nodeKey(nodeId), children)
                }
            }

            runPhase("Phase 4: Snapshot tree", metrics) {
                val output = runTransform(snapshotExec, rootEnv(tree.rootId), metrics)
                val treeMap = requireMap(output["tree"], "tree")
                val rootDistance = requireInt(treeMap["rootDistance"], "rootDistance")
                val leafDistance = requireInt(treeMap["leafDistance"], "leafDistance")
                val nodeCount = countTreeNodes(treeMap)
                println("Root distances: rootSum=$rootDistance | leafSum=$leafDistance")
                println("Tree nodes reconstructed: $nodeCount")
            }

            val totalMs = (System.nanoTime() - totalStart) / 1_000_000
            println("Total time: ${totalMs}ms")
        } finally {
            SharedStoreProvider.store = previousStore
        }
    }
}
