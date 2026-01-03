package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun run(body: String, row: Map<String, Any?> = emptyMap(), engine: ExecutionEngine): Any? =
    compileAndRun(body, row, engine = engine)

class DotBracketNumericKeyTest {

    @EngineTest
    fun `dot bracket with explicit path inside - numeric key in object literal`(engine: ExecutionEngine) {
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
    fun `numeric key must be non-negative integer - reject decimal`(engine: ExecutionEngine) {
        val ex = assertThrows<RuntimeException> {
            run("""LET bad = { 3.14 : 1 }; OUTPUT bad""", engine = engine)
        }
        assertTrue(ex.message!!.contains("Numeric field key"))
    }

    @EngineTest
    fun `numeric key must be non-negative integer - reject negative`(engine: ExecutionEngine) {
        val ex = assertThrows<RuntimeException> {
            run("""LET bad = { -1 : 1 }; OUTPUT bad""", engine = engine)
        }
        assertTrue(ex.message!!.contains("Numeric field key"))
        assertTrue(ex.message!!.contains("non-negative"))
    }
}
