package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.IRExprOutput
import io.github.ehlyzov.branchline.ir.IRForEach
import io.github.ehlyzov.branchline.ir.IRIf
import io.github.ehlyzov.branchline.ir.IRNode
import io.github.ehlyzov.branchline.std.HostFnMetadata
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import kotlin.concurrent.Volatile
import kotlin.time.measureTimedValue

/** Fallback is only safe before VM execution has begun. */
enum class VMFallbackPolicy {
    COMPILE_FAILURE,
    NONE,
}

enum class VMExecutionPath {
    VM_BYTECODE,
    INTERPRETER_COMPILE_FALLBACK,
    INTERPRETER_DISABLED,
    COMPILATION_REJECTED,
}

/** Terminal evidence for one invocation; a VM path does not imply successful execution. */
data class VMExecutionReport(
    val path: VMExecutionPath,
    val succeeded: Boolean,
    val compilationFailure: Throwable? = null,
    val failure: Throwable? = null,
)

private fun compileForVM(compile: () -> Bytecode): Result<Bytecode> = try {
    val timed = measureTimedValue(compile)
    VMFactory.Metrics.onCompileNanos(timed.duration.inWholeNanoseconds)
    Result.success(timed.value)
} catch (failure: NotImplementedError) {
    VMFactory.Metrics.onCompileFailed()
    Result.failure(failure)
} catch (failure: Exception) {
    VMFactory.Metrics.onCompileFailed()
    Result.failure(failure)
}

/** Preserve language-facing exception categories without replaying execution. */
private fun VM.executeForLanguage(bytecode: Bytecode, env: MutableMap<String, Any?>): Any? = try {
    execute(bytecode, env)
} catch (failure: VMException) {
    if (isHostFailure(failure)) throw failure
    throw when (failure) {
        is VMException.IndexOutOfBounds -> IllegalArgumentException(failure.message, failure)
        is VMException.VariableNotFound, is VMException.TypeMismatch -> IllegalStateException(failure.message, failure)
        else -> failure
    }
}

private class ExecutionTracker(private val observer: ((VMExecutionReport) -> Unit)?) {
    var lastExecution: VMExecutionReport? = null
        private set

