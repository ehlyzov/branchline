package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import kotlin.test.Test
import kotlin.test.assertEquals

class NumericJsContractTest {
    @Test
    fun explicitIntegersRetainLongBoundsButGeneralOverflowIsNotChecked() {
        NumericContractHarness.eachEngine { vm ->
            assertEquals("9223372036854775807", NumericContractHarness.evaluate("DEC(\"9223372036854775807\")", vm).toString())
            assertEquals("-9223372036854775808", NumericContractHarness.evaluate("DEC(\"-9223372036854775808\")", vm).toString())
            NumericContractHarness.assertFailure("DEC(\"9223372036854775808\")", vm)
            NumericContractHarness.assertFailure("POWER(DEC(2), 63)", vm)
            // Characterize the exclusion from the shared profile; this is not a checked-overflow promise.
            assertEquals("-9223372036854775808", NumericContractHarness.evaluate("DEC(\"9223372036854775807\") + 1", vm).toString())
        }
    }

    @Test
    fun decimalOptInCannotRecoverPrecisionLostToDoubleStorage() {
        NumericContractHarness.eachEngine { vm ->
            val sum = NumericContractHarness.evaluate("DEC(\"0.1\") + DEC(\"0.2\")", vm) as BLBigDec
            assertEquals(0.30000000000000004, sum.toDouble())
            val large = NumericContractHarness.evaluate("DEC(\"9007199254740993.0\")", vm) as BLBigDec
            assertEquals(9007199254740992.0, large.toDouble())
            val converted = NumericContractHarness.evaluate("DEC(\"9007199254740993\") + DEC(\"0.0\")", vm) as BLBigDec
            assertEquals(9007199254740992.0, converted.toDouble())
        }
    }
}
