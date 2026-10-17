@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.github.ehlyzov.branchline.vm.BytecodeIO
import io.github.ehlyzov.branchline.vm.BytecodeIO.SerializedBytecode
import io.github.ehlyzov.branchline.vm.BytecodeIO.SerializedInstruction
import io.github.ehlyzov.branchline.vm.BytecodeIO.SerializedValue
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.*
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.runtime.bignum.toBLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.plus
import io.github.ehlyzov.branchline.runtime.bignum.minus
import io.github.ehlyzov.branchline.runtime.bignum.times
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.rem
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus
import io.github.ehlyzov.branchline.runtime.bignum.toInt
import io.github.ehlyzov.branchline.runtime.isBigDec
import io.github.ehlyzov.branchline.runtime.isBigInt
import io.github.ehlyzov.branchline.runtime.NumericKind
import io.github.ehlyzov.branchline.runtime.addIntegersFast
import io.github.ehlyzov.branchline.runtime.addNumeric
import io.github.ehlyzov.branchline.runtime.bigDecOfLongCached
import io.github.ehlyzov.branchline.runtime.bigIntOfLongCached
import io.github.ehlyzov.branchline.runtime.divNumeric
import io.github.ehlyzov.branchline.runtime.idivIntegersFast
import io.github.ehlyzov.branchline.runtime.idivNumeric
import io.github.ehlyzov.branchline.runtime.isNumericValue
import io.github.ehlyzov.branchline.runtime.mulIntegersFast
import io.github.ehlyzov.branchline.runtime.mulNumeric
import io.github.ehlyzov.branchline.runtime.negateNumeric
import io.github.ehlyzov.branchline.runtime.numericCompare
import io.github.ehlyzov.branchline.runtime.numericEquals
import io.github.ehlyzov.branchline.runtime.numericKindOf
import io.github.ehlyzov.branchline.runtime.remIntegersFast
import io.github.ehlyzov.branchline.runtime.remNumeric
import io.github.ehlyzov.branchline.runtime.subIntegersFast
import io.github.ehlyzov.branchline.runtime.subNumeric

/**
 * Stack-based Virtual Machine for Branchline DSL
 *
 * This VM executes bytecode generated from Branchline IR nodes, providing
 * efficient stack-based execution with support for:
 * - Arithmetic and logical operations
 * - Object/array manipulation
 * - Control flow (jumps, conditionals, loops)
 * - Function calls and returns
 * - Exception handling
 * - Debug tracing
 */
