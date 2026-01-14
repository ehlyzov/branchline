package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.TraceReport
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.*

class StdDebugModuleTest {

    @Test fun checkpoint_emits_event_and_report() {
        val tracer = CollectingTracer(TraceOptions(includeCalls = true))
        Debug.tracer = tracer

        val out = compileAndRun(
            """
            LET xs = [1,2,3] ;
            LET ys = MAP(xs, (x) -> x + 1) ;
            CHECKPOINT("after map") ;
            OUTPUT { sum: REDUCE(ys, 0, (a,v) -> a + v) } ;
            """.trimIndent()
        )
        assertEquals(mapOf("sum" to 9), out)

        // We should see the checkpoint call and have monotonic times
        assertTrue(
            tracer.events.any { it.event is TraceEvent.Call && it.event.kind == "CHECKPOINT" }
        )
        val times = tracer.events.map { it.at }
        assertTrue(times.zipWithNext().all { (a, b) -> a <= b })

        val rep = TraceReport.from(tracer)
        assertTrue(rep.checkpoints.any { it.label == "after map" })
        assertTrue(rep.hotspots.isNotEmpty())
    }

    @Test fun assert_success_and_failure() {
        Debug.tracer = CollectingTracer()
        val ok = compileAndRun(
            """
            LET a = 5 ;
            ASSERT(a > 0, "must be positive") ;
            OUTPUT { a: a } ;
            """.trimIndent()
        )
        assertEquals(mapOf("a" to 5), ok)

        val ex = assertFailsWith<IllegalStateException> {
            compileAndRun(
                """
                LET a = -1 ;
                ASSERT(a > 0, "boom!") ;
                OUTPUT { a: a } ;
                """.trimIndent()
            )
        }
        assertTrue(ex.message?.contains("boom!") == true)
    }

    @Test fun explain_fallback() {
        Debug.tracer = CollectingTracer()
        val out = compileAndRun(
            """
            LET a = 123 ;
            OUTPUT { e: EXPLAIN("a") } ;
            """.trimIndent()
        )

        @Suppress("UNCHECKED_CAST")
        val e = (out as Map<String, Any?>)["e"] as Map<String, Any?>
        assertEquals("a", e["var"])
        assertEquals("no provenance", e["info"])
    }

    @Test fun report_fields_are_consistent() {
        val tracer = CollectingTracer(TraceOptions(includeCalls = true))
        Debug.tracer = tracer

        compileAndRun(
            """
            LET xs = [1,2,3] ;
            LET ys = MAP(xs, (x) -> x * 2) ;
            CHECKPOINT("cp1") ;
            OUTPUT { y: REDUCE(ys, 0, (a,v) -> a + v) } ;
            """.trimIndent()
        )

        val rep = TraceReport.from(tracer)
        assertTrue(rep.totalEvents > 0)
        assertTrue(rep.duration >= kotlin.time.Duration.ZERO)
        assertTrue(rep.timelineSample.isNotEmpty())
        assertTrue(rep.checkpoints.any { it.label == "cp1" })
    }
}
