package v2.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ExecutionEngine
import v2.runtime.bignum.blBigDecOfDouble
import v2.runtime.bignum.blBigIntOfLong
import v2.testutils.compileAndRun

class NumericCrossTypeVmTest {
    private val program = """
        LET a = input.a;
        LET dec = input.dec;
        LET gtZero = dec > 0;
        LET eqBigInt = a == 1;
        LET eqBigDec = dec == 1;
        LET ltMixed = 1.1 < input.c;
        OUTPUT { eqBigInt: eqBigInt, eqBigDec: eqBigDec, ltMixed: ltMixed, gtZero: gtZero };
    """.trimIndent()

    @Test
    fun cross_type_equality_and_ordering_vm() {
        val input = mapOf<String, Any?>(
                "a" to blBigIntOfLong(1),
                "dec" to blBigDecOfDouble(1.0),
                "c" to 2,
            )
        val result = compileAndRun(
            program,
            row = input,
            engine = ExecutionEngine.VM
        ) as Map<*, *>
        assertEquals(true, result["eqBigInt"])
        assertEquals(true, result["eqBigDec"])
        assertEquals(true, result["ltMixed"])
        assertEquals(true, result["gtZero"])
    }
}