class VM(
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val tracer: Tracer? = null,
) {
    // Constants defined in companion below

    // Execution state
    private val stack = ArrayDeque<Any?>(256)
    private val callStack = ArrayDeque<CallFrame>(64)
    private var pc = 0 // Program counter
    private lateinit var bytecode: Bytecode
    private var ordinaryEnvironment = false
    private var environment = mutableMapOf<String, Any?>()
        set(value) {
            field = value
            // Only these exact map types have unobservable reads and writes.
            // Function calls and unwinding replace the environment too.
            ordinaryEnvironment = value::class == HashMap::class || value::class == LinkedHashMap::class
        }
    private var locals: Array<Any?> = arrayOf()
    private val funcBytecode = HashMap<String, Bytecode>()
    private var lastSuspension: Any? = null
    // Cache only the current root and a bounded working set of its callees.
    // Identity comparisons must not invoke Bytecode's structural hashCode.
    private var preparedRoot: Bytecode? = null
    private val preparedPrograms = ArrayList<PreparedProgram>()
    private lateinit var preparedProgram: PreparedProgram
    // Incremental callers can observe values between instructions.
    private var privateMutationAllowed = false
    private val outputs = ArrayList<Map<Any, Any?>>()
    private var enterEvents: Map<Int, List<IRNode>> = emptyMap()
    private var exitEvents: Map<Int, List<IRNode>> = emptyMap()
    private var pathWrites: Map<Int, PathWriteMeta> = emptyMap()
    private var activePc: Int = -1
    private val pathScratch = ArrayList<Any>(8)

    // Exception handling
    private val tryStack = ArrayDeque<TryFrame>()
    private var lastException: Throwable? = null
    // Sequential invocation metadata, used only by language adapters. Keep host
    // exceptions unchanged even when a host deliberately throws a VMException.
    private var lastHostFailure: Throwable? = null
    internal fun isHostFailure(failure: Throwable): Boolean = lastHostFailure === failure

    // Loop handling
    private val loopStack = ArrayDeque<LoopState>()

    // Debugging
    private fun currentTracer(): Tracer? = tracer ?: Debug.tracer

    private inline fun <T> tracedCall(
        kind: String,
        name: String?,
        args: List<Any?>,
        crossinline block: () -> T,
    ): T {
        val t = currentTracer()
        if (t?.opts?.includeCalls == true) t.on(TraceEvent.Call(kind, name, args))
        return try {
            val r = block()
            if (t?.opts?.includeCalls == true) t.on(TraceEvent.Return(kind, name, r))
            r
        } catch (ex: Throwable) {
            if (kind == "HOST") lastHostFailure = ex
            t?.on(TraceEvent.Error("call $kind ${name ?: "<lambda>"}", ex))
            throw ex
        }
    }

    private fun loadTraceMetadata(bytecode: Bytecode) {
        val meta = bytecode.traceMetadata
        enterEvents = meta.enterEvents
        exitEvents = meta.exitEvents
        pathWrites = meta.pathWrites
        activePc = -1
    }

    /**
     * Execute bytecode with the given environment
     */
    fun execute(bytecode: Bytecode, env: MutableMap<String, Any?> = mutableMapOf()): Any? {
        check(!preparedBlockRunning || preparedBlockCanonicalEntryAllowed) { "Prepared block execution is not reentrant" }
        preparedBlockCanonicalEntryAllowed = false
        this.bytecode = bytecode
        this.environment = env
        privateMutationAllowed = ordinaryEnvironment
        this.pc = 0
        loadTraceMetadata(bytecode)

        stack.clear()
        callStack.clear()
        tryStack.clear()
        loopStack.clear()
        lastException = null
        lastSuspension = null
        lastHostFailure = null
        locals = arrayOf()
        prepareRootProgram(bytecode)
        outputs.clear()

        return try {
            val r = run()
            if (outputs.isNotEmpty()) normalizeOutputs() else r
        } catch (e: VMException.Abort) {
            val v = e.value
            if (v is Map<*, *> && v["__suspended"] == true) v else if (outputs.isNotEmpty()) normalizeOutputs() else v
        } finally {
            (currentTracer() as? AutoCloseable)?.close()
        }
    }

    /** Last invocation only; counters describe actual treatment work, including partial deoptimization. */
    @ExperimentalVMApi
    var experimentalReport: VMExperimentalReport = VMExperimentalReport(VMExperimentalOptions(), VMExperimentalOptions())
        private set

    @ExperimentalVMApi
    fun prepareExperimental(bytecode: Bytecode, options: VMExperimentalOptions = VMExperimentalOptions()): VMExperimentalPlan {
        var reason: String? = null
        for (at in 0 until bytecode.size()) {
            val op = OPCODES[bytecode.getOpcode(at)]
            if (op in setOf(Opcode.CALL_FN, Opcode.CALL_LAMBDA, Opcode.CALL, Opcode.TRY_START, Opcode.TRY_END,
                    Opcode.CATCH, Opcode.SUSPEND, Opcode.SET_STATIC, Opcode.SET_DYNAMIC, Opcode.PRIVATE_SET_STATIC,
                    Opcode.PRIVATE_SET_DYNAMIC, Opcode.MODIFY, Opcode.APPEND, Opcode.PLUS_ASSIGN, Opcode.CONCAT)) {
                reason = "unsupported opcode $op at $at"
                break
            }
            if (op == Opcode.ACCESS_STATIC) {
                val key = bytecode.getObjKeyOperand(at)
                if (key is IBig && key.v::class != BLBigInt::class) {
                    reason = "custom numeric key at $at"
                    break
                }
            }
            if (op == Opcode.PUSH && bytecode.getConstOperand(at) is LambdaTemplate) {
                reason = "closure at $at"
                break
            }
        }
        return VMExperimentalPlan(bytecode, options, reason,
            if (options.slots && reason == null) ExperimentalSlotPlan(bytecode) else null,
            if (options.fusion && reason == null) ExperimentalFusionPlan.prepare(bytecode) else null)
    }

    /** No execution is replayed. An observation boundary resumes ordinary VM dispatch at its original pc. */
    @ExperimentalVMApi
    fun executeExperimental(plan: VMExperimentalPlan, env: MutableMap<String, Any?> = mutableMapOf()): Any? {
        check(!preparedBlockRunning) { "Compiled execution is not reentrant" }
        val requested = plan.options
        val stats = if (requested.collectDiagnostics) ExperimentalDiagnostics() else null
        val reason = plan.fallbackReason ?: when {
            currentTracer() != null -> "tracing active"
            env::class != HashMap::class && env::class != LinkedHashMap::class -> "custom environment"
            else -> null
        }
        if ((!requested.slots && !requested.fusion) || reason != null) {
            experimentalReport = VMExperimentalReport(requested, VMExperimentalOptions(), reason)
            return execute(plan.bytecode, env)
        }
        begin(plan.bytecode, env)
        privateMutationAllowed = ordinaryEnvironment
        val slotPlan = plan.slots
        val frame = slotPlan?.let { ExperimentalSlotFrame(it, env) }
        val initialized = BooleanArray(slotPlan?.aliases?.size ?: 0)
        var attachedAliases = true
        var logicalLocalCapacity = 0
        var fallback: String? = null
        var usedSlots = false
        var usedFusion = false

        fun materializeAliases() {
            if (frame == null || !attachedAliases) return
            if (logicalLocalCapacity > 0) ensureLocals(logicalLocalCapacity)
            for (index in initialized.indices) if (initialized[index]) {
                val alias = slotPlan!!.aliases[index]
                if (alias >= 0) locals[index] = frame.read(alias, false)
            }
            attachedAliases = false
        }
        fun load(op: Opcode, at: Int) {
            when (op) {
                Opcode.LOAD_VAR, Opcode.LOAD_SCOPE -> {
                    if (frame == null) executeOpcode(op) else {
                        push(frame.read(slotPlan!!.byPc[at], op == Opcode.LOAD_VAR))
                        if (stats != null) stats.slotReads++; usedSlots = true
                    }
                }
                Opcode.LOAD_LOCAL -> {
                    val index = bytecode.getIntOperand(at)
                    val alias = slotPlan?.aliases?.getOrElse(index) { -1 } ?: -1
                    if (frame != null && attachedAliases && alias >= 0) {
                        push(if (initialized[index]) frame.read(alias, false) else null)
                        if (stats != null) stats.slotReads++; usedSlots = true
                    } else executeOpcode(op)
                }
                else -> error("not a load")
            }
        }
        fun stop(reason: String) {
            fallback = reason
            materializeAliases()
            frame?.flush()
        }
        fun ordinaryContainer(value: Any?): Boolean = value !== environment && when (value) {
            is Map<*, *> -> value::class == HashMap::class || value::class == LinkedHashMap::class
            is List<*> -> value::class == ArrayList::class || value::class == emptyList<Any?>()::class ||
                value::class == listOf<Any?>(null)::class || value::class == listOf(null, null)::class
            else -> true
        }
        // JVM bignum subclasses can execute user code. Materialize at their source PC
        // before ordinary dispatch; never invoke a callback to decide whether to deopt.
        fun scalar(value: Any?): Boolean = preparedBlockScalar(value)
        fun observerReason(op: Opcode): String? {
            val top = stack.peek()
            return when (op) {
                Opcode.ACCESS_STATIC, Opcode.INIT_FOREACH -> if (!ordinaryContainer(top) ||
                    (op == Opcode.INIT_FOREACH && top is Iterable<*> && top !is List<*>) || top is Sequence<*>) "custom collection at $pc" else null
                Opcode.ARRAY_BUILDER_ADD, Opcode.PRIVATE_ARRAY_APPEND ->
                    if (!ordinaryContainer(stack.elementAtOrNull(1))) "custom array builder at $pc" else null
                Opcode.ACCESS_DYNAMIC -> if (!scalar(top) || !ordinaryContainer(stack.elementAtOrNull(1))) "custom dynamic access at $pc" else null
                Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.DIV, Opcode.IDIV, Opcode.MOD,
                Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE ->
                    if (!scalar(top) || !scalar(stack.elementAtOrNull(1))) "observable scalar operation at $pc" else null
                Opcode.NEG -> if (!scalar(top)) "observable numeric operation at $pc" else null
                Opcode.AND, Opcode.OR ->
                    if (!preparedBlockTruthSafe(top) || !preparedBlockTruthSafe(stack.elementAtOrNull(1))) "observable truth operation at $pc" else null
                Opcode.NOT, Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE ->
                    if (!preparedBlockTruthSafe(top)) "observable truth operation at $pc" else null
                Opcode.MAKE_OBJECT, Opcode.OUTPUT -> {
                    val count = bytecode.getIntOperand(pc)
                    if ((0 until count).any { !scalar(stack.elementAtOrNull(it * 2 + if (op == Opcode.OUTPUT) 1 else 0)) }) "custom object key at $pc" else null
                }
                else -> null
            }
        }
        fun mirror(index: Int, local: Int) {
            // The removed DUP would have failed before either binding changed.
            if (stack.size >= MAX_STACK_SIZE) throw VMException.StackOverflow()
            val value = pop()
            frame!!.write(index, value)
            initialized[local] = true
            if (!attachedAliases) { ensureLocals(local + 1); locals[local] = value } else {
                val required = local + 1
                if (logicalLocalCapacity < required) {
                    val grow = if (logicalLocalCapacity == 0) 4 else logicalLocalCapacity * 2
                    logicalLocalCapacity = maxOf(4, maxOf(required, grow))
                }
            }
            if (stats != null) { stats.slotWrites++; stats.mirrors++; stats.elided += 2 }; usedSlots = true
        }
        fun storeLocal() {
            // Preserve the baseline's geometric capacity even while aliased values live only in slots.
            if (attachedAliases && logicalLocalCapacity > 0) ensureLocals(logicalLocalCapacity)
            executeOpcode(Opcode.STORE_LOCAL)
            logicalLocalCapacity = locals.size
        }
        try {
            while (pc < bytecode.size()) {
                if (pc < 0) pc = 0
                if (currentTracer() != null) { stop("tracing activated at $pc"); return run().let { if (outputs.isNotEmpty()) normalizeOutputs() else it } }
                val op = OPCODES[bytecode.getOpcode(pc)]
                val observation = observerReason(op)
                if (observation != null) { stop(observation); return run().let { if (outputs.isNotEmpty()) normalizeOutputs() else it } }
                if (frame != null && slotPlan!!.mirrorAt[pc] >= 0) {
                    mirror(slotPlan!!.mirrorAt[pc], bytecode.getIntOperand(pc + 2))
                    pc += 3
                    continue
                }
                val fusion = if (requested.fusion) plan.fusion?.byPc?.get(pc) else null
                if (fusion is ExperimentalFusion.StaticPath) {
                    load(fusion.load, pc)
                    pc++
                    var complete = true
                    for (key in fusion.keys) {
                        if (currentTracer() != null || !ordinaryContainer(peek())) { complete = false; break }
                        accessStatic(key)
                        pc++
                    }
                    val executed = pc - fusion.startPc
                    if (executed > 1) { if (stats != null) { stats.fused++; stats.elided += executed - 1 }; usedFusion = true }
                    if (!complete) {
                        stop("path observation boundary at $pc")
                        return run().let { if (outputs.isNotEmpty()) normalizeOutputs() else it }
                    }
                    continue
                }
                if (fusion is ExperimentalFusion.MirroredStore) {
                    // Fusion retains both baseline stores when slots are disabled.
                    executeOpcode(Opcode.DUP); pc++
                    if (frame == null) executeOpcode(Opcode.STORE_VAR) else {
                        frame.write(slotPlan!!.byPc[pc], pop()); if (stats != null) stats.slotWrites++; usedSlots = true
                    }
                    pc++
                    storeLocal(); pc++
                    if (stats != null) { stats.fused++; stats.elided += 2 }; usedFusion = true
                    continue
                }
                when (op) {
                    Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.LOAD_LOCAL -> load(op, pc)
                    Opcode.STORE_LOCAL -> storeLocal()
                    Opcode.STORE_VAR -> if (frame == null) executeOpcode(op) else {
                        frame.write(slotPlan!!.byPc[pc], pop()); if (stats != null) stats.slotWrites++; usedSlots = true
                    }
                    Opcode.INIT_FOREACH -> if (frame == null) executeOpcode(op) else {
                        val iterable = pop()
                        val list = iterable as? List<*> ?: throw VMException.TypeMismatch("iterable", iterable?.let { it::class.simpleName } ?: "null")
                        if (list.isEmpty()) pc = bytecode.getIntOperand(pc) - 1 else {
                            val index = slotPlan!!.byPc[pc]
                            val present = frame.contains(index)
                            val outer = frame.read(index, false)
                            loopStack.push(LoopState(bytecode.getStringOperand(pc), list, 0, present, outer, index))
                            frame.write(index, list[0]); if (stats != null) stats.loopWrites++; usedSlots = true
                        }
                    }
                    Opcode.NEXT_FOREACH -> if (frame == null) executeOpcode(op) else {
                        val state = loopStack.peek()
                        if (state == null) pc = bytecode.getIntOperand(pc, 1) - 1 else {
                            state.idx++
                            if (state.idx < state.list.size) {
                                frame.write(state.slotIndex, state.list[state.idx]); if (stats != null) stats.loopWrites++; usedSlots = true
                                pc = bytecode.getIntOperand(pc) - 1
                            } else {
                                if (state.hadOuter) frame.write(state.slotIndex, state.outerValue) else frame.remove(state.slotIndex)
                                if (stats != null) stats.loopWrites++; usedSlots = true; loopStack.pop()
                                pc = bytecode.getIntOperand(pc, 1) - 1
                            }
                        }
                    }
                    Opcode.CALL_HOST -> {
                        materializeAliases(); frame?.flush(); if (stats != null) stats.hostBarriers++
                        try { executeOpcode(op) } finally { frame?.invalidate() }
                        if (currentTracer() != null) emitExitEventsAt(pc)
                    }
                    else -> executeOpcode(op)
                }
                pc++
            }
            return if (outputs.isNotEmpty()) normalizeOutputs() else peekOrNull()
        } catch (abort: VMException.Abort) {
            // The ordinary entry point inspects the abort value for suspension.
            // Materialize before a user map can observe that lookup.
            materializeAliases()
            frame?.flush()
            val value = abort.value
            return if (value is Map<*, *> && value["__suspended"] == true) value
                else if (outputs.isNotEmpty()) normalizeOutputs() else value
        } finally {
            materializeAliases()
            frame?.flush()
            experimentalReport = VMExperimentalReport(requested, VMExperimentalOptions(usedSlots, usedFusion, requested.collectDiagnostics), fallback,
                stats?.slotReads ?: 0, stats?.slotWrites ?: 0, stats?.loopWrites ?: 0,
                stats?.mirrors ?: 0, stats?.fused ?: 0, stats?.elided ?: 0, stats?.hostBarriers ?: 0)
            (currentTracer() as? AutoCloseable)?.close()
        }
    }

    private var preparedBlockRunning = false
    private var preparedBlockCanonicalEntryAllowed = false
    private var preparedBlockFrame: PreparedBlockFrame? = null
    @ExperimentalVMApi
    var preparedBlockExecuted: Boolean = false
        private set
    @ExperimentalVMApi
    var preparedBlockOptimized: Boolean = false
        private set
    @ExperimentalVMApi
    var preparedBlockFallbackReason: String? = null
        private set

    @ExperimentalVMApi
    fun prepareBlocks(bytecode: Bytecode, optimize: Boolean = false): PreparedBlockPlan =
        PreparedBlockPlan.prepare(bytecode, optimize)

    /** Sequential, non-reentrant execution of an owned, resolved instruction plan. */
    @ExperimentalVMApi
    fun executeBlocks(plan: PreparedBlockPlan, env: MutableMap<String, Any?> = mutableMapOf()): Any? {
        check(!preparedBlockRunning) { "Prepared block execution is not reentrant" }
        preparedBlockExecuted = false
        preparedBlockOptimized = false
        preparedBlockFallbackReason = plan.fallbackReason ?: when {
            currentTracer() != null -> "tracing active"
            env::class != HashMap::class && env::class != LinkedHashMap::class -> "custom environment"
            else -> null
        }
        if (preparedBlockFallbackReason != null) {
            preparedBlockRunning = true
            preparedBlockCanonicalEntryAllowed = true
            return try { execute(plan.bytecode, env) } finally {
                preparedBlockCanonicalEntryAllowed = false
                preparedBlockRunning = false
            }
        }
        begin(plan.bytecode, env)
        privateMutationAllowed = ordinaryEnvironment
        val frame = if (plan.optimize) {
            val cached = preparedBlockFrame
            (if (cached?.plan === plan) cached else PreparedBlockFrame(plan).also { preparedBlockFrame = it })
                .also { it.reset(env) }
        } else null
        preparedBlockRunning = true
        preparedBlockExecuted = true
        try {
            return runPreparedBlocks(plan, frame)
        } catch (abort: VMException.Abort) {
            materializePreparedBlockLocals(frame)
            frame?.flush()
            val value = abort.value
            return if (value is Map<*, *> && value["__suspended"] == true) value
                else if (outputs.isNotEmpty()) normalizeOutputs() else value
        } finally {
            finishPreparedBlocks(frame)
        }
    }

    private fun runPreparedBlocks(plan: PreparedBlockPlan, frame: PreparedBlockFrame?): Any? {
        while (pc < bytecode.size()) {
            if (pc < 0) pc = 0
            val instruction = plan.instructions[pc] ?: error("Invalid prepared instruction entry $pc")
            val sourcePc = instruction.sourcePc
            var nextPc = instruction.nextPc
            when (instruction.kind) {
                PreparedBlockOp.PUSH -> push(instruction.value)
                PreparedBlockOp.DUP -> push(peek())
                PreparedBlockOp.POP -> pop()
                PreparedBlockOp.SWAP -> { val a = pop(); val b = pop(); push(a); push(b) }
                PreparedBlockOp.LOAD_VAR, PreparedBlockOp.LOAD_SCOPE, PreparedBlockOp.LOAD_LOCAL,
                PreparedBlockOp.SLOT_LOAD, PreparedBlockOp.SLOT_SCOPE, PreparedBlockOp.ALIAS_LOAD -> {
                    push(readPreparedBlockValue(instruction, frame))
                }
                PreparedBlockOp.STORE_VAR -> storeVar(instruction.name!!)
                PreparedBlockOp.STORE_LOCAL -> {
                    if (frame != null && frame.aliasesAttached && frame.logicalLocalCapacity > 0) ensureLocals(frame.logicalLocalCapacity)
                    val value = pop()
                    ensureLocals(instruction.a + 1)
                    locals[instruction.a] = value
                    if (frame != null) frame.logicalLocalCapacity = locals.size
                }
                PreparedBlockOp.SLOT_STORE -> {
                    frame!!.write(instruction.a, pop())
                    preparedBlockOptimized = true
                }
                PreparedBlockOp.MIRROR_STORE -> {
                    // Preserve DUP's failure before either binding is changed.
                    if (stack.size >= MAX_STACK_SIZE) throw VMException.StackOverflow()
                    val value = pop()
                    frame!!.write(instruction.a, value)
                    frame.initializedLocals[instruction.b] = true
                    if (frame.aliasesAttached) {
                        val required = instruction.b + 1
                        if (required > frame.logicalLocalCapacity) {
                            val grow = if (frame.logicalLocalCapacity == 0) 4 else frame.logicalLocalCapacity * 2
                            frame.logicalLocalCapacity = maxOf(4, maxOf(required, grow))
                        }
                    } else {
                        ensureLocals(instruction.b + 1)
                        locals[instruction.b] = value
                    }
                    preparedBlockOptimized = true
                }
                PreparedBlockOp.STATIC_PATH -> {
                    var container = readPreparedBlockValue(instruction.load!!, frame)
                    // The original load must still fail on a full stack, before the first access.
                    if (stack.size >= MAX_STACK_SIZE) throw VMException.StackOverflow()
                    val keys = instruction.keys!!
                    var index = 0
                    while (index < keys.size) {
                        pc = sourcePc + index + 1
                        if (!PreparedBlockGuards.ordinaryContainer(container, environment)) {
                            // Reconstruct the exact stack before this original access, without replaying the load/prefix.
                            push(container)
                            return deoptPreparedBlocks(frame, "static path observer")
                        }
                        container = readPreparedBlockStatic(container, keys[index])
                        index++
                    }
                    push(container)
                    preparedBlockOptimized = true
                }
                PreparedBlockOp.ADD -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.ADD)
                }
                PreparedBlockOp.SUB -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.SUB)
                }
                PreparedBlockOp.MUL -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.MUL)
                }
                PreparedBlockOp.DIV -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.DIV)
                }
                PreparedBlockOp.IDIV -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.IDIV)
                }
                PreparedBlockOp.MOD -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryNumeric(VmNumericOp.MOD)
                }
                PreparedBlockOp.NEG -> {
                    if (frame != null && !preparedBlockScalar(stack.peek())) return deoptPreparedBlocks(frame, "custom scalar")
                    unaryArithmetic { negateNumber(it) }
                }
                PreparedBlockOp.EQ -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> equalsValues(l, r) }
                }
                PreparedBlockOp.NEQ -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> !equalsValues(l, r) }
                }
                PreparedBlockOp.LT -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> compareValues(l, r) < 0 }
                }
                PreparedBlockOp.LE -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> compareValues(l, r) <= 0 }
                }
                PreparedBlockOp.GT -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> compareValues(l, r) > 0 }
                }
                PreparedBlockOp.GE -> {
                    if (frame != null && !preparedBlockPairSafe()) return deoptPreparedBlocks(frame, "custom scalar")
                    binaryComparison { l, r -> compareValues(l, r) >= 0 }
                }
                PreparedBlockOp.AND -> {
                    if (frame != null && (!preparedBlockTruthSafe(stack.peek()) || !preparedBlockTruthSafe(stack.elementAtOrNull(1)))) return deoptPreparedBlocks(frame, "custom truth value")
                    val right = pop(); val left = pop(); push(if (!left.asBool()) false else right.asBool())
                }
                PreparedBlockOp.OR -> {
                    if (frame != null && (!preparedBlockTruthSafe(stack.peek()) || !preparedBlockTruthSafe(stack.elementAtOrNull(1)))) return deoptPreparedBlocks(frame, "custom truth value")
                    val right = pop(); val left = pop(); push(if (left.asBool()) true else right.asBool())
                }
                PreparedBlockOp.NOT -> {
                    if (frame != null && !preparedBlockTruthSafe(stack.peek())) return deoptPreparedBlocks(frame, "custom truth value")
                    push(!pop().asBool())
                }
                PreparedBlockOp.COALESCE -> { val right = pop(); val left = pop(); push(left ?: right) }
                PreparedBlockOp.MAKE_OBJECT -> {
                    if (frame != null && !preparedBlockKeysSafe(instruction.a, 0)) return deoptPreparedBlocks(frame, "custom object key")
                    makeObject(instruction.a)
                }
                PreparedBlockOp.MAKE_ARRAY -> makeArray(instruction.a)
                PreparedBlockOp.ACCESS_STATIC -> {
                    if (frame != null && !PreparedBlockGuards.ordinaryContainer(stack.peek(), environment)) return deoptPreparedBlocks(frame, "custom static container")
                    accessStatic(instruction.key!!)
                }
                PreparedBlockOp.ACCESS_DYNAMIC -> {
                    if (frame != null && (!preparedBlockScalar(stack.peek()) || !PreparedBlockGuards.ordinaryContainer(stack.elementAtOrNull(1), environment))) return deoptPreparedBlocks(frame, "custom dynamic container")
                    accessDynamic()
                }
                PreparedBlockOp.ARRAY_BUILDER_INIT -> initArrayBuilder()
                PreparedBlockOp.ARRAY_BUILDER_ADD -> {
                    if (frame != null && !PreparedBlockGuards.ordinaryContainer(stack.elementAtOrNull(1), environment)) return deoptPreparedBlocks(frame, "custom array builder")
                    arrayBuilderAdd()
                }
                PreparedBlockOp.PRIVATE_ARRAY_APPEND -> {
                    if (frame != null && !PreparedBlockGuards.ordinaryContainer(stack.elementAtOrNull(1), environment)) return deoptPreparedBlocks(frame, "custom array builder")
                    arrayBuilderAdd(privateRoot = true)
                }
                PreparedBlockOp.ARRAY_BUILDER_FINISH -> arrayBuilderFinish()
                PreparedBlockOp.JUMP -> nextPc = instruction.a
                PreparedBlockOp.JUMP_IF_TRUE -> {
                    if (frame != null && !preparedBlockTruthSafe(stack.peek())) return deoptPreparedBlocks(frame, "custom truth value")
                    if (pop().asBool()) nextPc = instruction.a
                }
                PreparedBlockOp.JUMP_IF_FALSE -> {
                    if (frame != null && !preparedBlockTruthSafe(stack.peek())) return deoptPreparedBlocks(frame, "custom truth value")
                    if (!pop().asBool()) nextPc = instruction.a
                }
                PreparedBlockOp.JUMP_IF_NULL -> if (pop() == null) nextPc = instruction.a
                PreparedBlockOp.CALL_HOST -> {
                    materializePreparedBlockLocals(frame)
                    frame?.flush()
                    try { callHost(instruction.name!!, instruction.b) } finally { frame?.invalidate() }
                }
                PreparedBlockOp.RETURN -> handleReturn(null)
                PreparedBlockOp.RETURN_VALUE -> handleReturn(pop())
                PreparedBlockOp.OUTPUT -> {
                    if (frame != null && !preparedBlockKeysSafe(instruction.a, 1)) return deoptPreparedBlocks(frame, "custom output key")
                    when (instruction.a) {
                        1 -> createOutput1()
                        2 -> createOutput2()
                        else -> createOutput(instruction.a)
                    }
                }
                PreparedBlockOp.INIT_FOREACH -> {
                    if (frame != null) {
                        val value = stack.peek()
                        if (!PreparedBlockGuards.ordinaryContainer(value, environment) || (value is Iterable<*> && value !is List<*>) || value is Sequence<*>) {
                            return deoptPreparedBlocks(frame, "custom iterable")
                        }
                    }
                    if (frame == null) {
                        initForEach(instruction.name!!, instruction.a)
                        if (pc != sourcePc) nextPc = pc + 1
                    } else {
                        val iterable = pop()
                        val list = iterable as? List<*> ?: throw VMException.TypeMismatch("iterable", iterable?.let { it::class.simpleName } ?: "null")
                        if (list.isEmpty()) nextPc = instruction.a else {
                            val hadOuter = frame.contains(instruction.b)
                            val outer = frame.read(instruction.b, false)
                            loopStack.push(LoopState(instruction.name!!, list, 0, hadOuter, outer, instruction.b))
                            frame.write(instruction.b, list[0])
                            preparedBlockOptimized = true
                        }
                    }
                }
                PreparedBlockOp.NEXT_FOREACH -> {
                    if (frame == null) {
                        nextForEach(instruction.a, instruction.b)
                        nextPc = pc + 1
                    } else {
                        val state = loopStack.peek()
                        if (state == null) nextPc = instruction.b else {
                            val next = state.idx + 1
                            if (next < state.list.size) {
                                state.idx = next
                                frame.write(state.slotIndex, state.list[next])
                                nextPc = instruction.a
                            } else {
                                if (state.hadOuter) frame.write(state.slotIndex, state.outerValue) else frame.remove(state.slotIndex)
                                loopStack.pop()
                                nextPc = instruction.b
                            }
                            preparedBlockOptimized = true
                        }
                    }
                }
                PreparedBlockOp.ABORT -> throw VMException.Abort(if (stack.isEmpty()) null else pop())
                PreparedBlockOp.TRACE -> emitTrace(instruction.name!!)
                PreparedBlockOp.BREAKPOINT -> emitBreakpoint(instruction.name!!)
                PreparedBlockOp.LINE -> emitLineNumber(instruction.a)
                PreparedBlockOp.NOP -> Unit
            }
            // Control mode may encounter an observable value directly; optimized mode guards before it.
            if (instruction.observation != PreparedBlockObservation.NONE && currentTracer() != null) {
                emitExitEventsAt(sourcePc)
                pc = nextPc
                return deoptPreparedBlocks(frame, "tracing activated")
            }
            pc = nextPc
        }
        return if (outputs.isNotEmpty()) normalizeOutputs() else peekOrNull()
    }

    private fun finishPreparedBlocks(frame: PreparedBlockFrame?) {
        try {
            materializePreparedBlockLocals(frame)
            frame?.flush()
        } finally {
            frame?.clear()
            try {
                (currentTracer() as? AutoCloseable)?.close()
            } finally {
                preparedBlockRunning = false
            }
        }
    }

    private fun readPreparedBlockValue(instruction: PreparedBlockInstruction, frame: PreparedBlockFrame?): Any? = when (instruction.kind) {
        PreparedBlockOp.LOAD_VAR -> loadVariable(instruction.name!!)
        PreparedBlockOp.LOAD_SCOPE -> environment[instruction.name!!]
        PreparedBlockOp.LOAD_LOCAL -> locals.getOrNull(instruction.a)
        PreparedBlockOp.SLOT_LOAD, PreparedBlockOp.SLOT_SCOPE -> {
            preparedBlockOptimized = true
            frame!!.read(instruction.a, instruction.kind == PreparedBlockOp.SLOT_LOAD)
        }
        PreparedBlockOp.ALIAS_LOAD -> {
            if (frame!!.aliasesAttached) {
                preparedBlockOptimized = true
                if (frame.initializedLocals[instruction.a]) frame.read(instruction.b, false) else null
            } else locals.getOrNull(instruction.a)
        }
        else -> error("Not a prepared load")
    }

    private fun materializePreparedBlockLocals(frame: PreparedBlockFrame?) {
        if (frame == null || !frame.aliasesAttached) return
        if (frame.logicalLocalCapacity > 0) ensureLocals(frame.logicalLocalCapacity)
        for (index in frame.initializedLocals.indices) if (frame.initializedLocals[index]) {
            val alias = frame.plan.aliases[index]
            if (alias >= 0) locals[index] = frame.read(alias, false)
        }
        frame.aliasesAttached = false
    }

    private fun deoptPreparedBlocks(frame: PreparedBlockFrame?, reason: String): Any? {
        preparedBlockFallbackReason = reason
        materializePreparedBlockLocals(frame)
        frame?.flush()
        val value = run()
        return if (outputs.isNotEmpty()) normalizeOutputs() else value
    }

    private fun readPreparedBlockStatic(container: Any?, key: ObjKey): Any? = when (container) {
        is Map<*, *> -> container[unwrapKey(key)]
        is List<*> -> {
            val index = staticIndex(key)
            if (index < 0 || index >= container.size) throw VMException.IndexOutOfBounds(index, container.size)
            container[index]
        }
        else -> throw VMException.TypeMismatch("object or array", container?.let { it::class.simpleName } ?: "null")
    }

    private fun preparedBlockScalar(value: Any?): Boolean = value == null || value is String || value is Boolean ||
        value is Byte || value is Short || value is Int || value is Long || value is Float || value is Double ||
        (value is BLBigInt && value::class == BLBigInt::class) ||
        (value is BLBigDec && value::class == BLBigDec::class)

    private fun preparedBlockTruthSafe(value: Any?): Boolean = when (value) {
        is BLBigInt -> value::class == BLBigInt::class
        is BLBigDec -> value::class == BLBigDec::class
        else -> true
    }

    private fun preparedBlockPairSafe(): Boolean =
        preparedBlockScalar(stack.peek()) && preparedBlockScalar(stack.elementAtOrNull(1))

    private fun preparedBlockKeysSafe(count: Int, offset: Int): Boolean {
        var index = 0
        while (index < count) {
            if (!preparedBlockScalar(stack.elementAtOrNull(index * 2 + offset))) return false
            index++
        }
        return true
    }

    private var loopBodyContext: LoopContext? = null
    @ExperimentalVMApi
    var loopBodyExecuted: Boolean = false
        private set
    @ExperimentalVMApi
    var loopBodyEntries: Int = 0
        private set
    @ExperimentalVMApi
    var loopBodyFallbackReason: String? = null
        private set

    /** JVM plans have one generated comprehension; prefix/suffix retain ordinary VM dispatch. */
    @ExperimentalVMApi
    fun executeLoopBodies(plan: LoopBodyPlan, env: MutableMap<String, Any?> = mutableMapOf()): Any? {
        check(!preparedBlockRunning) { "Compiled execution is not reentrant" }
        loopBodyExecuted = false
        loopBodyEntries = 0
        loopBodyFallbackReason = plan.fallbackReason ?: when {
            currentTracer() != null -> "tracing active"
            env::class != HashMap::class && env::class != LinkedHashMap::class -> "custom environment"
            else -> null
        }
        if (loopBodyFallbackReason != null) {
            preparedBlockRunning = true
            preparedBlockCanonicalEntryAllowed = true
            return try { execute(plan.bytecode, env) } finally {
                preparedBlockCanonicalEntryAllowed = false
                preparedBlockRunning = false
            }
        }
        begin(plan.bytecode, env)
        privateMutationAllowed = ordinaryEnvironment
        val context = loopBodyContext ?: LoopContext().also { loopBodyContext = it }
        context.bind(plan)
        preparedBlockRunning = true
        try {
            // The recognized prefix is linear. Execute INIT exactly once with its original effects.
            run(limit = plan.bodyStart)
            if (pc == plan.bodyStart) {
                if (currentTracer() != null) loopBodyFallbackReason = "tracing active after loop initialization" else {
                    loopBodyExecuted = true
                    loopBodyEntries++
                    try { plan.kernel!!.run(context) } finally { activePc = -1 }
                }
            }
            val result = run()
            return if (outputs.isNotEmpty()) normalizeOutputs() else result
        } catch (abort: VMException.Abort) {
            val value = abort.value
            return if (value is Map<*, *> && value["__suspended"] == true) value
                else if (outputs.isNotEmpty()) normalizeOutputs() else value
        } finally {
            context.release()
            // Includes tracer.close within the non-reentrant lifetime, even when close throws.
            finishPreparedBlocks(null)
        }
    }

    private inner class LoopContext : LoopBodyContext {
        private var plan: LoopBodyPlan? = null
        private var sourcePc = -1
        fun bind(value: LoopBodyPlan) { plan = value }
        fun release() { plan = null; sourcePc = -1 }
        override fun mark(sourcePc: Int) {
            this.sourcePc = sourcePc
            pc = sourcePc
            activePc = sourcePc
        }
        override fun after(nextPc: Int): Boolean {
            // Canonical run emits Exit after clearing activePc and before its final pc++.
            pc = nextPc - 1
            activePc = -1
            val tracing = currentTracer() != null
            if (tracing) {
                emitExitEventsAt(sourcePc)
                loopBodyFallbackReason = "tracing activated in compiled loop"
            }
            pc = nextPc
            activePc = -1
            return tracing
        }
        override fun pushConstant(sourcePc: Int) = push(plan!!.constantsByPc[sourcePc])
        override fun dup() = push(peek())
        override fun pop() { this@VM.pop() }
        override fun swap() { val a = this@VM.pop(); val b = this@VM.pop(); push(a); push(b) }
        override fun loadVariable(name: String) {
            val value = this@VM.loadVariable(name)
            push(value)
            emitVarRead(name, value)
        }
        override fun loadScope(name: String) = push(environment[name])
        override fun loadLocal(index: Int) = push(locals.getOrNull(index))
        override fun accessStatic(sourcePc: Int) = this@VM.accessStatic(plan!!.keysByPc[sourcePc]!!)
        override fun accessDynamic() = this@VM.accessDynamic()
        override fun add() = binaryNumeric(VmNumericOp.ADD)
        override fun sub() = binaryNumeric(VmNumericOp.SUB)
        override fun mul() = binaryNumeric(VmNumericOp.MUL)
        override fun div() = binaryNumeric(VmNumericOp.DIV)
        override fun idiv() = binaryNumeric(VmNumericOp.IDIV)
        override fun mod() = binaryNumeric(VmNumericOp.MOD)
        override fun neg() = unaryArithmetic { negateNumber(it) }
        override fun eq() = binaryComparison { l, r -> equalsValues(l, r) }
        override fun neq() = binaryComparison { l, r -> !equalsValues(l, r) }
        override fun lt() = binaryComparison { l, r -> compareValues(l, r) < 0 }
        override fun le() = binaryComparison { l, r -> compareValues(l, r) <= 0 }
        override fun gt() = binaryComparison { l, r -> compareValues(l, r) > 0 }
        override fun ge() = binaryComparison { l, r -> compareValues(l, r) >= 0 }
        override fun and() { val right = this@VM.pop(); val left = this@VM.pop(); push(if (!left.asBool()) false else right.asBool()) }
        override fun or() { val right = this@VM.pop(); val left = this@VM.pop(); push(if (left.asBool()) true else right.asBool()) }
        override fun not() = push(!this@VM.pop().asBool())
        override fun coalesce() { val right = this@VM.pop(); val left = this@VM.pop(); push(left ?: right) }
        override fun makeObject(count: Int) = this@VM.makeObject(count)
        override fun makeArray(count: Int) = this@VM.makeArray(count)
        override fun arrayBuilderAdd() = this@VM.arrayBuilderAdd()
        override fun popTruth(): Boolean = this@VM.pop().asBool()
        override fun nextForeach(start: Int, end: Int): Boolean {
            nextForEach(start, end)
            return pc + 1 == start
        }
    }

    private var localLoopContext: LocalContext? = null
    @ExperimentalVMApi
    var localLoopExecuted: Boolean = false
        private set
    @ExperimentalVMApi
    var localLoopEntries: Int = 0
        private set
    @ExperimentalVMApi
    var localLoopPrimitiveUsed: Boolean = false
        private set
    @ExperimentalVMApi
    var localLoopFallbackReason: String? = null
        private set

    @ExperimentalVMApi
    fun executeLocalLoops(plan: LocalLoopPlan, env: MutableMap<String, Any?> = mutableMapOf()): Any? {
        check(!preparedBlockRunning) { "Compiled execution is not reentrant" }
        localLoopExecuted = false
        localLoopEntries = 0
        localLoopPrimitiveUsed = false
        localLoopFallbackReason = plan.fallbackReason ?: when {
            currentTracer() != null -> "tracing active"
            env::class != HashMap::class && env::class != LinkedHashMap::class -> "custom environment"
            else -> null
        }
        if (localLoopFallbackReason != null) {
            preparedBlockRunning = true
            preparedBlockCanonicalEntryAllowed = true
            return try { execute(plan.bytecode, env) } finally {
                preparedBlockCanonicalEntryAllowed = false
                preparedBlockRunning = false
            }
        }
        begin(plan.bytecode, env)
        privateMutationAllowed = ordinaryEnvironment
        val context = localLoopContext ?: LocalContext().also { localLoopContext = it }
        context.bind(plan)
        preparedBlockRunning = true
        try {
            run(limit = plan.bodyStart)
            if (pc == plan.bodyStart) {
                when {
                    currentTracer() != null -> localLoopFallbackReason = "tracing active after loop initialization"
                    stack.size.toLong() + plan.maxVirtualHeight > MAX_STACK_SIZE -> localLoopFallbackReason = "virtual stack capacity guard"
                    else -> {
                        localLoopExecuted = true
                        localLoopEntries++
                        try { plan.kernel!!.run(context) } finally { activePc = -1 }
                    }
                }
            }
            val result = run()
            return if (outputs.isNotEmpty()) normalizeOutputs() else result
        } catch (abort: VMException.Abort) {
            val value = abort.value
            return if (value is Map<*, *> && value["__suspended"] == true) value
                else if (outputs.isNotEmpty()) normalizeOutputs() else value
        } finally {
            context.release()
            finishPreparedBlocks(null)
        }
    }

    private inner class LocalContext : LocalLoopContext {
        private var plan: LocalLoopPlan? = null
        fun bind(value: LocalLoopPlan) { plan = value }
        fun release() { plan = null }
        override fun constant(sourcePc: Int): Any? = plan!!.constantsByPc[sourcePc]
        override fun key(sourcePc: Int): Any = plan!!.keysByPc[sourcePc]!!
        override fun hasVariable(name: String): Boolean = environment.containsKey(name)
        override fun variable(name: String): Any? = environment[name]
        override fun scope(name: String): Any? = environment[name]
        override fun local(index: Int): Any? = locals.getOrNull(index)
        override fun beginSpill(sourcePc: Int, primitiveUsed: Boolean) {
            localLoopPrimitiveUsed = localLoopPrimitiveUsed || primitiveUsed
            localLoopFallbackReason = "guard before source opcode at $sourcePc"
            pc = sourcePc
            activePc = -1
        }
        override fun spill(value: Any?) = push(value)
        override fun append(value: Any?, sourcePc: Int) {
            pc = sourcePc
            activePc = sourcePc
            push(value)
            arrayBuilderAdd()
            activePc = -1
            pc = sourcePc + 1
        }
        override fun next(sourcePc: Int, start: Int, end: Int, primitiveUsed: Boolean): Int {
            localLoopPrimitiveUsed = localLoopPrimitiveUsed || primitiveUsed
            pc = sourcePc
            activePc = sourcePc
            nextForEach(start, end)
            val target = pc + 1
            activePc = -1
            val tracing = currentTracer() != null
            if (tracing) {
                emitExitEventsAt(sourcePc)
                localLoopFallbackReason = "tracing activated at loop boundary"
            }
            pc = target
            return if (tracing) 0 else if (target == start) 1 else 2
        }
        override fun finish(primitiveUsed: Boolean) {
            localLoopPrimitiveUsed = localLoopPrimitiveUsed || primitiveUsed
        }
    }

    // ----- Incremental execution API (for pause/resume) -----

    /** Prepare VM for incremental execution without running. */
    fun begin(bytecode: Bytecode, env: MutableMap<String, Any?> = mutableMapOf()) {
        check(!preparedBlockRunning) { "Prepared block execution is not reentrant" }
        privateMutationAllowed = false
        this.bytecode = bytecode
        this.environment = env
        this.pc = 0
        loadTraceMetadata(bytecode)
        stack.clear()
        callStack.clear()
        tryStack.clear()
        loopStack.clear()
        lastException = null
        lastSuspension = null
        lastHostFailure = null
        locals = arrayOf()
        prepareRootProgram(bytecode)
        outputs.clear()
    }

    /**
     * Run up to maxSteps instructions (or to completion). Returns true if finished.
     */
    fun step(maxSteps: Int = Int.MAX_VALUE): Boolean {
        check(!preparedBlockRunning) { "Prepared block execution is not reentrant" }
        return try {
            run(limit = maxSteps)
            pc >= bytecode.size()
        } catch (e: VMException.Abort) {
            val v = e.value
            // Distinguish suspension vs. normal return
            if (v is Map<*, *> && v["__suspended"] == true) {
                lastSuspension = v
                false
            } else {
                // Treat as normal return: set result on stack and mark finished
                stack.clear()
                if (v != null) stack.push(v)
                pc = bytecode.size()
                true
            }
        } finally {
            if (pc >= bytecode.size()) (currentTracer() as? AutoCloseable)?.close()
        }
    }

    /**
     * Resume execution until the program either finishes or suspends.
     * Returns true if finished, false if suspended.
     */
    fun resume(): Boolean = step(Int.MAX_VALUE)

    /** Current result if finished, else null. */
    fun resultOrNull(): Any? = if (pc >= bytecode.size()) if (stack.isEmpty()) null else stack.peek() else null

    /**
     * Main execution loop
     */
    private fun run(limit: Int = Int.MAX_VALUE, targetCallDepth: Int = -1): Any? {
        var steps = 0
        while (steps < limit) {
            val currentBytecode = bytecode
            val size = currentBytecode.size()
            if (pc < 0) pc = 0
            if (pc >= size) break
            val currentPc = pc
            emitEnterEventsAt(currentPc)

            val opcode = OPCODES[currentBytecode.getOpcode(currentPc)]
            val tracer = currentTracer()
            if (tracer is CollectingTracer) {
                val key = opcode.name
                tracer.instructionCounts[key] = (tracer.instructionCounts[key] ?: 0L) + 1
            }

            if (tracer?.opts?.step == true) {
                tracer.on(TraceEvent.Call("STEP", opcode.name, emptyList()))
            }

            var executed = false
            activePc = currentPc
            try {
                executeOpcode(opcode)
                executed = true
                if (tracer?.opts?.step == true) {
                    tracer.on(TraceEvent.Return("STEP", opcode.name, peekOrNull()))
                }
            } catch (e: Exception) {
                if (tracer?.opts?.step == true) {
                    tracer.on(TraceEvent.Error("VM step ${opcode.name}", e))
                }
                val handled = handleException(e, targetCallDepth)
                if (!handled) {
                    if (e !is VMException.Abort) {
                        emitCallError(e)
                    }
                    throw e
                }
            } finally {
                activePc = -1
            }

            if (executed) {
                emitExitEventsAt(currentPc)
            }

            pc++
            while (pc >= bytecode.size() && callStack.isNotEmpty()) {
                returnFromFunction(null)
                // Explicit RETURN resumes through this loop's pc++ above. An
                // implicit return occurs after it, so advance the restored pc too.
                pc++
            }
            if (targetCallDepth >= 0 && callStack.size <= targetCallDepth) {
                break
            }
            steps++
        }
        return if (stack.isEmpty()) null else stack.peek()
    }

    /**
     * Execute a single instruction
     */
    private fun executeInstruction(instruction: Instruction) {
        when (instruction) {
            // Stack Operations
            is Instruction.PUSH -> {
                val v = instruction.value
                if (v is LambdaTemplate) {
                    // Closures retain their live lexical environment. Callee
                    // parameters and local bindings are isolated in OverlayEnv.
                    push(LambdaValue(v.params, v.bytecode, environment))
                } else {
                    push(v)
                }
            }
            is Instruction.DUP -> push(peek())
            is Instruction.POP -> pop()
            is Instruction.SWAP -> {
                val a = pop()
                val b = pop()
                push(a)
                push(b)
            }

            // Variable Operations
            is Instruction.LOAD_VAR -> {
                val value = loadVariable(instruction.name)
                push(value)
                emitVarRead(instruction.name, value)
            }

            is Instruction.STORE_VAR -> storeVar(instruction.name)

            is Instruction.LOAD_SCOPE -> {
                val value = environment[instruction.name]
                push(value)
            }
            is Instruction.LOAD_LOCAL -> {
                val idx = instruction.index
                val v = if (idx >= 0 && idx < locals.size) locals[idx] else null
                push(v)
            }
            is Instruction.STORE_LOCAL -> {
                val value = pop()
                val idx = instruction.index
                ensureLocals(idx + 1)
                locals[idx] = value
            }

            // Arithmetic Operations
            is Instruction.ADD -> binaryNumeric(VmNumericOp.ADD)
            is Instruction.SUB -> binaryNumeric(VmNumericOp.SUB)
            is Instruction.MUL -> binaryNumeric(VmNumericOp.MUL)
            is Instruction.DIV -> binaryNumeric(VmNumericOp.DIV)
            is Instruction.IDIV -> binaryNumeric(VmNumericOp.IDIV)
            is Instruction.MOD -> binaryNumeric(VmNumericOp.MOD)
            is Instruction.NEG -> unaryArithmetic { negateNumber(it) }

            // Comparison Operations
            is Instruction.EQ -> binaryComparison { l, r -> equalsValues(l, r) }
            is Instruction.NEQ -> binaryComparison { l, r -> !equalsValues(l, r) }
            is Instruction.LT -> binaryComparison { l, r -> compareValues(l, r) < 0 }
            is Instruction.LE -> binaryComparison { l, r -> compareValues(l, r) <= 0 }
            is Instruction.GT -> binaryComparison { l, r -> compareValues(l, r) > 0 }
            is Instruction.GE -> binaryComparison { l, r -> compareValues(l, r) >= 0 }

            // Logical Operations
            is Instruction.AND -> {
                val right = pop()
                val left = pop()
                push(if (!left.asBool()) false else right.asBool())
            }
            is Instruction.OR -> {
                val right = pop()
                val left = pop()
                push(if (left.asBool()) true else right.asBool())
            }
            is Instruction.NOT -> push(!pop().asBool())
            is Instruction.COALESCE -> {
                val right = pop()
                val left = pop()
                push(left ?: right)
            }

            // Object/Array Operations
            is Instruction.MAKE_OBJECT -> makeObject(instruction.fieldCount)
            is Instruction.MAKE_ARRAY -> makeArray(instruction.elementCount)
            is Instruction.ACCESS_STATIC -> accessStatic(instruction.key)
            is Instruction.ACCESS_DYNAMIC -> accessDynamic()
            is Instruction.SET_STATIC -> setStatic(instruction.key)
            is Instruction.SET_DYNAMIC -> setDynamic()
            is Instruction.PRIVATE_SET_STATIC -> setStatic(instruction.key, privateRoot = true)
            Instruction.PRIVATE_SET_DYNAMIC -> setDynamic(privateRoot = true)
            is Instruction.APPEND -> appendToArray()
            Instruction.ARRAY_BUILDER_INIT -> initArrayBuilder()
            Instruction.ARRAY_BUILDER_ADD -> arrayBuilderAdd()
            Instruction.PRIVATE_ARRAY_APPEND -> arrayBuilderAdd(privateRoot = true)
            Instruction.ARRAY_BUILDER_FINISH -> arrayBuilderFinish()
            is Instruction.PLUS_ASSIGN -> plusAssign()
            is Instruction.CONCAT -> concatenateArrays()

            // Control Flow
            is Instruction.JUMP -> pc = instruction.address - 1 // -1 because pc++ at end
            is Instruction.JUMP_IF_TRUE -> {
                if (pop().asBool()) pc = instruction.address - 1
            }
            is Instruction.JUMP_IF_FALSE -> {
                if (!pop().asBool()) pc = instruction.address - 1
            }
            is Instruction.JUMP_IF_NULL -> {
                if (pop() == null) pc = instruction.address - 1
            }

            // Function Calls
            is Instruction.CALL -> callFunction(instruction.name, instruction.argCount)
            is Instruction.CALL_HOST -> callHost(instruction.name, instruction.argCount)
            is Instruction.CALL_FN -> callUserFunction(instruction.name, instruction.argCount)
            is Instruction.CALL_LAMBDA -> callLambda(instruction.argCount)
            is Instruction.RETURN -> {
                if (callStack.isNotEmpty()) returnFromFunction(null) else handleReturn(null)
            }
            is Instruction.RETURN_VALUE -> {
                val v = pop()
                if (callStack.isNotEmpty()) returnFromFunction(v) else handleReturn(v)
            }

            // Special Operations
            is Instruction.OUTPUT -> createOutput(instruction.fieldCount)
            Instruction.OUTPUT_1 -> createOutput1()
            Instruction.OUTPUT_2 -> createOutput2()
            is Instruction.MODIFY -> modifyObject(instruction.updateCount)
            is Instruction.INIT_FOREACH -> initForEach(instruction.varName, instruction.jumpToEnd)
            is Instruction.NEXT_FOREACH -> nextForEach(instruction.jumpToStart, instruction.jumpToEnd)
            is Instruction.TRY_START -> startTry(instruction.catchAddress)
            is Instruction.TRY_END -> endTry()
            is Instruction.CATCH -> handleCatch(instruction.exceptionVar, instruction.retryCount)
            is Instruction.ABORT -> throw VMException.Abort(if (stack.isEmpty()) null else pop())

            // Debug Operations
            is Instruction.TRACE -> emitTrace(instruction.event)
            is Instruction.BREAKPOINT -> emitBreakpoint(instruction.info)
            is Instruction.NOP -> Unit
            is Instruction.LINE -> emitLineNumber(instruction.lineNumber)
            is Instruction.SUSPEND -> {
                val snap = buildSnapshotJson(pc + 1)
                throw VMException.Abort(mapOf("__suspended" to true, "snapshot" to snap))
            }
        }
    }

    /**
     * Table-driven execution path using opcode + operand arrays.
     * This is the hot path; avoid constructing Instruction objects.
     */
    private fun executeOpcode(op: Opcode) {
        when (op) {
            // Stack Operations
            Opcode.PUSH -> {
                val v = bytecode.getConstOperand(pc)
                if (v is LambdaTemplate) {
                    push(LambdaValue(v.params, v.bytecode, environment))
                } else {
                    push(v)
                }
            }
            Opcode.DUP -> push(peek())
            Opcode.POP -> pop()
            Opcode.SWAP -> {
                val a = pop()
                val b = pop()
                push(a)
                push(b)
            }

            // Variable Operations
            Opcode.LOAD_VAR -> {
                val name = bytecode.getStringOperand(pc)
                val value = loadVariable(name)
                push(value)
                emitVarRead(name, value)
            }
            Opcode.STORE_VAR -> storeVar(bytecode.getStringOperand(pc))
            Opcode.LOAD_SCOPE -> {
                val name = bytecode.getStringOperand(pc)
                val value = environment[name]
                push(value)
            }
            Opcode.LOAD_LOCAL -> {
                val idx = bytecode.getIntOperand(pc)
                val v = if (idx >= 0 && idx < locals.size) locals[idx] else null
                push(v)
            }
            Opcode.STORE_LOCAL -> {
                val idx = bytecode.getIntOperand(pc)
                val value = pop()
                ensureLocals(idx + 1)
                locals[idx] = value
            }

            // Arithmetic Operations
            Opcode.ADD -> binaryNumeric(VmNumericOp.ADD)
            Opcode.SUB -> binaryNumeric(VmNumericOp.SUB)
            Opcode.MUL -> binaryNumeric(VmNumericOp.MUL)
            Opcode.DIV -> binaryNumeric(VmNumericOp.DIV)
            Opcode.IDIV -> binaryNumeric(VmNumericOp.IDIV)
            Opcode.MOD -> binaryNumeric(VmNumericOp.MOD)
            Opcode.NEG -> unaryArithmetic { negateNumber(it) }

            // Comparison Operations
            Opcode.EQ -> binaryComparison { l, r -> equalsValues(l, r) }
            Opcode.NEQ -> binaryComparison { l, r -> !equalsValues(l, r) }
            Opcode.LT -> binaryComparison { l, r -> compareValues(l, r) < 0 }
            Opcode.LE -> binaryComparison { l, r -> compareValues(l, r) <= 0 }
            Opcode.GT -> binaryComparison { l, r -> compareValues(l, r) > 0 }
            Opcode.GE -> binaryComparison { l, r -> compareValues(l, r) >= 0 }

            // Logical Operations
            Opcode.AND -> {
                val right = pop()
                val left = pop()
                push(if (!left.asBool()) false else right.asBool())
            }
            Opcode.OR -> {
                val right = pop()
                val left = pop()
                push(if (left.asBool()) true else right.asBool())
            }
            Opcode.NOT -> push(!pop().asBool())
            Opcode.COALESCE -> {
                val right = pop()
                val left = pop()
                push(left ?: right)
            }

            // Object/Array Operations
            Opcode.MAKE_OBJECT -> makeObject(bytecode.getIntOperand(pc, 0))
            Opcode.MAKE_ARRAY -> makeArray(bytecode.getIntOperand(pc, 0))
            Opcode.ACCESS_STATIC -> accessStatic(bytecode.getObjKeyOperand(pc))
            Opcode.ACCESS_DYNAMIC -> accessDynamic()
            Opcode.SET_STATIC -> setStatic(bytecode.getObjKeyOperand(pc))
            Opcode.SET_DYNAMIC -> setDynamic()
            Opcode.PRIVATE_SET_STATIC -> setStatic(bytecode.getObjKeyOperand(pc), privateRoot = true)
            Opcode.PRIVATE_SET_DYNAMIC -> setDynamic(privateRoot = true)
            Opcode.APPEND -> appendToArray()
            Opcode.ARRAY_BUILDER_INIT -> initArrayBuilder()
            Opcode.ARRAY_BUILDER_ADD -> arrayBuilderAdd()
            Opcode.PRIVATE_ARRAY_APPEND -> arrayBuilderAdd(privateRoot = true)
            Opcode.ARRAY_BUILDER_FINISH -> arrayBuilderFinish()
            Opcode.PLUS_ASSIGN -> plusAssign()
            Opcode.CONCAT -> concatenateArrays()

            // Control Flow
            Opcode.JUMP -> pc = bytecode.getIntOperand(pc) - 1
            Opcode.JUMP_IF_TRUE -> { if (pop().asBool()) pc = bytecode.getIntOperand(pc) - 1 }
            Opcode.JUMP_IF_FALSE -> { if (!pop().asBool()) pc = bytecode.getIntOperand(pc) - 1 }
            Opcode.JUMP_IF_NULL -> { if (pop() == null) pc = bytecode.getIntOperand(pc) - 1 }

            // Function Calls
            Opcode.CALL -> {
                val name = bytecode.getStringOperand(pc)
                val argc = bytecode.getIntOperand(pc)
                callFunction(name, argc)
            }
            Opcode.CALL_HOST -> {
                val name = bytecode.getStringOperand(pc)
                val argc = bytecode.getIntOperand(pc, 1)
                callHost(name, argc)
            }
            Opcode.CALL_FN -> {
                val name = bytecode.getStringOperand(pc)
                val argc = bytecode.getIntOperand(pc)
                callUserFunction(name, argc)
            }
            Opcode.CALL_LAMBDA -> callLambda(bytecode.getIntOperand(pc))
            Opcode.RETURN -> { if (callStack.isNotEmpty()) returnFromFunction(null) else handleReturn(null) }
            Opcode.RETURN_VALUE -> {
                val v = pop()
                if (callStack.isNotEmpty()) returnFromFunction(v) else handleReturn(v)
            }

            // Special Operations
            Opcode.OUTPUT -> {
                val n = bytecode.getIntOperand(pc)
                when (n) {
                    1 -> createOutput1()
                    2 -> createOutput2()
                    else -> createOutput(n)
                }
            }
            Opcode.MODIFY -> modifyObject(bytecode.getIntOperand(pc, 0))
            Opcode.INIT_FOREACH -> initForEach(bytecode.getStringOperand(pc, 0), bytecode.getIntOperand(pc, 0))
            Opcode.NEXT_FOREACH -> nextForEach(bytecode.getIntOperand(pc, 0), bytecode.getIntOperand(pc, 1))
            Opcode.TRY_START -> startTry(bytecode.getIntOperand(pc, 0))
            Opcode.TRY_END -> endTry()
            Opcode.CATCH -> handleCatch(bytecode.getStringOperand(pc, 0), bytecode.getIntOperand(pc, 0))
            Opcode.ABORT -> throw VMException.Abort(if (stack.isEmpty()) null else pop())

            // Debug Operations
            Opcode.TRACE -> emitTrace(bytecode.getStringOperand(pc, 0))
            Opcode.BREAKPOINT -> emitBreakpoint(bytecode.getStringOperand(pc, 0))
            Opcode.LINE -> emitLineNumber(bytecode.getIntOperand(pc, 0))
            Opcode.NOP -> Unit
            Opcode.SUSPEND -> {
                val snap = buildSnapshotJson(pc + 1)
                throw VMException.Abort(mapOf("__suspended" to true, "snapshot" to snap))
            }
        }
    }

    // === Stack Management ===

    private fun push(value: Any?) {
        if (stack.size >= MAX_STACK_SIZE) {
            throw VMException.StackOverflow()
        }
        stack.push(value)
    }

    private fun pop(): Any? {
        if (stack.isEmpty()) {
            throw VMException.StackUnderflow()
        }
        return stack.pop()
    }

    private fun peek(): Any? {
        if (stack.isEmpty()) {
            throw VMException.StackUnderflow()
        }
        return stack.peek()
    }

    private fun peekOrNull(): Any? = if (stack.isEmpty()) null else stack.peek()

    // === Helper Functions ===

    private fun Any?.asBool(): Boolean = when (this) {
        null -> false
        is Boolean -> this
        is Int -> this != 0
        is Long -> this != 0L
        is Short -> this.toInt() != 0
        is Byte -> this.toInt() != 0
        is Double -> this != 0.0
        is Float -> this != 0f
        is BLBigInt -> this.signum() != 0
        is BLBigDec -> this.signum() != 0
        else -> true
    }

    private fun isNumeric(value: Any?): Boolean = isNumericValue(value)

    internal fun addNumbers(l: Any?, r: Any?): Any {
        return when {
            l is String || r is String -> l.toString() + r.toString()
            isNumeric(l) && isNumeric(r) -> {
                addNumeric(l, r)
            }
            else -> throw VMException.TypeMismatch(
                "number or string",
                "$l + $r"
            )
        }
    }

    internal fun subtractNumbers(l: Any?, r: Any?): Any {
        if (!isNumeric(l) || !isNumeric(r)) {
            throw VMException.TypeMismatch("number", "${l} - ${r}")
        }
        return subNumeric(l, r)
    }

    internal fun multiplyNumbers(l: Any?, r: Any?): Any {
        if (!isNumeric(l) || !isNumeric(r)) {
            throw VMException.TypeMismatch("number", "${l} * ${r}")
        }
        return mulNumeric(l, r)
    }

    internal fun divideNumbers(l: Any?, r: Any?): Any {
        if (!isNumeric(l) || !isNumeric(r)) {
            throw VMException.TypeMismatch("number", "${l} / ${r}")
        }
        return divNumeric(l, r)
    }

    private fun moduloNumbers(l: Any?, r: Any?): Any {
        if (!isNumeric(l) || !isNumeric(r)) {
            throw VMException.TypeMismatch("number", "${l} % ${r}")
        }
        return remNumeric(l, r)
    }

    private fun negateNumber(value: Any?): Any {
        if (!isNumeric(value)) {
            throw VMException.TypeMismatch("number", value?.let { it::class.simpleName } ?: "null")
        }
        return negateNumeric(value)
    }

    internal fun compareValues(left: Any?, right: Any?): Int {
        return if (isNumeric(left) && isNumeric(right)) {
            numericCompare(left, right)
        } else {
            left.toString().compareTo(right.toString())
        }
    }

    internal fun equalsValues(left: Any?, right: Any?): Boolean {
        return if (isNumeric(left) && isNumeric(right)) {
            numericEquals(left, right)
        } else {
            left == right
        }
    }

    // === Binary Operations Helpers ===

    private fun binaryNumeric(op: VmNumericOp) {
        val right = pop()
        val left = pop()
        if (op == VmNumericOp.ADD && (left is String || right is String)) {
            push(left.toString() + right.toString())
            return
        }
        val sites = preparedProgram.numericSites
        val site = sites[pc] ?: NumericBinarySite(op).also { sites[pc] = it }
        try {
            push(site.eval(left, right))
        } catch (e: IllegalArgumentException) {
            if (e.message == "Division by zero") {
                throw VMException.DivisionByZero()
            }
            throw e
        }
    }

    private inline fun binaryComparison(op: (Any?, Any?) -> Boolean) {
        val right = pop()
        val left = pop()
        push(op(left, right))
    }

    private inline fun unaryArithmetic(op: (Any?) -> Any) {
        val value = pop()
        push(op(value))
    }

    // === Complex Operations ===

    private fun makeObject(fieldCount: Int) {
        val obj = LinkedHashMap<Any, Any?>(fieldCount)
        repeat(fieldCount) {
            val key = pop()
            val value = pop()
            obj[key!!] = value
        }
        push(obj)
    }

    private fun makeArray(elementCount: Int) {
        val array = ArrayList<Any?>(elementCount)
        val elements = Array(elementCount) { pop() }
        // Reverse because we popped in reverse order
        for (i in elements.indices.reversed()) {
            array.add(elements[i])
        }
        push(array)
    }

    private fun accessStatic(key: ObjKey) {
        val container = pop()
        val result = when (container) {
            is Map<*, *> -> container[unwrapKey(key)]
            is List<*> -> {
                val index = staticIndex(key)
                if (index < 0 || index >= container.size) {
                    throw VMException.IndexOutOfBounds(index, container.size)
                }
                container[index]
            }
            else -> throw VMException.TypeMismatch("object or array", container?.let { it::class.simpleName } ?: "null")
        }
        push(result)
    }

    private fun accessDynamic() {
        val key = pop()
        val result = when (val container = pop()) {
            is Map<*, *> -> container[key]
            is List<*> -> {
                val index = when (key) {
                    is Int -> key
                    is Long -> {
                        if (key < 0 || key > Int.MAX_VALUE) {
                            throw VMException.IndexOutOfBounds(key.toInt(), container.size)
                        }
                        key.toInt()
                    }
                    else -> throw VMException.TypeMismatch("integer", key?.let { it::class.simpleName } ?: "null")
                }
                if (index < 0 || index >= container.size) {
                    throw VMException.IndexOutOfBounds(index, container.size)
                }
                container[index]
            }
            else -> throw VMException.TypeMismatch("object or array", container?.let { it::class.simpleName } ?: "null")
        }
        push(result)
    }

    // Additional helper functions would continue here...
    // For brevity, I'll implement the key remaining methods

    private fun unwrapKey(key: ObjKey): Any = when (key) {
        is ObjKey.Name -> key.v
        is I32 -> key.v
        is I64 -> key.v
        is IBig -> key.v
    }

    private fun staticIndex(key: ObjKey): Int = when (key) {
        is ObjKey.Name -> throw VMException.TypeMismatch("integer index", "name")
        is I32 -> key.v
        is I64 -> {
            if (key.v < 0 || key.v > Int.MAX_VALUE) {
                throw VMException.IndexOutOfBounds(key.v.toInt(), 0)
            }
            key.v.toInt()
        }
        is IBig -> {
            if (key.v.signum() < 0 || key.v > blBigIntOfLong(Int.MAX_VALUE.toLong())) {
                throw VMException.IndexOutOfBounds(0, 0)
            }
            key.v.toInt()
        }
    }

    // Placeholder implementations for remaining complex operations
    private fun setStatic(key: ObjKey, privateRoot: Boolean = false) {
        val value = pop()
        val container = pop()
        when (container) {
            is Map<*, *> -> {
                val mapKey = unwrapKey(key)
                if (privateRoot && privateMutationAllowed && currentTracer() == null && container is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (container as MutableMap<Any, Any?>)[mapKey] = value
                    push(container)
                } else {
                    val out = LinkedHashMap<Any, Any?>(container.size + if (!container.containsKey(mapKey)) 1 else 0)
                    for ((k, v) in container) out[k!!] = v
                    out[mapKey] = value
                    push(out)
                }
            }
            is List<*> -> {
                val idx = staticIndex(key)
                if (idx < 0 || idx >= container.size) {
                    throw VMException.IndexOutOfBounds(idx, container.size)
                }
                updateListElement(container, idx, value, privateRoot)
            }
            else -> throw VMException.TypeMismatch(
                "object or array",
                container?.let { it::class.simpleName } ?: "null"
            )
        }
    }
    private fun setDynamic(privateRoot: Boolean = false) {
        val value = pop()
        val key = pop()
        when (val container = pop()) {
            is Map<*, *> -> {
                if (privateRoot && privateMutationAllowed && currentTracer() == null && container is MutableMap<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (container as MutableMap<Any, Any?>)[key!!] = value
                    push(container)
                } else {
                    val out = LinkedHashMap<Any, Any?>(container.size + if (!container.containsKey(key)) 1 else 0)
                    for ((k, v) in container) out[k!!] = v
                    out[key!!] = value
                    push(out)
                }
            }
            is List<*> -> {
                val idx = when (key) {
                    is Int -> key
                    is Long -> {
                        if (key < 0 || key > Int.MAX_VALUE) throw VMException.IndexOutOfBounds(key.toInt(), container.size)
                        key.toInt()
                    }
                    else -> throw VMException.TypeMismatch("integer", key?.let { it::class.simpleName } ?: "null")
                }
                if (idx < 0 || idx >= container.size) throw VMException.IndexOutOfBounds(idx, container.size)
                updateListElement(container, idx, value, privateRoot)
            }
            else -> throw VMException.TypeMismatch(
                "object or array",
                container?.let { it::class.simpleName } ?: "null"
            )
        }
    }
    private fun updateListElement(container: List<*>, index: Int, value: Any?, privateRoot: Boolean) {
        if (privateRoot && privateMutationAllowed && currentTracer() == null && container is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            (container as MutableList<Any?>)[index] = value
            push(container)
        } else {
            val out = ArrayList<Any?>(container.size)
            for ((i, item) in container.withIndex()) out.add(if (i == index) value else item)
            push(out)
        }
    }

    private fun appendToArray() {
        val elem = pop()
        val arr = pop()
        val out = ArrayList<Any?>()
        when (arr) {
            null -> out.add(elem)
            is List<*> -> out.addAll(arr)
            else -> throw VMException.TypeMismatch("array", arr::class.simpleName ?: "null")
        }
        if (arr is List<*>) out.add(elem)
        push(out)
    }
    private fun initArrayBuilder() {
        push(ArrayList<Any?>())
    }
    private fun arrayBuilderAdd(privateRoot: Boolean = false) {
        val elem = pop()
        val builder = peek()
        if (builder !is List<*>) {
            throw VMException.TypeMismatch("array builder", builder?.let { it::class.simpleName } ?: "null")
        }
        // Private bindings can be observed through tracing or incremental/custom
        // environments. Ordinary comprehension builders stay hidden on stack.
        if (builder !is MutableList<*> || (privateRoot && (!privateMutationAllowed || currentTracer() != null))) {
            val updated = ArrayList<Any?>(builder.size + 1)
            updated.addAll(builder)
            updated.add(elem)
            pop()
            push(updated)
        } else {
            @Suppress("UNCHECKED_CAST")
            (builder as MutableList<Any?>).add(elem)
        }
    }
    private fun arrayBuilderFinish() {
        val builder = pop()
        if (builder !is List<*>) {
            throw VMException.TypeMismatch("array builder", builder?.let { it::class.simpleName } ?: "null")
        }
        push(builder)
    }
    private fun plusAssign() {
        val value = pop()
        val current = pop()
        val updated = when {
            current is List<*> -> {
                val out = ArrayList<Any?>(current.size + 1)
                out.addAll(current)
                out.add(value)
                out
            }
            current is String || value is String -> current.toString() + value.toString()
            isNumeric(current) && isNumeric(value) -> addNumbers(current, value)
            else -> throw VMException.TypeMismatch(
                "array, number, or string",
                "${current} += ${value}"
            )
        }
        push(updated)
    }
    private fun concatenateArrays() {
        val right = pop()
        val left = pop()
        if (left !is List<*> || right !is List<*>) {
            throw VMException.TypeMismatch(
                "array",
                "${left} + ${right}"
            )
        }
        val out = ArrayList<Any?>(left.size + right.size)
        out.addAll(left)
        out.addAll(right)
        push(out)
    }
    private fun loadVariable(name: String): Any? {
        val value = environment[name]
        if (value == null && !environment.containsKey(name)) throw VMException.VariableNotFound(name)
        return value
    }

    private enum class CallMode { SCHEDULED, IMMEDIATE }

    private fun compileFunctionBytecode(func: FuncDecl): Bytecode {
        val ir: List<IRNode> = when (val body = func.body) {
            is ExprBody -> listOf(IRReturn(body.expr))
            is BlockBody -> ToIR(funcs, hostFns).compile(body.block.statements)
        }
        Compiler.enforceFuncBody(ir)
        return Compiler(funcs, hostFns, useLocals = false).compile(ir)
    }

    private fun createCallEnvironment(params: List<String>, args: List<Any?>, captured: Map<String, Any?> = emptyMap()): OverlayEnv {
        val env = OverlayEnv(captured)
        for ((index, param) in params.withIndex()) {
            env[param] = args.getOrNull(index)
        }
        return env
    }

    private fun startFunctionCall(
        kind: String,
        name: String?,
        args: List<Any?>,
        env: OverlayEnv,
        functionBytecode: Bytecode,
        returnAddress: Int,
        mode: CallMode,
    ) {
        if (callStack.size >= MAX_CALL_DEPTH) {
            throw VMException.StackOverflow()
        }
        val tracer = currentTracer()
        val includeTrace = tracer?.opts?.includeCalls == true
        if (includeTrace) {
            tracer.on(TraceEvent.Call(kind, name, args))
        }
        callStack.push(
            CallFrame(
                returnAddress = returnAddress,
                environment = environment,
                outerProgram = preparedProgram,
                stackSize = stack.size,
                locals = locals,
                tryDepth = tryStack.size,
                loopDepth = loopStack.size,
                traceKind = if (includeTrace) kind else null,
                traceName = if (includeTrace) name else null,
                emitTraceReturn = includeTrace,
            ),
        )
        environment = env
        locals = arrayOf()
        bytecode = functionBytecode
        loadTraceMetadata(functionBytecode)
        preparedProgram = prepareProgram(functionBytecode)
        pc = if (mode == CallMode.IMMEDIATE) 0 else -1
    }

    private fun emitCallError(e: Exception) {
        val frame = callStack.peek() ?: return
        if (!frame.emitTraceReturn || frame.traceKind == null) return
        val tracer = currentTracer()
        val name = frame.traceName ?: "<lambda>"
        tracer?.on(TraceEvent.Error("call ${frame.traceKind} $name", e))
    }

    private fun callFunction(name: String, argCount: Int) {
        val args = ArrayList<Any?>(argCount)
        repeat(argCount) { args.add(pop()) }

        environment[name]?.let { value ->
            if (value is LambdaValue) {
                val env = createCallEnvironment(value.params, args, value.captured)
                startFunctionCall("LAMBDA", name, args, env, value.bytecode, pc + 1, CallMode.SCHEDULED)
                return
            }
        }

        hostFns[name]?.let { fn ->
            val hostArgs = args.map { arg ->
                if (arg is LambdaValue) {
                    { callArgs: List<Any?> -> invokeLambda(arg, callArgs) }
                } else {
                    arg
                }
            }
            val result = tracedCall("HOST", name, hostArgs) { fn(hostArgs) }
            push(result)
            return
        }

        // User-defined function declared in program
        funcs[name]?.let { fd ->
            val bc = funcBytecode[name] ?: compileFunctionBytecode(fd).also { funcBytecode[name] = it }
            val env = createCallEnvironment(fd.params, args)
            startFunctionCall("FUNC", name, args, env, bc, pc + 1, CallMode.SCHEDULED)
            return
        }

        throw VMException.VariableNotFound(name)
    }



    private fun callUserFunction(name: String, argCount: Int) {
        val args = ArrayList<Any?>(argCount)
        repeat(argCount) { args.add(pop()) }
        val func = funcs[name] ?: throw VMException.VariableNotFound(name)
        val bc = funcBytecode[name] ?: compileFunctionBytecode(func).also { funcBytecode[name] = it }
        val env = createCallEnvironment(func.params, args)
        startFunctionCall("FUNC", name, args, env, bc, pc + 1, CallMode.SCHEDULED)
    }

    private fun callLambda(argCount: Int) {
        val args = ArrayList<Any?>(argCount)
        repeat(argCount) { args.add(pop()) }
        val fn = pop()
        if (fn !is LambdaValue) {
            throw VMException.TypeMismatch("lambda", fn?.let { it::class.simpleName })
        }
        val env = createCallEnvironment(fn.params, args, fn.captured)
        startFunctionCall("LAMBDA", null, args, env, fn.bytecode, pc + 1, CallMode.SCHEDULED)
    }

    private fun callHost(name: String, argCount: Int) {
        val args = ArrayList<Any?>(argCount)
        repeat(argCount) { args.add(pop()) }
        // Host bindings are live authority, including changes made by a callee.
        // Resolve by name in O(1); prepared program state must not freeze them.
        val fn = hostFns[name] ?: throw VMException.VariableNotFound(name)
        val hostArgs = args.map { arg ->
            if (arg is LambdaValue) {
                { callArgs: List<Any?> -> invokeLambda(arg, callArgs) }
            } else {
                arg
            }
        }
        val result = tracedCall("HOST", name, hostArgs) { fn(hostArgs) }
        push(result)
    }

    private fun prepareRootProgram(root: Bytecode) {
        if (preparedRoot !== root) {
            preparedPrograms.clear()
            preparedRoot = root
        }
        preparedProgram = prepareProgram(root)
    }

    private fun prepareProgram(program: Bytecode): PreparedProgram {
        // This bounded lookup happens only on calls or entry, never per opcode.
        for (prepared in preparedPrograms) {
            if (prepared.bytecode === program) return prepared
        }
        val prepared = PreparedProgram(program)
        if (preparedPrograms.size == MAX_PREPARED_PROGRAMS) {
            // Transient callees must not evict the root's pc-indexed sites.
            val eviction = preparedPrograms.indexOfFirst { it.bytecode !== preparedRoot }
            preparedPrograms.removeAt(eviction)
        }
        preparedPrograms.add(prepared)
        return prepared
    }

    private class OverlayEnv(private val parent: Map<String, Any?>) : AbstractMutableMap<String, Any?>() {
        private val locals = HashMap<String, Any?>()
        override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
            get() {
                // Enumerating an overlay through size/putAll recursively
                // materializes its ancestors multiple times at every level.
                val layers = ArrayList<OverlayEnv>()
                var base: Map<String, Any?> = this
                while (base is OverlayEnv) {
                    layers.add(base)
                    base = base.parent
                }
                val combined = LinkedHashMap<String, Any?>()
                combined.putAll(base)
                for (index in layers.lastIndex downTo 0) {
                    combined.putAll(layers[index].locals)
                }
                return combined.entries
            }
        override fun put(key: String, value: Any?): Any? = locals.put(key, value)
        override fun remove(key: String): Any? = locals.remove(key)
        override fun containsKey(key: String): Boolean = locals.containsKey(key) || parent.containsKey(key)
        override fun get(key: String): Any? = if (locals.containsKey(key)) locals[key] else parent[key]
    }

    private fun invokeLambda(lambda: LambdaValue, args: List<Any?>): Any? {
        val depthBefore = callStack.size
        val env = createCallEnvironment(lambda.params, args, lambda.captured)
        val returnAddress = pc
        startFunctionCall("LAMBDA", null, args, env, lambda.bytecode, returnAddress, CallMode.IMMEDIATE)
        return try {
            run(targetCallDepth = depthBefore)
            if (stack.isEmpty()) null else pop()
        } catch (failure: Throwable) {
            if (failure is VMException.Abort &&
                (failure.value as? Map<*, *>)?.get("__suspended") == true) throw failure
            // Hosts may catch callback errors. Leave their caller's VM state
            // ready to continue even when no language TRY handles the failure.
            while (callStack.size > depthBefore) {
                val frame = callStack.pop()
                while (tryStack.size > frame.tryDepth) tryStack.pop()
                unwindLoops(frame.loopDepth)
                while (stack.size > frame.stackSize) stack.pop()
                environment = frame.environment
                bytecode = frame.outerBytecode
                preparedProgram = frame.outerProgram
                locals = frame.locals
                loadTraceMetadata(bytecode)
            }
            pc = returnAddress
            throw failure
        }
    }

    private fun handleReturn(value: Any?): Nothing {
        throw VMException.Abort(value)
    }

    private fun returnFromFunction(value: Any?) {
        // Restore previous frame
        val frame = callStack.pop()
        val outBc = frame.outerBytecode
        val retPc = frame.returnAddress
        val tracer = currentTracer()
        if (frame.emitTraceReturn && frame.traceKind != null) {
            tracer?.on(TraceEvent.Return(frame.traceKind, frame.traceName, value))
        }
        while (tryStack.size > frame.tryDepth) tryStack.pop()
        unwindLoops(frame.loopDepth)
        // Restore stack depth to the pre-call size, then push return value
        while (stack.size > frame.stackSize) pop()
        while (stack.size < frame.stackSize) push(null)
        push(value)
        // Restore environment and bytecode
        this.environment = frame.environment
        this.bytecode = outBc
        loadTraceMetadata(outBc)
        this.preparedProgram = frame.outerProgram
        this.locals = frame.locals
        // Resume at instruction after CALL_FN
        this.pc = retPc - 1
    }
    private fun createOutput(fieldCount: Int) {
        if (fieldCount == 0) {
            // Expression-output: the value is already on stack; keep as-is.
            return
        }
        val obj = LinkedHashMap<Any, Any?>(fieldCount)
        repeat(fieldCount) {
            val value = pop()
            val key = pop()
            obj[key!!] = value
        }
        push(obj)
        @Suppress("UNCHECKED_CAST")
        val output = obj as Map<Any, Any?>
        outputs.add(output)
        emitOutputEvent(output)
    }
    private fun createOutput1() {
        val value = pop()
        val key = pop()
        val obj = LinkedHashMap<Any, Any?>(1)
        obj[key!!] = value
        push(obj)
        @Suppress("UNCHECKED_CAST")
        val output = obj as Map<Any, Any?>
        outputs.add(output)
        emitOutputEvent(output)
    }
    private fun createOutput2() {
        val v2 = pop()
        val k2 = pop()
        val v1 = pop()
        val k1 = pop()
        val obj = LinkedHashMap<Any, Any?>(2)
        obj[k1!!] = v1
        obj[k2!!] = v2
        push(obj)
        @Suppress("UNCHECKED_CAST")
        val output = obj as Map<Any, Any?>
        outputs.add(output)
        emitOutputEvent(output)
    }

    private fun normalizeOutputs(): Any? = when (outputs.size) {
        0 -> null
        1 -> outputs[0]
        else -> outputs.toList()
    }
    private fun modifyObject(updateCount: Int) {
        val target = pop()
        if (target !is Map<*, *>) {
            throw VMException.TypeMismatch("object", target?.let { it::class.simpleName } ?: "null")
        }
        val out = LinkedHashMap<Any, Any?>(target.size + updateCount)
        for ((k, v) in target) out[k!!] = v
        repeat(updateCount) {
            val key = pop()
            val value = pop()
            out[key!!] = value
        }
        push(out)
    }
    private fun initForEach(varName: String, jumpToEnd: Int) {
        val iterable = pop()
        val list: List<Any?> = when (iterable) {
            is List<*> -> iterable
            is Iterable<*> -> (iterable).toList()
            is Sequence<*> -> (iterable).toList()
            else -> throw VMException.TypeMismatch("iterable", iterable?.let { it::class.simpleName } ?: "null")
        }

        if (list.isEmpty()) {
            pc = jumpToEnd - 1
            return
        }

        val outerExists = environment.containsKey(varName)
        val outerValue = environment[varName]
        loopStack.push(LoopState(varName, list, 0, outerExists, outerValue))
        environment[varName] = list[0]
    }
    private fun nextForEach(jumpToStart: Int, jumpToEnd: Int) {
        val state = loopStack.peek() ?: run { pc = jumpToEnd - 1; return }
        val nextIdx = state.idx + 1
        if (nextIdx < state.list.size) {
            state.idx = nextIdx
            environment[state.varName] = state.list[nextIdx]
            pc = jumpToStart - 1
        } else {
            if (state.hadOuter) environment[state.varName] = state.outerValue else environment.remove(state.varName)
            loopStack.pop()
            pc = jumpToEnd - 1
        }
    }
    private fun startTry(catchAddress: Int) {
        // Record start (next instruction) and current stack size for unwinding
        val startAt = pc + 1
        tryStack.push(TryFrame(catchAddress = catchAddress, stackSize = stack.size, startAddress = startAt,
            attempts = 0, callDepth = callStack.size, loopDepth = loopStack.size))
    }
    private fun endTry() {
        if (tryStack.isNotEmpty()) tryStack.pop()
    }
    private fun handleCatch(exceptionVar: String, retryCount: Int) {
        val frame = tryStack.peek() ?: return
        // Expose exception to environment if requested
        val err = lastException?.let { ex ->
            mapOf(
                "message" to (ex.message ?: ex.toString()),
                "type" to (ex::class.simpleName ?: "Exception"),
            )
        } ?: mapOf(
            "message" to "Unknown error",
            "type" to "Exception",
        )
        environment[exceptionVar] = err
        if (frame.attempts < retryCount) {
            frame.attempts += 1
            // Reset stack to pre-try size
            while (stack.size > frame.stackSize) stack.pop()
            // Jump back to start of try block
            pc = frame.startAddress - 1
            lastException = null
            return
        }
        // Exhausted retries: proceed to fallback. Pop the frame.
        tryStack.pop()
        lastException = null
    }

    // Return false when execution must propagate to a host or VM caller.
    private fun handleException(e: Exception, callbackDepth: Int): Boolean {
        // Propagate suspension out of TRY to the caller for snapshot handoff
        if (e is VMException.Abort) {
            val v = e.value
            if (v is Map<*, *> && v["__suspended"] == true) return false
        }
        val frame = tryStack.peek() ?: return false
        // A host callback must propagate to its host before a caller's TRY can
        // handle the host call. Only callback-local handlers run in nested run.
        if (callbackDepth >= 0 && frame.callDepth <= callbackDepth) return false
        // Catch addresses belong to the program that installed the handler.
        // Discard callee state before selecting that program's catch instruction.
        while (callStack.size > frame.callDepth) {
            val call = callStack.pop()
            if (call.emitTraceReturn && call.traceKind != null) {
                currentTracer()?.on(TraceEvent.Error("call ${call.traceKind} ${call.traceName ?: "<lambda>"}", e))
            }
            while (tryStack.size > call.tryDepth) tryStack.pop()
            unwindLoops(call.loopDepth)
            environment = call.environment
            bytecode = call.outerBytecode
            preparedProgram = call.outerProgram
            locals = call.locals
            loadTraceMetadata(bytecode)
        }
        unwindLoops(frame.loopDepth)
        // Unwind to the stack size captured at TRY_START
        while (stack.size > frame.stackSize) stack.pop()
        lastException = e
        pc = frame.catchAddress - 1
        return true
    }

    private fun unwindLoops(depth: Int) {
        while (loopStack.size > depth) {
            val state = loopStack.pop()
            if (state.hadOuter) environment[state.varName] = state.outerValue
            else environment.remove(state.varName)
        }
    }

    // ----- Snapshot (serialize/deserialize VM state for pause/resume) -----
    // Snapshot format via kotlinx.serialization
    @Serializable
    data class Snapshot(
        val bytecode: SerializedBytecode,
        val pc: Int,
        val env: Map<String, SerializedValue>,
        val stack: List<SerializedValue>,
        val locals: List<SerializedValue> = emptyList(),
        val calls: List<SerializedCall> = emptyList(),
        val trys: List<SerializedTry> = emptyList(),
        val loops: List<SerializedLoop> = emptyList(),
    )

    @Serializable
    data class SerializedCall(
        val returnPc: Int,
        val env: Map<String, SerializedValue>,
        val outer: SerializedBytecode,
        val stackSize: Int,
        val locals: List<SerializedValue> = emptyList(),
        val traceKind: String? = null,
        val traceName: String? = null,
        val emitTraceReturn: Boolean = false,
        val tryDepth: Int = 0,
        val loopDepth: Int = 0,
    )

    @Serializable
    data class SerializedTry(
        val catchPc: Int, val stackSize: Int, val startPc: Int, val attempts: Int,
        val callDepth: Int = 0, val loopDepth: Int = 0,
    )

    @Serializable
    data class SerializedLoop(
        val varName: String,
        val list: List<SerializedValue>,
        val idx: Int,
        val hadOuter: Boolean,
        val outer: SerializedValue?,
    )

    /** Convert current VM state to JSON string. */
    fun snapshotJson(): String {
        val snap = Snapshot(
            BytecodeIO.serializeBytecode(bytecode),
            pc,
            environment.mapValues { (_, v) -> BytecodeIO.serializeValue(v) },
            stack.toList().map { BytecodeIO.serializeValue(it) },
            locals = locals.take(locals.size).map { BytecodeIO.serializeValue(it) },
            calls = callStack.map { c ->
                SerializedCall(
                    c.returnAddress,
                    c.environment.mapValues { (_, v) -> BytecodeIO.serializeValue(v) },
                    BytecodeIO.serializeBytecode(c.outerBytecode),
                    c.stackSize,
                    locals = c.locals.map { BytecodeIO.serializeValue(it) },
                    traceKind = c.traceKind,
                    traceName = c.traceName,
                    emitTraceReturn = c.emitTraceReturn,
                    tryDepth = c.tryDepth,
                    loopDepth = c.loopDepth,
                )
            },
            trys = tryStack.map { t -> SerializedTry(t.catchAddress, t.stackSize, t.startAddress, t.attempts, t.callDepth, t.loopDepth) },
            loops = loopStack.map { l ->
                SerializedLoop(
                    l.varName,
                    l.list.map { BytecodeIO.serializeValue(it) },
                    l.idx,
                    l.hadOuter,
                    l.outerValue?.let { BytecodeIO.serializeValue(it) },
                )
            },
        )
        return Json.encodeToString(Snapshot.serializer(), snap)
    }

    private fun buildSnapshotJson(pcAt: Int): String {
        val snap = Snapshot(
            BytecodeIO.serializeBytecode(bytecode),
            pcAt,
            environment.mapValues { (_, v) -> BytecodeIO.serializeValue(v) },
            stack.toList().map { BytecodeIO.serializeValue(it) },
            locals = locals.take(locals.size).map { BytecodeIO.serializeValue(it) },
            calls = callStack.map { c ->
                SerializedCall(
                    c.returnAddress,
                    c.environment.mapValues { (_, v) -> BytecodeIO.serializeValue(v) },
                    BytecodeIO.serializeBytecode(c.outerBytecode),
                    c.stackSize,
                    locals = c.locals.map { BytecodeIO.serializeValue(it) },
                    traceKind = c.traceKind,
                    traceName = c.traceName,
                    emitTraceReturn = c.emitTraceReturn,
                    tryDepth = c.tryDepth,
                    loopDepth = c.loopDepth,
                )
            },
            trys = tryStack.map { t -> SerializedTry(t.catchAddress, t.stackSize, t.startAddress, t.attempts, t.callDepth, t.loopDepth) },
            loops = loopStack.map { l ->
                SerializedLoop(
                    l.varName,
                    l.list.map { BytecodeIO.serializeValue(it) },
                    l.idx,
                    l.hadOuter,
                    l.outerValue?.let { BytecodeIO.serializeValue(it) },
                )
            },
        )
        return Json.encodeToString(Snapshot.serializer(), snap)
    }

    fun suspendedSnapshotOrNull(): String? =
        (lastSuspension as? Map<*, *>)?.get("snapshot") as? String

    companion object {
        private const val MAX_STACK_SIZE = 10000
        private const val MAX_CALL_DEPTH = 500
        private const val MAX_PREPARED_PROGRAMS = 64
        private val OPCODES = Opcode.values()

        /** Restore a VM from a JSON snapshot. Provide hostFns/funcs for runtime. */
        fun restoreFromSnapshot(
            snapshotJson: String,
            hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
            funcs: Map<String, FuncDecl> = emptyMap(),
            tracer: Tracer? = null,
        ): VM {
            val snap = Json.decodeFromString(Snapshot.serializer(), snapshotJson)
            val vm = VM(hostFns, funcs, tracer)
            val bc = BytecodeIO.deserializeBytecode(snap.bytecode)
            val env = snap.env.mapValues { (_, v) -> BytecodeIO.deserializeValue(v) }.toMutableMap()
            vm.begin(bc, env)
            vm.pc = snap.pc
            // Rebuild operand stack
            vm.stack.clear()
            for (v in snap.stack.asReversed()) vm.stack.push(BytecodeIO.deserializeValue(v))
            // Restore locals
            vm.locals = arrayOf()
            if (snap.locals.isNotEmpty()) {
                vm.ensureLocals(snap.locals.size)
                for ((i, sv) in snap.locals.withIndex()) vm.locals[i] = BytecodeIO.deserializeValue(sv)
            }
            // Restore call stack
            vm.callStack.clear()
            for (c in snap.calls.asReversed()) {
                val cenv = HashMap(c.env.mapValues { (_, v) -> BytecodeIO.deserializeValue(v) })
                val outer = BytecodeIO.deserializeBytecode(c.outer)
                val localsArray = Array(c.locals.size) { idx -> BytecodeIO.deserializeValue(c.locals[idx]) }
                vm.callStack.push(
                    CallFrame(
                        c.returnPc,
                        cenv,
                        vm.prepareProgram(outer),
                        c.stackSize,
                        localsArray,
                        c.tryDepth,
                        c.loopDepth,
                        c.traceKind,
                        c.traceName,
                        c.emitTraceReturn,
                    ),
                )
            }
            // Restore try stack
            vm.tryStack.clear()
            for (t in snap.trys.asReversed()) vm.tryStack.push(TryFrame(t.catchPc, t.stackSize, t.startPc, t.attempts, t.callDepth, t.loopDepth))
            // Restore loop stack
            vm.loopStack.clear()
            for (l in snap.loops.asReversed()) {
                val li = l.list.map { BytecodeIO.deserializeValue(it) }
                val outer = l.outer?.let { BytecodeIO.deserializeValue(it) }
                vm.loopStack.push(LoopState(l.varName, li, l.idx, l.hadOuter, outer))
            }
            return vm
        }

        /** Pretty-print snapshot JSON to demonstrate its structure and bytecode. */
        fun prettySnapshot(snapshotJson: String): String {
            val snap = Json.decodeFromString(Snapshot.serializer(), snapshotJson)
            val b = StringBuilder()
            b.appendLine("VM Snapshot")
            b.appendLine("- pc: ${snap.pc}")
            b.appendLine("- env:")
            for ((k, v) in snap.env) b.appendLine(
                "    $k = ${v.t}${v.s?.let { "($it)" } ?: v.b?.toString()?.let { "($it)" } ?: ""}"
            )
            b.appendLine("- stack (top last):")
            for ((idx, v) in snap.stack.withIndex()) b.appendLine(
                "    #$idx = ${v.t}${v.s?.let { "($it)" } ?: v.b?.toString()?.let { "($it)" } ?: ""}"
            )
            b.appendLine("- bytecode:")
            snap.bytecode.instructions.forEachIndexed { idx, ins ->
                val valStr = ins.value?.let { v -> "value=${BytecodeIO.deserializeValue(v)}" }
                val args = listOfNotNull(
                    ins.op,
                    ins.s?.let { "s=$it" },
                    ins.i?.let { "i=$it" },
                    ins.n?.let { "n=$it" },
                    ins.key?.let { "key=$it" },
                    valStr,
                ).joinToString(" ")
                val idxStr = idx.toString().padStart(4, '0')
                b.appendLine("    $idxStr: $args")
            }
            return b.toString()
        }
    }

    // Debug/Trace helpers
    private fun emitTrace(event: String) {
        // currentTracer()?.on(VMTraceEvent.VMTrace(event))
    }

    private fun emitBreakpoint(info: String) {
        // currentTracer()?.on(VMTraceEvent.VMBreakpoint(info))
    }

    private fun emitLineNumber(lineNumber: Int) {
        // currentTracer()?.on(VMTraceEvent.VMLine(lineNumber))
    }

    private fun storeVar(name: String) {
        val value = pop()
        if (ordinaryEnvironment && currentTracer() == null) {
            environment[name] = value
            return
        }
        // Custom maps can install a tracer from get/put, so preserve their
        // original read/write order and resolve tracing again after the write.
        val old = environment[name]
        environment[name] = value
        emitVarWrite(name, old, value)
        emitPathWriteIfNeeded(activePc, name, old, value)
    }

    private fun emitEnterEventsAt(pc: Int) {
        val tracer = currentTracer() ?: return
        if (!tracer.opts.step) return
        val events = enterEvents[pc] ?: return
        for (node in events) tracer.on(TraceEvent.Enter(node))
    }

    private fun emitExitEventsAt(pc: Int) {
        val tracer = currentTracer() ?: return
        if (!tracer.opts.step) return
        val events = exitEvents[pc] ?: return
        for (node in events) tracer.on(TraceEvent.Exit(node))
    }

    private fun emitPathWriteIfNeeded(pc: Int, storeName: String, oldRoot: Any?, newRoot: Any?) {
        if (pc < 0) return
        val tracer = currentTracer() ?: return
        val meta = pathWrites[pc] ?: return
        if (meta.root != storeName) return
        val path: List<Any> = meta.displayPath?.let { ArrayList(it) } ?: resolvePath(meta, oldRoot, newRoot)
        val oldValue = if (meta.directValues) oldRoot else readAtPath(oldRoot, path)
        val newValue = if (meta.directValues) newRoot else readAtPath(newRoot, path)
        tracer.on(TraceEvent.PathWrite(meta.op.name, meta.root, path, oldValue, newValue))
    }

    private fun resolvePath(meta: PathWriteMeta, oldRoot: Any?, newRoot: Any?): List<Any> {
        if (meta.segments.isEmpty()) return emptyList()
        pathScratch.clear()
        var currentOld: Any? = oldRoot
        var currentNew: Any? = newRoot
        for (segment in meta.segments) {
            val key: Any = when (segment) {
                is PathSegmentMeta.Static -> unwrapKey(segment.key)
                is PathSegmentMeta.Dynamic -> {
                    val raw = environment[segment.tempName]
                    val container = when {
                        currentOld is Map<*, *> || currentOld is List<*> -> currentOld
                        currentNew is Map<*, *> || currentNew is List<*> -> currentNew
                        else -> null
                    }
                    when (container) {
                        is Map<*, *> -> normalizeMapKey(raw)
                        is List<*> -> normalizeListIndex(raw, container.size)
                        else -> raw ?: error("Dynamic path segment '${segment.tempName}' resolved to null")
                    }
                }
            }
            pathScratch.add(key)
            currentOld = childValue(currentOld, key)
            currentNew = childValue(currentNew, key)
        }
        val resolved = ArrayList<Any>(pathScratch)
        pathScratch.clear()
        return resolved
    }

    private fun readAtPath(root: Any?, path: List<Any>): Any? {
        if (path.isEmpty()) return root
        var current: Any? = root
        for (segment in path) {
            current = childValue(current, segment)
        }
        return current
    }

    private fun childValue(container: Any?, key: Any): Any? {
        return when (container) {
            is Map<*, *> -> container[key]
            is List<*> -> {
                val idx = when (key) {
                    is Int -> key
                    is Long -> {
                        if (key < 0L || key > Int.MAX_VALUE.toLong()) return null
                        key.toInt()
                    }
                    is BLBigInt -> {
                        if (key.signum() < 0 || key > blBigIntOfLong(Int.MAX_VALUE.toLong())) return null
                        key.toInt()
                    }
                    else -> return null
                }
                if (idx < 0 || idx >= container.size) return null
                container[idx]
            }
            else -> null
        }
    }

    private fun normalizeMapKey(value: Any?): Any {
        return when (value) {
            is String -> value
            is Int -> {
                require(value >= 0) { "Object key must be non-negative" }
                value
            }
            is Long -> {
                require(value >= 0L) { "Object key must be non-negative" }
                value
            }
            else -> if (isBigInt(value)) {
                val bi = value as BLBigInt
                require(bi.signum() >= 0) { "Object key must be non-negative" }
                bi
            } else {
                value ?: error("Object key must be string or non-negative integer")
            }
        }
    }

    private fun normalizeListIndex(value: Any?, size: Int): Int {
        return when (value) {
            is Int -> {
                require(value in 0 until size) { "Index $value out of bounds 0..${size - 1}" }
                value
            }
            is Long -> {
                require(value in 0..Int.MAX_VALUE.toLong()) { "Index $value out of bounds" }
                val idx = value.toInt()
                require(idx in 0 until size) { "Index $idx out of bounds 0..${size - 1}" }
                idx
            }
            else -> if (isBigInt(value)) {
                val bi = value as BLBigInt
                require(bi.signum() >= 0 && bi <= blBigIntOfLong(Int.MAX_VALUE.toLong())) { "Index $bi out of bounds" }
                val idx = bi.toInt()
                require(idx in 0 until size) { "Index $idx out of bounds 0..${size - 1}" }
                idx
            } else {
                error("List index must be integer")
            }
        }
    }

    private fun emitOutputEvent(obj: Map<Any, Any?>) {
        currentTracer()?.on(TraceEvent.Output(obj))
    }

    private fun emitVarRead(name: String, value: Any?) {
        currentTracer()?.let { tracer ->
            if (tracer.opts.watch.isEmpty() || name in tracer.opts.watch) {
                tracer.on(TraceEvent.Read(name, value))
            }
        }
    }

    private fun emitVarWrite(name: String, old: Any?, new: Any?) {
        currentTracer()?.let { tracer ->
            if (tracer.opts.step || name in tracer.opts.watch) {
                tracer.on(TraceEvent.Let(name, old, new))
            }
        }
    }

    private fun ensureLocals(size: Int) {
        if (locals.size >= size) return
        val grow = if (locals.size == 0) 4 else locals.size * 2
        val newSize = maxOf(4, maxOf(size, grow))
        locals = locals.copyOf(newSize)
    }
}

