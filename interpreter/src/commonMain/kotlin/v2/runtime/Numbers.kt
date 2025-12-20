package v2.runtime

import v2.runtime.bignum.BLBigDec
import v2.runtime.bignum.BLBigInt
import v2.runtime.bignum.blBigDecOfDouble
import v2.runtime.bignum.blBigDecOfLong
import v2.runtime.bignum.blBigIntOfLong

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
