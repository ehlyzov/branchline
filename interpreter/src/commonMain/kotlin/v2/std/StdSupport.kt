package v2.std

import kotlin.collections.LinkedHashMap
import v2.runtime.bignum.BLBigDec
import v2.runtime.bignum.BLBigInt
import v2.runtime.bignum.blBigDecOfDouble
import v2.runtime.bignum.blBigDecOfLong
import v2.runtime.bignum.blBigDecParse
import v2.runtime.bignum.blBigIntParse
import v2.runtime.bignum.bitLength
import v2.runtime.bignum.signum
import v2.runtime.bignum.toBLBigDec
import v2.runtime.bignum.compareTo
import v2.runtime.bignum.toInt
import v2.runtime.bignum.toLong
import kotlin.comparisons.compareValues

internal fun asIndex(x: Any?): Int = when (x) {
    is Int -> x
    is Long -> {
        require(x in 0..Int.MAX_VALUE.toLong()) { "Index $x out of bounds" }
        x.toInt()
    }
    is BLBigInt -> {
        require(x.signum() >= 0) { "Index $x out of bounds" }
        require(x.bitLength() <= 31) { "Index $x out of bounds" }
        x.toInt()
    }
    is UInt -> x.toInt()
    is ULong -> {
        require(x <= Int.MAX_VALUE.toULong()) { "Index $x out of bounds" }
        x.toInt()
    }
    is String -> {
        try {
            x.toInt()
        } catch (_: NumberFormatException) {
            val big = try {
                blBigIntParse(x)
            } catch (_: IllegalArgumentException) {
                null
            }
            require(big != null && big.signum() >= 0 && big.bitLength() <= 31) { "Index $x out of bounds" }
            big.toInt()
        }
    }
    else -> error("Index must be integer for list")
}

internal fun asObjectKey(x: Any?): Any = when (x) {
    is String -> x
    is Int -> {
        require(x >= 0) { "Object key must be non-negative" }
        x
    }
    is Long -> {
        require(x >= 0) { "Object key must be non-negative" }
        x
    }
    is BLBigInt -> {
        require(x.signum() >= 0) { "Object key must be non-negative" }
        x
    }
    is UInt -> x.toInt()
    is ULong -> {
        require(x <= Long.MAX_VALUE.toULong()) { "Object key must be non-negative" }
        x.toLong()
    }
    is String -> x
    else -> error("Object key must be string or non-negative integer")
}

internal fun clonePut(map: Map<*, *>, key: Any, value: Any?): LinkedHashMap<Any?, Any?> =
    LinkedHashMap<Any?, Any?>(map.size + if (map.containsKey(key)) 0 else 1).apply {
        for ((k, v) in map) this[k] = v
        this[key] = value
    }

internal fun cloneDelete(map: Map<*, *>, key: Any): LinkedHashMap<Any?, Any?> =
    LinkedHashMap<Any?, Any?>(map.size).apply {
        for ((k, v) in map) if (k != key) this[k] = v
    }

internal fun toBigDecOrNull(value: Any?): BLBigDec? = when (value) {
    is BLBigDec -> value
    is BLBigInt -> value.toBLBigDec()
    is Long -> blBigDecOfLong(value)
    is Int -> blBigDecOfLong(value.toLong())
    is Short -> blBigDecOfLong(value.toLong())
    is Byte -> blBigDecOfLong(value.toLong())
    is Double -> blBigDecOfDouble(value)
    is Float -> blBigDecOfDouble(value.toDouble())
    is Number -> blBigDecParse(value.toString())
    is UInt -> blBigDecParse(value.toString())
    is ULong -> blBigDecParse(value.toString())
    is UShort -> blBigDecParse(value.toString())
    is UByte -> blBigDecParse(value.toString())
    is String -> try {
        blBigDecParse(value)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
    else -> null
}

internal fun compareAny(a: Any?, b: Any?): Int {
    val aNum = toBigDecOrNull(a)
    val bNum = toBigDecOrNull(b)
    if (aNum != null && bNum != null) return aNum.compareTo(bNum)
    if (a is Boolean && b is Boolean) {
        return when {
            a == b -> 0
            !a && b -> -1
            else -> 1
        }
    }
    if (a is String && b is String) return compareValues(a, b)
    val aStr = a?.toString() ?: "null"
    val bStr = b?.toString() ?: "null"
    return compareValues(aStr, bStr)
}
