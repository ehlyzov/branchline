package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

public data class CorpusCase(
    public val name: String,
    public val program: String,
    public val input: Map<String, Any?>,
    public val expected: Map<String, Any?>,
)

public class CompatibilityCorpusTest {

    @Test
    public fun corpus_programs_match_expected_outputs() {
        for (case in CORPUS) {
            val runner = buildRunnerFromProgramMP(case.program)
            val out = runner(case.input)
            assertEquals(case.expected, out, "Compatibility corpus failed: ${case.name}")
        }
    }

    private companion object {
        private val CORPUS: List<CorpusCase> = listOf(
            CorpusCase(
                name = "arithmetic-and-string-concat",
                program = """
                    TRANSFORM T {
                        LET prefix = "id-";
                        OUTPUT { value: (1 + 2) * 3, label: prefix + "123" }
                    }
                """.trimIndent(),
                input = emptyMap(),
                expected = mapOf(
                    "value" to 9,
                    "label" to "id-123",
                ),
            ),
            CorpusCase(
                name = "conditional-output",
                program = """
                    TRANSFORM T {
                        IF input.flag THEN {
                            OUTPUT { status: "ok" }
                        } ELSE {
                            OUTPUT { status: "fail" }
                        }
                    }
                """.trimIndent(),
                input = mapOf("flag" to true),
                expected = mapOf("status" to "ok"),
            ),
            CorpusCase(
                name = "array-comprehension",
                program = """
                    TRANSFORM T {
                        OUTPUT { items: [x * 2 FOR EACH x IN input.values] }
                    }
                """.trimIndent(),
                input = mapOf("values" to listOf(1, 2, 3)),
                expected = mapOf("items" to listOf(2, 4, 6)),
            ),
            CorpusCase(
                name = "object-paths",
                program = """
                    TRANSFORM T {
                        OUTPUT {
                            name: input.user.name,
                            region: input.user.address.region,
                        }
                    }
                """.trimIndent(),
                input = mapOf(
                    "user" to mapOf(
                        "name" to "Ari",
                        "address" to mapOf("region" to "emea"),
                    ),
                ),
                expected = mapOf(
                    "name" to "Ari",
                    "region" to "emea",
                ),
            ),
            CorpusCase(
                name = "stdlib-join-upper",
                program = """
                    TRANSFORM T {
                        LET letters = input.letters;
                        OUTPUT { text: UPPER(JOIN(letters)) }
                    }
                """.trimIndent(),
                input = mapOf("letters" to listOf("b", "l", "")),
                expected = mapOf("text" to "BL"),
            ),
            CorpusCase(
                name = "stdlib-round-half-even",
                program = """
                    TRANSFORM T {
                        OUTPUT {
                            rounded: STRING(ROUND(2.050769212644806, 3)),
                            half_even_low: STRING(ROUND(2.5)),
                            half_even_high: STRING(ROUND(3.5)),
                            negative_half: STRING(ROUND(-1.5)),
                            neg_precision: STRING(ROUND(155, -2)),
                        }
                    }
                """.trimIndent(),
                input = emptyMap(),
                expected = mapOf(
                    "rounded" to "2.051",
                    "half_even_low" to "2",
                    "half_even_high" to "4",
                    "negative_half" to "-2",
                    "neg_precision" to "200",
                ),
            ),
        )
    }
}
