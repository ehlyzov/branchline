package io.github.ehlyzov.branchline.json

public class JsonInputException(message: String) : IllegalArgumentException(message)

public fun parseJsonValue(text: String, options: JsonParseOptions = JsonParseOptions()): Any? {
    if (text.isBlank()) return null
    val parser = JsonInputParser(text, options)
    val value = parser.parseRootValue()
    return if (options.keyMode == JsonKeyMode.NUMERIC) convertNumericKeys(value) else value
}

public fun parseJsonObjectInput(text: String, options: JsonParseOptions = JsonParseOptions()): Map<String, Any?> {
    if (text.isBlank()) return emptyMap()
    val parser = JsonInputParser(text, options)
    val value = parser.parseRootValue()
    if (value !is Map<*, *>) {
        throw JsonInputException("Input JSON must be an object")
    }
    val result = LinkedHashMap<String, Any?>(value.size)
    for ((key, entry) in value) {
        if (key !is String) {
            throw JsonInputException("Input JSON keys must be strings")
        }
        val parsedValue = if (options.keyMode == JsonKeyMode.NUMERIC) convertNumericKeys(entry) else entry
        result[key] = parsedValue
    }
    return result
}

private class JsonInputParser(
    private val text: String,
    private val options: JsonParseOptions,
) {
    private val length = text.length
    private var index = 0

    private val numberMode = options.numberMode

    fun parseRootValue(): Any? {
        skipWhitespace()
        if (index >= length) return null
        val value = readValue()
        skipWhitespace()
        if (index < length) {
            error("Unexpected trailing content")
        }
        return value
    }

    private fun readValue(): Any? {
        skipWhitespace()
        val ch = peekChar() ?: error("Unexpected end of input")
        return when (ch) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> {
                readLiteral("true")
                true
            }
            'f' -> {
                readLiteral("false")
                false
            }
            'n' -> {
                readLiteral("null")
                null
            }
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected character '$ch'")
        }
    }

    private fun readObject(): Map<String, Any?> {
        expectChar('{')
        skipWhitespace()
        val content = LinkedHashMap<String, Any?>()
        if (peekChar() == '}') {
            index += 1
            return content
        }
        while (true) {
            skipWhitespace()
            if (peekChar() != '"') {
                error("Expected object key")
            }
            val key = readString()
            skipWhitespace()
            expectChar(':')
            skipWhitespace()
            val value = readValue()
            if (content.containsKey(key)) {
                throw JsonInputException("Duplicate JSON object key '$key'")
            }
            content[key] = value
            skipWhitespace()
            val next = peekChar() ?: error("Unterminated object")
            when (next) {
                ',' -> {
                    index += 1
                    skipWhitespace()
                    if (peekChar() == '}') {
                        error("Trailing comma in object")
                    }
                }
                '}' -> {
                    index += 1
                    return content
                }
                else -> error("Expected ',' or '}'")
            }
        }
    }

    private fun readArray(): List<Any?> {
        expectChar('[')
        skipWhitespace()
        val items = ArrayList<Any?>()
        if (peekChar() == ']') {
            index += 1
            return items
        }
        while (true) {
            val value = readValue()
            items.add(value)
            skipWhitespace()
            val next = peekChar() ?: error("Unterminated array")
            when (next) {
                ',' -> {
                    index += 1
                    skipWhitespace()
                    if (peekChar() == ']') {
                        error("Trailing comma in array")
                    }
                }
                ']' -> {
                    index += 1
                    return items
                }
                else -> error("Expected ',' or ']'")
            }
        }
    }

    private fun readString(): String {
        expectChar('"')
        val builder = StringBuilder()
        while (true) {
            if (index >= length) {
                error("Unterminated string")
            }
            val ch = text[index++]
            when (ch) {
                '"' -> return builder.toString()
                '\\' -> appendEscape(builder)
                else -> {
                    if (ch < ' ') {
                        error("Unescaped control character in string")
                    }
                    builder.append(ch)
                }
            }
        }
    }

    private fun appendEscape(builder: StringBuilder) {
        if (index >= length) {
            error("Unterminated escape sequence")
        }
        val ch = text[index++]
        when (ch) {
            '"', '\\', '/' -> builder.append(ch)
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'u' -> appendUnicodeEscape(builder)
            else -> error("Invalid escape sequence '\\$ch'")
        }
    }

    private fun appendUnicodeEscape(builder: StringBuilder) {
        val codePoint = readUnicodeCodePoint()
        if (codePoint in 0xD800..0xDBFF) {
            val high = codePoint
            if (index + 2 >= length || text[index] != '\\' || text[index + 1] != 'u') {
                error("Invalid unicode surrogate pair")
            }
            index += 2
            val low = readUnicodeCodePoint()
            if (low !in 0xDC00..0xDFFF) {
                error("Invalid unicode surrogate pair")
            }
            appendCodePoint(builder, ((high - 0xD800) shl 10) + (low - 0xDC00) + 0x10000)
        } else {
            appendCodePoint(builder, codePoint)
        }
    }

    private fun readUnicodeCodePoint(): Int {
        if (index + 4 > length) {
            error("Invalid unicode escape")
        }
        var value = 0
        repeat(4) {
            val digit = hexDigitValue(text[index++])
            if (digit < 0) {
                error("Invalid unicode escape")
            }
            value = (value shl 4) or digit
        }
        return value
    }

    private fun readNumber(): Any {
        val start = index
        if (peekChar() == '-') {
            index += 1
        }
        val first = peekChar() ?: error("Unexpected end of input")
        if (first == '0') {
            index += 1
        } else if (first in '1'..'9') {
            index += 1
            while (peekChar()?.isDigit() == true) {
                index += 1
            }
        } else {
            error("Invalid number")
        }
        var hasFraction = false
        if (peekChar() == '.') {
            hasFraction = true
            index += 1
            if (peekChar()?.isDigit() != true) {
                error("Invalid number")
            }
            while (peekChar()?.isDigit() == true) {
                index += 1
            }
        }
        var hasExponent = false
        val expChar = peekChar()
        if (expChar == 'e' || expChar == 'E') {
            hasExponent = true
            index += 1
            val sign = peekChar()
            if (sign == '+' || sign == '-') {
                index += 1
            }
            if (peekChar()?.isDigit() != true) {
                error("Invalid number")
            }
            while (peekChar()?.isDigit() == true) {
                index += 1
            }
        }
        val token = text.substring(start, index)
        return if (!hasFraction && !hasExponent) {
            parseIntegerToken(token)
        } else {
            parseDecimalToken(token)
        }
    }

    private fun parseIntegerToken(token: String): Any {
        val parsed = token.toLongOrNull()
        if (parsed == null) {
            if (numberMode == JsonNumberMode.STRICT) {
                throw JsonInputException("Integer outside supported range '$token'")
            }
            return io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse(token)
        }
        if (!isJsonSafeInteger(parsed)) {
            if (numberMode == JsonNumberMode.STRICT) {
                throw JsonInputException("Integer outside safe JSON range '$token'")
            }
            return io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong(parsed)
        }
        return parsed
    }

    private fun parseDecimalToken(token: String): Any {
        val requiresBigDec = requiresBigDec(token)
        if (requiresBigDec) {
            if (numberMode == JsonNumberMode.STRICT) {
                throw JsonInputException("Decimal outside safe JSON precision '$token'")
            }
            return io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse(token)
        }
        val value = token.toDoubleOrNull() ?: throw JsonInputException("Invalid number '$token'")
        if (!value.isFinite()) {
            if (numberMode == JsonNumberMode.STRICT) {
                throw JsonInputException("Decimal outside safe JSON range '$token'")
            }
            return io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse(token)
        }
        return value
    }

    private fun requiresBigDec(token: String): Boolean {
        val trimmed = token.trimStart('+', '-')
        val expIndex = trimmed.indexOfAny(charArrayOf('e', 'E'))
        val mantissa = if (expIndex >= 0) trimmed.substring(0, expIndex) else trimmed
        val digits = mantissa.replace(".", "").trimStart('0')
        val significant = if (digits.isEmpty()) 0 else digits.length
        return significant > 15
    }

    private fun readLiteral(literal: String) {
        if (!text.regionMatches(index, literal, 0, literal.length, ignoreCase = false)) {
            error("Expected '$literal'")
        }
        index += literal.length
    }

    private fun skipWhitespace() {
        while (index < length) {
            when (text[index]) {
                ' ', '\t', '\n', '\r' -> index += 1
                else -> return
            }
        }
    }

    private fun peekChar(): Char? = if (index < length) text[index] else null

    private fun expectChar(expected: Char) {
        val actual = peekChar() ?: error("Unexpected end of input")
        if (actual != expected) {
            error("Expected '$expected' but found '$actual'")
        }
        index += 1
    }

    private fun error(message: String): Nothing {
        val position = if (index < length) index + 1 else length + 1
        throw JsonInputException("$message at position $position")
    }
}

