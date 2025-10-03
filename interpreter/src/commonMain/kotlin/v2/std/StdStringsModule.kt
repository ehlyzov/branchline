package v2.std

import v2.runtime.bignum.blBigDecParse

class StdStringsModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("STRING", ::fnSTRING)
        r.fn("NUMBER", ::fnNUMBER)
        r.fn("BOOLEAN", ::fnBOOLEAN)
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
