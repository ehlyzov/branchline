package io.github.ehlyzov.branchline.runtime

import java.math.BigDecimal
import java.math.BigInteger

actual fun isBigInt(x: Any?): Boolean = x is BigInteger

actual fun isBigDec(x: Any?): Boolean = x is BigDecimal

actual fun isSafeInteger(value: Long): Boolean = true
