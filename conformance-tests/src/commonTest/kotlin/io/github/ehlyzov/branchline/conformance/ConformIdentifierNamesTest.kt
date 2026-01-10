package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformIdentifierNamesTest {

    @Test
    fun keyword_like_identifiers() {
        val program = """
            TRANSFORM T {
                LET output = 1;
                LET enum = 2;
                LET `if` = 3;
                LET obj = { output: output, enum: enum, `if`: `if` };
                LET via_path = obj.output + obj.enum + obj.`if`;
                OUTPUT {
                    output: output,
                    enum: enum,
                    escaped: `if`,
                    via_path: via_path
                }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(
            mapOf(
                "output" to 1,
                "enum" to 2,
                "escaped" to 3,
                "via_path" to 6,
            ),
            out
        )
    }
}
