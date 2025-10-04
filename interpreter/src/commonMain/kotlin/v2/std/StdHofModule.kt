package v2.std

// FnValue is a function receiving positional args from the runtime/HOFs.
private typealias FnValue = (List<Any?>) -> Any?

class StdHofModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("MAP", ::fnMAP)
        r.fn("FILTER", ::fnFILTER)
        r.fn("REDUCE", ::fnREDUCE)
        r.fn("SOME", ::fnSOME)
        r.fn("EVERY", ::fnEVERY)
        r.fn("FIND", ::fnFIND)
        r.fn("APPLY", ::fnAPPLY)
        r.fn("IS_FUNCTION", ::fnIS_FUNCTION)
    }
}

private fun truthy(x: Any?): Boolean = when (x) {
    null -> false
    is Boolean -> x
    is Number -> x.toDouble() != 0.0
    is String -> x.isNotEmpty()
    else -> true
}

private fun asList(name: String, args: List<Any?>, idx: Int): List<*> =
    args.getOrNull(idx) as? List<*> ?: error("$name: arg ${idx + 1} must be list")

private fun asFn(name: String, args: List<Any?>, idx: Int): FnValue =
    args.getOrNull(idx).toFnValue(name, idx)

private fun fnMAP(args: List<Any?>): Any {
    require(args.size == 2) { "MAP(list, fn)" }
    val src = asList("MAP", args, 0)
    val f = asFn("MAP", args, 1)
    val out = ArrayList<Any?>(src.size)
    src.forEachIndexed { i, v -> out += f(listOf(v, i, src)) }
    return out
}

private fun fnFILTER(args: List<Any?>): Any {
    require(args.size == 2) { "FILTER(list, fn)" }
    val src = asList("FILTER", args, 0)
    val f = asFn("FILTER", args, 1)
    val out = ArrayList<Any?>(src.size)
    src.forEachIndexed { i, v -> if (truthy(f(listOf(v, i, src)))) out += v }
    return out
}

private fun fnFIND(args: List<Any?>): Any? {
    require(args.size == 2) { "FIND(list, fn)" }
    val src = asList("FIND", args, 0)
    val f = asFn("FIND", args, 1)
    for (i in src.indices) if (truthy(f(listOf(src[i], i, src)))) return src[i]
    return null
}

private fun fnSOME(args: List<Any?>): Any {
    require(args.size == 2) { "SOME(list, fn)" }
    val src = asList("SOME", args, 0)
    val f = asFn("SOME", args, 1)
    for (i in src.indices) if (truthy(f(listOf(src[i], i, src)))) return true
    return false
}

private fun fnEVERY(args: List<Any?>): Any {
    require(args.size == 2) { "EVERY(list, fn)" }
    val src = asList("EVERY", args, 0)
    val f = asFn("EVERY", args, 1)
    for (i in src.indices) if (!truthy(f(listOf(src[i], i, src)))) return false
    return true
}

private fun fnREDUCE(args: List<Any?>): Any? {
    require(args.size == 3) { "REDUCE(list, init, fn)" }
    val src = asList("REDUCE", args, 0)
    var acc = args[1]
    val f = asFn("REDUCE", args, 2)
    for (i in src.indices) acc = f(listOf(acc, src[i], i, src))
    return acc
}

private fun fnAPPLY(args: List<Any?>): Any? {
    require(args.isNotEmpty()) { "APPLY(fn, ...)" }
    val fn = args[0].toFnValue("APPLY", 0)
    return fn(args.drop(1))
}

private fun fnIS_FUNCTION(args: List<Any?>): Boolean {
    require(args.size == 1) { "IS_FUNCTION(x)" }
    return args[0] is Function1<*, *>
}

private fun Any?.toFnValue(name: String, idx: Int): FnValue {
    return when (this) {
        is Function1<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            this as FnValue
        }
        else -> null
    } ?: error("$name: arg ${idx + 1} must be function")
}
