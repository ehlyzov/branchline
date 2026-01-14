package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_DEC_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_INT_ONE
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_INT_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.bitLength
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.ceil
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.floor
import io.github.ehlyzov.branchline.runtime.bignum.minus
import io.github.ehlyzov.branchline.runtime.bignum.plus
import io.github.ehlyzov.branchline.runtime.bignum.rem
import io.github.ehlyzov.branchline.runtime.bignum.roundHalfEven
import io.github.ehlyzov.branchline.runtime.bignum.roundHalfEvenDouble
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.times
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class StdNumericModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("ABS", ::fnABS)
        r.fn("FLOOR", ::fnFLOOR)
        r.fn("CEIL", ::fnCEIL)
        r.fn("ROUND", ::fnROUND)
        r.fn("POWER", ::fnPOWER)
        r.fn("SQRT", ::fnSQRT)
        r.fn("RANDOM", ::fnRANDOM)
    }
}

/**
 * ABS(number) – returns the absolute value of a number.
 */
private fun fnABS(args: List<Any?>): Any? {
    require(args.size == 1) { "ABS(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> abs(v)
        is Long -> abs(v)
        is Float -> abs(v)
        is Double -> abs(v)
        is Short -> abs(v.toInt())
        is Byte -> abs(v.toInt())
        is BLBigInt -> if (v >= BL_BIG_INT_ZERO) v else -v
        is BLBigDec -> if (v >= BL_BIG_DEC_ZERO) v else -v
        else -> error("ABS: arg must be numeric")
    }
}

/** FLOOR(number) – returns the largest integer ≤ number. */
private fun fnFLOOR(args: List<Any?>): Any? {
    require(args.size == 1) { "FLOOR(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> v
        is Long -> v
        is Float -> floor(v)
        is Double -> floor(v)
        is Short -> v
        is Byte -> v
        is BLBigInt -> v
        is BLBigDec -> v.floor()
        else -> error("FLOOR: arg must be numeric")
    }
}

/** CEIL(number) – returns the smallest integer ≥ number. */
private fun fnCEIL(args: List<Any?>): Any? {
    require(args.size == 1) { "CEIL(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> v
        is Long -> v
        is Float -> ceil(v)
        is Double -> ceil(v)
        is Short -> v
        is Byte -> v
        is BLBigInt -> v
        is BLBigDec -> v.ceil()
        else -> error("CEIL: arg must be numeric")
    }
}

/** ROUND(number[, precision]) – rounds using “round half to even”. */
private fun fnROUND(args: List<Any?>): Any? {
    require(args.size in 1..2) { "ROUND(number[, precision])" }
    val v = args[0] ?: return null
    val precision = if (args.size == 2) parsePrecision(args[1]) else 0
    return when (v) {
        is Int -> roundIntegralInt(v, precision)
        is Long -> roundIntegralLong(v, precision)
        is Short -> roundIntegralInt(v.toInt(), precision)
        is Byte -> roundIntegralInt(v.toInt(), precision)
        is Float -> roundHalfEvenDouble(v.toDouble(), precision).toFloat()
        is Double -> roundHalfEvenDouble(v, precision)
        is BLBigInt -> roundIntegralBigInt(v, precision)
        is BLBigDec -> v.roundHalfEven(precision)
        else -> error("ROUND: arg must be numeric")
    }
}

/** Helper to parse the precision argument as an integer. */
private fun parsePrecision(arg: Any?): Int {
    return when (arg) {
        is Int -> arg
        is Long -> {
            require(arg in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "ROUND: precision out of bounds"
            }
            arg.toInt()
        }
        is Short -> arg.toInt()
        is Byte -> arg.toInt()
        is Float -> parsePrecisionDouble(arg.toDouble())
        is Double -> parsePrecisionDouble(arg)
        is BLBigInt -> {
            require(arg.bitLength() <= 31) { "ROUND: precision out of bounds" }
            arg.toInt()
        }
        is BLBigDec -> {
            val asInt = arg.toBLBigInt()
            require(asInt.toBLBigDec().compareTo(arg) == 0) {
                "ROUND: precision must be integer"
            }
            require(asInt.bitLength() <= 31) { "ROUND: precision out of bounds" }
            asInt.toInt()
        }
        is Number -> parsePrecisionDouble(arg.toDouble())
        else -> error("ROUND: precision must be integer")
    }
}

private fun parsePrecisionDouble(value: Double): Int {
    require(value.isFinite()) { "ROUND: precision must be finite" }
    val asLong = value.toLong()
    require(value == asLong.toDouble()) { "ROUND: precision must be integer" }
    require(asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        "ROUND: precision out of bounds"
    }
    return asLong.toInt()
}

private val BL_BIG_INT_TWO: BLBigInt = blBigIntOfLong(2)
private val BL_BIG_INT_TEN: BLBigInt = blBigIntOfLong(10)

private fun roundIntegralInt(value: Int, precision: Int): Any {
    if (precision >= 0) return value
    val rounded = roundIntegralBigInt(blBigIntOfLong(value.toLong()), precision)
    val asLong = rounded.toLong()
    val asLongBig = blBigIntOfLong(asLong)
    return if (asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
        asLongBig.compareTo(rounded) == 0
    ) {
        asLong.toInt()
    } else if (asLongBig.compareTo(rounded) == 0) {
        asLong
    } else {
        rounded
    }
}

private fun roundIntegralLong(value: Long, precision: Int): Any {
    if (precision >= 0) return value
    val rounded = roundIntegralBigInt(blBigIntOfLong(value), precision)
    val asLong = rounded.toLong()
    return if (blBigIntOfLong(asLong).compareTo(rounded) == 0) {
        asLong
    } else {
        rounded
    }
}

private fun roundIntegralBigInt(value: BLBigInt, precision: Int): BLBigInt {
    if (precision >= 0) return value
    val scale = pow10BigInt(-precision)
    val rem = value % scale
    if (rem == BL_BIG_INT_ZERO) return value
    val absRem = if (rem.signum() >= 0) rem else -rem
    val half = scale / BL_BIG_INT_TWO
    val truncated = value - rem
    val sign = value.signum()
    return when {
        absRem < half -> truncated
        absRem > half -> if (sign >= 0) truncated + scale else truncated - scale
        else -> {
            val div = value / scale
            val isEven = div % BL_BIG_INT_TWO == BL_BIG_INT_ZERO
            if (isEven) truncated else if (sign >= 0) truncated + scale else truncated - scale
        }
    }
}

private fun pow10BigInt(exp: Int): BLBigInt {
    require(exp >= 0) { "ROUND: precision must be integer" }
    var result = BL_BIG_INT_ONE
    var i = 0
    while (i < exp) {
        result = result * BL_BIG_INT_TEN
        i += 1
    }
    return result
}

/** POWER(base, exponent) – raises base to exponent. */
private fun fnPOWER(args: List<Any?>): Any? {
    require(args.size == 2) { "POWER(base, exponent)" }
    val b = args[0] ?: return null
    val e = args[1] ?: return null

    TODO()
}

/** SQRT(number) – returns the square root of a non‑negative number. */
private fun fnSQRT(args: List<Any?>): Any? {
    require(args.size == 1) { "SQRT(number)" }
    TODO()
}

/** RANDOM() – returns a pseudo‑random number in [0, 1). */
private fun fnRANDOM(args: List<Any?>): Any {
    require(args.isEmpty()) { "RANDOM()" }
    return kotlin.random.Random.nextDouble()
}
