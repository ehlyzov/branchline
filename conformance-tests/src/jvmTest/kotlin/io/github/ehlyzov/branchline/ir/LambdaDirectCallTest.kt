package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals

class LambdaDirectCallTest {

    @EngineTest
    fun call_lambda_var_expr_body(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET f = (x,y) -> x + y;
            LET s = f(2,3);
            OUTPUT {s:s}
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("s" to 5), out)
    }

    @EngineTest
    fun call_lambda_var_block_body(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET f = (x) -> {
                LET y = x * 2;
                RETURN y + 1;
            };
            LET s = f(10);
            OUTPUT { s: s }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("s" to 21), out)
    }

    @EngineTest
    fun zero_arg_lambda(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET f = () -> 42;
            OUTPUT { y: f() }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("y" to 42), out)
    }

    @EngineTest
    fun closure_capture(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET k = 10;
            LET f = (x) -> x + k;
            OUTPUT { y: f(5) }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("y" to 15), out)
    }

    @EngineTest
    fun hof_works(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET ys = MAP([1,2,3], (x) -> x + 1);
            OUTPUT { ys: ys }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("ys" to listOf(2, 3, 4)), out)
    }

    @EngineTest
    fun hof_works_indirect(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET fn = (x) -> x + 1;
            LET ys = MAP([1,2,3], fn);
            OUTPUT { ys: ys }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("ys" to listOf(2, 3, 4)), out)
    }
}
