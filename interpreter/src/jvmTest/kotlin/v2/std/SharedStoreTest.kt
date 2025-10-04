package v2.std

import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.buildMap
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SharedStoreTest {

    private lateinit var store: DefaultSharedStore

    @BeforeEach
    fun setUp() {
        store = DefaultSharedStore()
    }

    @Test
    fun `test addResource creates resources with correct semantics`() {
        store.addResource("single_resource", SharedResourceKind.SINGLE)
        store.addResource("many_resource", SharedResourceKind.MANY)

        assertTrue(store.hasResource("single_resource"))
        assertTrue(store.hasResource("many_resource"))
        assertFalse(store.hasResource("nonexistent"))
    }

    @Test
    fun `test SINGLE semantics - write once behavior`() = runTest {
        store.addResource("test_single", SharedResourceKind.SINGLE)

        // First write should succeed
        val firstWrite = store.setOnce("test_single", "key1", "value1")
        assertTrue(firstWrite)

        // Subsequent writes should be ignored
        val secondWrite = store.setOnce("test_single", "key1", "value2")
        assertFalse(secondWrite)

        // Value should remain the first written value
        val retrieved = store.get("test_single", "key1")
        assertEquals("value1", retrieved)
    }

    @Test
    fun `test MANY semantics - write many behavior`() = runTest {
        store.addResource("test_many", SharedResourceKind.MANY)

        // Multiple writes should all succeed and overwrite
        store.put("test_many", "key1", "value1")
        assertEquals("value1", store.get("test_many", "key1"))

        store.put("test_many", "key1", "value2")
        assertEquals("value2", store.get("test_many", "key1"))

        store.put("test_many", "key1", "value3")
        assertEquals("value3", store.get("test_many", "key1"))
    }

    @Test
    fun `test await functionality - immediate return if value exists`() = runTest {
        store.addResource("test_resource", SharedResourceKind.SINGLE)

        // Pre-populate with a value
        store.setOnce("test_resource", "existing_key", "existing_value")

        // await should return immediately
        val value = store.await("test_resource", "existing_key")
        assertEquals("existing_value", value)
    }

    @Test
    fun `test await functionality - waits for value to appear`() = runTest {
        store.addResource("test_resource", SharedResourceKind.SINGLE)
        
        var awaitedValue: Any? = null
        
        // Launch a coroutine that waits for the value
        val job = launch {
            awaitedValue = store.await("test_resource", "future_key")
        }
        
        // Give the await coroutine time to start
        delay(100)
        
        // Now provide the value
        store.setOnce("test_resource", "future_key", "future_value")
        
        // Wait for completion
        job.join()
        assertEquals("future_value", awaitedValue)
    }

    @Test
    fun `test multiple coroutines awaiting same key`() = runTest(timeout = 5.seconds) {
        store.addResource("test_resource", SharedResourceKind.SINGLE)

        val results = mutableListOf<Any?>()
        val jobs = mutableListOf<Job>()

        // Launch 5 coroutines all waiting for the same key
        repeat(5) { i ->
            val job = launch {
                val value = store.await("test_resource", "shared_key")
                synchronized(results) {
                    results.add(value)
                }
            }
            jobs.add(job)
        }

        delay(100) // Let all coroutines start waiting

        // Provide the value - all should receive it
        store.setOnce("test_resource", "shared_key", "shared_value")

        // Wait for all to complete
        jobs.joinAll()

        assertEquals(5, results.size)
        results.forEach { assertEquals("shared_value", it) }
    }

    @Test
    fun `test snapshot captures current state`() = runTest {
        store.addResource("resource1", SharedResourceKind.SINGLE)
        store.addResource("resource2", SharedResourceKind.MANY)

        // Populate with some data
        store.setOnce("resource1", "key1", "value1")
        store.setOnce("resource1", "key2", "value2")
        store.put("resource2", "key3", "value3")

        val snapshot = store.snapshot()

        assertEquals(2, snapshot.size)
        assertEquals("value1", snapshot["resource1"]?.get("key1"))
        assertEquals("value2", snapshot["resource1"]?.get("key2"))
        assertEquals("value3", snapshot["resource2"]?.get("key3"))
    }

    @Test
    fun `test commit applies delta changes`() = runTest {
        store.addResource("test_resource", SharedResourceKind.MANY)

        // Initial state
        store.put("test_resource", "key1", "initial")

        val snapshot = store.snapshot()

        // Create delta with changes
        val delta = mapOf(
            "test_resource" to mapOf(
                "key1" to "updated",
                "key2" to "new_value"
            )
        )

        store.commit(snapshot, delta)

        assertEquals("updated", store.get("test_resource", "key1"))
        assertEquals("new_value", store.get("test_resource", "key2"))
    }

    @Test
    fun `test error when accessing unknown resource`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking { store.get("unknown_resource", "key1") }
        }

        assertThrows<IllegalArgumentException> {
            runBlocking { store.setOnce("unknown_resource", "key1", "value") }
        }

        assertThrows<IllegalArgumentException> {
            runBlocking { store.put("unknown_resource", "key1", "value") }
        }

        assertThrows<IllegalArgumentException> {
            runBlocking { store.await("unknown_resource", "key1") }
        }
    }

    @Test
    fun `test concurrent access with SINGLE semantics`() = runTest {
        store.addResource("concurrent_test", SharedResourceKind.SINGLE)

        val successCount = AtomicInteger(0)
        val totalAttempts = 100
        val jobs = mutableListOf<Job>()

        // Launch many coroutines trying to write the same key
        repeat(totalAttempts) { i ->
            val job = launch {
                val success = store.setOnce("concurrent_test", "race_key", "value_$i")
                if (success) {
                    successCount.incrementAndGet()
                }
            }
            jobs.add(job)
        }

        jobs.joinAll()

        // Only one should have succeeded due to SINGLE semantics
        assertEquals(1, successCount.get())

        // The value should be from whichever coroutine won the race
        val finalValue = store.get("concurrent_test", "race_key") as String
        assertTrue(finalValue.startsWith("value_"))
    }

    @Test
    fun `test concurrent access with MANY semantics`() = runTest {
        store.addResource("concurrent_many", SharedResourceKind.MANY)

        val totalWrites = 50
        val jobs = mutableListOf<Job>()

        // Launch many coroutines writing to the same key
        repeat(totalWrites) { i ->
            val job = launch {
                store.put("concurrent_many", "race_key", "value_$i")
            }
            jobs.add(job)
        }

        jobs.joinAll()

        // Final value should be from one of the writes
        val finalValue = store.get("concurrent_many", "race_key") as String
        assertTrue(finalValue.startsWith("value_"))
    }

    @Test
    fun `test tree reconstruction scenario`() = runTest {
        // Simulate the tree reconstruction example
        store.addResource("nodeParents", SharedResourceKind.SINGLE)

        // Simulate tree edges arriving out of order
        val edges = listOf(
            Triple("tree1", "root", "node1"),
            Triple("tree1", "node1", "node2"), // Child arrives before parent is fully processed
            Triple("tree1", "node1", "node3"),
            Triple("tree1", "node2", "node4")
        )

        val results = mutableListOf<Map<String, Any>>()

        // Process edges concurrently
        val jobs = edges.map { (treeId, fromId, toId) ->
            async {
                val nodeLevel = if (fromId == "root") {
                    1
                } else {
                    // Wait for parent
                    val parentKey = "$treeId:$fromId"
                    val parentInfo = store.await("nodeParents", parentKey).requireStringMap("parent[$parentKey]")
                    (parentInfo["level"] as Int) + 1
                }

                val nodeColor = if (nodeLevel % 2 == 1) "RED" else "BLACK"

                // Store node info
                val nodeKey = "$treeId:$toId"
                val nodeInfo = mapOf(
                    "nodeId" to toId,
                    "level" to nodeLevel,
                    "treeId" to treeId
                )
                store.setOnce("nodeParents", nodeKey, nodeInfo)

                mapOf(
                    "nodeId" to toId,
                    "level" to nodeLevel,
                    "color" to nodeColor
                )
            }
        }

        val jobResults = jobs.awaitAll()
        results.addAll(jobResults)

        // Verify results
        assertEquals(4, results.size)

        // Verify color pattern: level 1 = RED, level 2 = BLACK, etc.
        results.forEach { result ->
            val level = result["level"] as Int
            val color = result["color"] as String
            val expectedColor = if (level % 2 == 1) "RED" else "BLACK"
            assertEquals(expectedColor, color, "Wrong color for level $level")
        }
    }
}

private fun Any?.requireStringMap(context: String): Map<String, Any?> {
    val map = this as? Map<*, *> ?: error("$context expected object")
    return buildMap(map.size) {
        for ((k, v) in map) {
            require(k is String) { "$context keys must be strings" }
            put(k, v)
        }
    }
}