private enum class VmNumericOp {
    ADD,
    SUB,
    MUL,
    DIV,
    IDIV,
    MOD,
}

private class PreparedProgram(val bytecode: Bytecode) {
    val numericSites: Array<NumericBinarySite?> = arrayOfNulls(bytecode.size())
}

private const val NUMERIC_SITE_VARIANT_LIMIT = 4

private class NumericBinarySite(
    private val op: VmNumericOp,
) {
    private val variants: MutableList<NumericVariant> = ArrayList(NUMERIC_SITE_VARIANT_LIMIT)
    private var megamorphic: Boolean = false

    fun eval(left: Any?, right: Any?): Any {
        val leftKind = numericKindOf(left) ?: error("$op expects numeric operands")
        val rightKind = numericKindOf(right) ?: error("$op expects numeric operands")
        if (!megamorphic) {
            for (variant in variants) {
                if (variant.matches(leftKind, rightKind)) {
                    return variant.eval(left, right)
                }
            }
            val variant = createNumericVariant(op, leftKind, rightKind)
            if (variant != null) {
                if (variants.size < NUMERIC_SITE_VARIANT_LIMIT) {
                    variants.add(variant)
                    return variant.eval(left, right)
                }
                megamorphic = true
                return evalGenericNumeric(op, left, right)
            }
        }
        return evalGenericNumeric(op, left, right)
    }
}

