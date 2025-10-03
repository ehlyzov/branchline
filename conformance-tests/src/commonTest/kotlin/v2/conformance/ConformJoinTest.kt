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
}

