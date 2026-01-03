package io.github.ehlyzov.branchline.shared

import kotlinx.coroutines.*
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.std.*
import io.github.ehlyzov.branchline.ir.*

/**
 * Tree Reconstruction Example using SHARED SINGLE memory
 * 
 * This example demonstrates how to reconstruct colored trees from edge messages.
 * Input: Stream of (treeId, fromId, toId) messages representing tree edges
 * Output: Reconstructed colored trees where:
 *   - Level 1: RED edges
 *   - Level 2: BLACK edges  
 *   - Level 3+: Alternating RED/BLACK pattern
 *
 * Uses SHARED SINGLE storage to coordinate between coroutines processing different messages.
 * Each coroutine waits for parent nodes using 'await SharedState.[key]' before building the tree.
 */

// Sample tree data structures
data class TreeNode(
    val id: String,
    val children: MutableList<TreeNode> = mutableListOf(),
    var parent: TreeNode? = null,
    var level: Int = 0
) {
    val color: String get() = when (level % 2) {
        1 -> "RED"
        0 -> "BLACK"  // level 2, 4, 6...
        else -> "BLACK"
    }
}

data class TreeEdge(val treeId: String, val fromId: String, val toId: String)

/**
 * Example BL DSL syntax for tree reconstruction transform
 * (This would be the actual DSL code that users write)
 */
val treeReconstructionDSL = """
// branchline 0.3
SHARED nodeParents SINGLE;
SHARED treeTops SINGLE;
SHARED completedTrees MANY;

TRANSFORM TreeReconstruction { LET message = $;
    LET treeId = message.treeId;
    LET fromId = message.fromId;
    LET toId = message.toId;
    
    // Wait for parent node to be processed if this isn't a root
    IF fromId != "root" THEN {
        LET parentKey = treeId ++ ":" ++ fromId;
        LET parentInfo = await nodeParents.parentKey;
        
        // Create child node with proper level and color
        LET childLevel = parentInfo.level + 1;
        LET childColor = IF childLevel % 2 == 1 THEN "RED" ELSE "BLACK";
        
        OUTPUT {
            type: "node_created",
            treeId: treeId,
            nodeId: toId,
            parentId: fromId,
            level: childLevel,
            color: childColor
        };
    } ELSE {
        // Root node - level 1, always RED
        OUTPUT {
            type: "root_created", 
            treeId: treeId,
            nodeId: toId,
            level: 1,
            color: "RED"
        };
    };
    
    // Register this node for future children to await
    LET nodeKey = treeId ++ ":" ++ toId;
    LET nodeLevel = IF fromId == "root" THEN 1 ELSE (await nodeParents.(treeId ++ ":" ++ fromId)).level + 1;
    
    // Store node info using SHARED SINGLE (write-once semantics)
    SET nodeParents[nodeKey] = {
        nodeId: toId,
        level: nodeLevel,
        treeId: treeId
    };
}
"""

class TreeReconstructionDemo {
    
    fun runExample() {
        println("=== Tree Reconstruction with SHARED SINGLE Example ===")
        
        // Create sample tree data: depth 5-10, branching factor 3-5
        val sampleEdges = generateSampleTree("tree1", maxDepth = 6, branchingFactor = 3)
        val shuffledEdges = sampleEdges.shuffled() // Simulate out-of-order message delivery
        
        println("Generated ${sampleEdges.size} tree edges (shuffled for realistic message delivery)")
        
        // Set up shared store with SINGLE semantics
        val sharedStore = DefaultSharedStore().apply {
            addResource("nodeParents", SharedResourceKind.SINGLE)
            addResource("treeTops", SharedResourceKind.SINGLE)
            addResource("completedTrees", SharedResourceKind.MANY)
        }
        
        // Process edges using coroutines
        runBlocking {
            val results = processTreeEdgesWithCoroutines(shuffledEdges, sharedStore)
            
            println("\n=== Tree Reconstruction Results ===")
            results.forEach { result ->
                println("${result["type"]}: treeId=${result["treeId"]}, nodeId=${result["nodeId"]}, " +
                       "level=${result["level"]}, color=${result["color"]}")
            }
            
            // Verify tree structure
            val reconstructedTree = buildTreeFromResults(results)
            println("\n=== Reconstructed Tree Structure ===")
            printTree(reconstructedTree)
            
            println("\n=== Color Pattern Verification ===")
            verifyColorPattern(reconstructedTree)
        }
    }
    
