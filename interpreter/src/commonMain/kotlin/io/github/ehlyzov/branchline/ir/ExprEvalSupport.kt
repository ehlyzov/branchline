package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.NumValue
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.runtime.isBigDec
import io.github.ehlyzov.branchline.runtime.isBigInt
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.bitLength
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.minus
import io.github.ehlyzov.branchline.runtime.bignum.plus
import io.github.ehlyzov.branchline.runtime.bignum.rem
import io.github.ehlyzov.branchline.runtime.bignum.times
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus

public typealias FnValue = (List<Any?>) -> Any?

internal fun Any?.asBool(): Boolean = when {
    this == null -> false
    this is Boolean -> this
    this is Int -> this != 0
    this is Long -> this != 0L
    this is Short -> this.toInt() != 0
    this is Byte -> this.toInt() != 0
    this is Double -> this != 0.0
    this is Float -> this != 0f
    isBigInt(this) -> (this as BLBigInt).signum() != 0
    isBigDec(this) -> (this as BLBigDec).signum() != 0
    else -> true
}

internal fun unwrapNum(n: NumValue): Any = when (n) {
    is I32 -> n.v
    is I64 -> n.v
    is IBig -> n.v
    is Dec -> n.v
}

internal fun unwrapComputedKey(v: Any?): Any = when (v) {
    is String -> v
    is Int -> {
        require(v >= 0) { "Computed key must be non-negative integer" }
        v
    }
    is Long -> {
        require(v >= 0) { "Computed key must be non-negative integer" }
        v
    }
    else -> if (isBigInt(v)) {
        val bi = v as BLBigInt
        require(bi.signum() >= 0) { "Computed key must be non-negative integer" }
        bi
    } else error("Computed key must be string or non-negative integer")
}

internal fun unwrapKey(k: ObjKey): Any = when (k) {
    is ObjKey.Name -> k.v
    is I32 -> k.v
    is I64 -> k.v
    is IBig -> k.v
}

internal fun isNumeric(x: Any?): Boolean = x is Number || isBigInt(x) || isBigDec(x)

internal fun toBLBigInt(n: Any?): BLBigInt = when {
    isBigInt(n) -> n as BLBigInt
    isBigDec(n) -> (n as BLBigDec).toBLBigInt()
    n is Long -> blBigIntOfLong(n)
    n is Int -> blBigIntOfLong(n.toLong())
    n is Number -> blBigIntOfLong(n.toLong())
    else -> error("Expected numeric, got ${n?.let { it::class.simpleName } ?: "null"}")
}

internal fun toBLBigDec(n: Any?): BLBigDec = when {
    isBigDec(n) -> n as BLBigDec
    isBigInt(n) -> (n as BLBigInt).toBLBigDec()
    n is Double -> blBigDecOfDouble(n)
    n is Float -> blBigDecOfDouble(n.toDouble())
    n is Long -> blBigDecOfLong(n)
    n is Int -> blBigDecOfLong(n.toLong())
    n is Number -> blBigDecOfDouble(n.toDouble())
    else -> error("Expected numeric, got ${n?.let { it::class.simpleName } ?: "null"}")
}

internal fun numericEquals(a: Any?, b: Any?): Boolean {
    if (!isNumeric(a) || !isNumeric(b)) return a == b
    val left = a
    val right = b
    val leftIsBig = isBigInt(left) || isBigDec(left)
    val rightIsBig = isBigInt(right) || isBigDec(right)
    if (leftIsBig || rightIsBig) {
        return toBLBigDec(left).compareTo(toBLBigDec(right)) == 0
    }
    return when {
        left is Int && right is Int -> left == right
        left is Long && right is Long -> left == right
        (left is Int || left is Long || left is Short || left is Byte) &&
            (right is Int || right is Long || right is Short || right is Byte) -> toLongPrimitive(left) == toLongPrimitive(right)
        left is Double || right is Double || left is Float || right is Float ->
            (left as Number).toDouble() == (right as Number).toDouble()
        else -> toBLBigDec(left).compareTo(toBLBigDec(right)) == 0
    }
}

internal fun numericCompare(a: Any?, b: Any?): Int {
    require(isNumeric(a) && isNumeric(b)) { "Expected numeric operands" }
    val left = a
    val right = b
    val leftIsBig = isBigInt(left) || isBigDec(left)
    val rightIsBig = isBigInt(right) || isBigDec(right)
    if (leftIsBig || rightIsBig) {
        return toBLBigDec(left).compareTo(toBLBigDec(right))
    }
    return when {
        (left is Int || left is Long || left is Short || left is Byte) &&
            (right is Int || right is Long || right is Short || right is Byte) ->
            toLongPrimitive(left).compareTo(toLongPrimitive(right))
        left is Double || right is Double || left is Float || right is Float ->
            (left as Number).toDouble().compareTo((right as Number).toDouble())
        else -> toBLBigDec(left).compareTo(toBLBigDec(right))
    }
}

internal fun addNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) + toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) + toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() + (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() + (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() + (b as Number).toLong()
    else -> (a as Number).toInt() + (b as Number).toInt()
}

internal fun subNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) - toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) - toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() - (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() - (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() - (b as Number).toLong()
    else -> (a as Number).toInt() - (b as Number).toInt()
}

internal fun mulNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) * toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) * toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() * (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() * (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() * (b as Number).toLong()
    else -> (a as Number).toInt() * (b as Number).toInt()
}

internal fun remNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) % toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) % toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() % (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() % (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() % (b as Number).toLong()
    else -> (a as Number).toInt() % (b as Number).toInt()
}

internal fun divNum(a: Any?, b: Any?): Any {
    if (isBigDec(a) || isBigDec(b)) return toBLBigDec(a) / toBLBigDec(b)
    val ai = toBLBigInt(a)
    val bi = toBLBigInt(b)
    val rem = ai % bi
    return if (rem.signum() == 0) {
        val q = ai / bi
        when {
            q.bitLength() <= 31 -> q.toInt()
            q.bitLength() <= 63 -> q.toLong()
            else -> q
        }
    } else {
        toBLBigDec(a) / toBLBigDec(b)
    }
}

internal fun negateNum(value: Any?): Any {
    require(isNumeric(value)) { "Operator '-' expects number" }
    val v = value
    return when {
        isBigInt(v) -> -(v as BLBigInt)
        isBigDec(v) -> -(v as BLBigDec)
        v is Double -> -v
        v is Float -> -v
        v is Long -> -v
        v is Int -> -v
        else -> -(v as Number).toInt()
    }
}

private fun toLongPrimitive(n: Any?): Long = when (n) {
    is Long -> n
    is Int -> n.toLong()
    is Short -> n.toLong()
    is Byte -> n.toLong()
    else -> error("Expected integer primitive, got ${n?.let { it::class.simpleName } ?: "null"}")
}
