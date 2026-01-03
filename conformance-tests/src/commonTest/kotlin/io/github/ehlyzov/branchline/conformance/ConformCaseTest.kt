package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.github.ehlyzov.branchline.ParseException
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformCaseTest {

    @Test
    fun case_guard_ordering() {
        val program = """
            TRANSFORM T { OUTPUT { v: CASE {
                WHEN true THEN "first"
                WHEN true THEN "second"
                ELSE "third"
            } }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to "first"), out)
    }

    @Test
    fun case_requires_else() {
        val program = """
            TRANSFORM T { OUTPUT { v: CASE { WHEN true THEN 1 } }
            }
        """.trimIndent()
        assertFailsWith<ParseException> {
            buildRunnerFromProgramMP(program)
        }
    }

    @Test
    fun case_nested_exprs() {
        val program = """
            TRANSFORM T { OUTPUT {
                items: [CASE { WHEN false THEN 1 ELSE 2 }],
                flag: CASE { WHEN true THEN input.ok ELSE "no" }
            }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(mapOf("ok" to "yes"))
        assertEquals(
            mapOf("items" to listOf(2), "flag" to "yes"),
            out
        )
    }
}
