package io.github.ehlyzov.branchline.std

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexCacheConcurrencyTest {
    @Test
    fun concurrentMatchAndReplaceChurnTheBoundedCache() {
        val match = StdLib.fns.getValue("MATCH")
        val replace = StdLib.fns.getValue("REPLACE")
        val barrier = CyclicBarrier(4)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 4).map { worker -> pool.submit(Callable {
                barrier.await(30, TimeUnit.SECONDS)
                repeat(256) { index ->
                    val token = "w${worker}x${index}"
                    val pattern = "(?:$token)"
                    assertEquals(listOf(token), match(listOf("before $token after", pattern)))
                    assertEquals("before done after", replace(listOf("before $token after", pattern, "done")))
                }
            }) }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }
}
