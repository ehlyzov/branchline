package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse

internal actual fun parseBytecodeDecimal(text: String): BLBigDec = blBigDecParse(text)

internal actual fun bytecodeDecimalText(value: BLBigDec): String = value.toString()
