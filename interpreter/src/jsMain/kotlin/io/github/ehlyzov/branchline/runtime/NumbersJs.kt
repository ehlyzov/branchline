package io.github.ehlyzov.branchline.runtime

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt

actual fun isBigInt(x: Any?): Boolean = x is BLBigInt

actual fun isBigDec(x: Any?): Boolean = x is BLBigDec

