package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun run(body: String, row: Map<String, Any?> = emptyMap(), engine: ExecutionEngine): Any? =
    compileAndRun(body, row, engine = engine)

class DynamicIndexRuntimeTest {

    @EngineTest
    fun `template dot bracket with variable key`(engine: ExecutionEngine) {
        val out = run(
            """
            LET value = "field";
            LET template = { [value] : 10 };
            OUTPUT { res : template.[value] }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("res" to 10), out)
    }

    @EngineTest
    fun `dot bracket with explicit path inside`(engine: ExecutionEngine) {
        val out = run(
            """
            LET template = { value : 10 };
            LET count = { 10 : "test" };
            OUTPUT { res : count.[template.value] }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("res" to "test"), out)
    }

    @EngineTest
    fun `dot bracket forbids nested brackets inside path`(engine: ExecutionEngine) {
        val ex = assertThrows<RuntimeException> {
            run(
                """
                LET value = "field";
                LET template = { [value] : 10 };
                LET count = { 10 : "test" };
                OUTPUT count.[template.[value]]
                """.trimIndent(),
                engine = engine,
            )
        }
        assertTrue(
            ex.message!!.contains("Expect name or index inside '.['") ||
                ex.message!!.contains("Expect ']' after .[")
        )
    }

    @EngineTest
    fun `array index with brackets`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { second : row.items[1] }""",
            mapOf("items" to listOf("a", "b", "c")),
            engine,
        )
        assertEquals(mapOf("second" to "b"), out)
    }

    @EngineTest
    fun `array dot-number path`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { v : row.items.2 }""",
            mapOf("items" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("v" to 30), out)
    }

    @EngineTest
    fun `array index out of bounds`(engine: ExecutionEngine) {
        val ex = assertThrows<IllegalArgumentException> {
            run("""OUTPUT row.items[5]""", mapOf("items" to listOf(1, 2)), engine)
        }
        assertTrue(ex.message!!.contains("out of bounds"))
    }

    @EngineTest
    fun `object index by string expr`(engine: ExecutionEngine) {
        val out = run(
            """
            LET k = "b";
            OUTPUT { val : row.obj[k] }
            """.trimIndent(),
            mapOf("obj" to mapOf("a" to 1, "b" to 2)),
            engine,
        )
        assertEquals(mapOf("val" to 2), out)
    }
}
