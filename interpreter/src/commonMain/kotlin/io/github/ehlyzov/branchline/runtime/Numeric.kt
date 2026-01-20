package io.github.ehlyzov.branchline.runtime

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.minus
import io.github.ehlyzov.branchline.runtime.bignum.plus
import io.github.ehlyzov.branchline.runtime.bignum.rem
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.times
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus

enum class NumericKind {
    I,
    F,
    BI,
    BD,
}

private const val SMALL_INT_MIN = -128
private const val SMALL_INT_MAX = 128
private val SMALL_BIGINT_CACHE = Array(SMALL_INT_MAX - SMALL_INT_MIN + 1) { index ->
    blBigIntOfLong((SMALL_INT_MIN + index).toLong())
}
private val SMALL_BIGDEC_CACHE = Array(SMALL_INT_MAX - SMALL_INT_MIN + 1) { index ->
    blBigDecOfLong((SMALL_INT_MIN + index).toLong())
}

fun bigIntOfLongCached(value: Long): BLBigInt {
    return if (value in SMALL_INT_MIN.toLong()..SMALL_INT_MAX.toLong()) {
        SMALL_BIGINT_CACHE[(value - SMALL_INT_MIN).toInt()]
    } else {
        blBigIntOfLong(value)
    }
}

fun bigDecOfLongCached(value: Long): BLBigDec {
    return if (value in SMALL_INT_MIN.toLong()..SMALL_INT_MAX.toLong()) {
        SMALL_BIGDEC_CACHE[(value - SMALL_INT_MIN).toInt()]
    } else {
        blBigDecOfLong(value)
    }
}

fun isNumericValue(value: Any?): Boolean =
    value is Number || isBigInt(value) || isBigDec(value)

fun numericKindOf(value: Any?): NumericKind? = when {
    isBigDec(value) -> NumericKind.BD
    isBigInt(value) -> NumericKind.BI
    isPlatformIntegerNumber(value) -> NumericKind.I
    value is Double || value is Float -> NumericKind.F
    value is Number -> NumericKind.F
    else -> null
}

fun numericEquals(a: Any?, b: Any?): Boolean {
    if (!isNumericValue(a) || !isNumericValue(b)) return a == b
    return numericCompare(a, b) == 0
}

fun numericCompare(a: Any?, b: Any?): Int {
    val leftKind = requireNumericKind(a, "compare")
    val rightKind = requireNumericKind(b, "compare")
    ensureNoDecimalFloatMix(leftKind, rightKind, "compare")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> {
            toBigDecExact(a).compareTo(toBigDecExact(b))
        }
        leftKind == NumericKind.F || rightKind == NumericKind.F -> {
            toDoubleValue(a).compareTo(toDoubleValue(b))
        }
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> {
            toBigIntExact(a).compareTo(toBigIntExact(b))
        }
        else -> toLongValue(a).compareTo(toLongValue(b))
    }
}

fun addNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '+'")
    val rightKind = requireNumericKind(b, "Operator '+'")
    ensureNoDecimalFloatMix(leftKind, rightKind, "Operator '+'")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> toBigDecExact(a) + toBigDecExact(b)
        leftKind == NumericKind.F || rightKind == NumericKind.F -> toDoubleValue(a) + toDoubleValue(b)
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> toBigIntExact(a) + toBigIntExact(b)
        else -> addIntegers(a, b)
    }
}

fun subNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '-'")
    val rightKind = requireNumericKind(b, "Operator '-'")
    ensureNoDecimalFloatMix(leftKind, rightKind, "Operator '-'")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> toBigDecExact(a) - toBigDecExact(b)
        leftKind == NumericKind.F || rightKind == NumericKind.F -> toDoubleValue(a) - toDoubleValue(b)
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> toBigIntExact(a) - toBigIntExact(b)
        else -> subIntegers(a, b)
    }
}

fun mulNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '*'")
    val rightKind = requireNumericKind(b, "Operator '*'")
    ensureNoDecimalFloatMix(leftKind, rightKind, "Operator '*'")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> toBigDecExact(a) * toBigDecExact(b)
        leftKind == NumericKind.F || rightKind == NumericKind.F -> toDoubleValue(a) * toDoubleValue(b)
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> toBigIntExact(a) * toBigIntExact(b)
        else -> mulIntegers(a, b)
    }
}

