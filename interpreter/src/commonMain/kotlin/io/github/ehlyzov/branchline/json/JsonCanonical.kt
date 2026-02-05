package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

public class JsonCanonicalException(message: String) : IllegalArgumentException(message)

public fun formatCanonicalJson(value: Any?): String {
    val builder = StringBuilder()
    appendCanonicalJson(builder, value)
    return builder.toString()
}

@Suppress("CyclomaticComplexMethod")
private fun appendCanonicalJson(builder: StringBuilder, value: Any?) {
    when (value) {
        null -> builder.append("null")
        is JsonElement -> appendCanonicalJsonElement(builder, value)
        is String -> appendJsonString(builder, value)
        is Boolean -> builder.append(if (value) "true" else "false")
        is Byte -> appendCanonicalNumber(builder, value.toDouble())
        is Short -> appendCanonicalNumber(builder, value.toDouble())
        is Int -> appendCanonicalNumber(builder, value.toDouble())
        is Long -> builder.append(value.toString())
        is Float -> appendCanonicalNumber(builder, value.toDouble())
        is Double -> appendCanonicalNumber(builder, value)
        is BLBigInt -> builder.append(value.toString())
        is BLBigDec -> appendCanonicalNumber(builder, value.toDouble())
        is I32 -> builder.append(value.v.toString())
        is I64 -> builder.append(value.v.toString())
        is IBig -> builder.append(value.v.toString())
        is Dec -> appendCanonicalNumber(builder, value.v.toDouble())
        is Number -> appendCanonicalNumber(builder, value.toDouble())
        is Map<*, *> -> appendCanonicalObject(builder, value)
        is Iterable<*> -> appendCanonicalArray(builder, value.iterator())
        is Array<*> -> appendCanonicalArray(builder, value.iterator())
        is Sequence<*> -> appendCanonicalArray(builder, value.iterator())
        else -> appendJsonString(builder, value.toString())
    }
}

@Suppress("CyclomaticComplexMethod")
private fun appendCanonicalJsonElement(builder: StringBuilder, element: JsonElement) {
    when (element) {
        is JsonNull -> builder.append("null")
        is JsonPrimitive -> when {
            element.isString -> appendJsonString(builder, element.content)
            element.booleanOrNull != null -> builder.append(if (element.booleanOrNull == true) "true" else "false")
            element.longOrNull != null -> builder.append(element.longOrNull.toString())
            element.doubleOrNull != null -> appendCanonicalNumber(builder, element.doubleOrNull!!)
            else -> appendJsonString(builder, element.content)
        }
        is JsonArray -> appendCanonicalArray(builder, element.iterator())
        is JsonObject -> appendCanonicalObject(builder, element)
    }
}

private fun appendCanonicalObject(builder: StringBuilder, value: Map<*, *>) {
    val entries = value.entries.map { entry ->
        (entry.key?.toString() ?: "null") to entry.value
    }.sortedBy { it.first }

    builder.append('{')
    entries.forEachIndexed { index, (key, entryValue) ->
        if (index > 0) builder.append(',')
        appendJsonString(builder, key)
        builder.append(':')
        appendCanonicalJson(builder, entryValue)
    }
    builder.append('}')
}

private fun appendCanonicalArray(builder: StringBuilder, iterator: Iterator<*>) {
    builder.append('[')
    var first = true
    for (item in iterator) {
        if (!first) builder.append(',')
        appendCanonicalJson(builder, item)
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
