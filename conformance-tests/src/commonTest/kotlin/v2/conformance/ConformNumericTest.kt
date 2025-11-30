package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

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
}
