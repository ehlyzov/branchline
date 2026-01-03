package io.github.ehlyzov.branchline.runtime

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong

// Platform-aware type checks to avoid commonMain warnings
expect fun isBigInt(x: Any?): Boolean
expect fun isBigDec(x: Any?): Boolean

fun toBigInt(n: Number): BLBigInt {
    val any = n as Any
    return when {
        any is BLBigInt -> any
        else -> blBigIntOfLong(n.toLong())
    }
}

fun toBigDec(n: Number): BLBigDec {
    val any = n as Any
    return when {
        any is BLBigDec -> any
        any is BLBigInt -> blBigDecOfLong(n.toLong())
        n is Double -> blBigDecOfDouble(n)
        n is Float -> blBigDecOfDouble(n.toDouble())
        else -> blBigDecOfLong(n.toLong())
    }
}