    fun run(path: VMExecutionPath, compilationFailure: Throwable? = null, execute: () -> Any?): Any? {
        // Notify outside the execution catch: observer errors must never trigger replay
        // or be misreported as failures in the engine itself.
        val result = try {
            Result.success(execute())
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
        val report = VMExecutionReport(path, result.isSuccess, compilationFailure, result.exceptionOrNull())
        lastExecution = report
        try {
            observer?.invoke(report)
        } catch (observerFailure: Throwable) {
            val executionFailure = result.exceptionOrNull() ?: throw observerFailure
            if (observerFailure !== executionFailure) executionFailure.addSuppressed(observerFailure)
        }
        return result.getOrThrow()
    }
}

/** VM adapter with optional compilation fallback. Runtime failures always propagate. */
class VMExec(
    private val ir: List<IRNode>,
    private val tracer: Tracer? = null,
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val precompiled: Bytecode? = null,
    private val fallbackPolicy: VMFallbackPolicy = VMFallbackPolicy.COMPILE_FAILURE,
    private val useVM: Boolean = true,
    onExecution: ((VMExecutionReport) -> Unit)? = null,
) {
    private val vm = VM(hostFns, funcs, tracer)
    private val tracker = ExecutionTracker(onExecution)
    val lastExecution: VMExecutionReport? get() = tracker.lastExecution

    // Compile once before execution, without touching the caller's environment.
    private val compilation: Result<Bytecode> by lazy {
        precompiled?.let { Result.success(it) } ?: compileForVM { Compiler(funcs, hostFns).compile(ir) }
    }

    private val interpreter: Exec by lazy {
        Exec(ir, tracer = tracer, hostFns = hostFns, hostFnMeta = hostFnMeta,
            funcs = funcs, sharedStore = SharedStoreProvider.store)
    }

    fun run(env: MutableMap<String, Any?>, stringifyKeys: Boolean = false): Any? {
        if (!useVM) {
            return tracker.run(VMExecutionPath.INTERPRETER_DISABLED) { interpreter.run(env, stringifyKeys) }
        }
        val compiled = compilation
        val failure = compiled.exceptionOrNull()
        if (failure != null) {
            if (fallbackPolicy == VMFallbackPolicy.NONE) {
                return tracker.run(VMExecutionPath.COMPILATION_REJECTED, failure) { throw failure }
            }
            tracer?.on(TraceEvent.Error("VM compilation failed; using interpreter: ${failure.message}", failure))
            return tracker.run(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, failure) {
                interpreter.run(env, stringifyKeys)
            }
        }
        return tracker.run(VMExecutionPath.VM_BYTECODE) {
            val timed = measureTimedValue { vm.executeForLanguage(compiled.getOrThrow(), env) }
            VMFactory.Metrics.onExecuteNanos(timed.duration.inWholeNanoseconds)
            if (stringifyKeys) stringifyKeys(timed.value) else timed.value
        }
    }

    private fun stringifyKeys(value: Any?): Any? = when (value) {
        null -> null
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply {
            for ((k, v) in value) put(k.toString(), stringifyKeys(v))
        }
        is List<*> -> value.map { stringifyKeys(it) }
        else -> value
    }
}

/**
 * Expression adapter with compilation-only fallback and one cached expression.
 * Cache hits validate a detached AST snapshot because read-only lists may have
 * mutable owners. Implicit reuse requires no function declarations and at most
 * 32 host/metadata bindings, bounding validation to O(AST) plus a fixed budget.
 * Larger live authorities compile afresh; [prepare] captures them once for reuse.
 * Evaluators are stateful and must be used sequentially.
 */
class VMEval(
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
    private val tracer: Tracer? = null,
    private val fallbackPolicy: VMFallbackPolicy = VMFallbackPolicy.COMPILE_FAILURE,
    private val useVM: Boolean = true,
    private val onExecution: ((VMExecutionReport) -> Unit)? = null,
) {
    private class CachedExpression(
        val original: Expr,
        val snapshot: Expr,
        val hostFns: Map<String, (List<Any?>) -> Any?>,
        val hostFnMeta: Map<String, HostFnMetadata>,
        val evaluator: PreparedVMEval,
    )

    private var cached: CachedExpression? = null
    var lastExecution: VMExecutionReport? = null
        private set

    private val disabledTracker = ExecutionTracker(::recordExecution)
    private val interpreter: Exec by lazy {
        Exec(ir = emptyList(), hostFns = hostFns, hostFnMeta = hostFnMeta,
            funcs = funcs, tracer = tracer, sharedStore = SharedStoreProvider.store)
    }

    private fun recordExecution(report: VMExecutionReport) {
        lastExecution = report
        onExecution?.invoke(report)
    }

    private fun liveEvaluator(expr: Expr): PreparedVMEval = PreparedVMEval(
        expr, funcs, hostFns, hostFnMeta, tracer, fallbackPolicy, useVM, ::recordExecution,
    )

    /**
     * Capture expression, function declarations and host bindings now. Later
     * mutations to the supplied AST/maps do not change this prepared evaluator.
     * Host closure state and evaluation environments remain live.
     */
    fun prepare(expr: Expr): PreparedVMEval = PreparedVMEval(
        snapshotExpr(expr), snapshotFunctions(funcs), hostFns.toMap(), hostFnMeta.toMap(),
        tracer, fallbackPolicy, useVM, onExecution,
    )

    fun eval(expr: Expr, env: MutableMap<String, Any?>): Any? {
        if (!useVM) {
            return disabledTracker.run(VMExecutionPath.INTERPRETER_DISABLED) { interpreter.eval(expr, env) }
        }
        // Function bodies may have arbitrary mutable children. Scanning unrelated
        // declarations (or an unbounded binding table) can exceed compilation cost.
        // Only explicit preparation snapshots those authorities in full.
        if (funcs.isNotEmpty() || hostFns.size > MAX_IMPLICIT_BINDINGS ||
            hostFnMeta.size > MAX_IMPLICIT_BINDINGS - hostFns.size) {
            cached = null
            return liveEvaluator(expr).eval(env)
        }
        val previous = cached
        val evaluator = if (previous != null && previous.original === expr &&
            previous.snapshot == expr && sameBindings(previous.hostFns, hostFns) &&
            sameBindings(previous.hostFnMeta, hostFnMeta)) {
            previous.evaluator
        } else {
            // A fresh VM also invalidates its function bytecode and host binding caches.
            val next = liveEvaluator(expr)
            cached = CachedExpression(
                expr, snapshotExpr(expr), hostFns.toMap(), hostFnMeta.toMap(), next,
            )
            next
        }
        return evaluator.eval(env)
    }

    private companion object {
        const val MAX_IMPLICIT_BINDINGS = 32
    }
}

private fun <T : Any> sameBindings(
    snapshot: Map<String, T>,
    current: Map<String, T>,
): Boolean = snapshot.size == current.size && snapshot.all { (name, fn) -> current[name] === fn }

/**
 * An expression and its compilation authority captured by [VMEval.prepare].
 * Repeated calls do not inspect the original AST or function maps. Like VMExec,
 * a prepared evaluator is stateful and must be used sequentially.
 */
class PreparedVMEval internal constructor(
    private val expr: Expr,
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
    private val tracer: Tracer? = null,
    private val fallbackPolicy: VMFallbackPolicy = VMFallbackPolicy.COMPILE_FAILURE,
    private val useVM: Boolean = true,
    onExecution: ((VMExecutionReport) -> Unit)? = null,
) {
    private val compilation: Result<Bytecode> by lazy {
        compileForVM { Compiler(funcs, hostFns).compile(listOf(IRExprOutput(expr))) }
    }
    private val vm: VM by lazy { VM(hostFns, funcs, tracer) }
    private val tracker = ExecutionTracker(onExecution)
    val lastExecution: VMExecutionReport? get() = tracker.lastExecution
    private val fallbackExec: Exec by lazy {
        Exec(ir = emptyList(), hostFns = hostFns, hostFnMeta = hostFnMeta,
            funcs = funcs, tracer = tracer, sharedStore = SharedStoreProvider.store)
    }

    fun eval(env: MutableMap<String, Any?>): Any? {
        if (!useVM) {
            return tracker.run(VMExecutionPath.INTERPRETER_DISABLED) { fallbackExec.eval(expr, env) }
        }
        val failure = compilation.exceptionOrNull()
        if (failure != null) {
            if (fallbackPolicy == VMFallbackPolicy.NONE) {
                return tracker.run(VMExecutionPath.COMPILATION_REJECTED, failure) { throw failure }
            }
            return tracker.run(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, failure) {
                tracer?.on(TraceEvent.Call("FALLBACK", "Interpreter", listOf(expr::class.simpleName)))
                val out = fallbackExec.eval(expr, env)
                tracer?.on(TraceEvent.Return("FALLBACK", "Interpreter", out))
                out
            }
        }
        return tracker.run(VMExecutionPath.VM_BYTECODE) {
            val timed = measureTimedValue { vm.executeForLanguage(compilation.getOrThrow(), env) }
            VMFactory.Metrics.onExecuteNanos(timed.duration.inWholeNanoseconds)
            timed.value
        }
    }
}

/** Factory for creating VM-enabled components */
object VMFactory {