private data class NumericVariant(
    private val leftKind: NumericKind,
    private val rightKind: NumericKind,
    val eval: (Any?, Any?) -> Any,
) {
    fun matches(left: NumericKind, right: NumericKind): Boolean =
        left == leftKind && right == rightKind
}

private fun createNumericVariant(
    op: VmNumericOp,
    leftKind: NumericKind,
    rightKind: NumericKind,
): NumericVariant? {
    return when (op) {
        VmNumericOp.ADD -> createAddVariant(leftKind, rightKind)
        VmNumericOp.SUB -> createSubVariant(leftKind, rightKind)
        VmNumericOp.MUL -> createMulVariant(leftKind, rightKind)
        VmNumericOp.DIV -> createDivVariant(leftKind, rightKind)
        VmNumericOp.IDIV -> createIDivVariant(leftKind, rightKind)
        VmNumericOp.MOD -> createRemVariant(leftKind, rightKind)
    }
}

private fun createAddVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.I && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r ->
                addIntegersFast(toLongValue(l), toLongValue(r), preferInt(l, r))
            }
        leftKind == NumericKind.F && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) + toDoubleValue(r) }
        leftKind == NumericKind.I && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) + toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) + toDoubleValue(r) }
        leftKind == NumericKind.BI && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) + (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) + bigIntOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> bigIntOfLongCached(toLongValue(l)) + (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) + toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) + toDoubleValue(r) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) + (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) + bigDecOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> bigDecOfLongCached(toLongValue(l)) + (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) + (r as BLBigInt).toBLBigDec() }
        leftKind == NumericKind.BI && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt).toBLBigDec() + (r as BLBigDec) }
        else -> null
    }
}

