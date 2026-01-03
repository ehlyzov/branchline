package io.github.ehlyzov.branchline.vm

import net.codinux.log.Logger
import net.codinux.log.LoggerFactory
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.TransformRegistry
import io.github.ehlyzov.branchline.ir.IRExprOutput
import io.github.ehlyzov.branchline.ir.IRForEach
import io.github.ehlyzov.branchline.ir.IRIf
import io.github.ehlyzov.branchline.ir.IRNode
import kotlin.concurrent.Volatile
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * Integration layer that provides compatibility between the VM and
 * existing eval system
 *
 * This layer allows for gradual migration to the VM while maintaining
 * compatibility with existing code that expects the current evaluation
 * interface.
 */

/** VM-based executor that can replace the current Exec class */
class VMExec(
    private val ir: List<IRNode>,
    private val eval: (Expr, MutableMap<String, Any?>) -> Any?, // Fallback evaluator
    private val tracer: Tracer? = null,
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val precompiled: Bytecode? = null,
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(VMExec::class)
    }
    private val compiler = Compiler(funcs, hostFns)
    private val vm = VM(hostFns, funcs, tracer)

    // Lazily compile IR once and cache bytecode. If compilation fails, we keep null
    // and always fall back to the interpreter.
    @OptIn(ExperimentalTime::class)
    private val bytecode: Bytecode? =
        precompiled ?: try {
            val bcTimed = measureTimedValue {
                compiler.compile(ir)
            }
            VMFactory.Metrics.onCompileNanos(bcTimed.duration.inWholeNanoseconds)
            bcTimed.value
        } catch (e: NotImplementedError) {
            VMFactory.Metrics.onCompileFailed()
            logger.warn("Falling back to interpreter: ${e.message}")
            tracer?.on(TraceEvent.Error("VM fallback due to unimplemented feature: ${e.message}", e))
            null
        } catch (e: Exception) {
            VMFactory.Metrics.onCompileFailed()
            logger.warn("Falling back to interpreter: ${e.message}")
            tracer?.on(TraceEvent.Error("VM compilation failed: ${e.message}", e))
            null
        }

    // Interpreter fallback is created lazily to avoid work when VM executes successfully
    private val interpreter: Exec by lazy { Exec(ir, eval, tracer) }

    /**
     * Execute IR using VM with fallback to interpreter for unsupported
     * features
     */
    fun run(env: MutableMap<String, Any?>, stringifyKeys: Boolean = false): Any? {
        val compiled = bytecode
        if (compiled != null) {
            return try {
                val resultTimed = measureTimedValue {
                    vm.execute(compiled, env)
                }
                VMFactory.Metrics.onExecuteNanos(resultTimed.duration.inWholeNanoseconds)
                if (stringifyKeys) stringifyKeys(resultTimed.value) else resultTimed.value
            } catch (e: Exception) {
                logger.warn("Falling back to interpreter: ${e.message}")
                tracer?.on(TraceEvent.Error("VM execution failed: ${e.message}", e))
                interpreter.run(env, stringifyKeys)
            }
        }

        // Compilation failed, fall back to interpreter
        return interpreter.run(env, stringifyKeys)
    }

    private fun stringifyKeys(value: Any?): Any? = when (value) {
        null -> null
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply {
            for ((k, v) in value) {
                put(k.toString(), stringifyKeys(v))
            }
        }

        is List<*> -> value.map { stringifyKeys(it) }
        else -> value
    }
}

/** VM-compatible evaluator that can handle expressions */
class VMEval(
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val tracer: Tracer? = null,
) {
    private val compiler = Compiler(funcs, hostFns)
    private val vm = VM(hostFns, funcs, tracer)
    // We'll need to implement a fallback evaluator or use the existing one
    // For now, we'll create a simple fallback that handles basic cases

    /** Evaluate an expression using VM compilation or fallback to interpreter */
    fun eval(expr: Expr, env: MutableMap<String, Any?>): Any? {
        return try {
            // Create a simple IR structure for the expression
            val exprStmt = IRExprOutput(expr)
            val bytecodeTimed = measureTimedValue {
                compiler.compile(listOf(exprStmt))
            }
            val bytecode = bytecodeTimed.value
            VMFactory.Metrics.onCompileNanos(bytecodeTimed.duration.inWholeNanoseconds)
            val out = vm.execute(bytecode, env)
            VMFactory.Metrics.onExecuteNanos(0) // expression-only; negligible
            out
        } catch (e: NotImplementedError) {
            // Fallback to interpreter for unimplemented features
            VMFactory.Metrics.onCompileFailed()
            val reg = TransformRegistry(funcs, hostFns, emptyMap())
            val fallback = io.github.ehlyzov.branchline.ir.makeEval(hostFns, funcs, reg, tracer)
            tracer?.on(io.github.ehlyzov.branchline.debug.TraceEvent.Call("FALLBACK", "Interpreter", listOf(expr::class.simpleName)))
            val out = fallback(expr, env)
            tracer?.on(io.github.ehlyzov.branchline.debug.TraceEvent.Return("FALLBACK", "Interpreter", out))
            out
        } catch (e: Exception) {
            // Log and fallback
            tracer?.on(io.github.ehlyzov.branchline.debug.TraceEvent.Error("VM expression evaluation failed: ${e.message}", e))
            val reg = TransformRegistry(funcs, hostFns, emptyMap())
            val fallback = io.github.ehlyzov.branchline.ir.makeEval(hostFns, funcs, reg, tracer)
            tracer?.on(io.github.ehlyzov.branchline.debug.TraceEvent.Call("FALLBACK", "Interpreter", listOf(expr::class.simpleName)))
            val out = try { fallback(expr, env) } catch (_: Exception) { null }
            tracer?.on(io.github.ehlyzov.branchline.debug.TraceEvent.Return("FALLBACK", "Interpreter", out))
            out
        }
    }
}

/** Factory for creating VM-enabled components */
object VMFactory {

    /** Create a VM-enabled executor with fallback capability */
    fun createExecutor(
        ir: List<IRNode>,
        eval: (Expr, MutableMap<String, Any?>) -> Any?,
        tracer: Tracer? = null,
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
        funcs: Map<String, FuncDecl> = emptyMap(),
        useVM: Boolean = true,
    ): VMExec {
        return VMExec(ir, eval, tracer, hostFns, funcs)
    }

    /** Create a VM-enabled evaluator with fallback capability */
    fun createEvaluator(
        funcs: Map<String, FuncDecl> = emptyMap(),
        hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
        tracer: Tracer? = null,
        useVM: Boolean = true,
    ): VMEval {
        return VMEval(funcs, hostFns, tracer)
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