    /** Create an executor with explicit compilation fallback and execution evidence. */
    fun createExecutor(
        ir: List<IRNode>,
        tracer: Tracer? = null,
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
        hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
        funcs: Map<String, FuncDecl> = emptyMap(),
        useVM: Boolean = true,
        fallbackPolicy: VMFallbackPolicy = VMFallbackPolicy.COMPILE_FAILURE,
        onExecution: ((VMExecutionReport) -> Unit)? = null,
    ): VMExec {
        return VMExec(ir, tracer, hostFns, hostFnMeta, funcs,
            fallbackPolicy = fallbackPolicy, useVM = useVM, onExecution = onExecution)
    }

    /** Create an evaluator with explicit compilation fallback and execution evidence. */
    fun createEvaluator(
        funcs: Map<String, FuncDecl> = emptyMap(),
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
        hostFnMeta: Map<String, HostFnMetadata> = emptyMap(),
        tracer: Tracer? = null,
        useVM: Boolean = true,
        fallbackPolicy: VMFallbackPolicy = VMFallbackPolicy.COMPILE_FAILURE,
        onExecution: ((VMExecutionReport) -> Unit)? = null,
    ): VMEval {
        return VMEval(funcs, hostFns, hostFnMeta, tracer, fallbackPolicy, useVM, onExecution)
    }

    /** Get VM compilation statistics for performance monitoring */
    fun getCompilationStats(): VMStats = Metrics.snapshot()

    /** Simple metrics aggregator for VM usage */
    object Metrics {
        @Volatile private var totalCompiles: Long = 0
        @Volatile private var successCompiles: Long = 0
        @Volatile private var failedCompiles: Long = 0
        @Volatile private var sumCompileNanos: Long = 0
        @Volatile private var sumExecuteNanos: Long = 0

