package v2.ir

import v2.*
import v2.debug.Debug
import v2.debug.TraceEvent
import v2.debug.Tracer
import v2.std.SharedStore
import v2.std.blockingAwait
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import v2.runtime.isBigDec
import v2.runtime.isBigInt
import v2.runtime.bignum.BLBigDec
import v2.runtime.bignum.BLBigInt
import v2.runtime.bignum.blBigDecOfDouble
import v2.runtime.bignum.blBigDecOfLong
import v2.runtime.bignum.blBigIntOfLong
import v2.runtime.bignum.signum
import v2.runtime.bignum.bitLength
import v2.runtime.bignum.compareTo
import v2.runtime.bignum.div
import v2.runtime.bignum.minus
import v2.runtime.bignum.plus
import v2.runtime.bignum.rem
import v2.runtime.bignum.times
import v2.runtime.bignum.toInt
import v2.runtime.bignum.toBLBigDec
import v2.runtime.bignum.toBLBigInt
import v2.runtime.bignum.toLong
import v2.runtime.bignum.unaryMinus

/* ───────────────── numeric helpers (top-level, unchanged API) ───────────────── */
// Removed MathContext; BLBigDec ops are provided via expect/actual operators.

private fun isNumeric(x: Any?): Boolean = x is Number || isBigInt(x) || isBigDec(x)

private fun toBLBigInt(n: Any?): BLBigInt = when {
    isBigInt(n) -> n as BLBigInt
    isBigDec(n) -> (n as BLBigDec).toBLBigInt()
    n is Long -> blBigIntOfLong(n)
    n is Int -> blBigIntOfLong(n.toLong())
    n is Number -> blBigIntOfLong(n.toLong())
    else -> error("Expected numeric, got ${n?.let { it::class.simpleName } ?: "null"}")
}

private fun toBLBigDec(n: Any?): BLBigDec = when {
    isBigDec(n) -> n as BLBigDec
    isBigInt(n) -> (n as BLBigInt).toBLBigDec()
    n is Double -> blBigDecOfDouble(n)
    n is Float -> blBigDecOfDouble(n.toDouble())
    n is Long -> blBigDecOfLong(n)
    n is Int -> blBigDecOfLong(n.toLong())
    n is Number -> blBigDecOfDouble(n.toDouble())
    else -> error("Expected numeric, got ${n?.let { it::class.simpleName } ?: "null"}")
}

