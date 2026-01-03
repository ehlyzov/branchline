package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals

class EdgesTest {

    @EngineTest
    fun appendTo_with_grouped_value(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET acc = { errs: [] };
            LET k = "X";
            APPEND TO acc.errs ("missing: " + k) INIT [];
            OUTPUT acc;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(listOf("missing: X"), (out as Map<*, *>)["errs"])
    }

    @EngineTest
    fun appendTo_on_indexed_lvalue_and_grouped_value(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET o = { a: { b: [] } };
            APPEND TO o.a.b (1 + 2) INIT [];
            OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(listOf(3), ((out as Map<*, *>)["a"] as Map<*, *>)["b"])
    }

    @EngineTest
    fun grouping_inside_call_arg_with_coalesce(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET pick = (a,b,c) -> c;
            LET a = null;
            LET b = 7;
            OUTPUT { res: pick(1,2,(a ?? b)) };
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("res" to 7), out)
    }
}