fun divNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '/'")
    val rightKind = requireNumericKind(b, "Operator '/'")
    ensureNoDecimalFloatMix(leftKind, rightKind, "Operator '/'")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> {
            toBigDecExact(a) / toBigDecExact(b)
        }
        else -> {
            val divisor = toDoubleValue(b)
            require(divisor != 0.0) { "Division by zero" }
            toDoubleValue(a) / divisor
        }
    }
}

fun idivNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '//'")
    val rightKind = requireNumericKind(b, "Operator '//'")
    require(leftKind != NumericKind.BD && rightKind != NumericKind.BD) {
        "Operator '//' does not support decimal values"
    }
    require(leftKind != NumericKind.F && rightKind != NumericKind.F) {
        "Operator '//' expects integer values; wrap floats with INT()"
    }
    return when {
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> {
            val divisor = toBigIntExact(b)
            require(divisor.signum() != 0) { "Division by zero" }
            toBigIntExact(a) / divisor
        }
        else -> {
            val left = toLongValue(a)
            val right = toLongValue(b)
            require(right != 0L) { "Division by zero" }
            idivIntegersFast(left, right, preferInt = isIntLike(a) && isIntLike(b))
        }
    }
}

fun remNumeric(a: Any?, b: Any?): Any {
    val leftKind = requireNumericKind(a, "Operator '%'")
    val rightKind = requireNumericKind(b, "Operator '%'")
    ensureNoDecimalFloatMix(leftKind, rightKind, "Operator '%'")
    return when {
        leftKind == NumericKind.BD || rightKind == NumericKind.BD -> toBigDecExact(a) % toBigDecExact(b)
        leftKind == NumericKind.F || rightKind == NumericKind.F -> {
            val divisor = toDoubleValue(b)
            require(divisor != 0.0) { "Division by zero" }
            toDoubleValue(a) % divisor
        }
        leftKind == NumericKind.BI || rightKind == NumericKind.BI -> {
            val divisor = toBigIntExact(b)
            require(divisor.signum() != 0) { "Division by zero" }
            toBigIntExact(a) % divisor
        }
        else -> {
            val left = toLongValue(a)
            val right = toLongValue(b)
            require(right != 0L) { "Division by zero" }
            narrowInteger(left % right, preferInt = isIntLike(a) && isIntLike(b))
        }
    }
}

fun negateNumeric(value: Any?): Any {
    val kind = requireNumericKind(value, "Operator '-'")
    return when (kind) {
        NumericKind.BD -> -(value as BLBigDec)
        NumericKind.BI -> -(value as BLBigInt)
        NumericKind.F -> -toDoubleValue(value)
        NumericKind.I -> {
            val v = toLongValue(value)
            if (v == Long.MIN_VALUE) {
                -(bigIntOfLongCached(v))
            } else {
                narrowInteger(-v, preferInt = isIntLike(value))
            }
        }
    }
}

fun addIntegersFast(left: Long, right: Long, preferInt: Boolean): Any {
    if (!isSafeInteger(left) || !isSafeInteger(right)) {
        return bigIntOfLongCached(left) + bigIntOfLongCached(right)
    }
    val result = safeAddLong(left, right)
    return if (result != null && isSafeInteger(result)) {
        narrowInteger(result, preferInt)
    } else {
        bigIntOfLongCached(left) + bigIntOfLongCached(right)
    }
}

fun subIntegersFast(left: Long, right: Long, preferInt: Boolean): Any {
    if (!isSafeInteger(left) || !isSafeInteger(right)) {
        return bigIntOfLongCached(left) - bigIntOfLongCached(right)
    }
    val result = safeSubLong(left, right)
    return if (result != null && isSafeInteger(result)) {
        narrowInteger(result, preferInt)
    } else {
        bigIntOfLongCached(left) - bigIntOfLongCached(right)
    }
}

fun mulIntegersFast(left: Long, right: Long, preferInt: Boolean): Any {
    if (!isSafeInteger(left) || !isSafeInteger(right)) {
        return bigIntOfLongCached(left) * bigIntOfLongCached(right)
    }
    val result = safeMulLong(left, right)
    return if (result != null && isSafeInteger(result)) {
        narrowInteger(result, preferInt)
    } else {
        bigIntOfLongCached(left) * bigIntOfLongCached(right)
    }
}