private fun addNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) + toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) + toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() + (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() + (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() + (b as Number).toLong()
    else -> (a as Number).toInt() + (b as Number).toInt()
}

private fun subNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) - toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) - toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() - (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() - (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() - (b as Number).toLong()
    else -> (a as Number).toInt() - (b as Number).toInt()
}

private fun mulNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) * toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) * toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() * (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() * (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() * (b as Number).toLong()
    else -> (a as Number).toInt() * (b as Number).toInt()
}

private fun remNum(a: Any?, b: Any?): Any = when {
    isBigInt(a) || isBigInt(b) -> toBLBigInt(a) % toBLBigInt(b)
    isBigDec(a) || isBigDec(b) -> toBLBigDec(a) % toBLBigDec(b)
    a is Double || b is Double -> (a as Number).toDouble() % (b as Number).toDouble()
    a is Float || b is Float -> (a as Number).toFloat() % (b as Number).toFloat()
    a is Long || b is Long -> (a as Number).toLong() % (b as Number).toLong()
    else -> (a as Number).toInt() % (b as Number).toInt()
}

/** Division: if both are integers and divide evenly → return integer; otherwise BLBigDec(DECIMAL128). */
private fun divNum(a: Any?, b: Any?): Any {
    if (isBigDec(a) || isBigDec(b)) return toBLBigDec(a) / toBLBigDec(b)
    val ai = toBLBigInt(a)
    val bi = toBLBigInt(b)
    val rem = ai % bi
    return if (rem.signum() == 0) {
        val q = ai / bi
        when {
            q.bitLength() <= 31 -> q.toInt()
            q.bitLength() <= 63 -> q.toLong()
            else -> q
        }
    } else {
        toBLBigDec(a) / toBLBigDec(b)
    }
}

/* ───────────────── small shared helpers (top-level) ───────────────── */
fun Any?.asBool(): Boolean = when {
    this == null -> false
    this is Boolean -> this
    this is Number -> this.toDouble() != 0.0
    isBigInt(this) -> (this as BLBigInt).signum() != 0
    isBigDec(this) -> toBLBigDec(this).compareTo(blBigDecOfLong(0)) != 0
    else -> true
}

private fun negateNum(value: Any?): Any {
    require(isNumeric(value)) { "Operator '-' expects number" }
    val v = value
    return when {
        isBigInt(v) -> -(v as BLBigInt)
        isBigDec(v) -> -(v as BLBigDec)
        v is Double -> -v
        v is Float -> -v
        v is Long -> -v
        v is Int -> -v
        else -> -(v as Number).toInt()
    }
}

internal fun toListOrEmpty(x: Any?): List<Map<String, Any?>> = when (x) {
    null -> emptyList()
    is List<*> -> x.mapIndexed { index, value -> value.toStringKeyedMap("list[$index]") }
    is Map<*, *> -> listOf(x.toStringKeyedMap("value"))
    else -> error("Expected object or list, got ${x::class.simpleName}")
}

private fun Any?.toStringKeyedMap(context: String): Map<String, Any?> {
    val map = this as? Map<*, *> ?: error("$context must be an object")
    val result = LinkedHashMap<String, Any?>(map.size)
    for ((key, value) in map) {
        result[key?.toString() ?: "null"] = value
    }
    return result
}

private fun Any?.toFnValue(): FnValue? = when (this) {
    is Function1<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        (this as FnValue)
    }
    else -> null
}

internal fun normalizeOut(list: List<Map<String, Any?>>): Any? = when (list.size) {
    0 -> null
    1 -> list.first()
    else -> list
}

private fun unwrapKey(k: ObjKey): Any = when (k) {
    is ObjKey.Name -> k.v
    is I32 -> k.v
    is I64 -> k.v
    is IBig -> k.v
}

private fun unwrapNum(n: NumValue): Any = when (n) {
    is I32 -> n.v
    is I64 -> n.v
    is IBig -> n.v
    is Dec -> n.v
}

fun unwrapComputedKey(v: Any?): Any = when (v) {
    is String -> v
    is Int -> {
        require(v >= 0) { "Computed key must be non-negative integer" }
        v
    }
    is Long -> {
        require(v >= 0) { "Computed key must be non-negative integer" }
        v
    }
    else -> if (isBigInt(v)) {
        val bi = v as BLBigInt
        require(bi.signum() >= 0) { "Computed key must be non-negative integer" }
        bi
    } else error("Computed key must be string or non-negative integer")
}

/* ───────────────── aliases ───────────────── */
typealias FnValue = (List<Any?>) -> Any?
private typealias Env = MutableMap<String, Any?>