private fun createSubVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.I && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r ->
                subIntegersFast(toLongValue(l), toLongValue(r), preferInt(l, r))
            }
        leftKind == NumericKind.F && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) - toDoubleValue(r) }
        leftKind == NumericKind.I && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) - toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) - toDoubleValue(r) }
        leftKind == NumericKind.BI && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) - (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) - bigIntOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> bigIntOfLongCached(toLongValue(l)) - (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) - toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) - toDoubleValue(r) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) - (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) - bigDecOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> bigDecOfLongCached(toLongValue(l)) - (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) - (r as BLBigInt).toBLBigDec() }
        leftKind == NumericKind.BI && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt).toBLBigDec() - (r as BLBigDec) }
        else -> null
    }
}

private fun createMulVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.I && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r ->
                mulIntegersFast(toLongValue(l), toLongValue(r), preferInt(l, r))
            }
        leftKind == NumericKind.F && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) * toDoubleValue(r) }
        leftKind == NumericKind.I && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) * toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) * toDoubleValue(r) }
        leftKind == NumericKind.BI && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) * (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) * bigIntOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> bigIntOfLongCached(toLongValue(l)) * (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) * toDoubleValue(r) }
        leftKind == NumericKind.F && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> toDoubleValue(l) * toDoubleValue(r) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) * (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) * bigDecOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> bigDecOfLongCached(toLongValue(l)) * (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) * (r as BLBigInt).toBLBigDec() }
        leftKind == NumericKind.BI && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt).toBLBigDec() * (r as BLBigDec) }
        else -> null
    }
}

