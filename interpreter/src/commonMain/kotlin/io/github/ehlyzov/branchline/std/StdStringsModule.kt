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
    val v = args[0]
    return when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        else -> true
    }
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