fun remIntegersFast(left: Long, right: Long, preferInt: Boolean): Any {
    require(right != 0L) { "Division by zero" }
    val result = left % right
    return narrowInteger(result, preferInt)
}

fun idivIntegersFast(left: Long, right: Long, preferInt: Boolean): Any {
    require(right != 0L) { "Division by zero" }
    if (left == Long.MIN_VALUE && right == -1L) {
        return blBigIntOfLong(left) / blBigIntOfLong(right)
    }
    val result = left / right
    return narrowInteger(result, preferInt)
}

private fun requireNumericKind(value: Any?, opName: String): NumericKind {
    return numericKindOf(value) ?: error("$opName expects numeric operands")
}

private fun ensureNoDecimalFloatMix(left: NumericKind, right: NumericKind, opName: String) {
    require(!(left == NumericKind.BD && right == NumericKind.F) && !(left == NumericKind.F && right == NumericKind.BD)) {
        "$opName does not allow mixing decimal and float; wrap floats with DEC()"
    }
}

private fun toBigIntExact(value: Any?): BLBigInt = when (value) {
    is BLBigInt -> value
    is BLBigDec -> value.toBLBigInt()
    is Long -> bigIntOfLongCached(value)
    is Int -> bigIntOfLongCached(value.toLong())
    is Short -> bigIntOfLongCached(value.toLong())
    is Byte -> bigIntOfLongCached(value.toLong())
    is Number -> bigIntOfLongCached(value.toLong())
    else -> error("Expected integer numeric")
}

private fun toBigDecExact(value: Any?): BLBigDec = when (value) {
    is BLBigDec -> value
    is BLBigInt -> value.toBLBigDec()
    is Long -> bigDecOfLongCached(value)
    is Int -> bigDecOfLongCached(value.toLong())
    is Short -> bigDecOfLongCached(value.toLong())
    is Byte -> bigDecOfLongCached(value.toLong())
    else -> error("Expected decimal-compatible numeric")
}

private fun toDoubleValue(value: Any?): Double = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Long -> value.toDouble()
    is Int -> value.toDouble()
    is Short -> value.toDouble()
    is Byte -> value.toDouble()
    is BLBigInt -> value.toBLBigDec().toDouble()
    is BLBigDec -> value.toDouble()
    is Number -> value.toDouble()
    else -> error("Expected numeric")
}

private fun addIntegers(a: Any?, b: Any?): Any {
    val left = toLongValue(a)
    val right = toLongValue(b)
    return addIntegersFast(left, right, preferInt = isIntLike(a) && isIntLike(b))
}

private fun subIntegers(a: Any?, b: Any?): Any {
    val left = toLongValue(a)
    val right = toLongValue(b)
    return subIntegersFast(left, right, preferInt = isIntLike(a) && isIntLike(b))
}

private fun mulIntegers(a: Any?, b: Any?): Any {
    val left = toLongValue(a)
    val right = toLongValue(b)
    return mulIntegersFast(left, right, preferInt = isIntLike(a) && isIntLike(b))
}

private fun safeAddLong(a: Long, b: Long): Long? {
    val result = a + b
    return if (((a xor result) and (b xor result)) < 0L) null else result
}

private fun safeSubLong(a: Long, b: Long): Long? {
    val result = a - b
    return if (((a xor b) and (a xor result)) < 0L) null else result
}

private fun safeMulLong(a: Long, b: Long): Long? {
    if (a == 0L || b == 0L) return 0L
    val result = a * b
    return if (result / a == b) result else null
}

private fun toLongValue(value: Any?): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Short -> value.toLong()
    is Byte -> value.toLong()
    is Number -> value.toLong()
    is BLBigInt -> value.toLong()
    else -> error("Expected integer numeric")
}

private fun isIntLike(value: Any?): Boolean =
    value is Int || value is Short || value is Byte

private fun narrowInteger(value: Long, preferInt: Boolean): Any {
    if (preferInt && value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        return value.toInt()
    }
    return value
}
