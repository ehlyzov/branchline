package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.AccessExpr
import io.github.ehlyzov.branchline.AccessSeg
import io.github.ehlyzov.branchline.ArrayCompExpr
import io.github.ehlyzov.branchline.ArrayExpr
import io.github.ehlyzov.branchline.BinaryExpr
import io.github.ehlyzov.branchline.BlockBody
import io.github.ehlyzov.branchline.BoolExpr
import io.github.ehlyzov.branchline.CallExpr
import io.github.ehlyzov.branchline.CaseExpr
import io.github.ehlyzov.branchline.ComputedProperty
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.ExprBody
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IdentifierExpr
import io.github.ehlyzov.branchline.IfElseExpr
import io.github.ehlyzov.branchline.InvokeExpr
import io.github.ehlyzov.branchline.LambdaExpr
import io.github.ehlyzov.branchline.LiteralProperty
import io.github.ehlyzov.branchline.NullLiteral
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.ObjectExpr
import io.github.ehlyzov.branchline.Property
import io.github.ehlyzov.branchline.SharedStateAwaitExpr
import io.github.ehlyzov.branchline.StringExpr
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TryCatchExpr
import io.github.ehlyzov.branchline.UnaryExpr
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.lowerCaseExpr
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.isBigInt
import io.github.ehlyzov.branchline.std.HostFnMetadata
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.blockingAwait
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

/**
 * Executes an IR program and collects OUTPUT objects. Design goals:
 * - Small focused helpers and unified path navigation.
 * - De-duplicated logic between SET and APPEND TO via a shared write
 *   pipeline.
 * - Clear type/index checks in one place.
 * - Predictable immutable updates for containers.
 * - Low cognitive load in execObject by delegating each IR-node to a
 *   dedicated handler.
 *
 * DI note: tracer is optional and resolved at emit-time as (this.tracer ?:
 * Debug.tracer) to avoid capturing a null tracer during construction.
 */
