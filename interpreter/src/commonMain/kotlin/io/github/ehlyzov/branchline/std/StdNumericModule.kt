package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_DEC_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_INT_ONE
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_INT_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.bitLength
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
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
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class StdNumericModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("ABS", ::fnABS)
        r.fn("DEC", ::fnDEC)
        r.fn("FLOOR", ::fnFLOOR)
        r.fn("CEIL", ::fnCEIL)
        r.fn("ROUND", ::fnROUND)
        r.fn("POWER", ::fnPOWER)
        r.fn("SQRT", ::fnSQRT)
        r.fn("RANDOM", ::fnRANDOM)
    }
}

/**
 * DEC(x) – explicit opt-in to precise numeric domain (BigInt/BigDec).
 */
private fun fnDEC(args: List<Any?>): Any? {
    require(args.size == 1) { "DEC(x)" }
    val v = args[0] ?: return null
    return when (v) {
        is BLBigInt -> v
        is BLBigDec -> v
        is String -> parseDecString(v)
        is Long -> blBigIntOfLong(v)
        is Int -> blBigIntOfLong(v.toLong())
        is Short -> blBigIntOfLong(v.toLong())
        is Byte -> blBigIntOfLong(v.toLong())
        is Double -> blBigDecOfDouble(v)
        is Float -> blBigDecOfDouble(v.toDouble())
        is Number -> blBigDecOfDouble(v.toDouble())
        else -> error("DEC: unsupported type ${v::class.simpleName}")
    }
}

private fun parseDecString(value: String): Any {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        error("DEC: cannot parse '$value'")
    }
    return if (trimmed.contains('.') || trimmed.contains('e', true)) {
        blBigDecParse(trimmed)
    } else {
        blBigIntParse(trimmed)
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
        is Int -> roundIntegerValue(v.toLong(), precision, preferInt = true)
        is Long -> roundIntegerValue(v, precision, preferInt = false)
        is Short -> roundIntegerValue(v.toLong(), precision, preferInt = true)
        is Byte -> roundIntegerValue(v.toLong(), precision, preferInt = true)
        is Float -> roundFloatValue(v.toDouble(), precision)
        is Double -> roundFloatValue(v, precision)
        is BLBigInt -> roundBigIntValue(v, precision)
        is BLBigDec -> roundBigDecValue(v, precision)
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

private fun roundIntegerValue(value: Long, precision: Int, preferInt: Boolean): Any {
    if (precision > 0) {
        return roundHalfEvenDouble(value.toDouble(), precision)
    }
    if (precision == 0) {
        return narrowInteger(value, preferInt)
    }
    val rounded = roundIntegralBigInt(blBigIntOfLong(value), precision)
    return narrowBigInt(rounded, preferInt)
}

private fun roundFloatValue(value: Double, precision: Int): Any {
    if (precision > 0) {
        return roundHalfEvenDouble(value, precision)
    }
    val rounded = roundHalfEvenDouble(value, precision)
    return doubleToInteger(rounded)
}

private fun roundBigIntValue(value: BLBigInt, precision: Int): Any {
    if (precision > 0) {
        return roundHalfEvenDouble(value.toBLBigDec().toDouble(), precision)
    }
    if (precision == 0) return narrowBigInt(value, preferInt = false)
    val rounded = roundIntegralBigInt(value, precision)
    return narrowBigInt(rounded, preferInt = false)
}

private fun roundBigDecValue(value: BLBigDec, precision: Int): Any {
    if (precision > 0) {
        return value.roundHalfEven(precision)
    }
    val rounded = value.roundHalfEven(precision).toBLBigInt()
    return narrowBigInt(rounded, preferInt = false)
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

private fun doubleToInteger(value: Double): Any {
    require(value.isFinite()) { "ROUND: value must be finite" }
    val asLong = value.toLong()
    if (value == asLong.toDouble()) {
        return narrowInteger(asLong, preferInt = true)
    }
    val bigInt = blBigDecOfDouble(value).toBLBigInt()
    return narrowBigInt(bigInt, preferInt = true)
}

private fun narrowBigInt(value: BLBigInt, preferInt: Boolean): Any {
    val asLong = value.toLong()
    return if (blBigIntOfLong(asLong).compareTo(value) == 0) {
        narrowInteger(asLong, preferInt)
    } else {
        value
    }
}

private fun narrowInteger(value: Long, preferInt: Boolean): Any {
    return if (preferInt && value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        value.toInt()
    } else {
        value
    }
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
