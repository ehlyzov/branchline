package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BytecodeNumericAdmissionJsTest {
    @Test
    fun signedLongBigIntDomainRetainsKindAndDigits() {
        for (text in listOf("-9223372036854775808", "9223372036854775807", "0")) {
            assertEquals(text, assertIs<BLBigInt>(decode("bi", text)).toString())
        }
        for (text in listOf("-9223372036854775809", "9223372036854775808", "123456789012345678901234567890")) {
            assertFailsWith<IllegalArgumentException>(text) { decode("bi", text) }
        }
    }

    @Test
    fun finiteDoubleDecimalsAcceptEquivalentScaleAndExponentSpellings() {
        for (text in listOf("1.2500", "125e-2", "+001.25", "0.1000", "1e100", "1e-100")) {
            assertEquals(text.toDouble(), assertIs<BLBigDec>(decode("bd", text)).toDouble(), text)
        }
        for (number in listOf(0.1, -0.1, Double.MAX_VALUE, Double.MIN_VALUE, -0.0)) {
            // The arithmetic factory canonicalizes -0 to its cached zero; parse
            // constructs an actual stored negative zero for the codec check.
            val original = if (number.toBits() == Long.MIN_VALUE) blBigDecParse("-0.0") else blBigDecOfDouble(number)
            val reloaded = assertIs<BLBigDec>(BytecodeIO.deserializeValue(BytecodeIO.serializeValue(original)))
            assertEquals(number.toBits(), reloaded.toDouble().toBits(), number.toString())
        }
    }

    @Test
    fun decimalPrecisionLossOverflowAndUnderflowAreExplicitErrors() {
        for (text in listOf(
            "9007199254740993", "0.10000000000000001", "1.2345678901234567890123456789",
            "1e309", "-1e309", "1e-400", "1e999999999999999999999999", "0e999999999999999999999999",
        )) {
            assertFailsWith<IllegalArgumentException>(text) { decode("bd", text) }
        }
    }

    private fun decode(tag: String, text: String): Any? =
        BytecodeIO.deserializeValue(BytecodeIO.SerializedValue(tag, s = text))
}