    private suspend fun processTreeEdgesWithCoroutines(
        edges: List<TreeEdge>,
        sharedStore: SharedStore
    ): List<Map<String, Any?>> = coroutineScope {
        
        val results = mutableListOf<Map<String, Any?>>()
        val jobs = edges.map { edge ->
            async {
                processTreeEdge(edge, sharedStore)
            }
        }
        
        // Collect all results
        jobs.awaitAll().forEach { result ->
            results.add(result)
        }
        
        results.sortedBy { it["nodeId"].toString() }
    }
    
    private suspend fun processTreeEdge(
        edge: TreeEdge,
        sharedStore: SharedStore
    ): Map<String, Any?> {
        
        val nodeLevel = if (edge.fromId == "root") {
            // Root node
            1
        } else {
            // Wait for parent node using SHARED SINGLE semantics
            val parentKey = "${edge.treeId}:${edge.fromId}"
            val parentInfo = sharedStore.await("nodeParents", parentKey) as Map<String, Any?>
            (parentInfo["level"] as Int) + 1
        }
        
        val nodeColor = if (nodeLevel % 2 == 1) "RED" else "BLACK"
        
        // Store this node info for future children (SINGLE semantics - write once)
        val nodeKey = "${edge.treeId}:${edge.toId}"
        val nodeInfo = mapOf(
            "nodeId" to edge.toId,
            "level" to nodeLevel,
            "treeId" to edge.treeId,
            "parentId" to edge.fromId
        )
        
        // This will only succeed on first write due to SINGLE semantics
        sharedStore.setOnce("nodeParents", nodeKey, nodeInfo)
        
        return mapOf(
            "type" to if (edge.fromId == "root") "root_created" else "node_created",
            "treeId" to edge.treeId,
            "nodeId" to edge.toId,
            "parentId" to edge.fromId,
            "level" to nodeLevel,
            "color" to nodeColor
        )
    }
    
    private fun generateSampleTree(treeId: String, maxDepth: Int, branchingFactor: Int): List<TreeEdge> {
        val edges = mutableListOf<TreeEdge>()
        var nodeCounter = 1
        
        fun generateSubtree(parentId: String, currentDepth: Int) {
            if (currentDepth >= maxDepth) return
            
            val numChildren = (1..branchingFactor).random()
            repeat(numChildren) {
                val childId = "node${nodeCounter++}"
                edges.add(TreeEdge(treeId, parentId, childId))
                generateSubtree(childId, currentDepth + 1)
            }
        }
        
        // Create root
        val rootId = "node${nodeCounter++}"
        edges.add(TreeEdge(treeId, "root", rootId))
        generateSubtree(rootId, 1)
        
        return edges
    }
    
    private fun buildTreeFromResults(results: List<Map<String, Any?>>): TreeNode {
        val nodes = mutableMapOf<String, TreeNode>()
        var root: TreeNode? = null
        
        // Create all nodes
        results.forEach { result ->
            val nodeId = result["nodeId"] as String
            val level = result["level"] as Int
            val node = TreeNode(nodeId, level = level)
            nodes[nodeId] = node
            
            if (result["type"] == "root_created") {
                root = node
            }
        }
        
        // Build parent-child relationships
        results.forEach { result ->
            val nodeId = result["nodeId"] as String
            val parentId = result["parentId"] as String
            
            if (parentId != "root") {
                val node = nodes[nodeId]!!
                val parent = nodes[parentId]!!
                node.parent = parent
                parent.children.add(node)
            }
        }
        
        return root ?: throw IllegalStateException("No root node found")
    }
    
    private fun printTree(node: TreeNode, indent: String = "") {
        println("$indent${node.id} (Level ${node.level}, Color: ${node.color})")
        node.children.forEach { child ->
            printTree(child, "$indent  ")
        }
    }
    
    private fun verifyColorPattern(node: TreeNode, expectedLevel: Int = 1) {
        val expectedColor = if (expectedLevel % 2 == 1) "RED" else "BLACK"
        
        if (node.level != expectedLevel || node.color != expectedColor) {
            println("❌ Color pattern error at ${node.id}: expected level=$expectedLevel, color=$expectedColor, " +
                   "but got level=${node.level}, color=${node.color}")
        } else {
            println("✅ Node ${node.id}: Level ${node.level}, Color ${node.color} - Correct!")
        }
        
        node.children.forEach { child ->
            verifyColorPattern(child, expectedLevel + 1)
        }
    }
}

fun main() {
    TreeReconstructionDemo().runExample()
}
