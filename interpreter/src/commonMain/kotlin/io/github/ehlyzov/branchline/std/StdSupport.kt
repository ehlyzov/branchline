package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.ir.FnValue
import kotlin.collections.LinkedHashMap
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.bitLength
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.isNumericValue
import io.github.ehlyzov.branchline.runtime.numericCompare
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

internal fun compareAny(a: Any?, b: Any?): Int {
    if (isNumericValue(a) && isNumericValue(b)) return numericCompare(a, b)
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

internal fun truthy(x: Any?): Boolean = when (x) {
    null -> false
    is Boolean -> x
    is Number -> x.toDouble() != 0.0
    is String -> x.isNotEmpty()
    else -> true
}

internal fun asList(name: String, args: List<Any?>, idx: Int): List<*> =
    args.getOrNull(idx) as? List<*> ?: error("$name: arg ${idx + 1} must be list")

internal fun asFn(name: String, args: List<Any?>, idx: Int): FnValue =
    args.getOrNull(idx).toFnValue(name, idx)

internal fun Any?.toFnValue(name: String, idx: Int): FnValue {
    return when (this) {
        is Function1<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            this as FnValue
        }
        else -> null
    } ?: error("$name: arg ${idx + 1} must be function")
}