private fun createDivVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.BD && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) / (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) / bigDecOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> bigDecOfLongCached(toLongValue(l)) / (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) / (r as BLBigInt).toBLBigDec() }
        leftKind == NumericKind.BI && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt).toBLBigDec() / (r as BLBigDec) }
        leftKind != NumericKind.BD && rightKind != NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r ->
                val divisor = toDoubleValue(r)
                require(divisor != 0.0) { "Division by zero" }
                toDoubleValue(l) / divisor
            }
        else -> null
    }
}

private fun createIDivVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.I && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r ->
                idivIntegersFast(toLongValue(l), toLongValue(r), preferInt(l, r))
            }
        leftKind == NumericKind.BI && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) / (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) / bigIntOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> bigIntOfLongCached(toLongValue(l)) / (r as BLBigInt) }
        else -> null
    }
}

private fun createRemVariant(leftKind: NumericKind, rightKind: NumericKind): NumericVariant? {
    return when {
        leftKind == NumericKind.BD && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) % (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) % bigDecOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> bigDecOfLongCached(toLongValue(l)) % (r as BLBigDec) }
        leftKind == NumericKind.BD && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigDec) % (r as BLBigInt).toBLBigDec() }
        leftKind == NumericKind.BI && rightKind == NumericKind.BD ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt).toBLBigDec() % (r as BLBigDec) }
        leftKind == NumericKind.BI && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) % (r as BLBigInt) }
        leftKind == NumericKind.BI && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r -> (l as BLBigInt) % bigIntOfLongCached(toLongValue(r)) }
        leftKind == NumericKind.I && rightKind == NumericKind.BI ->
            NumericVariant(leftKind, rightKind) { l, r -> bigIntOfLongCached(toLongValue(l)) % (r as BLBigInt) }
        leftKind == NumericKind.F || rightKind == NumericKind.F ->
            NumericVariant(leftKind, rightKind) { l, r ->
                val divisor = toDoubleValue(r)
                require(divisor != 0.0) { "Division by zero" }
                toDoubleValue(l) % divisor
            }
        leftKind == NumericKind.I && rightKind == NumericKind.I ->
            NumericVariant(leftKind, rightKind) { l, r ->
                remIntegersFast(toLongValue(l), toLongValue(r), preferInt(l, r))
            }
        else -> null
    }
}

