package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumericPrecisionTest {
    private fun value(expression: String): Any? =
        (buildRunnerFromProgramMP("TRANSFORM Main { OUTPUT { value: $expression } }")(emptyMap()) as Map<*, *>)["value"]

    @Test
    fun powersPreserveExactValuesBeyondDoubleAndLong() {
        assertEquals("100000000000000000000000000000000000000000000000000000000000000000000000000000000", value("POWER(DEC(10), 80)").toString())
        assertEquals("81129638414606699710187514626049", value("POWER(DEC(\"9007199254740993\"), 2)").toString())
        val decimal = value("POWER(DEC(\"1.00000000000000000001\"), 2)")
        assertTrue(decimal is BLBigDec)
        assertEquals(0, BigDecimal("1.0000000000000000000200000000000000000001").compareTo(decimal))
    }

    @Test
    fun decimalRootsAndReciprocalsUseExisting34DigitHalfUpContext() {
        assertEquals(0, BigDecimal("1.414213562373095048801688724209698").compareTo(value("SQRT(DEC(2))") as BLBigDec))
        assertEquals(0, BigDecimal("0.3333333333333333333333333333333333").compareTo(value("POWER(DEC(3), -1)") as BLBigDec))
        assertEquals(0, BigDecimal("1e200").compareTo(value("SQRT(DEC(\"1e400\"))") as BLBigDec))
        assertTrue(value("POWER(DEC(2), 10)") is BLBigInt)
    }
}
