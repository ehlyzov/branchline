package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun exec(body: String, engine: ExecutionEngine) = buildRunner(body, engine = engine)

class IfRuntimeTest {

    @EngineTest
    fun `if else chooses branch`(engine: ExecutionEngine) {
        val f = exec(
            """
            IF row.x > 0 THEN {
                OUTPUT { res : "pos" }
            } ELSE {
                OUTPUT { res : "neg" }
            }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("res" to "pos"), f(mapOf("x" to 3)))
        assertEquals(mapOf("res" to "neg"), f(mapOf("x" to -1)))
    }

    @EngineTest
    fun `if without else may skip output`(engine: ExecutionEngine) {
        val f = exec(
            """
            IF row.flag THEN {
                OUTPUT { ok : true }
            }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("ok" to true), f(mapOf("flag" to true)))
        assertThrows<IllegalStateException> { f(mapOf("flag" to false)) }
    }

    @EngineTest
    fun `nested if inside loop`(engine: ExecutionEngine) {
        val f = exec(
            """
            FOR EACH n IN row.nums {
                IF n % 2 == 0 THEN {
                    OUTPUT { even : n }
                } ELSE {
                    OUTPUT { odd  : n }
                }
            }
            """.trimIndent(),
            engine,
        )
        val out = f(mapOf("nums" to listOf(1, 2)))
        assertEquals(listOf(mapOf("odd" to 1), mapOf("even" to 2)), out)
    }

    @EngineTest
    fun if_expr_returns_value_in_let(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
              LET a = null;
              LET b = 7;
              LET x = IF a == null THEN b ELSE a;
              OUTPUT { x: x };
            """.trimIndent(),
            engine = engine,
        ) as Map<*, *>
        assertEquals(7, out["x"])
    }

    @EngineTest
    fun if_body_side_effects_are_kept(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
              LET acc = { ok: true, errs: [] };
              IF true THEN {
                SET acc.ok = false;
                APPEND TO acc.errs "e1" INIT [];
              } ELSE { }
              OUTPUT { ok: acc.ok, errs: acc.errs };
            """.trimIndent(),
            engine = engine,
        ) as Map<*, *>
        assertEquals(false, out["ok"])
        assertEquals(listOf("e1"), out["errs"])
    }
}
