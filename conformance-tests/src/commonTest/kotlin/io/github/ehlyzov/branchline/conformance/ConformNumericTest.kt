package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformNumericTest {

    private fun programOf(body: String) = """
        TRANSFORM T { OUTPUT { v: $body }
        }
    """.trimIndent()

    @Test
    fun add_ints() {
        val run = buildRunnerFromProgramMP(programOf("2 + 3"))
        assertEquals(mapOf("v" to 5), run(emptyMap()))
    }

    @Test
    fun sub_ints() {
        val run = buildRunnerFromProgramMP(programOf("9 - 4"))
        assertEquals(mapOf("v" to 5), run(emptyMap()))
    }

    @Test
    fun mul_ints() {
        val run = buildRunnerFromProgramMP(programOf("6 * 7"))
        assertEquals(mapOf("v" to 42), run(emptyMap()))
    }

    @Test
    fun div_even_returns_int() {
        val run = buildRunnerFromProgramMP(programOf("6 / 3"))
        assertEquals(mapOf("v" to 2), run(emptyMap()))
    }

    @Test
    fun mod_ints() {
        val run = buildRunnerFromProgramMP(programOf("7 % 3"))
        assertEquals(mapOf("v" to 1), run(emptyMap()))
    }

    @Test
    fun cross_type_equality_and_ordering_interpreter() {
        val program = """
            TRANSFORM Compare {
                LET a = input.a;
                LET dec = input.dec;
                LET gtZero = dec > 0;
                LET eqBigInt = a == 1;
                LET eqBigDec = dec == 1;
                LET ltMixed = 1.1 < input.c;
                OUTPUT { eqBigInt: eqBigInt, eqBigDec: eqBigDec, ltMixed: ltMixed, gtZero: gtZero };
            }
        """.trimIndent()
        val run = buildRunnerFromProgramMP(program, runSema = true)
        val input = mapOf<String, Any?>(
            "a" to io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong(1),
            "dec" to io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble(1.0),
            "c" to 2,
        )
        val result = run(input) as Map<*, *>
        assertEquals(true, result["eqBigInt"])
        assertEquals(true, result["eqBigDec"])
        assertEquals(true, result["ltMixed"])
        assertEquals(true, result["gtZero"])
    }
}
