package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfDouble
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString

class StdStringsModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("STRING", ::fnSTRING)
        r.fn("NUMBER", ::fnNUMBER)
        r.fn("BOOLEAN", ::fnBOOLEAN)
        r.fn("INT", ::fnINT)
        r.fn("PARSE_INT", ::fnPARSE_INT)
        r.fn("SUBSTRING", ::fnSUBSTRING)
        r.fn("CONTAINS", ::fnCONTAINS)
        r.fn("MATCH", ::fnMATCH)
        r.fn("REPLACE", ::fnREPLACE)
        r.fn("SPLIT", ::fnSPLIT)
        r.fn("JOIN", ::fnJOIN)
        r.fn("UPPER", ::fnUPPER)
        r.fn("LOWER", ::fnLOWER)
        r.fn("TRIM", ::fnTRIM)
        r.fn("FORMAT", ::fnFORMAT)
        r.fn("SUBSTRING_BEFORE", ::fnSUBSTRING_BEFORE)
        r.fn("SUBSTRING_AFTER", ::fnSUBSTRING_AFTER)
        r.fn("PAD", ::fnPAD)
    }
}

private fun fnSTRING(args: List<Any?>): Any? {
    require(args.size == 1) { "STRING(x)" }
    val v = args[0]
    return v?.toString()
}

private fun fnNUMBER(args: List<Any?>): Any? {
    require(args.size == 1) { "NUMBER(x)" }
    val v = args[0] ?: return null
    return when (v) {
        is Number -> v
        is String -> try {
            blBigDecParse(v)
        } catch (_: IllegalArgumentException) {
            error("NUMBER: cannot parse '$v'")
        } catch (_: NumberFormatException) {
            error("NUMBER: cannot parse '$v'")
        }

        is Boolean -> if (v) 1 else 0
        else -> error("NUMBER: unsupported type ${v::class.simpleName}")
    }
}

private fun fnBOOLEAN(args: List<Any?>): Any {
    require(args.size == 1) { "BOOLEAN(x)" }
    return truthy(args[0])
}

private fun fnINT(args: List<Any?>): Any? {
    require(args.size == 1) { "INT(x)" }
    val v = args[0] ?: return null
    return when (v) {
        is Boolean -> if (v) 1 else 0
        else -> narrowInt(requireIntegral(v, "INT"))
    }
}

private fun fnPARSE_INT(args: List<Any?>): Any? {
    require(args.size == 1 || args.size == 2) { "PARSE_INT(x[, default])" }
    val default = if (args.size == 2) args[1] else 0
    val v = args[0] ?: return default
    return when (v) {
        is String -> {
            val parsed = parseDigits(v) ?: return default
            narrowInt(parsed)
        }

        is Boolean -> if (v) 1 else 0
        else -> narrowInt(requireIntegral(v, "PARSE_INT"))
    }
}

private fun fnSUBSTRING(args: List<Any?>): Any {
    require(args.size in 2..3) { "SUBSTRING(str, start[, len])" }
    val s = args[0] as? String ?: error("SUBSTRING: first arg must be string")
    val start = asIndex(args[1]).coerceAtLeast(0)
    val len = if (args.size == 3) asIndex(args[2]).coerceAtLeast(0) else (s.length - start)
    val end = (start + len).coerceAtMost(s.length)
    require(start <= s.length) { "SUBSTRING: start > length" }
    return s.substring(start, end)
}

/**
 * SUBSTRING_BEFORE(str, chars) – returns the substring before the first occurrence of `chars`
 * or the original string if it does not contain `chars`:contentReference[oaicite:4]{index=4}.
 */
private fun fnSUBSTRING_BEFORE(args: List<Any?>): Any {
    require(args.size == 2) { "SUBSTRING_BEFORE(str, chars)" }
    val s = args[0] as? String ?: error("SUBSTRING_BEFORE: first arg must be string")
    val chars = args[1] as? String ?: error("SUBSTRING_BEFORE: second arg must be string")
    val idx = s.indexOf(chars)
    return if (idx < 0) s else s.substring(0, idx)
}

