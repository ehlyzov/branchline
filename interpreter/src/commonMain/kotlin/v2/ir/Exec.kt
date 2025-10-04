package v2.ir

import v2.*
import v2.debug.Debug
import v2.debug.TraceEvent
import v2.debug.Tracer
import v2.runtime.bignum.BLBigInt
import v2.runtime.bignum.blBigIntOfLong
import v2.runtime.bignum.compareTo
import v2.runtime.bignum.signum
import v2.runtime.bignum.toInt
import v2.runtime.isBigInt

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
// File-private alias to avoid nested typealias (requires experimental flag)
// Removed nested/file-private typealias to avoid experimental flag usage.

class Exec(
    private val ir: List<IRNode>,
    private val eval: (Expr, MutableMap<String, Any?>) -> Any?,
    private val tracer: Tracer? = null, // DI: pass in, or rely on Debug.tracer fallback
) {
    // ---------- tracer helpers (resolve at call-time) ----------
    private fun currentTracer(): Tracer? = tracer ?: Debug.tracer
    private fun emitEnter(n: IRNode) {
        val t = currentTracer();
        if (t?.opts?.step == true) t.on(TraceEvent.Enter(n))
    }

    private fun emitExit(n: IRNode) {
        val t = currentTracer();
        if (t?.opts?.step == true) t.on(TraceEvent.Exit(n))
    }

    private fun emitError(where: String, ex: Throwable) {
        currentTracer()?.on(TraceEvent.Error(where, ex))
    }

    private fun emitLet(name: String, old: Any?, new: Any?) {
        val t = currentTracer();
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
    fun run(env: MutableMap<String, Any?>, stringifyKeys: Boolean = false): Any? {
        val out = mutableListOf<Map<Any, Any?>>()
        execObject(ir, env, out)
        val res: Any? = when (out.size) {
            0 -> null
            1 -> out.first()
            else -> out
        }
        return if (!stringifyKeys) res else stringify(res)
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    // --- Tiny types
    private data class Frame(val container: Any?, val keyOrIdx: Any)
    private enum class CKind { MAP, LIST }
    private data class LeafAddress(val kind: CKind, val addr: Any)

    // --- Basic utilities
    private fun Any?.asBool(): Boolean = when (this) {
        null -> false
        is Boolean -> this
        is Number -> this.toDouble() != 0.0
        else -> true
    }

    private fun unwrapKeyAny(k: Any?): Any = when (k) {
        is ObjKey.Name -> k.v
        is I32 -> k.v
        is I64 -> k.v
        is IBig -> k.v
        else -> k!!
    }

    private fun unwrapKey(k: ObjKey): Any = when (k) {
        is ObjKey.Name -> k.v
        is I32 -> k.v
        is I64 -> k.v
        is IBig -> k.v
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
            require(v >= 0) { "Object key must be non-negative" };
            v
        }

        is Long -> {
            require(v >= 0) { "Object key must be non-negative" };
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
            require(v in 0..Int.MAX_VALUE.toLong()) { "Index $v out of bounds" };
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
            val v = seg.v;
            require(v in 0..Int.MAX_VALUE.toLong());
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
            addAll(this@withAppended);
            add(value)
        }

    // --- Properties → Map
    private fun propertiesToMap(fields: List<Property>, env: MutableMap<String, Any?>): LinkedHashMap<Any, Any?> =
        LinkedHashMap<Any, Any?>().apply {
            fields.forEach { p ->
                when (p) {
                    is LiteralProperty -> {
                        val key = unwrapKey(p.key)
                        val value = Debug.captureOutputField(key ?: "<key>") {
                            eval(p.value, env)
                        }
                        put(key, value)
                    }

                    is ComputedProperty -> {
                        val keyValue = eval(p.keyExpr, env)
                        val key = mapKeyFromDynamic(keyValue)
                        val value = Debug.captureOutputField(key ?: "<key>") {
                            eval(p.value, env)
                        }
                        put(key, value)
                    }
                }
            }
        }

    // --- Path traversal (to parent of the last segment)
    private data class PathContext(
        val rootName: String,
        val frames: ArrayList<Frame>,
        val parent: Any?,
        val last: AccessSeg,
    )

    private fun traverseToParent(target: AccessExpr, env: MutableMap<String, Any?>, opName: String): PathContext {
        val baseIdent = target.base as? IdentifierExpr ?: error("$opName target must start with identifier")
        val rootName = baseIdent.name
        val rootVal = env[rootName] ?: error("$opName root '$rootName' not found")
        require(target.segs.isNotEmpty()) { "$opName needs a non-empty path" }

        val frames = ArrayList<Frame>(target.segs.size)
        var cur: Any? = rootVal

        for (i in 0 until target.segs.lastIndex) {
            cur = when (val seg = target.segs[i]) {
                is AccessSeg.Static -> stepStatic(cur, seg.key, frames, opName)
                is AccessSeg.Dynamic -> stepDynamic(cur, eval(seg.keyExpr, env), frames, opName)
            }
        }
        return PathContext(rootName, frames, cur, target.segs.last())
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
    private fun resolveLeafAddress(parent: Any?, last: AccessSeg, env: MutableMap<String, Any?>): LeafAddress = when (parent) {
        is Map<*, *> -> {
            val key: Any = when (last) {
                is AccessSeg.Static -> unwrapKey(last.key)
                is AccessSeg.Dynamic -> mapKeyFromDynamic(eval(last.keyExpr, env))
            }
            LeafAddress(CKind.MAP, key)
        }

        is List<*> -> {
            val idx: Int = when (last) {
                is AccessSeg.Static -> staticIndex(last.key)
                is AccessSeg.Dynamic -> listIndexFromDynamic(eval(last.keyExpr, env), parent.size)
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

    private fun resolveInitList(initExpr: Expr?, env: MutableMap<String, Any?>): List<Any?> {
        val iv = initExpr?.let { eval(it, env) } ?: emptyList<Any?>()
        require(iv is List<*>) { "INIT for APPEND TO must evaluate to a list (got ${iv::class.simpleName})" }
        @Suppress("UNCHECKED_CAST")
        return iv as List<Any?>
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

    private fun handleLet(n: IRLet, env: MutableMap<String, Any?>) {
        val old = env[n.name]
        val new = eval(n.expr, env)
        env[n.name] = new
        emitLet(n.name, old, new)
    }

    private fun handleSet(n: IRSet, env: MutableMap<String, Any?>) {
        val ctx = traverseToParent(n.target, env, opName = "SET")
        val value = eval(n.value, env)
        val addr = resolveLeafAddress(ctx.parent, ctx.last, env)
        val parentUpdated = writeReplaceAt(ctx.parent, addr, value)
        val newRoot = bubbleUp(ctx.frames, parentUpdated)
        emitPathWrite("SET", ctx.rootName, ctx.fullPath(addr), readAt(ctx.parent, addr), value)
        env[ctx.rootName] = newRoot
    }

    private fun handleSetVar(n: IRSetVar, env: MutableMap<String, Any?>) {
        require(env.containsKey(n.name)) { "SET variable '${n.name}' not found; declare with LET first" }
        val old = env[n.name]
        val new = eval(n.value, env)
        env[n.name] = new
        emitLet(n.name, old, new)
    }

    private fun handleAppendTo(n: IRAppendTo, env: MutableMap<String, Any?>) {
        val ctx = traverseToParent(n.target, env, opName = "APPEND TO")
        val addr = resolveLeafAddress(ctx.parent, ctx.last, env)
        val oldV = readAt(ctx.parent, addr)
        val v = eval(n.value, env)
        val init = resolveInitList(n.init, env)
        val parentUpdated = writeAppendAt(ctx.parent, addr, v, init)
        val newRoot = bubbleUp(ctx.frames, parentUpdated)
        env[ctx.rootName] = newRoot
        val newV = readAt(parentUpdated, addr)
        emitPathWrite("APPEND", ctx.rootName, ctx.fullPath(addr), oldV, newV)
    }

    private fun handleAppendVar(n: IRAppendVar, env: MutableMap<String, Any?>) {
        require(env.containsKey(n.name)) { "APPEND TO variable '${n.name}' not found; declare with LET first" }
        val cur = env[n.name]
        val base: List<Any?> = when (cur) {
            null -> {
                val iv = n.init?.let { eval(it, env) } ?: emptyList<Any?>()
                require(iv is List<*>) { "INIT for APPEND TO must evaluate to a list" }
                @Suppress("UNCHECKED_CAST")
                (iv as List<Any?>)
            }
            is List<*> ->
                @Suppress("UNCHECKED_CAST")
                (cur as List<Any?>)
            else -> error("APPEND TO expects list in variable '${n.name}'")
        }
        val appended = ArrayList<Any?>(base.size + 1).apply {
            addAll(base);
            add(eval(n.value, env))
        }
        env[n.name] = appended
        emitPathWrite("APPEND", n.name, listOf(n.name), cur, appended)
    }

    private fun handleModify(n: IRModify, env: MutableMap<String, Any?>) {
        val delta = propertiesToMap(n.updates, env)
        val baseIdent = n.target.base as? IdentifierExpr ?: error("MODIFY target must start with identifier")
        val rootName = baseIdent.name
        val rootVal = env[rootName] ?: error("MODIFY root '$rootName' not found")
        val parts: List<ObjKey> = n.target.segs.map {
            when (it) {
                is AccessSeg.Static -> it.key
                is AccessSeg.Dynamic -> error("MODIFY target must be a static path")
            }
        }
        val updatedRoot = applyAt(rootVal, parts) { obj ->
            LinkedHashMap<Any, Any?>(obj.size + delta.size).apply {
                putAll(obj);
                putAll(delta)
            }
        }
        env[rootName] = updatedRoot
    }

    private fun handleOutput(n: IROutput, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        out += propertiesToMap(n.fields, env)
    }

    private fun handleExprStmt(n: IRExprStmt, env: MutableMap<String, Any?>) {
        eval(n.expr, env) // value intentionally ignored (side effects only)
    }

    private fun handleIf(n: IRIf, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        val body = if (eval(n.condition, env).asBool()) n.thenBody else n.elseBody ?: emptyList()
        execObject(body, env, out)
    }

    private inline fun <T> MutableMap<String, Any?>.childScope(block: MutableMap<String, Any?>.() -> T): T {
        val baseKeys = keys.toSet()
        val result = block(this)
        val kept = HashMap<String, Any?>(baseKeys.size)
        for (k in baseKeys) if (containsKey(k)) kept[k] = this[k]
        clear();
        putAll(kept)
        return result
    }

    private fun MutableMap<String, Any?>.makeBaseSnapshot(baseKeys: Set<String>): Map<String, Any?> =
        HashMap<String, Any?>(baseKeys.size).apply { for (k in baseKeys) this[k] = this@makeBaseSnapshot[k] }

    private fun MutableMap<String, Any?>.pruneToBaseProtectingVar(
        baseKeys: Set<String>,
        baseSnapshot: Map<String, Any?>,
        varName: String,
    ) {
        val hadOuterVar = baseSnapshot.containsKey(varName)
        val kept = HashMap<String, Any?>(baseKeys.size)
        for (k in baseKeys) {
            if (k == varName) {
                if (hadOuterVar) kept[k] = baseSnapshot[k]
            } else {
                kept[k] = this[k]
            }
        }
        clear();
        putAll(kept)
    }

    private fun handleForEach(n: IRForEach, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        val iterVal = eval(n.iterable, env)
        val iterable: Iterable<*> = when (iterVal) {
            is Iterable<*> -> iterVal
            is Sequence<*> -> iterVal.asIterable()
            else -> error("FOR EACH expects list/iterable/sequence")
        }

        val hadOuterVar = env.containsKey(n.varName)
        val savedOuter = env[n.varName]

        for (item in iterable) {
            env[n.varName] = item
            if (n.where == null || eval(n.where, env).asBool()) {
                execObject(n.body, env, out)
            }
        }

        if (hadOuterVar) env[n.varName] = savedOuter else env.remove(n.varName)
    }

    private fun handleTryCatch(n: IRTryCatch, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>): Boolean {
        var attempts = 0
        while (true) {
            try {
                eval(n.tryExpr, env)
                break
            } catch (_: Exception) {
                if (attempts++ >= n.retry) {
                    if (n.fallbackAbort != null) {
                        val obj = eval(n.fallbackAbort, env) as Map<*, *>
                        @Suppress("UNCHECKED_CAST")
                        out += obj as Map<Any, Any>
                        return false
                    }
                    n.fallbackExpr?.let { appendOutFromValue(eval(it, env), out) }
                    break
                }
            }
        }
        return true
    }

    private fun handleExprOutput(n: IRExprOutput, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        appendOutFromValue(eval(n.expr, env), out)
    }

    private fun handleAbort(n: IRAbort, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        if (n.value == null) throw IllegalStateException("ABORT")
        val obj = eval(n.value, env) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        out += obj as Map<Any, Any>
    }

    // --- OUTPUT helpers
    private fun appendOutFromValue(v: Any?, out: MutableList<Map<Any, Any?>>) {
        when (v) {
            null -> Unit
            is Map<*, *> -> out += normalizeMapKeys(v)
            is List<*> -> for (e in v) {
                require(
                    e is Map<*, *>
                ) { "Expected list of objects in OUTPUT, got ${e?.let { it::class.simpleName } ?: "null"}" }
                @Suppress("UNCHECKED_CAST")
                out += normalizeMapKeys(e as Map<*, *>)
            }

            else -> error("Expected object or list of objects in OUTPUT, got ${v::class.simpleName}")
        }
    }

    // ---------------------------------------------------------------------
    // Main dispatcher — deliberately thin
    // ---------------------------------------------------------------------
    private fun execObject(nodes: List<IRNode>, env: MutableMap<String, Any?>, out: MutableList<Map<Any, Any?>>) {
        for (n in nodes) {
            emitEnter(n)
            try {
                when (n) {
                    is IRLet -> handleLet(n, env)
                    is IRSet -> handleSet(n, env)
                    is IRAppendTo -> handleAppendTo(n, env)
                    is IRModify -> handleModify(n, env)
                    is IROutput -> handleOutput(n, env, out)
                    is IRIf -> handleIf(n, env, out)
                    is IRForEach -> handleForEach(n, env, out)
                    is IRTryCatch -> {
                        val ok = handleTryCatch(n, env, out);
                        if (!ok) return
                    }

                    is IRExprOutput -> handleExprOutput(n, env, out)
                    is IRAbort -> {
                        handleAbort(n, env, out);
                        return
                    }

                    is IRExprStmt -> handleExprStmt(n, env)
                    is IRReturn -> return
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
    }
}
