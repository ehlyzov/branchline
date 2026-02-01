package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun exec(body: String, engine: ExecutionEngine) = buildRunner(body, engine = engine)

class CaseRuntimeTest {

    @EngineTest
    fun case_orders_guards(engine: ExecutionEngine) {
        val f = exec(
            """
            LET status = CASE {
                WHEN input.score > 10 THEN "high"
                WHEN input.score > 0 THEN "mid"
                ELSE "low"
            };
            OUTPUT { status: status };
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("status" to "high"), f(mapOf("score" to 11)))
        assertEquals(mapOf("status" to "mid"), f(mapOf("score" to 3)))
        assertEquals(mapOf("status" to "low"), f(mapOf("score" to 0)))
    }

    @EngineTest
    fun case_inside_expression(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            OUTPUT {
                values: [CASE { WHEN input.flag THEN 1 ELSE 2 }],
                label: CASE { WHEN input.flag THEN "yes" ELSE "no" }
            };
            """.trimIndent(),
            row = mapOf("flag" to true),
            engine = engine,
        ) as Map<*, *>
        assertEquals(listOf(1), out["values"])
        assertEquals("yes", out["label"])
    }
}
