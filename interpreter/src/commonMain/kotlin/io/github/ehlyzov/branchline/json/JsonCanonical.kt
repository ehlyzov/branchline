package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

public class JsonCanonicalException(message: String) : IllegalArgumentException(message)

public fun formatCanonicalJson(
    value: Any?,
    numberMode: JsonNumberMode = JsonNumberMode.SAFE,
): String {
    val builder = StringBuilder()
    appendCanonicalJson(builder, value, numberMode)
    return builder.toString()
}

@Suppress("CyclomaticComplexMethod")
private fun appendCanonicalJson(
    builder: StringBuilder,
    value: Any?,
    numberMode: JsonNumberMode,
) {
    when (value) {
        null -> builder.append("null")
        is JsonElement -> appendCanonicalJsonElement(builder, value, numberMode)
        is String -> appendJsonString(builder, value)
        is Boolean -> builder.append(if (value) "true" else "false")
        is Long -> appendCanonicalInteger(builder, value, numberMode)
        is BLBigInt -> appendCanonicalBigInt(builder, value, numberMode)
        is BLBigDec -> appendCanonicalBigDec(builder, value, numberMode)
        is I32 -> appendCanonicalInteger(builder, value.v.toLong(), numberMode)
        is I64 -> appendCanonicalInteger(builder, value.v, numberMode)
        is IBig -> appendCanonicalBigInt(builder, value.v, numberMode)
        is Dec -> appendCanonicalBigDec(builder, value.v, numberMode)
        is Number -> appendCanonicalNumberValue(builder, value, numberMode)
        is Map<*, *> -> appendCanonicalObject(builder, value, numberMode)
        is Iterable<*> -> appendCanonicalArray(builder, value.iterator(), numberMode)
        is Array<*> -> appendCanonicalArray(builder, value.iterator(), numberMode)
        is Sequence<*> -> appendCanonicalArray(builder, value.iterator(), numberMode)
        else -> appendJsonString(builder, value.toString())
    }
}

@Suppress("CyclomaticComplexMethod")
private fun appendCanonicalJsonElement(
    builder: StringBuilder,
    element: JsonElement,
    numberMode: JsonNumberMode,
) {
    when (element) {
        is JsonNull -> builder.append("null")
        is JsonPrimitive -> when {
            element.isString -> appendJsonString(builder, element.content)
            element.booleanOrNull != null -> builder.append(if (element.booleanOrNull == true) "true" else "false")
            element.longOrNull != null -> appendCanonicalInteger(builder, element.longOrNull!!, numberMode)
            else -> {
                val doubleValue = element.doubleOrNull
                if (doubleValue != null && doubleValue.isFinite()) {
                    appendCanonicalNumber(builder, doubleValue)
                } else {
                    appendCanonicalPrimitiveLiteral(builder, element, numberMode)
                }
            }
        }
        is JsonArray -> appendCanonicalArray(builder, element.iterator(), numberMode)
        is JsonObject -> appendCanonicalObject(builder, element, numberMode)
    }
}

private fun appendCanonicalObject(builder: StringBuilder, value: Map<*, *>, numberMode: JsonNumberMode) {
    val entries = value.entries.map { entry ->
        (entry.key?.toString() ?: "null") to entry.value
    }.sortedBy { it.first }

    builder.append('{')
    entries.forEachIndexed { index, (key, entryValue) ->
        if (index > 0) builder.append(',')
        appendJsonString(builder, key)
        builder.append(':')
        appendCanonicalJson(builder, entryValue, numberMode)
    }
    builder.append('}')
}

private fun appendCanonicalArray(builder: StringBuilder, iterator: Iterator<*>, numberMode: JsonNumberMode) {
    builder.append('[')
    var first = true
    for (item in iterator) {
        if (!first) builder.append(',')
        appendCanonicalJson(builder, item, numberMode)
        first = false
    }
    builder.append(']')
}

private fun appendJsonString(builder: StringBuilder, value: String) {
    builder.append('"')
    for (ch in value) {
        when {
            ch == '"' -> builder.append("\\\"")
            ch == '\\' -> builder.append("\\\\")
            ch < ' ' -> appendUnicodeEscape(builder, ch.code)
            else -> builder.append(ch)
        }
    }
    builder.append('"')
}

private fun appendUnicodeEscape(builder: StringBuilder, codePoint: Int) {
    builder.append("\\u")
    builder.append(HEX_DIGITS[(codePoint shr 12) and 0xF])
    builder.append(HEX_DIGITS[(codePoint shr 8) and 0xF])
    builder.append(HEX_DIGITS[(codePoint shr 4) and 0xF])
    builder.append(HEX_DIGITS[codePoint and 0xF])
}

private fun appendCanonicalNumber(builder: StringBuilder, value: Double) {
    if (!value.isFinite()) {
        throw JsonCanonicalException("Canonical JSON cannot encode NaN or Infinity")
    }
    if (value == 0.0) {
        builder.append('0')
        return
    }
    val raw = value.toString()
    builder.append(normalizeNumberString(raw))
}

private fun appendCanonicalNumberValue(builder: StringBuilder, value: Number, numberMode: JsonNumberMode) {
    val doubleValue = value.toDouble()
    if (!doubleValue.isFinite()) {
        throw JsonCanonicalException("Canonical JSON cannot encode NaN or Infinity")
    }
    val isWhole = doubleValue % 1.0 == 0.0
    if (isWhole &&
        doubleValue >= Long.MIN_VALUE.toDouble() &&
        doubleValue <= Long.MAX_VALUE.toDouble()
    ) {
        appendCanonicalInteger(builder, doubleValue.toLong(), numberMode)
    } else {
        appendCanonicalNumber(builder, doubleValue)
    }
}

