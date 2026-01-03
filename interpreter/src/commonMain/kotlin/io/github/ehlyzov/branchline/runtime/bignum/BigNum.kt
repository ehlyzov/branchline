package io.github.ehlyzov.branchline.runtime.bignum

// Minimal BigNum facade for multiplatform. JVM uses java.math via typealiases.

expect class BLBigInt
expect class BLBigDec

// --- BLBigInt ---
expect fun blBigIntOfLong(v: Long): BLBigInt
expect fun blBigIntParse(s: String): BLBigInt
expect operator fun BLBigInt.plus(other: BLBigInt): BLBigInt
expect operator fun BLBigInt.minus(other: BLBigInt): BLBigInt
expect operator fun BLBigInt.times(other: BLBigInt): BLBigInt
expect operator fun BLBigInt.div(other: BLBigInt): BLBigInt
expect operator fun BLBigInt.rem(other: BLBigInt): BLBigInt
expect operator fun BLBigInt.unaryMinus(): BLBigInt
expect operator fun BLBigInt.compareTo(other: BLBigInt): Int
expect fun BLBigInt.signum(): Int
expect fun BLBigInt.toInt(): Int
expect fun BLBigInt.toLong(): Long
expect fun BLBigInt.bitLength(): Int
expect fun BLBigInt.toBLBigDec(): BLBigDec

// --- BLBigDec ---
expect fun blBigDecOfLong(v: Long): BLBigDec
expect fun blBigDecOfDouble(v: Double): BLBigDec
expect fun blBigDecParse(s: String): BLBigDec
expect operator fun BLBigDec.plus(other: BLBigDec): BLBigDec
expect operator fun BLBigDec.minus(other: BLBigDec): BLBigDec
expect operator fun BLBigDec.times(other: BLBigDec): BLBigDec
expect operator fun BLBigDec.div(other: BLBigDec): BLBigDec
expect operator fun BLBigDec.rem(other: BLBigDec): BLBigDec
expect operator fun BLBigDec.unaryMinus(): BLBigDec
expect operator fun BLBigDec.compareTo(other: BLBigDec): Int
expect fun BLBigDec.toPlainString(): String
expect fun BLBigDec.toBLBigInt(): BLBigInt
expect fun BLBigDec.toDouble(): Double
expect fun BLBigDec.signum(): Int

// Shared constants to avoid allocations in hot paths
val BL_BIG_DEC_ZERO: BLBigDec by lazy { blBigDecOfLong(0) }
val BL_BIG_DEC_ONE: BLBigDec by lazy { blBigDecOfLong(1) }
val BL_BIG_INT_ZERO: BLBigInt by lazy { blBigIntOfLong(0) }
val BL_BIG_INT_ONE: BLBigInt by lazy { blBigIntOfLong(1) }
