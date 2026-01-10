package io.github.ehlyzov.branchline.debug

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class StackTracer : Tracer, AutoCloseable {
    private data class Frame(
        val name: String,
        val start: TimeMark,
        var child: Duration = Duration.ZERO,
    )
    private val clock = TimeSource.Monotonic
    private val stack = ArrayDeque<Frame>()
    private val _counts = LinkedHashMap<String, Int>()
    private val _totals = LinkedHashMap<String, Duration>()
    override val opts: TraceOptions = TraceOptions(step = true, captureValues = false, includeCalls = false, includeEval = false)
    val counts: Map<String, Int> get() = _counts
    val totals: Map<String, Duration> get() = _totals
    override fun on(event: TraceEvent) {
        when (event) {
            is TraceEvent.Call -> if (event.kind == "STEP") begin(event.name ?: "?")
            is TraceEvent.Return -> if (event.kind == "STEP") end()
            else -> Unit
        }
    }
    override fun shouldBreak(): Boolean = false
    private fun begin(name: String) { stack.addLast(Frame(name, clock.markNow())) }
    private fun end() {
        val frame = (if (stack.isEmpty()) null else stack.removeLast()) ?: return
        val elapsed = frame.start.elapsedNow()
        val exclusive = elapsed - frame.child
        val name = frame.name
        _totals[name] = (_totals[name] ?: Duration.ZERO) + exclusive
        _counts[name] = (_counts[name] ?: 0) + 1
        if (stack.isNotEmpty()) stack.last().child += elapsed
    }
    fun report(): String {
        val sb = StringBuilder()
        sb.appendLine("opcode\tcount\ttotal")
        for ((name, total) in _totals.entries.sortedByDescending { it.value }) {
            val count = _counts[name] ?: 0
            sb.appendLine("$name\t$count\t$total")
        }
        return sb.toString()
    }
    override fun close() { /* no-op in common */ }
}

