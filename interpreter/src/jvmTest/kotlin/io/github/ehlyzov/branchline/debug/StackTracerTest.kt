package io.github.ehlyzov.branchline.debug

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

class StackTracerTest {
    @Test
    fun collects_counts_and_totals() {
        val tracer = StackTracer()
        Debug.tracer = tracer
        compileAndRun("OUTPUT { v: 1 + 2 };", engine = ExecutionEngine.VM)
        Debug.tracer = null
        assertTrue(tracer.counts.isNotEmpty())
        assertTrue(tracer.totals.values.all { it >= Duration.ZERO })
    }

    @Test
    fun combined_tracer_dispatches_events() {
        val stackTracer = StackTracer()
        val collecting = CollectingTracer(TraceOptions(step = true))
        val combo = CombinedTracer(stackTracer, collecting)
        Debug.tracer = combo
        compileAndRun("OUTPUT { v: 1 };", engine = ExecutionEngine.VM)
        Debug.tracer = null
        assertTrue(stackTracer.counts.isNotEmpty())
        assertTrue(collecting.events.isNotEmpty())
    }
}
