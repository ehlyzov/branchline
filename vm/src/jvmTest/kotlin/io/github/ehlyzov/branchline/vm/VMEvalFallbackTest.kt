package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.SharedStateAwaitExpr
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VMEvalFallbackTest {
    @Test
    fun vmeval_falls_back_with_trace_on_error() {
        VMFactory.Metrics.reset()
        val tracer = CollectingTracer()
        val vmeval = VMEval(funcs = emptyMap(), hostFns = emptyMap(), tracer = tracer)

        // Build AWAIT shared expression which will fail at runtime (no SharedStore configured)
        val tok = Token(TokenType.AWAIT, "AWAIT", 1, 1)
        val expr = SharedStateAwaitExpr("cache", "k", tok)
        val env = mutableMapOf<String, Any?>()

        val out = vmeval.eval(expr, env)
        // VM throws, fallback also fails -> returns null via VMEval catch
        assertNull(out)

        // Ensure a FALLBACK trace Call/Return pair was emitted
        val hasFallbackCall = tracer.events.any { timed ->
            val call = timed.event as? TraceEvent.Call
            call?.kind == "FALLBACK"
        }
        val hasFallbackRet = tracer.events.any { timed ->
            val ret = timed.event as? TraceEvent.Return
            ret?.kind == "FALLBACK"
        }
        assertEquals(true, hasFallbackCall && hasFallbackRet)

        // Compile metrics should have at least 1 compile
        val stats = VMFactory.getCompilationStats()
        assertNotNull(stats)
        kotlin.test.assertTrue(stats.totalCompilations >= 1)
    }
}
