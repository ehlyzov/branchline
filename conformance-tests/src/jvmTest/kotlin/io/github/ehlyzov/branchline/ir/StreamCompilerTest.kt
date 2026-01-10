package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunnerFromProgram

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
