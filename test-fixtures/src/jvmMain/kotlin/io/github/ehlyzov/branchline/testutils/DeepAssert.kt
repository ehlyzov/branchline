package io.github.ehlyzov.branchline.testutils

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.collections.get
import kotlin.collections.iterator
import kotlin.test.DefaultAsserter.fail

enum class NumericMode { Strict, Value }

/** Глубокое сравнение деревьев (Map/List/скаляры) с понятным diff и указанием пути. */
fun assertDeepEquals(
    expected: Any?,
    actual: Any?,
    numericMode: NumericMode = NumericMode.Strict,
    message: String? = null
) {
    val diffs = mutableListOf<String>()
    if (!deepEquals(expected, actual, "$", numericMode, diffs)) {
        val msg = buildString {
            if (message != null) append(message).append('\n')
            append("Deep equality failed.\n")
            append("First difference at ").append(diffs.firstOrNull() ?: "<unknown>").append('\n')
            append("Expected: ").append(render(expected)).append('\n')
            append("Actual  : ").append(render(actual)).append('\n')
        }
        fail(msg)
    }
}

private fun deepEquals(
    e: Any?,
    a: Any?,
    path: String,
    mode: NumericMode,
    diffs: MutableList<String>
): Boolean {
    if (e === a) return true
    if (e == null || a == null) {
        if (e != a) diffs += "$path: expected ${render(e)} but was ${render(a)}"
        return e == a
    }

    when (e) {
        is Map<*, *> -> {
            if (a !is Map<*, *>) {
                diffs += "$path: expected Map but was ${a::class.simpleName}"
                return false
            }
            if (e.size != a.size) {
                diffs += "$path: expected map size ${e.size} but was ${a.size}"
                return false
            }
            for ((k, ev) in e) {
                if (!a.containsKey(k)) {
                    diffs += "$path: missing key ${renderKey(k)}"
                    return false
                }
                val av = a[k]
                if (!deepEquals(ev, av, "$path[${renderKey(k)}]", mode, diffs)) return false
            }
            return true
        }

        is List<*> -> {
            if (a !is List<*>) {
                diffs += "$path: expected List but was ${a::class.simpleName}"
                return false
            }
            if (e.size != a.size) {
                diffs += "$path: expected list size ${e.size} but was ${a.size}"
                return false
            }
            for (i in e.indices) {
                if (!deepEquals(e[i], a[i], "$path[$i]", mode, diffs)) return false
            }
            return true
        }

        is BigDecimal -> {
            val eq = when (a) {
                is BigDecimal -> if (mode == NumericMode.Value) e.compareTo(a) == 0 else e == a
                is Number -> mode == NumericMode.Value && e.compareTo(toBigDecimal(a)) == 0
                else -> false
            }
            if (!eq) diffs += "$path: expected $e but was ${render(a)}"
            return eq
        }

        is Int, is Long, is BigInteger, is Short, is Byte -> {
            val eq = when (mode) {
                NumericMode.Strict -> e == a
                NumericMode.Value -> (a is Number) && toBigInteger(e as Number) == toBigInteger(a)
            }
            if (!eq) diffs += "$path: expected ${render(e)} (${e::class.simpleName}) but was ${render(a)} (${a::class.simpleName})"
            return eq
        }

        else -> {
            val eq = e == a
            if (!eq) diffs += "$path: expected ${render(e)} but was ${render(a)}"
            return eq
        }
    }
}

private fun toBigInteger(n: Number): BigInteger = when (n) {
    is BigInteger -> n
    is BigDecimal -> n.toBigIntegerExact()
    is Long -> BigInteger.valueOf(n)
    is Int -> BigInteger.valueOf(n.toLong())
    is Short -> BigInteger.valueOf(n.toLong())
    is Byte -> BigInteger.valueOf(n.toLong())
    else -> BigInteger.valueOf(n.toLong())
}

private fun toBigDecimal(n: Number): BigDecimal = when (n) {
    is BigDecimal -> n
    is BigInteger -> n.toBigDecimal()
    is Long -> BigDecimal.valueOf(n)
    is Int -> BigDecimal.valueOf(n.toLong())
    is Short -> BigDecimal.valueOf(n.toLong())
    is Byte -> BigDecimal.valueOf(n.toLong())
    else -> BigDecimal(n.toString())
}

private fun render(x: Any?): String = when (x) {
    is String -> "\"$x\""
    is Map<*, *> -> x.entries.joinToString(prefix = "{", postfix = "}") {
        "${render(it.key)}=${render(it.value)}"
    }
    is List<*> -> x.joinToString(prefix = "[", postfix = "]") { render(it) }
    else -> x.toString()
}

private fun renderKey(k: Any?): String = when (k) {
    is String -> "\"$k\""
    else -> k.toString()
}