private fun appendCodePoint(builder: StringBuilder, codePoint: Int) {
    if (codePoint <= 0xFFFF) {
        builder.append(codePoint.toChar())
        return
    }
    val adjusted = codePoint - 0x10000
    val high = ((adjusted shr 10) + 0xD800).toChar()
    val low = ((adjusted and 0x3FF) + 0xDC00).toChar()
    builder.append(high)
    builder.append(low)
}

private fun hexDigitValue(ch: Char): Int = when (ch) {
    in '0'..'9' -> ch.code - '0'.code
    in 'a'..'f' -> ch.code - 'a'.code + 10
    in 'A'..'F' -> ch.code - 'A'.code + 10
    else -> -1
}

private fun convertNumericKeys(value: Any?): Any? = when (value) {
    is Map<*, *> -> {
        val out = LinkedHashMap<Any, Any?>(value.size)
        for ((rawKey, rawValue) in value) {
            val key = if (rawKey is String) parseNumericKey(rawKey) else rawKey ?: "null"
            out[key] = convertNumericKeys(rawValue)
        }
        out
    }
    is Iterable<*> -> value.map { convertNumericKeys(it) }
    is Array<*> -> value.map { convertNumericKeys(it) }
    else -> value
}

private fun parseNumericKey(key: String): Any {
    if (!isNumericKeyCandidate(key)) return key
    key.toIntOrNull()?.let { return it }
    key.toLongOrNull()?.let { return it }
    return io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse(key)
}

private fun isNumericKeyCandidate(key: String): Boolean {
    if (key.isEmpty()) return false
    if (key == "0") return true
    if (key[0] == '0') return false
    for (ch in key) {
        if (ch !in '0'..'9') return false
    }
    return true
}
