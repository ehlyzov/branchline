package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfLong
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.plus

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

private fun requireBigDec(value: Any?, name: String): BLBigDec =
    toBigDecOrNull(value) ?: error("$name: all elements must be numbers")

private fun fnSUM(args: List<Any?>): Any {
    require(args.size == 1) { "SUM(listOfNumbers)" }
    val xs = args[0] as? List<*> ?: error("SUM: arg must be list")
    var acc = blBigDecOfLong(0)
    xs.forEach { value ->
        acc = acc + requireBigDec(value, "SUM")
    }
    return acc
}

private fun fnAVG(args: List<Any?>): Any? {
    require(args.size == 1) { "AVG(listOfNumbers)" }
    val xs = args[0] as? List<*> ?: error("AVG: arg must be list")
    if (xs.isEmpty()) return null
    var acc = blBigDecOfLong(0)
    xs.forEach { value ->
        acc = acc + requireBigDec(value, "AVG")
    }
    return acc / blBigDecOfLong(xs.size.toLong())
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
