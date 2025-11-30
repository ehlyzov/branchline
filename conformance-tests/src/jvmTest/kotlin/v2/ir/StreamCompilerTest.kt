package v2.ir

import org.junit.jupiter.api.Assertions.assertEquals
import v2.ExecutionEngine
import v2.testutils.EngineTest
import v2.testutils.buildRunnerFromProgram

private fun buildExec(src: String, engine: ExecutionEngine): (Map<String, Any?>) -> Any? =
    buildRunnerFromProgram(src, engine = engine)

class StreamCompilerTest {

    @EngineTest
    fun `row passes through let output`(engine: ExecutionEngine) {
        val bl = """
            TRANSFORM Sum { LET total = input.a + input.b ;
                OUTPUT { total : total }
            }
        """.trimIndent()

        val exec = buildExec(bl, engine)
        val out = exec(mapOf("a" to 2, "b" to 3))
        assertEquals(mapOf("total" to 5), out)
    }
}
