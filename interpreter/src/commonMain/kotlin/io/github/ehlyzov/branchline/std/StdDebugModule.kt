package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent

class StdDebugModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("CHECKPOINT", ::fnCHECKPOINT)
        r.fn("ASSERT", ::fnASSERT)
        r.fn("EXPLAIN", ::fnEXPLAIN)
    }
}

private fun truthy(x: Any?): Boolean = when (x) {
    null -> false
    is Boolean -> x
    is Number -> x.toDouble() != 0.0
    is String -> x.isNotEmpty()
    else -> true
}

private fun fnCHECKPOINT(args: List<Any?>): Any {
    val label = args.firstOrNull()?.toString()
    Debug.tracer?.on(TraceEvent.Call("CHECKPOINT", label, emptyList()))
    return true
}

private fun fnASSERT(args: List<Any?>): Any {
    require(args.isNotEmpty()) { "ASSERT(cond[, msg])" }
    if (!truthy(args[0])) error(args.getOrNull(1)?.toString() ?: "ASSERT failed")
    return true
}

private fun fnEXPLAIN(args: List<Any?>): Any? {
    require(args.size == 1 && args[0] is String) { "EXPLAIN(varName)" }
    return Debug.explain(args[0] as String) ?: mapOf("var" to args[0], "info" to "no provenance")
}

