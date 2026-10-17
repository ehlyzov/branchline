package io.github.ehlyzov.branchline.cli

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParallelWorkerJvmTest {
    @Test
    fun fourWorkersOverlapAndReuseTheirOwnMutableState() {
        val owner = Thread.currentThread()
        val barrier = CyclicBarrier(4)
        var created = 0
        val results = parallelMapWithWorker(4, (0 until 20).toList(), createWorker = {
            assertTrue(Thread.currentThread() === owner, "Initialize workers before dispatch")
            created++
            mutableListOf<Int>()
        }) { seen, item ->
            if (seen.isEmpty()) barrier.await(30, TimeUnit.SECONDS)
            seen += item
            Triple(item, Thread.currentThread(), seen.toList())
        }
        assertEquals(4, created)
        assertEquals((0 until 20).toList(), results.map { it.first })
        val byWorker = results.groupBy { it.second }
        assertEquals(4, byWorker.size)
        for (workerResults in byWorker.values) {
            workerResults.forEachIndexed { index, result ->
                assertEquals(workerResults.take(index + 1).map { it.first }, result.third)
            }
        }
    }

    @Test
    fun interruptedCollectorKeepsSharedContextUntilWorkersComplete() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val completedAtReturn = java.util.concurrent.atomic.AtomicInteger(-1)
        val interruptedAtReturn = AtomicBoolean()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val priorStore = io.github.ehlyzov.branchline.std.SharedStoreProvider.store
        val store = io.github.ehlyzov.branchline.std.createDefaultSharedStore()
        val collector = Thread {
            io.github.ehlyzov.branchline.std.SharedStoreProvider.store = store
            Thread.currentThread().interrupt()
            try {
                parallelMapWithWorker(2, listOf(0, 1), createWorker = { Unit }) { _, _ ->
                    entered.countDown()
                    assertTrue(release.await(30, TimeUnit.SECONDS))
                    assertTrue(io.github.ehlyzov.branchline.std.SharedStoreProvider.store === store)
                    completed.incrementAndGet()
                }
            } catch (ex: Throwable) {
                failure.set(ex)
            } finally {
                completedAtReturn.set(completed.get())
                interruptedAtReturn.set(Thread.currentThread().isInterrupted)
                io.github.ehlyzov.branchline.std.SharedStoreProvider.store = priorStore
                returned.countDown()
            }
        }
        collector.start()
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS))
            // Both workers are deliberately blocked. Returning during this bounded
            // observation would restore their context while they still need it.
            assertEquals(false, returned.await(200, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            collector.join(30_000)
        }
        assertEquals(false, collector.isAlive)
        assertTrue(failure.get() is InterruptedException, failure.get().toString())
        assertEquals(2, completedAtReturn.get())
        assertTrue(interruptedAtReturn.get())
        assertTrue(io.github.ehlyzov.branchline.std.SharedStoreProvider.store === priorStore)
    }

    @Test
    fun failureWaitsForOtherWorkersBeforeReturning() {
        val started = CyclicBarrier(2)
        val failed = CountDownLatch(1)
        val completed = AtomicBoolean()
        val error = assertFailsWith<ExecutionException> {
            parallelMapWithWorker(2, listOf(0, 1), createWorker = { Unit }) { _, item ->
                started.await(30, TimeUnit.SECONDS)
                if (item == 0) {
                    failed.countDown()
                    error("first item failed")
                }
                assertTrue(failed.await(30, TimeUnit.SECONDS))
                completed.set(true)
            }
        }
        assertEquals("first item failed", error.cause?.message)
        assertTrue(completed.get(), "Tasks must finish before shared-store restoration")
    }
}
