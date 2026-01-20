package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.addNumeric
import io.github.ehlyzov.branchline.runtime.divNumeric
import io.github.ehlyzov.branchline.runtime.isNumericValue

class StdAggModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("LENGTH", ::fnLENGTH)
        r.fn("COUNT", ::fnLENGTH)
        r.fn("SUM", ::fnSUM)
        r.fn("AVG", ::fnAVG)
        r.fn("MIN", ::fnMIN)
        r.fn("MAX", ::fnMAX)
    }
}

private fun fnLENGTH(args: List<Any?>): Any {
    require(args.size == 1) { "LENGTH(x)" }
    return when (val v = args[0]) {
        is String -> v.length
        is List<*> -> v.size
        is Map<*, *> -> v.size
        null -> 0
        else -> error("LENGTH: unsupported type ${v::class.simpleName}")
    }
}

private fun requireNumeric(value: Any?, name: String): Any {
    require(isNumericValue(value)) { "$name: all elements must be numbers" }
    return value as Any
}

private fun fnSUM(args: List<Any?>): Any {
    require(args.size == 1) { "SUM(listOfNumbers)" }
    val xs = args[0] as? List<*> ?: error("SUM: arg must be list")
    var acc: Any = 0
    xs.forEach { value ->
        acc = addNumeric(acc, requireNumeric(value, "SUM"))
    }
    return acc
}

private fun fnAVG(args: List<Any?>): Any? {
    require(args.size == 1) { "AVG(listOfNumbers)" }
    val xs = args[0] as? List<*> ?: error("AVG: arg must be list")
    if (xs.isEmpty()) return null
    var acc: Any = 0
    xs.forEach { value ->
        acc = addNumeric(acc, requireNumeric(value, "AVG"))
    }
    return divNumeric(acc, xs.size.toLong())
}

private fun fnMIN(args: List<Any?>): Any? {
    require(args.size == 1) { "MIN(list)" }
    val xs = args[0] as? List<*> ?: error("MIN: arg must be list")
    if (xs.isEmpty()) return null
    return xs.minWithOrNull { a, b -> compareAny(a, b) }
}

private fun fnMAX(args: List<Any?>): Any? {
    require(args.size == 1) { "MAX(list)" }
    val xs = args[0] as? List<*> ?: error("MAX: arg must be list")
    if (xs.isEmpty()) return null
    return xs.maxWithOrNull { a, b -> compareAny(a, b) }
}
