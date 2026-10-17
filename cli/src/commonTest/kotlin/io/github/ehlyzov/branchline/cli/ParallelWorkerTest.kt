package io.github.ehlyzov.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelWorkerTest {
    @Test
    fun workerStateIsReusedAndResultsKeepInputOrder() {
        var created = 0
        val results = parallelMapWithWorker(1, listOf(3, 1, 2),
            createWorker = { created++; mutableListOf<Int>() },
        ) { seen, item ->
            seen += item
            seen.toList()
        }
        assertEquals(1, created)
        assertEquals(listOf(listOf(3), listOf(3, 1), listOf(3, 1, 2)), results)
    }

    @Test
    fun emptyInputCreatesNoWorker() {
        var created = false
        val results = parallelMapWithWorker(4, emptyList<Int>(),
            createWorker = { created = true; Unit },
        ) { _, item -> item }
        assertTrue(results.isEmpty())
        assertEquals(false, created)
    }
}
