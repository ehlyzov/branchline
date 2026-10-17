package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.std.HostFnMetadata
import kotlin.test.*

class VMEvalPreparationTest {
    private val token = Token(TokenType.IDENTIFIER, "test", 1, 1)
    private fun number(value: Int) = NumberLiteral(I32(value), token)
    private fun call(name: String) = CallExpr(IdentifierExpr(name, token), emptyList(), token)
    private fun function(name: String, value: Int) = FuncDecl(name, emptyList(), ExprBody(number(value), token), token)

    private fun fallbackExpression(): Expr = InvokeExpr(
        LambdaExpr(emptyList(), BlockBody(CodeBlock(listOf(
            ExprStmt(call("tick"), token), ReturnStmt(number(7), token),
        ), token), token), token), emptyList(), token,
    )

    @Test
    fun repeated_identity_reuses_compilation_but_reads_each_environment() {
        VMFactory.Metrics.reset()
        val eval = VMEval(fallbackPolicy = VMFallbackPolicy.NONE)
        val expr = IdentifierExpr("value", token)
        repeat(100) { value -> assertEquals(value, eval.eval(expr, mutableMapOf("value" to value))) }
        assertEquals(1, VMFactory.getCompilationStats().totalCompilations)
        assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), eval.lastExecution)
    }

    @Test
    fun different_expression_identity_and_eviction_recompile() {
        VMFactory.Metrics.reset()
        val eval = VMEval()
        val first = number(7)
        assertEquals(7, eval.eval(first, mutableMapOf()))
        // Even structurally equal new ASTs are a new authority.
        assertEquals(7, eval.eval(number(7), mutableMapOf()))
        repeat(100) { assertEquals(it, eval.eval(number(it), mutableMapOf())) }
        assertEquals(7, eval.eval(first, mutableMapOf()))
        assertEquals(103, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun mutable_expression_children_invalidate_same_identity() {
        VMFactory.Metrics.reset()
        val elements = mutableListOf<Expr>(number(1))
        val expr = ObjectExpr(listOf(LiteralProperty(ObjKey.Name("nested"), ArrayExpr(elements, token))), token)
        val eval = VMEval()
        assertEquals(mapOf("nested" to listOf(1)), eval.eval(expr, mutableMapOf()))
        elements[0] = number(2)
        assertEquals(mapOf("nested" to listOf(2)), eval.eval(expr, mutableMapOf()))
        assertEquals(2, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun mutable_function_declarations_and_nested_bodies_invalidate_vm_function_cache() {
        VMFactory.Metrics.reset()
        val statements = mutableListOf<Stmt>(ReturnStmt(number(1), token))
        val declaration = FuncDecl("answer", emptyList(), BlockBody(CodeBlock(statements, token), token), token)
        val funcs = mutableMapOf("answer" to declaration)
        val eval = VMEval(funcs = funcs)
        val expr = call("answer")
        assertEquals(1, eval.eval(expr, mutableMapOf()))
        statements[0] = ReturnStmt(number(2), token)
        assertSame(declaration, funcs["answer"])
        assertEquals(2, eval.eval(expr, mutableMapOf()))
        funcs["answer"] = function("answer", 3)
        assertEquals(3, eval.eval(expr, mutableMapOf()))
        assertEquals(3, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun host_replacement_and_host_to_function_change_invalidate_prepared_bindings() {
        VMFactory.Metrics.reset()
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("answer" to { 1 })
        val funcs = mutableMapOf<String, FuncDecl>()
        val eval = VMEval(funcs = funcs, hostFns = hosts)
        val expr = call("answer")
        assertEquals(1, eval.eval(expr, mutableMapOf()))
        hosts["answer"] = { 2 }
        assertEquals(2, eval.eval(expr, mutableMapOf()))
        hosts.clear()
        funcs["answer"] = function("answer", 3)
        assertEquals(3, eval.eval(expr, mutableMapOf()))
        assertEquals(3, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun changed_host_metadata_is_live_in_cached_fallback_and_invalidates_authority() {
        VMFactory.Metrics.reset()
        var metadataCalls = 0
        var effects = 0
        val metadata = mutableMapOf("tick" to HostFnMetadata { metadataCalls++; false })
        val eval = VMEval(hostFns = mapOf("tick" to { effects++; null }), hostFnMeta = metadata)
        val expr = fallbackExpression()
        assertEquals(7, eval.eval(expr, mutableMapOf()))
        metadata["tick"] = HostFnMetadata { metadataCalls += 10; false }
        assertEquals(7, eval.eval(expr, mutableMapOf()))
        assertEquals(11, metadataCalls)
        assertEquals(2, effects)
        assertEquals(2, VMFactory.getCompilationStats().failedCompilations)
    }

    @Test
    fun prepared_expression_freezes_ast_functions_hosts_and_metadata_before_first_execution() {
        VMFactory.Metrics.reset()
        val elements = mutableListOf<Expr>(number(1))
        val functionElements = mutableListOf<Expr>(number(2))
        val funcs = mutableMapOf("answer" to FuncDecl("answer", emptyList(),
            ExprBody(ArrayExpr(functionElements, token), token), token))
        var calls = 0
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("tick" to { ++calls })
        val fields = mutableListOf<Property>(
            LiteralProperty(ObjKey.Name("array"), ArrayExpr(elements, token)),
            LiteralProperty(ObjKey.Name("function"), call("answer")),
            LiteralProperty(ObjKey.Name("host"), call("tick")),
            LiteralProperty(ObjKey.Name("input"), IdentifierExpr("input", token)),
        )
        val prepared = VMEval(funcs = funcs, hostFns = hosts, fallbackPolicy = VMFallbackPolicy.NONE)
            .prepare(ObjectExpr(fields, token))
        elements[0] = number(10)
        functionElements[0] = number(20)
        funcs.clear()
        hosts["tick"] = { -1 }
        fields.clear()
        repeat(100) { value ->
            assertEquals(mapOf("array" to listOf(1), "function" to listOf(2), "host" to value + 1, "input" to value),
                prepared.eval(mutableMapOf("input" to value)))
        }
        assertEquals(100, calls)
        assertEquals(1, VMFactory.getCompilationStats().totalCompilations)
        assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), prepared.lastExecution)
    }

    @Test
    fun prepared_fallback_captures_host_metadata_and_compiles_failure_only_once() {
        VMFactory.Metrics.reset()
        var effects = 0
        var metadataCalls = 0
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("tick" to { effects++; null })
        val metadata = mutableMapOf("tick" to HostFnMetadata { metadataCalls++; false })
        val prepared = VMEval(hostFns = hosts, hostFnMeta = metadata).prepare(fallbackExpression())
        hosts["tick"] = { error("changed binding must not run") }
        metadata["tick"] = HostFnMetadata { error("changed metadata must not run") }
        repeat(3) { assertEquals(7, prepared.eval(mutableMapOf())) }
        assertEquals(3, effects)
        assertEquals(3, metadataCalls)
        assertEquals(1, VMFactory.getCompilationStats().failedCompilations)
        assertEquals(VMExecutionPath.INTERPRETER_COMPILE_FALLBACK, prepared.lastExecution?.path)
    }

    @Test
    fun implicit_fallback_and_strict_rejection_cache_failure_without_replay() {
        for (policy in VMFallbackPolicy.entries) {
            VMFactory.Metrics.reset()
            var effects = 0
            val eval = VMEval(hostFns = mapOf("tick" to { effects++; null }), fallbackPolicy = policy)
            val expr = fallbackExpression()
            repeat(3) {
                if (policy == VMFallbackPolicy.NONE) assertFailsWith<IllegalStateException> { eval.eval(expr, mutableMapOf()) }
                else assertEquals(7, eval.eval(expr, mutableMapOf()))
            }
            assertEquals(if (policy == VMFallbackPolicy.NONE) 0 else 3, effects)
            assertEquals(1, VMFactory.getCompilationStats().failedCompilations)
        }
    }

    @Test
    fun prepared_host_failure_keeps_exception_identity_and_runs_once_per_call() {
        VMFactory.Metrics.reset()
        var effects = 0
        val failure = IllegalArgumentException("after effect")
        val prepared = VMEval(hostFns = mapOf("fail" to { effects++; throw failure })).prepare(call("fail"))
        repeat(3) {
            assertSame(failure, assertFailsWith<IllegalArgumentException> { prepared.eval(mutableMapOf()) })
            assertSame(failure, prepared.lastExecution?.failure)
            assertNull(prepared.lastExecution?.compilationFailure)
            assertEquals(VMExecutionPath.VM_BYTECODE, prepared.lastExecution?.path)
        }
        assertEquals(3, effects)
        assertEquals(1, VMFactory.getCompilationStats().totalCompilations)
    }

    @Test
    fun disabled_prepared_evaluation_never_compiles() {
        VMFactory.Metrics.reset()
        val prepared = VMEval(useVM = false).prepare(IdentifierExpr("value", token))
        repeat(3) { assertEquals(it, prepared.eval(mutableMapOf("value" to it))) }
        assertEquals(0, VMFactory.getCompilationStats().totalCompilations)
        assertEquals(VMExecutionPath.INTERPRETER_DISABLED, prepared.lastExecution?.path)
    }

    private class LookupOnlyMap<V>(private val bindings: Map<String, V>) : Map<String, V> by bindings {
        override val entries: Set<Map.Entry<String, V>> get() = error("unrelated authority must not be enumerated")
        override val keys: Set<String> get() = error("unrelated authority must not be enumerated")
        override val values: Collection<V> get() = error("unrelated authority must not be enumerated")
    }

    private class UnreadableExpressionList : AbstractList<Expr>() {
        override val size: Int get() = error("unused function body must not be inspected")
        override fun get(index: Int): Expr = error("unused function body must not be inspected")
    }

    @Test
    fun unused_function_authority_is_never_enumerated_or_snapshotted_by_live_eval() {
        val unused = FuncDecl("unused", emptyList(), ExprBody(ArrayExpr(UnreadableExpressionList(), token), token), token)
        val functions = LookupOnlyMap(mapOf("unused" to unused))
        val expr = StringExpr("ok", token)
        for (useVM in listOf(true, false)) {
            VMFactory.Metrics.reset()
            val eval = VMEval(funcs = functions, useVM = useVM, fallbackPolicy = VMFallbackPolicy.NONE)
            repeat(3) { assertEquals("ok", eval.eval(expr, mutableMapOf())) }
            assertEquals(if (useVM) 3L else 0L, VMFactory.getCompilationStats().totalCompilations)
            assertEquals(if (useVM) VMExecutionPath.VM_BYTECODE else VMExecutionPath.INTERPRETER_DISABLED,
                eval.lastExecution?.path)
        }
    }

    @Test
    fun large_host_and_metadata_authorities_are_not_enumerated_by_live_eval() {
        val bindings = (0 until 128).associate { "host$it" to { _: List<Any?> -> it } }
        val metadata = (0 until 128).associate { "host$it" to HostFnMetadata { false } }
        val expr = call("host0")
        for (useVM in listOf(true, false)) {
            // Exercise each opt-out independently, so a metadata scan cannot hide
            // behind the large host table check.
            for (largeHosts in listOf(true, false)) {
                VMFactory.Metrics.reset()
                val hosts = if (largeHosts) LookupOnlyMap(bindings) else mapOf("host0" to bindings.getValue("host0"))
                val meta = if (largeHosts) emptyMap() else LookupOnlyMap(metadata)
                val eval = VMEval(hostFns = hosts, hostFnMeta = meta, useVM = useVM,
                    fallbackPolicy = VMFallbackPolicy.NONE)
                repeat(3) { assertEquals(0, eval.eval(expr, mutableMapOf())) }
                assertEquals(if (useVM) 3L else 0L, VMFactory.getCompilationStats().totalCompilations)
            }
        }
    }

    @Test
    fun disabled_eval_keeps_mutable_bindings_metadata_and_expression_live_without_compilation() {
        VMFactory.Metrics.reset()
        var metadataCalls = 0
        var effects = 0
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("tick" to { effects++; 1 })
        val metadata = mutableMapOf("tick" to HostFnMetadata { metadataCalls++; false })
        val reports = mutableListOf<VMExecutionReport>()
        val eval = VMEval(hostFns = hosts, hostFnMeta = metadata, useVM = false, onExecution = reports::add)
        val elements = mutableListOf<Expr>(call("tick"))
        val expr = ArrayExpr(elements, token)
        assertEquals(listOf(1), eval.eval(expr, mutableMapOf()))
        hosts["tick"] = { effects++; 2 }
        metadata["tick"] = HostFnMetadata { metadataCalls += 10; false }
        elements.add(number(7))
        assertEquals(listOf(2, 7), eval.eval(expr, mutableMapOf()))
        val failure = IllegalArgumentException("after effect")
        hosts["tick"] = { effects++; throw failure }
        assertSame(failure, assertFailsWith<IllegalArgumentException> { eval.eval(expr, mutableMapOf()) })
        assertEquals(3, effects)
        assertEquals(21, metadataCalls)
        assertEquals(0, VMFactory.getCompilationStats().totalCompilations)
        assertEquals(3, reports.size)
        assertEquals(VMExecutionReport(VMExecutionPath.INTERPRETER_DISABLED, false, failure = failure), reports.last())
        assertEquals(reports.last(), eval.lastExecution)
    }

    @Test
    fun oversized_authority_evicts_a_previous_implicit_entry_before_becoming_small_again() {
        VMFactory.Metrics.reset()
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("answer" to { 1 })
        val eval = VMEval(hostFns = hosts)
        val expr = call("answer")
        assertEquals(1, eval.eval(expr, mutableMapOf()))
        repeat(128) { hosts["unused$it"] = { null } }
        hosts["answer"] = { 2 }
        assertEquals(2, eval.eval(expr, mutableMapOf()))
        hosts.clear()
        hosts["answer"] = { 3 }
        assertEquals(3, eval.eval(expr, mutableMapOf()))
        assertEquals(3, VMFactory.getCompilationStats().totalCompilations)
    }

}