/**
 * SUBSTRING_AFTER(str, chars) – returns the substring after the first occurrence of `chars`
 * or the original string if it does not contain `chars`:contentReference[oaicite:5]{index=5}.
 */
private fun fnSUBSTRING_AFTER(args: List<Any?>): Any {
    require(args.size == 2) { "SUBSTRING_AFTER(str, chars)" }
    val s = args[0] as? String ?: error("SUBSTRING_AFTER: first arg must be string")
    val chars = args[1] as? String ?: error("SUBSTRING_AFTER: second arg must be string")
    val idx = s.indexOf(chars)
    return if (idx < 0) s else s.substring(idx + chars.length)
}

private fun fnCONTAINS(args: List<Any?>): Any {
    require(args.size == 2) { "CONTAINS(str, substr)" }
    val s = args[0] as? String ?: error("CONTAINS: first arg must be string")
    val sub = args[1]?.toString() ?: return false
    return s.contains(sub)
}

private fun fnMATCH(args: List<Any?>): Any {
    require(args.size == 2) { "MATCH(str, pattern)" }
    val s = args[0] as? String ?: error("MATCH: first arg must be string")
    val pat = args[1]?.toString() ?: ""
    val regex = pat.toRegex()
    return regex.findAll(s).map { it.value }.toList()
}

private fun fnREPLACE(args: List<Any?>): Any {
    require(args.size == 3) { "REPLACE(str, pattern, repl)" }
    val s = args[0] as? String ?: error("REPLACE: first arg must be string")
    val pat = args[1]?.toString() ?: ""
    val repl = args[2]?.toString() ?: ""
    return s.replace(pat.toRegex(), repl)
}


/**
 * PAD(str, width[, padChars]) – returns `str` padded on the left or right to reach the
 * absolute `width`.  A positive `width` pads on the right; a negative width pads on
 * the left.  `padChars` specifies the padding characters (defaults to a single space)
 *:contentReference[oaicite:6]{index=6}.
 */
private fun fnPAD(args: List<Any?>): Any {
    require(args.size in 2..3) { "PAD(str, width[, padChars])" }
    val s = args[0] as? String ?: error("PAD: first arg must be string")
    val width = (args[1] as? Number ?: error("PAD: width must be numeric")).toInt()
    val padChars = args.getOrNull(2)?.toString() ?: " "
    val target = kotlin.math.abs(width)
    if (s.length >= target) return s
    val padNeeded = target - s.length
    // Build a padding string repeating padChars as necessary
    val repeated = buildString {
        while (length < padNeeded) append(padChars)
    }.substring(0, padNeeded)
    return if (width > 0) s + repeated else repeated + s
}

private fun fnSPLIT(args: List<Any?>): Any {
    require(args.size == 2) { "SPLIT(str, sep)" }
    val s = args[0] as? String ?: error("SPLIT: first arg must be string")
    val sep = args[1]?.toString() ?: ""
    return s.split(sep)
}

private fun fnJOIN(args: List<Any?>): Any {
    require(args.size == 1 || args.size == 2) { "JOIN(list, sep) or JOIN(list)" }
    val xs = args[0] as? List<*> ?: error("JOIN: first arg must be list")
    val sep = if (args.size >= 2) args[1]?.toString() ?: "" else ""
    return xs.joinToString(sep) { it?.toString() ?: "" }
}

private fun fnUPPER(args: List<Any?>): Any {
    require(args.size == 1) { "UPPER(str)" }
    val s = args[0] as? String ?: error("UPPER: arg must be string")
    return s.uppercase()
}

private fun fnLOWER(args: List<Any?>): Any {
    require(args.size == 1) { "LOWER(str)" }
    val s = args[0] as? String ?: error("LOWER: arg must be string")
    return s.lowercase()
}

private fun fnTRIM(args: List<Any?>): Any {
    require(args.size == 1) { "TRIM(str)" }
    val s = args[0] as? String ?: error("TRIM: arg must be string")
    return s.trim()
}

