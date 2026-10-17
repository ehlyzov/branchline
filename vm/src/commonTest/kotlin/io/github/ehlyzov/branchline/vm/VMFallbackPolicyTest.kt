package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.ir.IRExprOutput
import io.github.ehlyzov.branchline.ir.IRExprStmt
import io.github.ehlyzov.branchline.ir.IRLet
import io.github.ehlyzov.branchline.ir.compileStream
import kotlin.test.*

class VMFallbackPolicyTest {
    private val token = Token(TokenType.IDENTIFIER, "test", 1, 1)
    private fun number(value: Int) = NumberLiteral(I32(value), token)
    private fun call(name: String) = CallExpr(IdentifierExpr(name, token), emptyList(), token)
    private fun obj(expr: Expr) = ObjectExpr(listOf(LiteralProperty(ObjKey.Name("value"), expr)), token)

    // Interpreter accepts a host-call statement in a lambda; the VM compiler's
    // enforceFuncBody currently rejects it before any user code can run.
    private fun interpreterOnlyExpression(): Expr = InvokeExpr(
        LambdaExpr(emptyList(), BlockBody(CodeBlock(listOf(
            ExprStmt(call("tick"), token),
            ReturnStmt(number(7), token),
        ), token), token), token), emptyList(), token,
    )

    @Test
    fun executor_runtime_error_never_replays_host_effects() {
        for (policy in VMFallbackPolicy.entries) {
            var effects = 0
            val failure = IllegalStateException("host failed after effect")
            val hostFns = mapOf<String, (List<Any?>) -> Any?>("fail" to { effects++; throw failure })
            val reports = mutableListOf<VMExecutionReport>()
            val exec = VMExec(listOf(IRExprOutput(call("fail"))), hostFns = hostFns,
                fallbackPolicy = policy, onExecution = reports::add)
            assertSame(failure, assertFailsWith<IllegalStateException> { exec.run(mutableMapOf()) })
            assertEquals(1, effects)
            assertEquals(VMExecutionPath.VM_BYTECODE, exec.lastExecution?.path)
            assertEquals(false, exec.lastExecution?.succeeded)
            assertSame(failure, exec.lastExecution?.failure)
            assertNull(exec.lastExecution?.compilationFailure)
            assertEquals(listOf(exec.lastExecution), reports)
        }
    }

    @Test
    fun executor_does_not_repeat_prior_effect_or_mutation_after_later_failure() {
        var effects = 0
        val exec = VMExec(listOf(
            IRExprStmt(call("tick")), IRLet("changed", number(1)), IRExprOutput(call("missing")),
        ), hostFns = mapOf("tick" to { effects++; null }))
        val env = mutableMapOf<String, Any?>()
        val failure = assertFailsWith<IllegalStateException> { exec.run(env) }
        assertTrue(failure.cause is VMException.VariableNotFound)
        assertEquals(1, effects)
        assertEquals(1, env["changed"])
        assertEquals(VMExecutionPath.VM_BYTECODE, exec.lastExecution?.path)
    }

    @Test
    fun evaluator_runtime_error_propagates_once_instead_of_returning_null() {
        for (policy in VMFallbackPolicy.entries) {
            var effects = 0
            val failure = IllegalStateException("host failed after effect")
            val tracer = CollectingTracer()
            val eval = VMEval(hostFns = mapOf("fail" to { effects++; throw failure }),
                fallbackPolicy = policy, tracer = tracer)
            assertSame(failure, assertFailsWith<IllegalStateException> { eval.eval(call("fail"), mutableMapOf()) })
            assertEquals(1, effects)
            assertEquals(VMExecutionPath.VM_BYTECODE, eval.lastExecution?.path)
            assertEquals(false, eval.lastExecution?.succeeded)
            assertSame(failure, eval.lastExecution?.failure)
            assertFalse(tracer.events.any { (it.event as? TraceEvent.Call)?.kind == "FALLBACK" })
        }
    }

