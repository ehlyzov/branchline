package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.debug.CollectingTracer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsVMTest {

    @Test
    fun vmeval_records_compile_timings() {
        // Reset metrics
        VMFactory.Metrics.reset()

        // Build a simple expression: 1 + 2
        val oneTok = Token(TokenType.NUMBER, "1", 0, 0)
        val twoTok = Token(TokenType.NUMBER, "2", 0, 0)
        val plusTok = Token(TokenType.PLUS, "+", 0, 0)

        val expr = BinaryExpr(
            left = NumberLiteral(I32(1), oneTok),
            right = NumberLiteral(I32(2), twoTok),
            token = plusTok
        )

        val tracer = CollectingTracer()
        val eval = VMEval(funcs = emptyMap(), hostFns = emptyMap(), tracer = tracer)
        val env = mutableMapOf<String, Any?>()

        val out = eval.eval(expr, env)
        assertEquals(3, (out as Number).toInt())

        val stats1 = VMFactory.getCompilationStats()
        assertEquals(1, stats1.totalCompilations)
        assertEquals(1, stats1.successfulCompilations)
        assertEquals(0, stats1.failedCompilations)
        assertTrue(stats1.avgCompileTime >= 0.0)

        // Evaluate another expression to see counters advance
        val expr2 = BinaryExpr(
            left = NumberLiteral(I32(10), Token(TokenType.NUMBER, "10", 0, 0)),
            right = NumberLiteral(I32(5), Token(TokenType.NUMBER, "5", 0, 0)),
            token = Token(TokenType.MINUS, "-", 0, 0)
        )
        val out2 = eval.eval(expr2, mutableMapOf())
        assertEquals(5, (out2 as Number).toInt())

        val stats2 = VMFactory.getCompilationStats()
        assertEquals(2, stats2.totalCompilations)
        assertEquals(2, stats2.successfulCompilations)
        assertEquals(0, stats2.failedCompilations)
        assertTrue(stats2.avgCompileTime >= 0.0)
    }
}