private fun appendCanonicalNumberString(builder: StringBuilder, raw: String) {
    builder.append(normalizeNumberString(raw))
}

private fun appendCanonicalInteger(builder: StringBuilder, value: Long, numberMode: JsonNumberMode) {
    when (numberMode) {
        JsonNumberMode.STRICT -> {
            if (!isJsonSafeInteger(value)) {
                throw JsonCanonicalException("Integer output outside safe JSON range")
            }
            builder.append(value.toString())
        }
        JsonNumberMode.SAFE -> {
            if (isJsonSafeInteger(value)) {
                builder.append(value.toString())
            } else {
                appendJsonString(builder, value.toString())
            }
        }
        JsonNumberMode.EXTENDED -> builder.append(value.toString())
    }
}

private fun appendCanonicalBigInt(builder: StringBuilder, value: BLBigInt, numberMode: JsonNumberMode) {
    when (numberMode) {
        JsonNumberMode.STRICT -> throw JsonCanonicalException("BigInt output requires json-numbers safe or extended")
        JsonNumberMode.SAFE -> appendJsonString(builder, value.toString())
        JsonNumberMode.EXTENDED -> builder.append(value.toString())
    }
}

private fun appendCanonicalBigDec(builder: StringBuilder, value: BLBigDec, numberMode: JsonNumberMode) {
    val rendered = value.toPlainString()
    when (numberMode) {
        JsonNumberMode.STRICT -> throw JsonCanonicalException("BigDecimal output requires json-numbers safe or extended")
        JsonNumberMode.SAFE -> appendJsonString(builder, rendered)
        JsonNumberMode.EXTENDED -> appendCanonicalNumberString(builder, rendered)
    }
}

private fun appendCanonicalPrimitiveLiteral(
    builder: StringBuilder,
    element: JsonPrimitive,
    numberMode: JsonNumberMode,
) {
    val content = element.content
    val parsed = tryParseUnquotedNumber(content)
    if (parsed == null) {
        appendJsonString(builder, content)
        return
    }
    when (parsed) {
        is ParsedNumber.ParsedInt -> appendCanonicalBigInt(builder, parsed.value, numberMode)
        is ParsedNumber.ParsedDec -> appendCanonicalBigDec(builder, parsed.value, numberMode)
    }
}

private sealed interface ParsedNumber {
    data class ParsedInt(val value: BLBigInt) : ParsedNumber
    data class ParsedDec(val value: BLBigDec) : ParsedNumber
}

private fun tryParseUnquotedNumber(value: String): ParsedNumber? {
    return try {
        if (looksDecimal(value)) {
            ParsedNumber.ParsedDec(blBigDecParse(value))
        } else {
            ParsedNumber.ParsedInt(blBigIntParse(value))
        }
    } catch (_: Exception) {
        null
    }
}

private fun looksDecimal(value: String): Boolean =
    value.indexOfAny(charArrayOf('.', 'e', 'E')) >= 0

private fun normalizeNumberString(raw: String): String {
    var text = raw.replace("+", "")
    var sign = ""
    if (text.startsWith('-')) {
        sign = "-"
        text = text.substring(1)
    }

    val expIndex = run {
        val lower = text.indexOf('e')
        val upper = text.indexOf('E')
        when {
            lower == -1 -> upper
            upper == -1 -> lower
            else -> minOf(lower, upper)
        }
    }
    val mantissa = if (expIndex >= 0) text.substring(0, expIndex) else text
    val exp = if (expIndex >= 0) {
        var expText = text.substring(expIndex + 1)
        if (expText.startsWith("+")) {
            expText = expText.substring(1)
        }
        expText.toInt()
    } else {
        0
    }

    val dotIndex = mantissa.indexOf('.')
    val fracDigits = if (dotIndex >= 0) mantissa.length - dotIndex - 1 else 0
    var digits = if (dotIndex >= 0) {
        mantissa.removeRange(dotIndex, dotIndex + 1)
    } else {
        mantissa
    }

    val firstNonZero = digits.indexOfFirst { it != '0' }
    if (firstNonZero == -1) {
        return "0"
    }
    if (firstNonZero > 0) {
        digits = digits.substring(firstNonZero)
    }

    var decimalExp = exp - fracDigits
    while (digits.length > 1 && digits.last() == '0') {
        digits = digits.dropLast(1)
        decimalExp += 1
    }

    val scientificExp = digits.length - 1 + decimalExp
    val useExponent = scientificExp >= 21 || scientificExp <= -7

    val result = if (useExponent) {
        val mantissaText = if (digits.length == 1) {
            digits
        } else {
            digits[0] + "." + digits.substring(1)
        }
        sign + mantissaText + "e" + scientificExp.toString()
    } else if (decimalExp >= 0) {
        sign + digits + "0".repeat(decimalExp)
    } else {
        val pointPos = digits.length + decimalExp
        if (pointPos > 0) {
            sign + digits.substring(0, pointPos) + "." + digits.substring(pointPos)
        } else {
            sign + "0." + "0".repeat(-pointPos) + digits
        }
    }
    return normalizeExponentToken(result)
}

private val HEX_DIGITS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    'A', 'B', 'C', 'D', 'E', 'F'
)

private fun normalizeExponentToken(value: String): String {
    val builder = StringBuilder(value.length)
    var idx = 0
    while (idx < value.length) {
        val ch = value[idx]
        if (ch == 'e' || ch == 'E') {
            builder.append('e')
            if (idx + 1 < value.length && value[idx + 1] == '+') {
                idx += 2
                continue
            }
            idx += 1
            continue
        }
        builder.append(ch)
        idx += 1
    }
    return builder.toString()
}