    @Test
    fun runtime_not_implemented_error_is_not_a_compilation_fallback() {
        var effects = 0
        val failure = NotImplementedError("host implementation missing")
        val hosts = mapOf<String, (List<Any?>) -> Any?>("fail" to { effects++; throw failure })
        val eval = VMEval(hostFns = hosts)
        assertSame(failure, assertFailsWith<NotImplementedError> { eval.eval(call("fail"), mutableMapOf()) })
        assertEquals(1, effects)
        assertEquals(VMExecutionPath.VM_BYTECODE, eval.lastExecution?.path)
        assertNull(eval.lastExecution?.compilationFailure)
    }

    @Test
    fun executor_compilation_fallback_is_observable_and_runs_interpreter_once() {
        var effects = 0
        val exec = VMExec(listOf(IRExprOutput(obj(interpreterOnlyExpression()))),
            hostFns = mapOf("tick" to { effects++; null }))
        assertNull(exec.lastExecution)
        assertEquals(mapOf("value" to 7), exec.run(mutableMapOf()))
        assertEquals(1, effects)
        assertEquals(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, exec.lastExecution?.path)
        assertEquals(true, exec.lastExecution?.succeeded)
        assertNotNull(exec.lastExecution?.compilationFailure)
        assertNull(exec.lastExecution?.failure)
        // Cached compilation failure remains a compilation fallback on subsequent calls.
        assertEquals(mapOf("value" to 7), exec.run(mutableMapOf()))
        assertEquals(2, effects)
    }

    @Test
    fun evaluator_compilation_fallback_emits_trace_and_evidence() {
        var effects = 0
        val tracer = CollectingTracer()
        val eval = VMEval(hostFns = mapOf("tick" to { effects++; null }), tracer = tracer)
        assertEquals(7, eval.eval(interpreterOnlyExpression(), mutableMapOf()))
        assertEquals(1, effects)
        assertEquals(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, eval.lastExecution?.path)
        assertEquals(true, eval.lastExecution?.succeeded)
        assertNotNull(eval.lastExecution?.compilationFailure)
        assertTrue(tracer.events.any { (it.event as? TraceEvent.Call)?.kind == "FALLBACK" })
        assertTrue(tracer.events.any { (it.event as? TraceEvent.Return)?.kind == "FALLBACK" })
    }

    @Test
    fun strict_executor_rejects_compilation_without_running_interpreter() {
        var effects = 0
        val exec = VMFactory.createExecutor(listOf(IRExprOutput(interpreterOnlyExpression())),
            hostFns = mapOf("tick" to { effects++; null }), fallbackPolicy = VMFallbackPolicy.NONE)
        assertFailsWith<IllegalStateException> { exec.run(mutableMapOf()) }
        assertEquals(0, effects)
        assertEquals(VMExecutionPath.COMPILATION_REJECTED, exec.lastExecution?.path)
        assertEquals(false, exec.lastExecution?.succeeded)
        assertSame(exec.lastExecution?.compilationFailure, exec.lastExecution?.failure)
    }

    @Test
    fun strict_evaluator_rejects_compilation_without_running_interpreter() {
        var effects = 0
        val eval = VMFactory.createEvaluator(hostFns = mapOf("tick" to { effects++; null }),
            fallbackPolicy = VMFallbackPolicy.NONE)
        assertFailsWith<IllegalStateException> { eval.eval(interpreterOnlyExpression(), mutableMapOf()) }
        assertEquals(0, effects)
        assertEquals(VMExecutionPath.COMPILATION_REJECTED, eval.lastExecution?.path)
        assertEquals(false, eval.lastExecution?.succeeded)
    }

