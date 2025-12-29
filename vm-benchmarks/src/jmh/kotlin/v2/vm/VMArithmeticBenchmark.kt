package v2.vm

import java.math.BigDecimal
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Benchmark)
open class VMArithmeticBenchmark {
    private val vm = VM()

    private val intA = 123
    private val intB = 456
    private val longA = 123L
    private val doubleB = 456.0
    private val bigA = BigDecimal("100")
    private val bigB = BigDecimal("20")

    @Benchmark
    fun addIntInt() = vm.addNumbers(intA, intB)

    @Benchmark
    fun addLongDouble() = vm.addNumbers(longA, doubleB)

    @Benchmark
    fun addBigDecimal() = vm.addNumbers(bigA, bigB)

    @Benchmark
    fun subtractIntInt() = vm.subtractNumbers(intA, intB)

    @Benchmark
    fun subtractLongDouble() = vm.subtractNumbers(longA, doubleB)

    @Benchmark
    fun subtractBigDecimal() = vm.subtractNumbers(bigA, bigB)

    @Benchmark
    fun multiplyIntInt() = vm.multiplyNumbers(intA, intB)

    @Benchmark
    fun multiplyLongDouble() = vm.multiplyNumbers(longA, doubleB)

    @Benchmark
    fun multiplyBigDecimal() = vm.multiplyNumbers(bigA, bigB)

    @Benchmark
    fun divideIntInt() = vm.divideNumbers(intA, intB)

    @Benchmark
    fun divideLongDouble() = vm.divideNumbers(longA, doubleB)

    @Benchmark
    fun divideBigDecimal() = vm.divideNumbers(bigA, bigB)

    @Benchmark
    fun compareIntInt() = vm.compareValues(intA, intB)

    @Benchmark
    fun compareLongDouble() = vm.compareValues(longA, doubleB)

    @Benchmark
    fun compareBigDecimal() = vm.compareValues(bigA, bigB)
}
