package io.github.ehlyzov.branchline.vm

import net.jqwik.api.*
import net.jqwik.api.constraints.DoubleRange
import net.jqwik.api.constraints.LongRange
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.compileAndRun

class NumericParityPropertyTest {

    private fun runBoth(body: String, row: Map<String, Any?>): Pair<Any?, Any?> {
        val i = compileAndRun(body, row = row, engine = ExecutionEngine.INTERPRETER)
        val v = compileAndRun(body, row = row, engine = ExecutionEngine.VM)
        return i to v
    }

    @Property
    fun int_add_mul_parity(@ForAll a: Int, @ForAll b: Int) {
        val body = "OUTPUT { s: row.a + row.b, p: row.a * row.b };"
        val (i, v) = runBoth(body, mapOf("a" to a, "b" to b))
        assertEquals(i, v)
    }

    @Property
    fun long_sub_mod_parity(@ForAll @LongRange(min = -1_000_000_000_000, max = 1_000_000_000_000) a: Long,
                            @ForAll @LongRange(min = 1, max = 1_000_000_000_000) b: Long) {
        val body = "OUTPUT { d: row.a - row.b, m: row.a % row.b };"
        val (i, v) = runBoth(body, mapOf("a" to a, "b" to b))
        assertEquals(i, v)
    }

    @Provide
    fun bigIntegers(): Arbitrary<BigInteger> = Arbitraries.longs().between(-1_000_000L, 1_000_000L).map { BigInteger.valueOf(it) }

    @Property
    fun bigint_compare_parity(@ForAll("bigIntegers") a: BigInteger, @ForAll("bigIntegers") b: BigInteger) {
        val body = "OUTPUT { lt: row.a < row.b, le: row.a <= row.b, gt: row.a > row.b, ge: row.a >= row.b, eq: row.a == row.b, ne: row.a != row.b };"
        val (i, v) = runBoth(body, mapOf("a" to a, "b" to b))
        assertEquals(i, v)
    }

    @Provide
    fun bigDecimals(): Arbitrary<BigDecimal> = Arbitraries.doubles().between(-1e6, 1e6)
        .map { BigDecimal.valueOf(it).setScale(4, java.math.RoundingMode.HALF_UP) }

    @Property
    fun decimal_add_mul_parity(@ForAll("bigDecimals") a: BigDecimal, @ForAll("bigDecimals") b: BigDecimal) {
        val body = "OUTPUT { s: row.a + row.b, p: row.a * row.b };"
        val (i, v) = runBoth(body, mapOf("a" to a, "b" to b))
        assertEquals(i, v)
    }

    @Property
    fun cross_type_compare_parity(
        @ForAll("bigDecimals") a: BigDecimal,
        @ForAll @DoubleRange(min = -1e6, max = 1e6) b: Double,
    ) {
        val body =
            $$"""OUTPUT { 
                |lt: row.a < row.b, 
                |le: row.a <= row.b, 
                |gt: row.a > row.b, 
                |ge: row.a >= row.b, 
                |eq: row.a == row.b, 
                |ne: row.a != row.b 
            |};""".trimMargin()
        val interpreter = runCatching {
            compileAndRun(body, row = mapOf("a" to a, "b" to b), engine = ExecutionEngine.INTERPRETER)
        }
        val vm = runCatching {
            compileAndRun(body, row = mapOf("a" to a, "b" to b), engine = ExecutionEngine.VM)
        }
        assertTrue(interpreter.isFailure, "Interpreter should reject decimal/float comparisons")
        assertTrue(vm.isFailure, "VM should reject decimal/float comparisons")
    }
}
