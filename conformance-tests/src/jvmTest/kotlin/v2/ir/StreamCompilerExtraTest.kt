package v2.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import v2.ExecutionEngine
import v2.testutils.EngineTest
import v2.testutils.buildRunner
import v2.testutils.buildRunnerFromProgram

private fun buildExec(body: String, engine: ExecutionEngine): (Map<String, Any?>) -> Any? =
    buildRunner(body, engine = engine)

class StreamCompilerExtraTest {

    @EngineTest
    fun `string concat`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            LET msg = "Hello, " ;
            OUTPUT { greet : msg + input.name }
            """.trimIndent(),
            engine,
        )
        val out = exec(mapOf("name" to "Bob"))
        assertEquals(mapOf("greet" to "Hello, Bob"), out)
    }

    @EngineTest
    fun `precedence mul before plus`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            LET result = 2 + 5 * 8 ;
            OUTPUT { r : result }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("r" to 42), exec(emptyMap()))
    }

    @EngineTest
    fun `deep path`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            OUTPUT { city : input.customer.address.city }
            """.trimIndent(),
            engine,
        )
        val inRow = mapOf(
            "customer" to mapOf(
                "address" to mapOf("city" to "Paris"),
            ),
        )
        assertEquals(mapOf("city" to "Paris"), exec(inRow))
    }

    @EngineTest
    fun `let depends on let`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            LET a = 2 ;
            LET b = a * 5 ;
            OUTPUT { b : b }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("b" to 10), exec(emptyMap()))
    }

    @EngineTest
    fun `no output throws`(engine: ExecutionEngine) {
        val src = """
            TRANSFORM T { LET x = 1; }
        """.trimIndent()
        val exec = buildRunnerFromProgram(src, engine = engine)
        assertThrows<IllegalStateException> { exec(emptyMap()) }
    }
}
