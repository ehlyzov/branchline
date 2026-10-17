package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.runtime.isBigInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFails
import io.github.ehlyzov.branchline.runtime.bignum.*

class NumericJsTest {
    @Test
    fun promotes_to_bigint_on_safe_integer_overflow() {
        val program = """
            TRANSFORM T {
                OUTPUT { v: 9007199254740991 + 1 }
            }
        """.trimIndent()
        val run = buildRunnerFromProgramMP(program)
        val result = run(emptyMap()) as Map<*, *>
        assertTrue(isBigInt(result["v"]))
        assertEquals("9007199254740992", result["v"].toString())
    }
    @Test
    fun explicitIntegerPowerChecksLongOverflowAndKeepsSupportedValues() {
        fun power(expression: String): Any? =
            (buildRunnerFromProgramMP("TRANSFORM T { OUTPUT { value: $expression } }")(emptyMap()) as Map<*, *>)["value"]
        assertEquals("9007199254740993", power("POWER(DEC(\"9007199254740993\"), 1)").toString())
        assertEquals("4611686018427387904", power("POWER(DEC(2), 62)").toString())
        assertEquals("-9223372036854775808", power("POWER(DEC(-2), 63)").toString())
        assertFails { power("POWER(DEC(2), 63)") }
        assertFails { blBigIntParse("9223372036854775808") }
    }

    @Test
    fun decimalStorageRetainsDocumentedDoubleLimit() {
        val sum = blBigDecParse("0.1") + blBigDecParse("0.2")
        assertEquals(0.30000000000000004, sum.toDouble())
        assertEquals(9007199254740992.0, blBigDecParse("9007199254740993.0").toDouble())
        assertTrue(sum.compareTo(blBigDecParse("0.3")) != 0)
    }

}
