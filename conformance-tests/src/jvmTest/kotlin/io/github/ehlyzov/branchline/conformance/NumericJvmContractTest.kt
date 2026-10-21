package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString
import kotlin.test.Test
import kotlin.test.assertEquals

class NumericJvmContractTest {
    @Test
    fun integerOverflowPromotesAndExplicitParsingRetainsAllDigits() {
        val cases = mapOf(
            "9223372036854775807 + 1" to "9223372036854775808",
            "9223372036854775807 + 2147483648" to "9223372039002259455",
            "9223372036854775807 * 2147483648" to "19807040628566084396238503936",
            "-1 * NUMBER(\"-9223372036854775808\")" to "9223372036854775808",
            "NUMBER(\"-9223372036854775808\") * -1" to "9223372036854775808",
            "DEC(\"9223372036854775807\") * 2" to "18446744073709551614",
            "DEC(\"-9223372036854775808\") // -1" to "9223372036854775808",
            "INT(\"123456789012345678901234567890\")" to "123456789012345678901234567890",
        )
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in cases) {
                assertEquals(expected, NumericContractHarness.evaluate(expression, vm).toString(), expression)
            }
        }
    }

    @Test
    fun decimalArithmeticIsExactUntilAnExplicitlyRoundedOperation() {
        val cases = mapOf(
            "DEC(\"0.1\") + DEC(\"0.2\")" to "0.3",
            "DEC(\"9007199254740993.0\") + DEC(\"0.1\")" to "9007199254740993.1",
            "DEC(\"12345678901234567890.12\") * DEC(\"10.0\")" to "123456789012345678901.200",
            "DEC(\"1.0\") / DEC(\"3.0\")" to "0.3333333333333333333333333333333333",
            "ROUND(DEC(\"1.245\"), 2)" to "1.24",
            "ROUND(DEC(\"1.255\"), 2)" to "1.26",
        )
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in cases) {
                val actual = NumericContractHarness.evaluate(expression, vm) as BLBigDec
                assertEquals(expected, actual.toPlainString(), expression)
            }
            NumericContractHarness.assertFailure("DEC(\"1.0\") / DEC(\"0.0\")", vm)
        }
    }

    @Test
    fun decimalFloorAndCeilUseScaleZeroRatherThanUnlimitedPrecisionRounding() {
        val cases = mapOf(
            "FLOOR(DEC(\"1.5\"))" to "1",
            "CEIL(DEC(\"1.5\"))" to "2",
            "FLOOR(DEC(\"-1.5\"))" to "-2",
            "CEIL(DEC(\"-1.5\"))" to "-1",
            "FLOOR(DEC(\"9007199254740993.75\"))" to "9007199254740993",
            "CEIL(DEC(\"9007199254740993.75\"))" to "9007199254740994",
            "FLOOR(DEC(\"2.00\"))" to "2",
            "CEIL(DEC(\"-2.00\"))" to "-2",
            "FLOOR(DEC(\"0.00\"))" to "0",
            "CEIL(DEC(\"0.00\"))" to "0",
        )
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in cases) {
                assertEquals(expected, NumericContractHarness.evaluate(expression, vm).toString(), expression)
            }
        }
    }
}
