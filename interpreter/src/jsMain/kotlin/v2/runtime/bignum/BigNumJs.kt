package v2.runtime.bignum

actual class BLBigInt internal constructor(internal val v: Long) {
    override fun toString(): String = v.toString()
}

actual class BLBigDec internal constructor(internal val v: Double) {
    override fun toString(): String = toPlainString()
}

// --- BLBigInt ---
actual fun blBigIntOfLong(v: Long): BLBigInt = BLBigInt(v)
actual fun blBigIntParse(s: String): BLBigInt = BLBigInt(s.toLong())
actual operator fun BLBigInt.plus(other: BLBigInt): BLBigInt = BLBigInt(this.v + other.v)
actual operator fun BLBigInt.minus(other: BLBigInt): BLBigInt = BLBigInt(this.v - other.v)
actual operator fun BLBigInt.times(other: BLBigInt): BLBigInt = BLBigInt(this.v * other.v)
actual operator fun BLBigInt.div(other: BLBigInt): BLBigInt = BLBigInt(this.v / other.v)
actual operator fun BLBigInt.rem(other: BLBigInt): BLBigInt = BLBigInt(this.v % other.v)
actual operator fun BLBigInt.unaryMinus(): BLBigInt = BLBigInt(-this.v)
actual operator fun BLBigInt.compareTo(other: BLBigInt): Int = this.v.compareTo(other.v)
actual fun BLBigInt.signum(): Int = when {
    this.v > 0L -> 1
    this.v < 0L -> -1
    else -> 0
}
actual fun BLBigInt.toInt(): Int = this.v.toInt()
actual fun BLBigInt.toLong(): Long = this.v
actual fun BLBigInt.bitLength(): Int {
    val x = this.v
    if (x == 0L) return 0
    val abs = if (x < 0) -x else x
    return 64 - abs.countLeadingZeroBits()
}
actual fun BLBigInt.toBLBigDec(): BLBigDec = BLBigDec(this.v.toDouble())

// --- BLBigDec ---
actual fun blBigDecOfLong(v: Long): BLBigDec = BLBigDec(v.toDouble())
actual fun blBigDecOfDouble(v: Double): BLBigDec = BLBigDec(v)
actual fun blBigDecParse(s: String): BLBigDec = BLBigDec(s.toDouble())
actual operator fun BLBigDec.plus(other: BLBigDec): BLBigDec = BLBigDec(this.v + other.v)
actual operator fun BLBigDec.minus(other: BLBigDec): BLBigDec = BLBigDec(this.v - other.v)
actual operator fun BLBigDec.times(other: BLBigDec): BLBigDec = BLBigDec(this.v * other.v)
actual operator fun BLBigDec.div(other: BLBigDec): BLBigDec = BLBigDec(this.v / other.v)
actual operator fun BLBigDec.rem(other: BLBigDec): BLBigDec = BLBigDec(this.v % other.v)
actual operator fun BLBigDec.unaryMinus(): BLBigDec = BLBigDec(-this.v)
actual operator fun BLBigDec.compareTo(other: BLBigDec): Int = this.v.compareTo(other.v)
actual fun BLBigDec.toPlainString(): String = this.v.toString()
actual fun BLBigDec.toBLBigInt(): BLBigInt = BLBigInt(this.v.toLong())
actual fun BLBigDec.toDouble(): Double = this.v
