package io.github.ehlyzov.branchline.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner
import io.github.ehlyzov.branchline.testutils.compileAndRun

class IfParityEngineTest {

    @EngineTest
    fun `if else chooses branch`(engine: ExecutionEngine) {
        val f = buildRunner(
            """
            IF row.x > 0 THEN {
                OUTPUT { res : "pos" }
            } ELSE {
                OUTPUT { res : "neg" }
            }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("res" to "pos"), f(mapOf("x" to 3)))
        assertEquals(mapOf("res" to "neg"), f(mapOf("x" to -1)))
    }

    @EngineTest
    fun `if without else may skip output`(engine: ExecutionEngine) {
        val f = buildRunner(
            """
            IF row.flag THEN {
                OUTPUT { ok : true }
            }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("ok" to true), f(mapOf("flag" to true)))
        assertThrows<IllegalStateException> { f(mapOf("flag" to false)) }
    }

    @EngineTest
    fun `nested if inside loop`(engine: ExecutionEngine) {
        val f = buildRunner(
            """
            FOR EACH n IN row.nums {
                IF n % 2 == 0 THEN {
                    OUTPUT { even : n }
                } ELSE {
                    OUTPUT { odd  : n }
                }
            }
            """.trimIndent(),
            engine = engine,
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
}
