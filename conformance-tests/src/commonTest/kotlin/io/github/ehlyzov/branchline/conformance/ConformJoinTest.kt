package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformJoinTest {
    @Test
    fun join_strings_with_separator() {
        val program = """
            TRANSFORM T { OUTPUT { v: JOIN(["a", "b", "c"], "-") }
            }
        """.trimIndent()

        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap()) as Map<*, *>
        assertEquals("a-b-c", out["v"])
    }

    @Test
    fun join_strings_default_separator_empty() {
        val program = """
            TRANSFORM T { OUTPUT { v: JOIN(["x", "y", "z"]) }
            }
        """.trimIndent()

        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap()) as Map<*, *>
        assertEquals("xyz", out["v"])
    }
}
