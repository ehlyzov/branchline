package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformFormatTest {

    @Test
    fun format_supports_named_and_index_placeholders() {
        val program = """
            TRANSFORM T {
                OUTPUT {
                    named: FORMAT("Hello {name}", { name: "Ada" }),
                    indexed: FORMAT("{0}-{1}", ["a", "b"])
                }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap()) as Map<*, *>
        assertEquals("Hello Ada", result["named"])
        assertEquals("a-b", result["indexed"])
    }

    @Test
    fun format_leaves_unknown_placeholders_and_supports_escaping() {
        val program = """
            TRANSFORM T {
                OUTPUT {
                    unknown: FORMAT("Hello {missing}", { name: "Ada" }),
                    escaped: FORMAT("{{value}}", { value: "ignored" })
                }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap()) as Map<*, *>
        assertEquals("Hello {missing}", result["unknown"])
        assertEquals("{value}", result["escaped"])
    }
}