        fun onCompileNanos(nanos: Long) {
            totalCompiles += 1
            successCompiles += 1
            sumCompileNanos += nanos
        }
        fun onCompileFailed() { totalCompiles += 1; failedCompiles += 1 }
        fun onExecuteNanos(nanos: Long) { sumExecuteNanos += nanos }

        fun snapshot(): VMStats = VMStats(
            totalCompilations = totalCompiles,
            successfulCompilations = successCompiles,
            failedCompilations = failedCompiles,
            avgCompileTime = if (successCompiles > 0) (sumCompileNanos / successCompiles) / 1_000_000.0 else 0.0,
            avgExecutionTime =  if (totalCompiles > 0) (sumExecuteNanos / totalCompiles) / 1_000_000.0 else 0.0,
        )
        fun reset() {
            totalCompiles = 0; successCompiles = 0; failedCompiles = 0
            sumCompileNanos = 0; sumExecuteNanos = 0
        }
    }
}

/** VM performance and usage statistics */
data class VMStats(
    val totalCompilations: Long,
    val successfulCompilations: Long,
    val failedCompilations: Long,
    val avgCompileTime: Double, // milliseconds
    val avgExecutionTime: Double, // milliseconds
    val compilerMetrics: Map<String, Int> = Compiler.Metrics.snapshot(),
)

/** Configuration for VM behavior */
data class VMConfig(
    val enableVM: Boolean = true,
    @Deprecated("Unused legacy option. Configure VMExec/VMEval with VMFallbackPolicy; runtime errors never fall back.")
    val fallbackOnError: Boolean = true,
    val enableOptimizations: Boolean = true,
    val maxStackSize: Int = 10000,
    val maxCallDepth: Int = 500,
    val enableTracing: Boolean = false,
    val debugMode: Boolean = false,
)

/** VM runtime with configuration and statistics */
class VMRuntime(private val config: VMConfig = VMConfig()) {
    private val stats = VMStats(0, 0, 0, 0.0, 0.0)

    fun createVM(
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
        funcs: Map<String, FuncDecl> = emptyMap(),
        tracer: Tracer? = null,
    ): VM {
        return VM(hostFns, funcs, if (config.enableTracing) tracer else null)
    }

    fun createCompiler(
        funcs: Map<String, FuncDecl> = emptyMap(),
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    ): Compiler {
        return Compiler(funcs, hostFns)
    }

    fun getStats(): VMStats = stats

    fun getConfig(): VMConfig = config
}

/** Utility functions for VM integration */
object VMUtil {

    /** Convert IR nodes to a format suitable for VM compilation */
    fun prepareIRForVM(ir: List<IRNode>): List<IRNode> {
        // For now, return as-is, but this could include:
        // - IR optimization passes
        // - Dead code elimination
        // - Constant folding
        // - Control flow simplification
        return ir
    }

    /** Validate that IR is compatible with VM execution */
    fun validateIRForVM(ir: List<IRNode>): ValidationResult {
        // Array comprehensions are supported. Extend this method to flag
        // new unsupported features as the VM evolves.
        return ValidationResult(
            isValid = true,
            unsupportedFeatures = emptyList()
        )
    }

    /** Estimate the performance benefit of using VM vs interpreter */
    fun estimatePerformanceBenefit(ir: List<IRNode>): PerformanceEstimate {
        var arithmeticOps = 0
        var accessOps = 0
        var controlFlowOps = 0

        for (node in ir) {
            when (node) {
                is IRIf -> controlFlowOps++
                is IRForEach -> controlFlowOps++
                // Count other operations...
                else -> Unit // Handle other node types
            }
        }

        // Simple heuristic: VM benefits from arithmetic-heavy and control-flow-heavy code
        val totalOps = arithmeticOps + accessOps + controlFlowOps
        val expectedSpeedup = when {
            totalOps > 100 -> 2.0 // Significant speedup for large programs
            totalOps > 50 -> 1.5 // Moderate speedup
            totalOps > 10 -> 1.2 // Small speedup
            else -> 1.0 // No significant benefit
        }

        return PerformanceEstimate(
            expectedSpeedup = expectedSpeedup,
            confidence = if (totalOps > 50) 0.8 else 0.5,
            recommendation = if (expectedSpeedup > 1.2) "Use VM" else "Use interpreter"
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val unsupportedFeatures: List<String>,
)

data class PerformanceEstimate(
    val expectedSpeedup: Double,
    val confidence: Double, // 0.0 to 1.0
    val recommendation: String,
)