private fun evalGenericNumeric(op: VmNumericOp, left: Any?, right: Any?): Any = when (op) {
    VmNumericOp.ADD -> addNumeric(left, right)
    VmNumericOp.SUB -> subNumeric(left, right)
    VmNumericOp.MUL -> mulNumeric(left, right)
    VmNumericOp.DIV -> divNumeric(left, right)
    VmNumericOp.IDIV -> idivNumeric(left, right)
    VmNumericOp.MOD -> remNumeric(left, right)
}

private fun preferInt(left: Any?, right: Any?): Boolean =
    isIntLike(left) && isIntLike(right)

private fun isIntLike(value: Any?): Boolean =
    value is Int || value is Short || value is Byte

private fun toLongValue(value: Any?): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Short -> value.toLong()
    is Byte -> value.toLong()
    is Number -> value.toLong()
    is BLBigInt -> value.toLong()
    else -> error("Expected integer numeric")
}

private fun toDoubleValue(value: Any?): Double = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Long -> value.toDouble()
    is Int -> value.toDouble()
    is Short -> value.toDouble()
    is Byte -> value.toDouble()
    is BLBigInt -> value.toBLBigDec().toDouble()
    is BLBigDec -> value.toDouble()
    is Number -> value.toDouble()
    else -> error("Expected numeric")
}