private fun fnFORMAT(args: List<Any?>): Any {
    require(args.size == 2) { "FORMAT(template, args)" }
    val template = args[0] as? String ?: error("FORMAT: template must be string")
    val values = args[1]
    val list = values as? List<*>
    val map = values as? Map<*, *>
    if (list == null && map == null) {
        error("FORMAT: args must be list or object")
    }
    val out = StringBuilder(template.length)
    var i = 0
    while (i < template.length) {
        val ch = template[i]
        when (ch) {
            '{' -> {
                if (i + 1 < template.length && template[i + 1] == '{') {
                    out.append('{')
                    i += 2
                    continue
                }
                val end = template.indexOf('}', startIndex = i + 1)
                if (end == -1) {
                    out.append('{')
                    i += 1
                    continue
                }
                val key = template.substring(i + 1, end)
                val resolved = resolveFormatValue(key, list, map)
                if (resolved.found) {
                    out.append(resolved.value?.toString() ?: "null")
                } else {
                    out.append('{').append(key).append('}')
                }
                i = end + 1
            }
            '}' -> {
                if (i + 1 < template.length && template[i + 1] == '}') {
                    out.append('}')
                    i += 2
                } else {
                    out.append('}')
                    i += 1
                }
            }
            else -> {
                out.append(ch)
                i += 1
            }
        }
    }
    return out.toString()
}

private data class FormatValue(val found: Boolean, val value: Any?)

private fun resolveFormatValue(
    key: String,
    list: List<*>?,
    map: Map<*, *>?,
): FormatValue {
    if (list != null) {
        val index = key.toIntOrNull()
        if (index != null && index in list.indices) {
            return FormatValue(true, list[index])
        }
    }
    if (map != null) {
        val match = map.keys.firstOrNull { it?.toString() == key }
        if (match != null) {
            return FormatValue(true, map[match])
        }
    }
    return FormatValue(false, null)
}

private val intMin = blBigIntParse(Int.MIN_VALUE.toString())
private val intMax = blBigIntParse(Int.MAX_VALUE.toString())
private val longMin = blBigIntParse(Long.MIN_VALUE.toString())
private val longMax = blBigIntParse(Long.MAX_VALUE.toString())

private fun narrowInt(value: BLBigInt): Any = when {
    value >= intMin && value <= intMax -> value.toInt()
    value >= longMin && value <= longMax -> value.toLong()
    else -> value
}

private fun parseDigits(text: String): BLBigInt? {
    val digits = StringBuilder(text.length)
    for (ch in text) {
        if (ch in '0'..'9') {
            digits.append(ch)
        }
    }
    if (digits.isEmpty()) return null
    return blBigIntParse(digits.toString())
}

private fun requireIntegral(value: Any?, fnName: String): BLBigInt = when (value) {
    is BLBigInt -> value
    is BLBigDec -> requireWhole(value, fnName)
    is Int -> blBigIntOfLong(value.toLong())
    is Long -> blBigIntOfLong(value)
    is Short -> blBigIntOfLong(value.toLong())
    is Byte -> blBigIntOfLong(value.toLong())
    is Double -> requireWhole(blBigDecOfDouble(value), fnName)
    is Float -> requireWhole(blBigDecOfDouble(value.toDouble()), fnName)
    is UInt -> blBigIntParse(value.toString())
    is ULong -> blBigIntParse(value.toString())
    is UShort -> blBigIntParse(value.toString())
    is UByte -> blBigIntParse(value.toString())
    is String -> try {
        blBigIntParse(value)
    } catch (_: IllegalArgumentException) {
        error("$fnName: cannot parse '$value' as int")
    } catch (_: NumberFormatException) {
        error("$fnName: cannot parse '$value' as int")
    }

    else -> error("$fnName: unsupported type ${value?.let { it::class.simpleName } ?: "null"}")
}

private fun requireWhole(value: BLBigDec, fnName: String): BLBigInt {
    val asInt = value.toBLBigInt()
    require(asInt.toBLBigDec().compareTo(value) == 0) {
        "$fnName: expected integer, got ${value.toPlainString()}"
    }
    return asInt
}
