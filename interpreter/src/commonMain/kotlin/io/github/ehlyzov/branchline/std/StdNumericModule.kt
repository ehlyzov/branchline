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
import io.github.ehlyzov.branchline.runtime.bignum.powIntegral
import io.github.ehlyzov.branchline.runtime.bignum.powNonNegative
import io.github.ehlyzov.branchline.runtime.bignum.sqrtDecimal
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
import io.github.ehlyzov.branchline.runtime.isPlatformIntegerNumber
import io.github.ehlyzov.branchline.runtime.numericKindOf
import io.github.ehlyzov.branchline.runtime.NumericKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

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
        // JS primitive numeric types share one representation. Classify the
        // value before conversion so fractional numbers cannot enter a Long.
        is Number -> if (isPlatformIntegerNumber(v)) {
            blBigIntOfLong(v.toLong())
        } else {
            blBigDecOfDouble(v.toDouble())
        }
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
    // Kotlin/JS primitive Int/Float/Double type checks overlap. Classify
    // integrality before the primitive branches so fractions are rounded.
    return when {
        v is BLBigInt -> v
        v is BLBigDec -> v.floor()
        isPlatformIntegerNumber(v) -> v
        v is Float -> floor(v)
        v is Double -> floor(v)
        else -> error("FLOOR: arg must be numeric")
    }
}

/** CEIL(number) – returns the smallest integer ≥ number. */
private fun fnCEIL(args: List<Any?>): Any? {
    require(args.size == 1) { "CEIL(number)" }
    val v = args[0] ?: return null
    // Kotlin/JS primitive Int/Float/Double type checks overlap. Classify
    // integrality before the primitive branches so fractions are rounded.
    return when {
        v is BLBigInt -> v
        v is BLBigDec -> v.ceil()
        isPlatformIntegerNumber(v) -> v
        v is Float -> ceil(v)
        v is Double -> ceil(v)
        else -> error("CEIL: arg must be numeric")
    }
}

/** ROUND(number[, precision]) – rounds using “round half to even”. */
private fun fnROUND(args: List<Any?>): Any? {
    require(args.size in 1..2) { "ROUND(number[, precision])" }
    val v = args[0] ?: return null
    val precision = if (args.size == 2) parsePrecision(args[1]) else 0
    return when {
        v is BLBigDec -> roundBigDecValue(v, precision)
        v is BLBigInt -> roundBigIntValue(v, precision)
        isPlatformIntegerNumber(v) -> {
            val asLong = when (v) {
                is Long -> v
                is Number -> v.toLong()
                else -> error("ROUND: arg must be numeric")
            }
            roundIntegerValue(asLong, precision, preferInt = v is Int || v is Short || v is Byte)
        }
        v is Number -> roundFloatValue(v.toDouble(), precision)
        else -> error("ROUND: arg must be numeric")
    }
}

/** Helper to parse the precision argument as an integer. */
private fun parsePrecision(arg: Any?): Int {
    // Precise wrappers must precede Number on JVM. Primitive numbers must all
    // pass the same validation: Kotlin/JS `is Int` also matches fractions.
    return when (arg) {
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
        is Long -> {
            require(arg in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                "ROUND: precision out of bounds"
            }
            arg.toInt()
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
    if (rem.compareTo(BL_BIG_INT_ZERO) == 0) return value
    val absRem = if (rem.signum() >= 0) rem else -rem
    val half = scale / BL_BIG_INT_TWO
    val truncated = value - rem
    val sign = value.signum()
    return when {
        absRem < half -> truncated
        absRem > half -> if (sign >= 0) truncated + scale else truncated - scale
        else -> {
            val div = value / scale
            val isEven = (div % BL_BIG_INT_TWO).compareTo(BL_BIG_INT_ZERO) == 0
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

    if (b is BLBigInt || b is BLBigDec || e is BLBigInt || e is BLBigDec) {
        require(numericKindOf(b) != NumericKind.F && numericKindOf(e) != NumericKind.F) {
            "POWER: cannot mix precise numbers with floats; use DEC(...) explicitly"
        }
        val exponent = powerExponent(e)
        if (b !is BLBigDec && exponent >= 0) {
            val integer = when (b) {
                is BLBigInt -> b
                is Long -> blBigIntOfLong(b)
                is Int -> blBigIntOfLong(b.toLong())
                is Short -> blBigIntOfLong(b.toLong())
                is Byte -> blBigIntOfLong(b.toLong())
                is Number -> blBigIntOfLong(b.toLong())
                else -> error("POWER: args must be numeric")
            }
            return integer.powNonNegative(exponent)
        }
        val base = preciseBase(b)
        require(base.signum() != 0 || exponent >= 0) { "POWER: zero base with negative exponent" }
        return base.powIntegral(exponent)
    }
    val base = finiteNumber(b, "POWER")
    val exponent = finiteNumber(e, "POWER")
    require(base >= 0 || exponent == floor(exponent)) { "POWER: negative base requires integer exponent" }
    require(base != 0.0 || exponent >= 0) { "POWER: zero base with negative exponent" }
    return base.pow(exponent).also { require(it.isFinite()) { "POWER: result must be finite" } }
}

private fun powerExponent(value: Any): Int {
    val decimal = preciseBase(value)
    val integer = decimal.toBLBigInt()
    require(integer.toBLBigDec().compareTo(decimal) == 0) { "POWER: precise exponent must be integer" }
    val limit = blBigIntOfLong(999999999)
    require(integer >= -limit && integer <= limit) { "POWER: precise exponent out of bounds" }
    return integer.toInt()
}

private fun preciseBase(value: Any): BLBigDec = when (value) {
    is BLBigDec -> value
    is BLBigInt -> value.toBLBigDec()
    is Long -> blBigIntOfLong(value).toBLBigDec()
    is Int -> blBigIntOfLong(value.toLong()).toBLBigDec()
    is Short -> blBigIntOfLong(value.toLong()).toBLBigDec()
    is Byte -> blBigIntOfLong(value.toLong()).toBLBigDec()
    is Number -> {
        require(isPlatformIntegerNumber(value)) { "POWER: precise exponent must be integer" }
        blBigIntOfLong(value.toLong()).toBLBigDec()
    }
    else -> error("POWER: args must be numeric")
}

private fun finiteNumber(value: Any, operation: String): Double {
    require(value is Number) { "$operation: args must be numeric" }
    return value.toDouble().also { require(it.isFinite()) { "$operation: args must be finite" } }
}

/** SQRT(number) – returns the square root of a non‑negative number. */
private fun fnSQRT(args: List<Any?>): Any? {
    require(args.size == 1) { "SQRT(number)" }
    val value = args[0] ?: return null
    if (value is BLBigInt || value is BLBigDec) {
        val decimal = if (value is BLBigInt) value.toBLBigDec() else value as BLBigDec
        require(decimal.signum() >= 0) { "SQRT: arg must be non-negative" }
        return decimal.sqrtDecimal()
    }
    val number = finiteNumber(value, "SQRT")
    require(number >= 0) { "SQRT: arg must be non-negative" }
    return sqrt(number)
}

/** RANDOM() – returns a pseudo‑random number in [0, 1). */
private fun fnRANDOM(args: List<Any?>): Any {
    require(args.isEmpty()) { "RANDOM()" }
    return kotlin.random.Random.nextDouble()
}