/**
 * Call frame for function calls
 */
private data class CallFrame(
    val returnAddress: Int,
    val environment: MutableMap<String, Any?>,
    val outerProgram: PreparedProgram,
    val stackSize: Int,
    val locals: Array<Any?>,
    val tryDepth: Int,
    val loopDepth: Int,
    val traceKind: String?,
    val traceName: String?,
    val emitTraceReturn: Boolean,
) {
    val outerBytecode: Bytecode get() = outerProgram.bytecode
}


/**
 * Exception handling frame
 */
private data class TryFrame(
    val catchAddress: Int,
    val stackSize: Int,
    val startAddress: Int,
    var attempts: Int,
    val callDepth: Int,
    val loopDepth: Int,
)

/** Loop state for FOR EACH constructs */
private data class LoopState(
    val varName: String,
    val list: List<Any?>,
    var idx: Int,
    val hadOuter: Boolean,
    val outerValue: Any?,
    val slotIndex: Int = -1,
)

/**
 * Extended TraceEvent types for VM
 */
sealed class VMTraceEvent {
    data class VMStep(val pc: Int, val instruction: Instruction, val stackTop: Any?) : VMTraceEvent()
    data class VMTrace(val event: String) : VMTraceEvent()
    data class VMBreakpoint(val info: String) : VMTraceEvent()
    data class VMLine(val lineNumber: Int) : VMTraceEvent()
}