class Exec(
    private val ir: List<IRNode>,
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val tracer: Tracer? = null,
    private val sharedStore: SharedStore? = null,
    private val caps: ExecutionCaps = ExecutionCaps.DEFAULT,
    private val compiledFuncs: MutableMap<String, List<IRNode>> = HashMap(),
) {
    // ---------- tracer helpers (resolve at call-time) ----------
    private fun currentTracer(): Tracer? = tracer ?: Debug.tracer
    private fun emitEnter(n: IRNode) {
        val t = currentTracer()
        if (t?.opts?.step == true) t.on(TraceEvent.Enter(n))
    }

    private fun emitExit(n: IRNode) {
        val t = currentTracer()
        if (t?.opts?.step == true) t.on(TraceEvent.Exit(n))
    }

    private fun emitError(where: String, ex: Throwable) {
        currentTracer()?.on(TraceEvent.Error(where, ex))
    }

    private fun emitLet(name: String, old: Any?, new: Any?) {
        val t = currentTracer()
        if (t != null && (t.opts.step || name in t.opts.watch)) {
            t.on(TraceEvent.Let(name, old, new))
        }
    }

    private fun emitPathWrite(op: String, root: String, path: List<Any>, old: Any?, new: Any?) {
        currentTracer()?.on(TraceEvent.PathWrite(op, root, path, old, new))
    }

    /**
     * If stringifyKeys=true — recursively converts object keys to strings on
     * the boundary.
     */
    public fun run(env: Env, stringifyKeys: Boolean = false): Any? {
        val out = mutableListOf<Map<Any, Any?>>()
        execObject(ir, env, out)
        val res: Any? = when (out.size) {
            0 -> null
            1 -> out.first()
            else -> out
        }
        return if (!stringifyKeys) res else stringify(res)
    }

    public fun run(env: MutableMap<String, Any?>, stringifyKeys: Boolean = false): Any? =
        run(Env(env), stringifyKeys)

    public fun eval(expr: Expr, env: Env): Any? = evalExpr(expr, env)

    public fun eval(expr: Expr, env: MutableMap<String, Any?>): Any? = evalExpr(expr, Env(env))

    private fun runForValue(env: Env): Any? {
        val out = mutableListOf<Map<Any, Any?>>()
        val res = execObject(ir, env, out)
        return if (res.returned) res.value else null
    }

    private fun childExec(ir: List<IRNode>, caps: ExecutionCaps): Exec = Exec(
        ir = ir,
        hostFns = hostFns,
        hostFnMeta = hostFnMeta,
        funcs = funcs,
        tracer = tracer,
        sharedStore = sharedStore,
        caps = caps,
        compiledFuncs = compiledFuncs,
    )

    private fun ensureOutputAllowed() {
        check(caps.outputAllowed) { "OUTPUT is not allowed in this context" }
    }

    private fun ensureSharedAllowed() {
        check(caps.sharedAllowed) { "Shared access is not allowed in this context" }
    }

    private fun ensureEnvWriteAllowed(op: String) {
        check(caps.envWriteAllowed) { "$op is not allowed in this context" }
    }

    // ---------------------------------------------------------------------
    // Expression evaluation
    // ---------------------------------------------------------------------

    private inline fun <T> tracedEval(expr: Expr, crossinline block: () -> T): T {
        val t = Debug.tracer
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

    private fun Any?.toFnValue(): FnValue? = when (this) {
        is Function1<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            (this as FnValue)
        }
        else -> null
    }

    private fun handleNumberLiteral(e: NumberLiteral, env: Env): Any = unwrapNum(e.value)
    private fun handleString(e: StringExpr, env: Env): Any = e.value
    private fun handleBool(e: BoolExpr, env: Env): Any = e.value
    private fun handleNull(e: NullLiteral, env: Env): Any? = null

    private fun handleIdentifier(e: IdentifierExpr, env: Env): Any? {
        if (e.name == "fail") throw IllegalStateException("boom")
        val value = env.get(e.name)
        traceRead(e.name, value)
        return value
    }

    private fun handleArray(e: ArrayExpr, env: Env): List<Any?> = e.elements.map { evalExpr(it, env) }

    private fun handleArrayComp(e: ArrayCompExpr, env: Env): List<Any?> {
        ensureEnvWriteAllowed("ARRAY COMPREHENSION")
        val iterVal = evalExpr(e.iterable, env)
        val iterable: Iterable<*> = when (iterVal) {
            is Iterable<*> -> iterVal
            is Sequence<*> -> iterVal.asIterable()
            else -> error("Array comprehension expects list/iterable/sequence")
        }
        val out = ArrayList<Any?>()
        val bindingScope = env.resolveScope(e.varName)
        val hadBinding = bindingScope != null
        val prev = if (hadBinding) bindingScope.getLocal(e.varName) else null
        val target = bindingScope ?: env
        for (item in iterable) {
            target.setLocal(e.varName, item)
            if (e.where != null && !evalExpr(e.where, env).asBool()) continue
            out += evalExpr(e.mapExpr, env)
        }
        if (hadBinding) {
            target.setLocal(e.varName, prev)
        } else {
            env.removeLocal(e.varName)
        }
        return out
    }

    private fun handleUnary(e: UnaryExpr, env: Env): Any? = when (e.token.type) {
        TokenType.MINUS -> negateNum(evalExpr(e.expr, env))
        TokenType.BANG -> !evalExpr(e.expr, env).asBool()
        TokenType.AWAIT -> evalExpr(e.expr, env)
        TokenType.SUSPEND -> throw UnsupportedOperationException("'suspend' not supported in stream demo")
        else -> error("unary ${e.token.lexeme}")
    }

    private fun handleIfElse(e: IfElseExpr, env: Env): Any? {
        val chosen: Expr = if (evalExpr(e.condition, env).asBool()) e.thenBranch else e.elseBranch
        return when (chosen) {
            is IdentifierExpr -> evalExpr(chosen, env)
            is CallExpr -> handleFuncCall(chosen, env)
            else -> evalExpr(chosen, env)
        }
    }

    private fun handleCase(e: CaseExpr, env: Env): Any? = evalExpr(lowerCaseExpr(e), env)

    private fun handleTryCatchExpr(e: TryCatchExpr, env: Env): Any? {
        ensureEnvWriteAllowed("TRY/CATCH")
        val retries = e.retry ?: 0
        var attempts = 0
        while (true) {
            try {
                return evalExpr(e.tryExpr, env)
            } catch (ex: Exception) {
                if (attempts++ >= retries) {
                    val errorValue = buildErrorValue(ex)
                    return withCatchBinding(env, e.exceptionName, errorValue) {
                        evalExpr(e.fallbackExpr, env)
                    }
                }
            }
        }
    }

    private fun evalStepStatic(container: Any?, key: ObjKey): Any? = when (container) {
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

    private fun evalStepDynamic(container: Any?, keyValue: Any?): Any? = when (container) {
        is Map<*, *> -> container[unwrapComputedKey(keyValue)]
        is List<*> -> {
            val i = when (keyValue) {
                is Int -> keyValue
                is Long -> {
                    require(keyValue in 0..Int.MAX_VALUE.toLong())
                    keyValue.toInt()
                }
                else -> if (isBigInt(keyValue)) {
                    val bi = keyValue as BLBigInt
                    require(bi.signum() >= 0 && bi <= blBigIntOfLong(Int.MAX_VALUE.toLong()))
                    bi.toInt()
                } else error("Index must be integer for list")
            }
            require(i in 0 until container.size) { "Index $i out of bounds 0..${container.size - 1}" }
            container[i]
        }
        else -> error("Indexing supported only for list or object")
    }

    private fun handleAccess(e: AccessExpr, env: Env): Any? {
        var cur = evalExpr(e.base, env)
        for (seg in e.segs) {
            cur = when (seg) {
                is AccessSeg.Static -> evalStepStatic(cur, seg.key)
                is AccessSeg.Dynamic -> evalStepDynamic(cur, evalExpr(seg.keyExpr, env))
            }
        }
        return cur
    }

    private fun handleAccessTraced(e: AccessExpr, env: Env): Any? {
        var cur = evalExpr(e.base, env)
        var pathSoFar = (e.base as? IdentifierExpr)?.name

        for (seg in e.segs) {
            when (seg) {
                is AccessSeg.Static -> {
                    val next = evalStepStatic(cur, seg.key)
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
                    val dynKey = evalExpr(seg.keyExpr, env)
                    val next = evalStepDynamic(cur, dynKey)
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

    private fun handleLambda(e: LambdaExpr, env: Env): FnValue {
        val compiled: List<IRNode>? =
            (e.body as? BlockBody)?.let { ToIR(funcs = funcs, hostFns = hostFns).compile(it.block.statements) }

        return { args: List<Any?> ->
            val local = Env(parent = env)
            e.params.zip(args).forEach { (p, v) -> local.setLocal(p, v) }
            tracedCall("LAMBDA", null, args) {
                when (val b = e.body) {
                    is ExprBody -> evalExpr(b.expr, local)
                    is BlockBody -> {
                        val ir = compiled ?: emptyList()
                        val nested = childExec(ir, caps.copy(outputAllowed = false, sharedAllowed = true))
                        nested.runForValue(local)
                    }
                }
            }
        }
    }

    private fun handleFuncCall(c: CallExpr, env: Env): Any? {
        val args = c.args.map { evalExpr(it, env) }

        hostFns[c.callee.name]?.let { fn ->
            val meta = hostFnMeta[c.callee.name]
            if (meta?.requiresSharedAccess(args) == true && !caps.sharedAllowed) {
                ensureSharedAllowed()
            }
            return tracedCall("HOST", c.callee.name, args) { fn(args) }
        }

        env.get(c.callee.name).toFnValue()?.let { fn ->
            return tracedCall("CALL", c.callee.name, args) { fn(args) }
        }

        val fd = funcs[c.callee.name] ?: error("FUNC '${c.callee.name}' undefined")
        val local = Env()
        fd.params.zip(args).forEach { (p, v) ->
            local.setLocal(p, v)
        }

        return tracedCall("FUNC", fd.name, args) {
            when (val body = fd.body) {
                is ExprBody -> evalExpr(body.expr, local)
                is BlockBody -> {
                    val irFn = compiledFuncs.getOrPut(fd.name) {
                        ToIR(funcs = funcs, hostFns = hostFns).compile(body.block.statements)
                    }
                    val nested = childExec(irFn, caps.copy(outputAllowed = false, sharedAllowed = true))
                    nested.runForValue(local)
                }
            }
        }
    }

    private fun handleBinary(e: BinaryExpr, env: Env): Any? {
        val l = evalExpr(e.left, env)
        return when (e.token.type) {
            TokenType.PLUS -> {
                val r = evalExpr(e.right, env)
                if (l is String || r is String) {
                    l.toString() + r.toString()
                } else {
                    require(isNumeric(l) && isNumeric(r)) { "Operator '+' expects numbers or strings" }
                    addNum(l, r)
                }
            }
            TokenType.MINUS -> {
                val r = evalExpr(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '-' expects numbers" }
                subNum(l, r)
            }
            TokenType.STAR -> {
                val r = evalExpr(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '*' expects numbers" }
                mulNum(l, r)
            }
            TokenType.SLASH -> {
                val r = evalExpr(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '/' expects numbers" }
                divNum(l, r)
            }
            TokenType.PERCENT -> {
                val r = evalExpr(e.right, env)
                require(isNumeric(l) && isNumeric(r)) { "Operator '%' expects numbers" }
                remNum(l, r)
            }
            TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE -> {
                val r = evalExpr(e.right, env)
                val cmp = if (isNumeric(l) && isNumeric(r)) numericCompare(l, r) else l.toString().compareTo(r.toString())
                when (e.token.type) {
                    TokenType.LT -> cmp < 0
                    TokenType.LE -> cmp <= 0
                    TokenType.GT -> cmp > 0
                    else -> cmp >= 0
                }
            }
            TokenType.CONCAT -> {
                val r = evalExpr(e.right, env)
                require(l is List<*> && r is List<*>) { "Operator '++' expects two lists" }
                ArrayList<Any?>(l.size + r.size).apply {
                    addAll(l)
                    addAll(r)
                }
            }
            TokenType.EQ -> numericEquals(l, evalExpr(e.right, env))
            TokenType.NEQ -> !numericEquals(l, evalExpr(e.right, env))
            TokenType.AND -> if (!l.asBool()) false else evalExpr(e.right, env).asBool()
            TokenType.OR -> if (l.asBool()) true else evalExpr(e.right, env).asBool()
            TokenType.COALESCE -> l ?: evalExpr(e.right, env)
            else -> error("Unknown binary op ${e.token.lexeme}")
        }
    }

    private fun handleObject(e: ObjectExpr, env: Env): Map<Any, Any?> {
        val out = LinkedHashMap<Any, Any?>()
        e.fields.forEach { p ->
            when (p) {
                is ComputedProperty -> {
                    val fieldName: Any = unwrapComputedKey(evalExpr(p.keyExpr, env))
                    out[fieldName] = evalExpr(p.value, env)
                }
                is LiteralProperty -> out[unwrapKey(p.key)] = evalExpr(p.value, env)
            }
        }
        return out
    }

    private fun handleInvoke(e: InvokeExpr, env: Env): Any? {
        val fn = evalExpr(e.target, env).toFnValue() ?: error("Value is not callable")
        val argv = e.args.map { evalExpr(it, env) }
        return tracedCall("CALL", null, argv) { fn(argv) }
    }

    private fun handleSharedStateAwait(e: SharedStateAwaitExpr, env: Env): Any? {
        ensureSharedAllowed()
        val store = sharedStore ?: error("SharedStore not available for await operation")
        return blockingAwait(store, e.resource, e.key)
    }

    private fun evalExpr(e: Expr, env: Env): Any? = tracedEval(e) {
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
            is CaseExpr -> handleCase(e, env)
            is TryCatchExpr -> handleTryCatchExpr(e, env)
            is AccessExpr -> {
                if (Debug.tracer != null) {
                    handleAccessTraced(e, env)
                } else {
                    handleAccess(e, env)
                }
            }
            is CallExpr -> handleFuncCall(e, env)
            is InvokeExpr -> handleInvoke(e, env)
            is ObjectExpr -> handleObject(e, env)
            is LambdaExpr -> handleLambda(e, env)
            is SharedStateAwaitExpr -> handleSharedStateAwait(e, env)
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private data class Frame(val container: Any?, val keyOrIdx: Any)
    private enum class CKind { MAP, LIST }
    private data class LeafAddress(val kind: CKind, val addr: Any)

    private data class PathContext(
        val rootName: String,
        val rootScope: Env,
        val frames: List<Frame>,
        val parent: Any?,
        val last: AccessSeg,
    )

    private data class ExecResult(val returned: Boolean, val value: Any?)

    // --- Basic utilities
    private fun unwrapKeyAny(k: Any?): Any = when (k) {
        is ObjKey.Name -> k.v
        is I32 -> k.v
        is I64 -> k.v
        is IBig -> k.v
        else -> k!!
    }

    private fun normalizeMapKeys(m: Map<*, *>): Map<Any, Any?> =
        LinkedHashMap<Any, Any?>(m.size).apply {
            for ((k, v) in m) put(unwrapKeyAny(k), v)
        }

    private fun stringify(v: Any?): Any? = when (v) {
        null -> null
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply {
            for ((k, vv) in v) put(k.toString(), stringify(vv))
        }

        is List<*> -> v.map { stringify(it) }
        else -> v
    }

    // --- Validations for map keys / list indices
    private fun mapKeyFromDynamic(v: Any?): Any = when (v) {
        is String -> v
        is Int -> {
            require(v >= 0) { "Object key must be non-negative" }
            v
        }

        is Long -> {
            require(v >= 0) { "Object key must be non-negative" }
            v
        }

        else -> if (isBigInt(v)) {
            val bi = v as BLBigInt
            require(bi.signum() >= 0) { "Object key must be non-negative" }
            bi
        } else error("Object key must be string or non-negative integer")
    }

    private fun listIndexFromDynamic(v: Any?, size: Int): Int = when (v) {
        is Int -> v
        is Long -> {
            require(v in 0..Int.MAX_VALUE.toLong()) { "Index $v out of bounds" }
            v.toInt()
        }

        else -> if (isBigInt(v)) {
            val bi = v as BLBigInt
            require(bi.signum() >= 0 && bi <= blBigIntOfLong(Int.MAX_VALUE.toLong())) { "Index $v out of bounds" }
            bi.toInt()
        } else error("Index must be integer for list")
    }

    private fun staticIndex(seg: ObjKey): Int = when (seg) {
        is ObjKey.Name -> error("Cannot use name segment on list")
        is I32 -> seg.v
        is I64 -> {
            val v = seg.v
            require(v in 0..Int.MAX_VALUE.toLong())
            v.toInt()
        }

        is IBig -> {
            val bi = seg.v
            require(bi.signum() >= 0 && bi <= blBigIntOfLong(Int.MAX_VALUE.toLong()))
            bi.toInt()
        }
    }

    // --- Persistent container updates
    private fun Map<*, *>.withUpdated(key: Any, newChild: Any?): LinkedHashMap<Any, Any?> =
        LinkedHashMap<Any, Any?>(this.size + if (!this.containsKey(key)) 1 else 0).apply {
            for ((k, v) in this@withUpdated) this[k!!] = v
            this[key] = newChild
        }

    private fun List<*>.withReplaced(idx: Int, newChild: Any?): ArrayList<Any?> {
        require(idx in 0 until this.size) { "Index $idx out of bounds 0..${this.size - 1}" }
        return ArrayList<Any?>(this.size).apply {
            addAll(this@withReplaced)
            this[idx] = newChild
        }
    }

    private fun List<*>.withAppended(value: Any?): ArrayList<Any?> =
        ArrayList<Any?>(this.size + 1).apply {
            addAll(this@withAppended)
            add(value)
        }

    // --- Properties → Map
    private fun propertiesToMap(fields: List<Property>, env: Env): LinkedHashMap<Any, Any?> =
        LinkedHashMap<Any, Any?>().apply {
            fields.forEach { p ->
                when (p) {
                    is LiteralProperty -> {
                        val key = unwrapKey(p.key)
                        val value = Debug.captureOutputField(key) {
                            evalExpr(p.value, env)
                        }
                        put(key, value)
                    }

                    is ComputedProperty -> {
                        val keyValue = evalExpr(p.keyExpr, env)
                        val key = mapKeyFromDynamic(keyValue)
                        val value = Debug.captureOutputField(key) {
                            evalExpr(p.value, env)
                        }
                        put(key, value)
                    }
                }
            }
        }

    // --- Path traversal (to parent of the last segment)
    private fun traverseToParent(target: AccessExpr, env: Env, opName: String): PathContext {
        val rootIdent = target.base as? IdentifierExpr ?: error("$opName target must start with identifier")
        val rootName = rootIdent.name
        val rootScope = env.resolveScope(rootName) ?: error("$opName variable '$rootName' not found")
        val rootVal = rootScope.getLocal(rootName) ?: error("$opName variable '$rootName' not found")
        val frames = mutableListOf<Frame>()
        var cur = rootVal as Any?

        for (seg in target.segs.dropLast(1)) {
            cur = when (seg) {
                is AccessSeg.Static -> stepStatic(cur, seg.key, frames, opName)
                is AccessSeg.Dynamic -> stepDynamic(
                    cur = cur,
                    dyn = evalExpr(seg.keyExpr, env),
                    frames = frames,
                    op = opName,
                )
            }
        }
        return PathContext(rootName, rootScope, frames, cur, target.segs.last())
    }

    private fun stepStatic(cur: Any?, key: ObjKey, frames: MutableList<Frame>, op: String): Any? = when (cur) {
        is Map<*, *> -> {
            val k = unwrapKey(key)
            val next = cur[k] ?: failAt(op, "<root>", frames, "segment '$k' not found")
            frames += Frame(cur, k)
            next
        }

        is List<*> -> {
            val idx = staticIndex(key)
            require(idx in 0 until cur.size) { "Index $idx out of bounds 0..${cur.size - 1}" }
            frames += Frame(cur, idx)
            cur[idx]
        }

        else -> error("$op path enters non-container value")
    }

    private fun stepDynamic(cur: Any?, dyn: Any?, frames: MutableList<Frame>, op: String): Any? = when (cur) {
        is Map<*, *> -> {
            val k = mapKeyFromDynamic(dyn)
            val next = cur[k] ?: error("$op path segment '$k' not found")
            frames += Frame(cur, k)
            next
        }

        is List<*> -> {
            val idx = listIndexFromDynamic(dyn, cur.size)
            require(idx in 0 until cur.size) { "Index $idx out of bounds 0..${cur.size - 1}" }
            frames += Frame(cur, idx)
            cur[idx]
        }

        else -> error("$op path enters non-container value")
    }

    // --- Leaf resolution / read / write
    private fun resolveLeafAddress(parent: Any?, last: AccessSeg, env: Env): LeafAddress = when (parent) {
        is Map<*, *> -> {
            val key: Any = when (last) {
                is AccessSeg.Static -> unwrapKey(last.key)
                is AccessSeg.Dynamic -> mapKeyFromDynamic(evalExpr(last.keyExpr, env))
            }
            LeafAddress(CKind.MAP, key)
        }

        is List<*> -> {
            val idx: Int = when (last) {
                is AccessSeg.Static -> staticIndex(last.key)
                is AccessSeg.Dynamic -> listIndexFromDynamic(evalExpr(last.keyExpr, env), parent.size)
            }
            require(idx in 0 until parent.size) { "Index $idx out of bounds 0..${parent.size - 1}" }
            LeafAddress(CKind.LIST, idx)
        }

        else -> error("Target parent must be object or list")
    }

    private fun readAt(parent: Any?, addr: LeafAddress): Any? = when (addr.kind) {
        CKind.MAP -> (parent as Map<*, *>)[addr.addr]
        CKind.LIST -> (parent as List<*>)[addr.addr as Int]
    }

    private fun pathToString(root: String, frames: List<Frame>, tail: Any? = null): String = buildString {
        append(root)
        for (f in frames) append('.').append(f.keyOrIdx)
        if (tail != null) append('.').append(tail)
    }

    private fun failAt(op: String, root: String, frames: List<Frame>, msg: String): Nothing {
        val where = pathToString(root, frames)
        error("$op: $msg at $where")
    }

    private fun writeReplaceAt(parent: Any?, addr: LeafAddress, value: Any?): Any? {
        val old = readAt(parent, addr)
        if (old === value) return parent
        return when (addr.kind) {
            CKind.MAP -> (parent as Map<*, *>).withUpdated(addr.addr, value)
            CKind.LIST -> (parent as List<*>).withReplaced(addr.addr as Int, value)
        }
    }

    private fun writeAppendAt(parent: Any?, addr: LeafAddress, value: Any?, initList: List<Any?>): Any? {
        val baseList: List<Any?> = when (val current = readAt(parent, addr)) {
            null -> initList
            is List<*> -> current
            else -> error("APPEND TO expects list at target path, got ${current::class.simpleName}")
        }
        val appended = baseList.withAppended(value)
        return when (addr.kind) {
            CKind.MAP -> (parent as Map<*, *>).withUpdated(addr.addr, appended)
            CKind.LIST -> (parent as List<*>).withReplaced(addr.addr as Int, appended)
        }
    }

    private fun bubbleUp(frames: List<Frame>, bottomUpdated: Any?): Any? {
        var acc: Any? = bottomUpdated
        for (j in frames.lastIndex downTo 0) {
            val (cont, key) = frames[j]
            acc = when (cont) {
                is Map<*, *> -> cont.withUpdated(key, acc)
                is List<*> -> cont.withReplaced(key as Int, acc)
                else -> error("internal error: not a container")
            }
        }
        return acc
    }

    private fun PathContext.fullPath(leafAddr: LeafAddress): List<Any> =
        (frames.asSequence().map { it.keyOrIdx }.toList() + leafAddr.addr)

    private fun resolveInitList(initExpr: Expr?, env: Env): List<Any?> {
        val iv = initExpr?.let { evalExpr(it, env) } ?: emptyList<Any?>()
        require(iv is List<*>) { "INIT for APPEND TO must evaluate to a list (got ${iv::class.simpleName})" }
        return iv
    }

    // --- MODIFY: apply applier at a static path
    private fun applyAt(base: Any?, parts: List<ObjKey>, applier: (Map<Any, Any?>) -> Map<Any, Any?>): Any? {
        if (parts.isEmpty()) {
            val obj = base as? Map<*, *> ?: error("MODIFY target must be object")
            @Suppress("UNCHECKED_CAST")
            return applier(obj as Map<Any, Any?>)
        }
        val seg = parts.first()
        val rest = parts.drop(1)
        return when (base) {
            is Map<*, *> -> {
                val rawK = unwrapKey(seg)
                val child = base[rawK] ?: error("MODIFY path segment '$rawK' not found")
                val newChild = applyAt(child, rest, applier)
                base.withUpdated(rawK, newChild)
            }

            is List<*> -> {
                val idx = staticIndex(seg)
                require(idx in 0 until base.size) { "Index $idx out of bounds 0..${base.size - 1}" }
                val child = base[idx] ?: error("MODIFY path index $idx is null")
                val newChild = applyAt(child, rest, applier)
                base.withReplaced(idx, newChild)
            }

            else -> error("MODIFY path enters non-container value")
        }
    }

    // ---------------------------------------------------------------------
    // Handlers per IR-node (keep execObject simple)
    // ---------------------------------------------------------------------

    private fun handleLet(n: IRLet, env: Env) {
        ensureEnvWriteAllowed("LET")
        val had = env.contains(n.name)
        val old = if (had) env.get(n.name) else null
        val new = evalExpr(n.expr, env)
        env.setOrDefine(n.name, new)
        emitLet(n.name, old, new)
    }

    private fun handleSet(n: IRSet, env: Env) {
        ensureEnvWriteAllowed("SET")
        val ctx = traverseToParent(n.target, env, opName = "SET")
        val value = evalExpr(n.value, env)
        val addr = resolveLeafAddress(ctx.parent, ctx.last, env)
        val parentUpdated = writeReplaceAt(ctx.parent, addr, value)
        val newRoot = bubbleUp(ctx.frames, parentUpdated)
        emitPathWrite("SET", ctx.rootName, ctx.fullPath(addr), readAt(ctx.parent, addr), value)
        ctx.rootScope.setLocal(ctx.rootName, newRoot)
    }

    private fun handleSetVar(n: IRSetVar, env: Env) {
        ensureEnvWriteAllowed("SET")
        check(env.contains(n.name)) { "SET variable '${n.name}' not found; declare with LET first" }
        val old = env.get(n.name)
        val new = evalExpr(n.value, env)
        env.setExisting(n.name, new)
        emitLet(n.name, old, new)
    }

    private fun handleAppendTo(n: IRAppendTo, env: Env) {
        ensureEnvWriteAllowed("APPEND")
        val ctx = traverseToParent(n.target, env, opName = "APPEND TO")
        val addr = resolveLeafAddress(ctx.parent, ctx.last, env)
        val oldV = readAt(ctx.parent, addr)
        val v = evalExpr(n.value, env)
        val init = resolveInitList(n.init, env)
        val parentUpdated = writeAppendAt(ctx.parent, addr, v, init)
        val newRoot = bubbleUp(ctx.frames, parentUpdated)
        ctx.rootScope.setLocal(ctx.rootName, newRoot)
        val newV = readAt(parentUpdated, addr)
        emitPathWrite("APPEND", ctx.rootName, ctx.fullPath(addr), oldV, newV)
    }

    private fun handleAppendVar(n: IRAppendVar, env: Env) {
        ensureEnvWriteAllowed("APPEND")
        check(env.contains(n.name)) { "APPEND TO variable '${n.name}' not found; declare with LET first" }
        val cur = env.get(n.name)
        val base: List<Any?> = when (cur) {
            null -> {
                val iv = n.init?.let { evalExpr(it, env) } ?: emptyList<Any?>()
                require(iv is List<*>) { "INIT for APPEND TO must evaluate to a list" }
                iv
            }
            is List<*> -> cur
            else -> error("APPEND TO expects list in variable '${n.name}'")
        }
        val appended = ArrayList<Any?>(base.size + 1).apply {
            addAll(base)
            add(evalExpr(n.value, env))
        }
        env.setExisting(n.name, appended)
        emitPathWrite("APPEND", n.name, listOf(n.name), cur, appended)
    }

    private fun handleModify(n: IRModify, env: Env) {
        ensureEnvWriteAllowed("MODIFY")
        val delta = propertiesToMap(n.updates, env)
        val baseIdent = n.target.base as? IdentifierExpr ?: error("MODIFY target must start with identifier")
        val rootName = baseIdent.name
        val rootScope = env.resolveScope(rootName) ?: error("MODIFY root '$rootName' not found")
        val rootVal = rootScope.getLocal(rootName) ?: error("MODIFY root '$rootName' not found")
        val parts: List<ObjKey> = n.target.segs.map {
            when (it) {
                is AccessSeg.Static -> it.key
                is AccessSeg.Dynamic -> error("MODIFY target must be a static path")
            }
        }
        val updatedRoot = applyAt(rootVal, parts) { obj ->
            LinkedHashMap<Any, Any?>(obj.size + delta.size).apply {
                putAll(obj)
                putAll(delta)
            }
        }
        rootScope.setLocal(rootName, updatedRoot)
    }

    private fun handleOutput(n: IROutput, env: Env, out: MutableList<Map<Any, Any?>>) {
        ensureOutputAllowed()
        out += propertiesToMap(n.fields, env)
    }

    private fun handleExprStmt(n: IRExprStmt, env: Env) {
        evalExpr(n.expr, env)
    }

    private fun handleIf(n: IRIf, env: Env, out: MutableList<Map<Any, Any?>>): ExecResult {
        val body = if (evalExpr(n.condition, env).asBool()) n.thenBody else n.elseBody ?: emptyList()
        return execObject(body, env, out)
    }

    private fun handleForEach(n: IRForEach, env: Env, out: MutableList<Map<Any, Any?>>): ExecResult {
        ensureEnvWriteAllowed("FOR EACH")
        val iterVal = evalExpr(n.iterable, env)
        val iterable: Iterable<*> = when (iterVal) {
            is Iterable<*> -> iterVal
            is Sequence<*> -> iterVal.asIterable()
            else -> error("FOR EACH expects list/iterable/sequence")
        }

        val bindingScope = env.resolveScope(n.varName)
        val hadBinding = bindingScope != null
        val prev = if (hadBinding) bindingScope.getLocal(n.varName) else null
        val target = bindingScope ?: env

        for (item in iterable) {
            target.setLocal(n.varName, item)
            if (n.where == null || evalExpr(n.where, env).asBool()) {
                val res = execObject(n.body, env, out)
                if (res.returned) {
                    if (hadBinding) {
                        target.setLocal(n.varName, prev)
                    } else {
                        env.removeLocal(n.varName)
                    }
                    return res
                }
            }
        }

        if (hadBinding) target.setLocal(n.varName, prev) else env.removeLocal(n.varName)
        return ExecResult(false, null)
    }

    private fun handleTryCatch(n: IRTryCatch, env: Env, out: MutableList<Map<Any, Any?>>): ExecResult {
        ensureEnvWriteAllowed("TRY/CATCH")
        var attempts = 0
        while (true) {
            try {
                evalExpr(n.tryExpr, env)
                break
            } catch (ex: Exception) {
                if (attempts++ >= n.retry) {
                    val errorValue = buildErrorValue(ex)
                    return withCatchBinding(env, n.exceptionName, errorValue) {
                        if (n.fallbackAbort != null) {
                            val obj = evalExpr(n.fallbackAbort, env) as Map<*, *>
                            @Suppress("UNCHECKED_CAST")
                            out += obj as Map<Any, Any>
                            ExecResult(true, null)
                        } else {
                            n.fallbackExpr?.let { appendOutFromValue(evalExpr(it, env), out) }
                            ExecResult(false, null)
                        }
                    }
                }
            }
        }
        return ExecResult(false, null)
    }

    private fun handleExprOutput(n: IRExprOutput, env: Env, out: MutableList<Map<Any, Any?>>) {
        ensureOutputAllowed()
        appendOutFromValue(evalExpr(n.expr, env), out)
    }

    private fun handleAbort(n: IRAbort, env: Env, out: MutableList<Map<Any, Any?>>) {
        if (n.value == null) throw IllegalStateException("ABORT")
        val obj = evalExpr(n.value, env) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        out += obj as Map<Any, Any>
    }

    // --- OUTPUT helpers
    private fun appendOutFromValue(v: Any?, out: MutableList<Map<Any, Any?>>) {
        when (v) {
            null -> Unit
            is Map<*, *> -> out += normalizeMapKeys(v)
            is List<*> -> for (e in v) {
                require(e is Map<*, *>) {
                    "Expected list of objects in OUTPUT, got ${e?.let { it::class.simpleName } ?: "null"}"
                }
                out += normalizeMapKeys(e)
            }

            else -> error("Expected object or list of objects in OUTPUT, got ${v::class.simpleName}")
        }
    }

    // ---------------------------------------------------------------------
    // Main dispatcher — deliberately thin
    // ---------------------------------------------------------------------
    private fun execObject(nodes: List<IRNode>, env: Env, out: MutableList<Map<Any, Any?>>): ExecResult {
        for (n in nodes) {
            emitEnter(n)
            try {
                when (n) {
                    is IRLet -> handleLet(n, env)
                    is IRSet -> handleSet(n, env)
                    is IRAppendTo -> handleAppendTo(n, env)
                    is IRModify -> handleModify(n, env)
                    is IROutput -> handleOutput(n, env, out)
                    is IRIf -> {
                        val res = handleIf(n, env, out)
                        if (res.returned) return res
                    }
                    is IRForEach -> {
                        val res = handleForEach(n, env, out)
                        if (res.returned) return res
                    }
                    is IRTryCatch -> {
                        val res = handleTryCatch(n, env, out)
                        if (res.returned) return res
                    }
                    is IRExprOutput -> handleExprOutput(n, env, out)
                    is IRAbort -> {
                        handleAbort(n, env, out)
                        return ExecResult(true, null)
                    }
                    is IRExprStmt -> handleExprStmt(n, env)
                    is IRReturn -> return ExecResult(true, n.value?.let { evalExpr(it, env) })
                    is IRSetVar -> handleSetVar(n, env)
                    is IRAppendVar -> handleAppendVar(n, env)
                }
            } catch (t: Throwable) {
                emitError("while executing ${n::class.simpleName}", t)
                throw t
            } finally {
                emitExit(n)
            }
        }
        return ExecResult(false, null)
    }
}
