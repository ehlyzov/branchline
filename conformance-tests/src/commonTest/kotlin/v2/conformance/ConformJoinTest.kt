package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformJoinTest {
    @Test
    fun join_strings_with_separator() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { v: JOIN(["a", "b", "c"], "-") }
            }
        """.trimIndent()

        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap()) as Map<String, Any?>
        assertEquals("a-b-c", out["v"])
    }

    @Test
    fun join_strings_default_separator_empty() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { v: JOIN(["x", "y", "z"]) }
            }
        """.trimIndent()

        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap()) as Map<String, Any?>
        assertEquals("xyz", out["v"])
    }
}