/* ───────────────── engine ───────────────── */
private class Evaluator(
    private val hostFns: Map<String, (List<Any?>) -> Any?>,
    private val funcs: Map<String, FuncDecl>,
    private val reg: TransformRegistry,
    private val tracer: Tracer?,
    private val sharedStore: SharedStore? = null
) {
    // Optional tiny cache for compiled FUNC bodies
    private val compiledFuncs = HashMap<String, List<IRNode>>()

    /* —— tracing wrappers —— */
    private inline fun <T> tracedEval(expr: Expr, crossinline block: () -> T): T {
        val t = Debug.tracer // <-- read now
        if (t?.opts?.includeEval == true) t.on(TraceEvent.EvalEnter(expr))
        return try {
            val v = block()
            if (t?.opts?.includeEval == true) t.on(TraceEvent.EvalExit(expr, v))
            v
        } catch (ex: Throwable) {
            t?.on(TraceEvent.Error("eval ${expr::class.simpleName}", ex))
            throw ex
        }
    }

    private inline fun <T> tracedCall(kind: String, name: String?, args: List<Any?>, crossinline inv: () -> T): T {
        val t = Debug.tracer
        if (t?.opts?.includeCalls == true) t.on(TraceEvent.Call(kind, name, args))
        return try {
            val r = inv()
            if (t?.opts?.includeCalls == true) t.on(TraceEvent.Return(kind, name, r))
            r
        } catch (ex: Throwable) {
            t?.on(TraceEvent.Error("call $kind ${name ?: "<lambda>"}", ex))
            throw ex
        }
    }

    private fun traceRead(name: String, value: Any?) {
        val t = Debug.tracer
        if (t != null && (t.opts.watch.isEmpty() || name in t.opts.watch)) {
            t.on(TraceEvent.Read(name, value))
        }
    }

    private fun renderNextPathSegment(container: Any?, segLabel: String, isDynamic: Boolean): String = when (container) {
        is List<*> -> "[$segLabel]"
        else -> if (isDynamic && segLabel.any { it == '.' || it == '[' || it == ']' }) ".$segLabel" else ".$segLabel"
    }

    /* —— function IR executor (LET/RETURN/IF/FOR only) —— */
    private data class ExecResult(val returned: Boolean, val value: Any?)

    private fun execFuncIR(ir: List<IRNode>, e: Env): ExecResult {
        for (n in ir) {
            when (n) {
                is IRLet -> e[n.name] = eval(n.expr, e)
                is IRReturn -> return ExecResult(true, n.value?.let { eval(it, e) })
                is IRIf -> {
                    val cond = eval(n.condition, e).asBool()
                    val branch = if (cond) n.thenBody else (n.elseBody ?: emptyList())
                    val res = execFuncIR(branch, e)
                    if (res.returned) return res
                }
                is IRForEach -> {
                    val iter = eval(n.iterable, e) as? Iterable<*>
                        ?: error("func FOR EACH iterable must be list")
                    val snap = HashMap(e)
                    for (item in iter) {
                        e.clear()
                        e.putAll(snap)
                        e[n.varName] = item
                        val res = execFuncIR(n.body, e)
                        if (res.returned) return res
                    }
                }
                else -> error("Only LET/RETURN/IF/FOR in FUNC body")
            }
        }
        return ExecResult(false, null)
    }

    /* —— lambda construction —— */
    private fun handleLambda(e: LambdaExpr, env: Env): FnValue {
        val captured = HashMap(env)
        val compiled: List<IRNode>? =
            (e.body as? BlockBody)?.let { ToIR(funcs = funcs, hostFns = hostFns).compile(it.block.statements) }

        return { args: List<Any?> ->
            val local = HashMap(captured).apply { e.params.zip(args).forEach { (p, v) -> this[p] = v } }
            tracedCall("LAMBDA", null, args) {
                when (val b = e.body) {
                    is ExprBody -> eval(b.expr, local)
                    is BlockBody -> {
                        val ir = compiled ?: emptyList()
                        execFuncIR(ir, local).value
                    }
                }
            }
        }
    }

    /* —— function & host function calls —— */
    private fun handleFuncCall(c: CallExpr, env: Env): Any? {
        val args = c.args.map { eval(it, env) }

        // host fn?
        hostFns[c.callee.name]?.let { return tracedCall("HOST", c.callee.name, args) { it(args) } }

        // value-captured lambda in env?
        env[c.callee.name].toFnValue()?.let { fn ->
            return tracedCall("CALL", c.callee.name, args) { fn(args) }
        }

        // declared FUNC
        val fd = funcs[c.callee.name] ?: error("FUNC '${c.callee.name}' undefined")
        // Functions do not capture outer variables; only parameters are available
        val local = HashMap<String, Any?>().apply { fd.params.zip(args).forEach { (p, v) -> this[p] = v } }
        return tracedCall("FUNC", fd.name, args) {
            when (val body = fd.body) {
                is ExprBody -> eval(body.expr, local)
                is BlockBody -> {
                    val irFn = compiledFuncs.getOrPut(fd.name) {
                        ToIR(funcs = funcs, hostFns = hostFns).compile(body.block.statements)
                    }
                    execFuncIR(irFn, local).value
                }
            }
        }
    }

    /* —— binary ops (incl. comparison/logic/pipe/concat) —— */
    private fun handleBinary(e: BinaryExpr, env: Env): Any? {
        val l = eval(e.left, env)
        return when (e.token.type) {
            TokenType.PLUS -> {
                val r = eval(e.right, env)
                if (l is String || r is String) {
                    l.toString() + r.toString()
                } else {
                    require(isNumeric(l) && isNumeric(r)) { "Operator '+' expects numbers or strings" }
                    addNum(l, r)
                }
            }
            TokenType.MINUS -> {
                val r = eval(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '-' expects numbers" }
                subNum(l, r)
            }
            TokenType.STAR -> {
                val r = eval(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '*' expects numbers" }
                mulNum(l, r)
            }
            TokenType.SLASH -> {
                val r = eval(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '/' expects numbers" }
                divNum(l, r)
            }
            TokenType.PERCENT -> {
                val r = eval(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '%' expects numbers" }
                remNum(l, r)
            }
            TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE -> {
                val r = eval(e.right, env)
                val cmp = if (isNumeric(l) && isNumeric(r)) {
                    toBLBigDec(l).compareTo(toBLBigDec(r))
                } else {
                    l.toString().compareTo(r.toString())
                }
                when (e.token.type) {
                    TokenType.LT -> cmp < 0
                    TokenType.LE -> cmp <= 0
                    TokenType.GT -> cmp > 0
                    else -> cmp >= 0
                }
            }
            TokenType.CONCAT -> {
                val r = eval(e.right, env)
                require(l is List<*> && r is List<*>) { "Operator '++' expects two lists" }
                ArrayList<Any?>(l.size + r.size).apply {
                    addAll(l)
                    addAll(r)
                }
            }
            TokenType.EQ -> l == eval(e.right, env)
            TokenType.NEQ -> l != eval(e.right, env)
            TokenType.AND -> if (!l.asBool()) false else eval(e.right, env).asBool()
            TokenType.OR -> if (l.asBool()) true else eval(e.right, env).asBool()
            TokenType.COALESCE -> l ?: eval(e.right, env)
            else -> error("Unknown binary op ${e.token.lexeme}")
        }
    }

    /* —— access helpers (unified) —— */
    private fun stepStatic(container: Any?, key: ObjKey): Any? = when (container) {
        is Map<*, *> -> container[unwrapKey(key)]
        is List<*> -> {
            val i = when (key) {
                is ObjKey.Name -> error("Cannot use name '${key.v}' on list")
                is I32 -> key.v
                is I64 -> {
                    require(key.v in 0..Int.MAX_VALUE.toLong())
                    key.v.toInt()
                }
                is IBig -> {
                    val bi = key.v
                    require(bi.signum() >= 0 && bi <= blBigIntOfLong(Int.MAX_VALUE.toLong()))
                    bi.toInt()
                }
            }
            require(i in 0 until container.size) { "Index $i out of bounds 0..${container.size - 1}" }
            container[i]
        }
        else -> error("Indexing supported only for list or object")
    }

    private fun stepDynamic(container: Any?, dynKey: Any?): Any? = when (container) {
        is Map<*, *> -> {
            val k: Any = when (dynKey) {
                is String -> dynKey
                is Int -> {
                    require(dynKey >= 0)
                    dynKey
                }
                is Long -> {
                    require(dynKey >= 0)
                    dynKey
                }
                is BLBigInt -> {
                    require(dynKey.signum() >= 0)
                    dynKey
                }
                else -> error("Object key must be string or non-negative integer")
            }
            container[k]
        }
        is List<*> -> {
            val i: Int = when (dynKey) {
                is Int -> dynKey
                is Long -> {
                    require(dynKey in 0..Int.MAX_VALUE.toLong())
                    dynKey.toInt()
                }
                is BLBigInt -> {
                    require(dynKey.signum() >= 0 && dynKey <= blBigIntOfLong(Int.MAX_VALUE.toLong()))
                    dynKey.toInt()
                }
                else -> error("Index must be integer for list")
            }
            require(i in 0 until container.size) { "Index $i out of bounds 0..${container.size - 1}" }
            container[i]
        }
        else -> error("Indexing supported only for list or object")
    }

    /* —— per-node handlers —— */
    private fun handleNumberLiteral(e: NumberLiteral, env: Env): Any = unwrapNum(e.value)
    private fun handleString(e: StringExpr, env: Env): Any = e.value
    private fun handleBool(e: BoolExpr, env: Env): Any = e.value
    private fun handleNull(e: NullLiteral, env: Env): Any? = null

    private fun handleIdentifier(e: IdentifierExpr, env: Env): Any? {
        // Special-case used in tests to simulate failure in TRY/CATCH.
        if (e.name == "fail") throw IllegalStateException("boom")
        val value = env[e.name]
        traceRead(e.name, value)
        return value
    }

    private fun handleArray(e: ArrayExpr, env: Env): List<Any?> = e.elements.map { eval(it, env) }

    private fun handleArrayComp(e: ArrayCompExpr, env: Env): List<Any?> {
        val iterVal = eval(e.iterable, env)
        val iterable: Iterable<*> = when (iterVal) {
            is Iterable<*> -> iterVal
            is Sequence<*> -> iterVal.asIterable()
            else -> error("Array comprehension expects list/iterable/sequence")
        }
        val out = ArrayList<Any?>()
        val snap = HashMap(env)
        for (item in iterable) {
            env.clear()
            env.putAll(snap)
            env[e.varName] = item
            if (e.where != null && !eval(e.where, env).asBool()) continue
            out += eval(e.mapExpr, env)
        }
        return out
    }

    private fun handleUnary(e: UnaryExpr, env: Env): Any? = when (e.token.type) {
        TokenType.MINUS -> negateNum(eval(e.expr, env))
        TokenType.BANG -> !eval(e.expr, env).asBool()
        TokenType.AWAIT -> eval(e.expr, env) // no async in demo
        TokenType.SUSPEND -> throw UnsupportedOperationException("'suspend' not supported in stream demo")
        else -> error("unary ${e.token.lexeme}")
    }

    private fun handleIfElse(e: IfElseExpr, env: Env): Any? {
        val chosen: Expr = if (eval(e.condition, env).asBool()) e.thenBranch else e.elseBranch
        return when (chosen) {
            is IdentifierExpr -> {
                eval(chosen, env)
            }
            is CallExpr -> {
                handleFuncCall(chosen, env)
            }
            else -> eval(chosen, env)
        }
    }

    private fun handleAccess(e: AccessExpr, env: Env): Any? {
        // вычисляем базу (handleIdentifier уже сам эмитит Read(e.name, ...) при включённом трассере)
        var cur = eval(e.base, env)

        for (seg in e.segs) {
            cur = when (seg) {
                is AccessSeg.Static -> stepStatic(cur, seg.key)
                is AccessSeg.Dynamic -> stepDynamic(cur, eval(seg.keyExpr, env))
            }
        }
        return cur
    }

    private fun handleAccessTraced(e: AccessExpr, env: Env): Any? {
        // вычисляем базу (handleIdentifier уже сам эмитит Read(e.name, ...) при включённом трассере)
        var cur = eval(e.base, env)
        var pathSoFar = (e.base as? IdentifierExpr)?.name

        for (seg in e.segs) {
            when (seg) {
                is AccessSeg.Static -> {
                    val next = stepStatic(cur, seg.key)

                    if (pathSoFar != null) {
                        val segLabel: String = when (cur) {
                            is Map<*, *> -> unwrapKey(seg.key).toString()
                            is List<*> -> when (seg.key) {
                                is ObjKey.Name -> error("Cannot use name '${seg.key.v}' on list")
                                is I32 -> seg.key.v.toString()
                                is I64 -> seg.key.v.toString()
                                is IBig -> seg.key.v.toString()
                            }
                            else -> "<non-container>"
                        }
                        pathSoFar += renderNextPathSegment(cur, segLabel, isDynamic = false)
                        traceRead(pathSoFar, next)
                    }

                    cur = next
                }

                is AccessSeg.Dynamic -> {
                    val dynKey = eval(seg.keyExpr, env)
                    val next = stepDynamic(cur, dynKey)

                    if (pathSoFar != null) {
                        val segLabel: String = when (cur) {
                            is Map<*, *> -> when (dynKey) {
                                is String, is Int, is Long -> dynKey.toString()
                                else -> if (isBigInt(dynKey)) dynKey.toString() else "<key>"
                            }
                            is List<*> -> when (dynKey) {
                                is Int, is Long -> dynKey.toString()
                                else -> if (isBigInt(dynKey)) dynKey.toString() else "<idx>"
                            }
                            else -> "<non-container>"
                        }
                        pathSoFar += renderNextPathSegment(cur, segLabel, isDynamic = true)
                        traceRead(pathSoFar, next)
                    }

                    cur = next
                }
            }
        }
        return cur
    }

    private fun handleCall(c: CallExpr, env: Env): Any? = handleFuncCall(c, env)

    private fun handleObject(e: ObjectExpr, env: Env): Map<Any, Any?> {
        val out = LinkedHashMap<Any, Any?>()
        e.fields.forEach { p ->
            when (p) {
                is ComputedProperty -> {
                    val fieldName: Any = unwrapComputedKey(eval(p.keyExpr, env))
                    out[fieldName] = eval(p.value, env)
                }
                is LiteralProperty -> out[unwrapKey(p.key)] = eval(p.value, env)
            }
        }
        return out
    }

    private fun handleInvoke(e: InvokeExpr, env: Env): Any? {
        val fn = eval(e.target, env).toFnValue() ?: error("Value is not callable")
        val argv = e.args.map { eval(it, env) }
        return tracedCall("CALL", null, argv) { fn(argv) }
    }

    private fun handleSharedStateAwait(e: SharedStateAwaitExpr, env: Env): Any? {
        val store = sharedStore ?: error("SharedStore not available for await operation")
        return blockingAwait(store, e.resource, e.key)
    }

    /* —— dispatcher —— */
    fun eval(e: Expr, env: Env): Any? = tracedEval(e) {
        when (e) {
            is NumberLiteral -> handleNumberLiteral(e, env)
            is StringExpr -> handleString(e, env)
            is BoolExpr -> handleBool(e, env)
            is NullLiteral -> handleNull(e, env)

            is IdentifierExpr -> handleIdentifier(e, env)
            is ArrayExpr -> handleArray(e, env)
            is ArrayCompExpr -> handleArrayComp(e, env)
            is UnaryExpr -> handleUnary(e, env)
            is BinaryExpr -> handleBinary(e, env)
            is IfElseExpr -> handleIfElse(e, env)
            is AccessExpr -> {
                if (Debug.tracer != null) {
                    handleAccessTraced(e, env)
                } else {
                    handleAccess(e, env)
                }
            }
            is CallExpr -> handleCall(e, env)
            is InvokeExpr -> handleInvoke(e, env)
            is ObjectExpr -> handleObject(e, env)
            is LambdaExpr -> handleLambda(e, env)
            is SharedStateAwaitExpr -> handleSharedStateAwait(e, env)
        }
    }
}

/* ───────────────── evaluator factory (same API) ───────────────── */
/**
 * Creates an "eval" function for expressions. The returned eval is a closure
 * over hostFns/funcs/registry with small, named handlers per node type.
 */
fun makeEval(
    hostFns: Map<String, (List<Any?>) -> Any?>,
    funcs: Map<String, FuncDecl>,
    reg: TransformRegistry,
    tracer: Tracer? = null,
    sharedStore: SharedStore? = null,
): (Expr, Env) -> Any? {
    val engine = Evaluator(hostFns, funcs, reg, tracer, sharedStore)
    return engine::eval
}
