package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.toDouble

internal actual fun parseBytecodeDecimal(text: String): BLBigDec {
    val number = text.toDoubleOrNull()
    require(number != null && number.isFinite()) { "BigDec exceeds the JavaScript finite Double representation" }
    require(normalizedDecimal(text) == normalizedDecimal(number.toString())) {
        "BigDec would lose precision in the JavaScript Double representation"
    }
    return blBigDecParse(text)
}

internal actual fun bytecodeDecimalText(value: BLBigDec): String {
    val number = value.toDouble()
    require(number.isFinite()) { "Cannot persist non-finite JavaScript BigDec" }
    return if (number == 0.0 && 1.0 / number < 0.0) "-0.0" else value.toString()
}

// Compare decimal values without expanding exponents or allocating exponent-sized
// strings. Scale is intentionally not a property of the JS Double-backed type.
private fun normalizedDecimal(text: String): Triple<Boolean, String, Long> {
    val exponentAt = text.indexOfFirst { it == 'e' || it == 'E' }
    val coefficient = if (exponentAt < 0) text else text.substring(0, exponentAt)
    val exponent = if (exponentAt < 0) 0L else text.substring(exponentAt + 1).toLongOrNull()
        ?: throw IllegalArgumentException("Decimal exponent exceeds the JavaScript representation")
    val dotAt = coefficient.indexOf('.')
    val fractionDigits = if (dotAt < 0) 0 else coefficient.length - dotAt - 1
    val digits = coefficient.removePrefix("+").removePrefix("-").replace(".", "").trimStart('0')
    if (digits.isEmpty()) return Triple(false, "0", 0L)
    val significant = digits.trimEnd('0')
    val adjustment = -fractionDigits.toLong() + digits.length - significant.length
    require((adjustment <= 0 || exponent <= Long.MAX_VALUE - adjustment) &&
        (adjustment >= 0 || exponent >= Long.MIN_VALUE - adjustment)) { "Decimal exponent is out of range" }
    return Triple(coefficient.startsWith('-'), significant, exponent + adjustment)
}