    @Test
    fun strict_execution_reports_successful_bytecode() {
        val exec = VMExec(listOf(IRExprOutput(number(7))), fallbackPolicy = VMFallbackPolicy.NONE)
        assertEquals(7, exec.run(mutableMapOf()))
        assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), exec.lastExecution)
        val eval = VMEval(fallbackPolicy = VMFallbackPolicy.NONE)
        assertEquals(7, eval.eval(number(7), mutableMapOf()))
        assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), eval.lastExecution)
    }

    @Test
    fun failed_interpreter_fallback_propagates_and_reports_both_failures() {
        val failure = IllegalArgumentException("fallback host failed")
        val eval = VMEval(hostFns = mapOf("tick" to { throw failure }))
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            eval.eval(interpreterOnlyExpression(), mutableMapOf())
        })
        assertEquals(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, eval.lastExecution?.path)
        assertEquals(false, eval.lastExecution?.succeeded)
        assertNotNull(eval.lastExecution?.compilationFailure)
        assertSame(failure, eval.lastExecution?.failure)
    }

    @Test
    fun disabled_factory_components_report_interpreter_without_compiling() {
        VMFactory.Metrics.reset()
        var effects = 0
        val hosts = mapOf<String, (List<Any?>) -> Any?>("tick" to { effects++; 7 })
        val exec = VMFactory.createExecutor(listOf(IRExprOutput(obj(call("tick")))), hostFns = hosts,
            useVM = false, fallbackPolicy = VMFallbackPolicy.NONE)
        assertEquals(mapOf("value" to 7), exec.run(mutableMapOf()))
        assertEquals(VMExecutionReport(VMExecutionPath.INTERPRETER_DISABLED, true), exec.lastExecution)
        val eval = VMFactory.createEvaluator(hostFns = hosts, useVM = false, fallbackPolicy = VMFallbackPolicy.NONE)
        assertEquals(7, eval.eval(call("tick"), mutableMapOf()))
        assertEquals(VMExecutionReport(VMExecutionPath.INTERPRETER_DISABLED, true), eval.lastExecution)
        assertEquals(2, effects)
        assertEquals(0, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun observer_failure_never_replays_successful_execution() {
        var effects = 0
        val exec = VMExec(listOf(IRExprOutput(call("tick"))),
            hostFns = mapOf("tick" to { effects++; 7 }),
            onExecution = { error("observer failed") })
        assertFailsWith<IllegalStateException> { exec.run(mutableMapOf()) }
        assertEquals(1, effects)
        assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), exec.lastExecution)
    }
    @Test
    fun observer_failure_preserves_original_execution_error() {
        var effects = 0
        val failure = IllegalStateException("execution failure")
        val observerFailure = IllegalArgumentException("observer failure")
        val exec = VMExec(listOf(IRExprOutput(call("fail"))),
            hostFns = mapOf("fail" to { effects++; throw failure }),
            onExecution = { throw observerFailure })
        assertSame(failure, assertFailsWith<IllegalStateException> { exec.run(mutableMapOf()) })
        assertEquals(1, effects)
        assertSame(failure, exec.lastExecution?.failure)
        assertTrue(failure.suppressedExceptions.contains(observerFailure))
    }

    @Test
    fun stream_runner_forwards_strict_policy_and_execution_report() {
        val transform = Parser(Lexer("TRANSFORM T { OUTPUT { value: 7 } }").lex())
            .parse().decls.filterIsInstance<TransformDecl>().single()
        val reports = mutableListOf<VMExecutionReport>()
        val runner = compileStream(transform, engine = ExecutionEngine.VM,
            vmFallbackPolicy = VMFallbackPolicy.NONE, onVMExecution = reports::add)
        assertEquals(mapOf("value" to 7), runner(emptyMap()))
        assertEquals(listOf(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true)), reports)

        var effects = 0
        val unsupported = transform.copy(body = CodeBlock(listOf(
            OutputStmt(obj(interpreterOnlyExpression()), token),
        ), token))
        val strictRunner = compileStream(unsupported, hostFns = mapOf("tick" to { effects++; null }),
            engine = ExecutionEngine.VM, vmFallbackPolicy = VMFallbackPolicy.NONE,
            onVMExecution = reports::add)
        assertFailsWith<IllegalStateException> { strictRunner(emptyMap()) }
        assertEquals(0, effects)
        assertEquals(VMExecutionPath.COMPILATION_REJECTED, reports.last().path)
        assertEquals(false, reports.last().succeeded)
    }

}
