package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.NumValue
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.F64
import io.github.ehlyzov.branchline.runtime.isBigDec
import io.github.ehlyzov.branchline.runtime.isBigInt
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.addNumeric
import io.github.ehlyzov.branchline.runtime.divNumeric
import io.github.ehlyzov.branchline.runtime.idivNumeric
import io.github.ehlyzov.branchline.runtime.isNumericValue
import io.github.ehlyzov.branchline.runtime.mulNumeric
import io.github.ehlyzov.branchline.runtime.negateNumeric
import io.github.ehlyzov.branchline.runtime.numericCompare as numericCompareRuntime
import io.github.ehlyzov.branchline.runtime.numericEquals as numericEqualsRuntime
import io.github.ehlyzov.branchline.runtime.remNumeric
import io.github.ehlyzov.branchline.runtime.subNumeric

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
    is F64 -> n.v
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

internal fun isNumeric(x: Any?): Boolean = isNumericValue(x)

internal fun numericEquals(a: Any?, b: Any?): Boolean = numericEqualsRuntime(a, b)

internal fun numericCompare(a: Any?, b: Any?): Int = numericCompareRuntime(a, b)

internal fun addNum(a: Any?, b: Any?): Any = addNumeric(a, b)

internal fun subNum(a: Any?, b: Any?): Any = subNumeric(a, b)

internal fun mulNum(a: Any?, b: Any?): Any = mulNumeric(a, b)

internal fun remNum(a: Any?, b: Any?): Any = remNumeric(a, b)

internal fun divNum(a: Any?, b: Any?): Any = divNumeric(a, b)

internal fun idivNum(a: Any?, b: Any?): Any = idivNumeric(a, b)

internal fun negateNum(value: Any?): Any = negateNumeric(value)
