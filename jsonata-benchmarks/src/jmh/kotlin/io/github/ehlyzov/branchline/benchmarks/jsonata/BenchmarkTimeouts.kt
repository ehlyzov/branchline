package io.github.ehlyzov.branchline.benchmarks.jsonata

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

private const val TIMEOUT_PROPERTY: String = "jsonata.benchTimeoutMs"
private const val DEFAULT_TIMEOUT_MS: Long = 500L

internal fun resolveBenchmarkTimeoutMs(): Long? {
    val propertyValue = System.getProperty(TIMEOUT_PROPERTY)?.trim()
    if (propertyValue.isNullOrEmpty()) {
        return DEFAULT_TIMEOUT_MS
    }
    val parsed = propertyValue.toLongOrNull()
        ?: error("Invalid $TIMEOUT_PROPERTY: $propertyValue")
    if (parsed <= 0L) {
        return null
    }
    return parsed
}

internal class BenchmarkTimeoutRunner(private val timeoutMs: Long) : AutoCloseable {
    private val threadCounter = AtomicInteger(0)
    private val executor = Executors.newCachedThreadPool { runnable ->
        val thread = Thread(runnable, "jsonata-bench-timeout-${threadCounter.incrementAndGet()}")
        thread.isDaemon = true
        thread
    }

    fun <T> run(block: () -> T): T {
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            future.cancel(true)
            throw TimeoutException("Timeout after ${timeoutMs}ms")
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
