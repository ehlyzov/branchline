package io.github.ehlyzov.branchline.runtime

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt

actual fun isBigInt(x: Any?): Boolean = x is BLBigInt

actual fun isBigDec(x: Any?): Boolean = x is BLBigDec

actual fun isSafeInteger(value: Long): Boolean {
    val maxSafe = 9_007_199_254_740_991L
    return value in -maxSafe..maxSafe
}

actual fun isPlatformIntegerNumber(value: Any?): Boolean = when (value) {
    is Long -> true
    is Number -> js("Number.isInteger(value)") as Boolean
    else -> false
}
