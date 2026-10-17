package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.*

class VMRuntimePreparationTest {
    @Test
    fun numeric_dispatch_never_hashes_unexecuted_constants() {
        val unused = object {
            override fun hashCode(): Int = error("Bytecode structure reached from numeric dispatch")
        }
        val code = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("a"), Instruction.LOAD_VAR("b"), Instruction.ADD,
        )).copy(constants = listOf(unused))
        val vm = VM()
        assertEquals(3, vm.execute(code, mutableMapOf("a" to 1, "b" to 2)))
        assertEquals(3.5, vm.execute(code, mutableMapOf("a" to 1, "b" to 2.5)))
        assertEquals("12", vm.execute(code, mutableMapOf("a" to "1", "b" to 2)))
        assertEquals(7, vm.execute(code, mutableMapOf("a" to 3, "b" to 4)))
    }

    @Test
    fun calls_do_not_enumerate_unrelated_caller_bindings() {
        val callee = Bytecode.fromInstructions(listOf(Instruction.PUSH(7), Instruction.RETURN_VALUE))
        val code = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), callee)), Instruction.CALL_LAMBDA(0),
        ))
        val source = (0..4095).associate { "unused$it" to it }.toMutableMap<String, Any?>()
        val env = object : MutableMap<String, Any?> by source {
            override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
                get() = error("Function call enumerated unrelated caller bindings")
        }
        assertEquals(7, VM().execute(code, env))
    }

    @Test
    fun indexed_host_calls_observe_replaced_or_removed_authority_on_repeated_execute() {
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("target" to { "old" })
        val root = Bytecode.fromInstructions(listOf(Instruction.CALL_HOST(0, "target", 0)))
        val vm = VM(hosts)
        assertEquals("old", vm.execute(root))
        hosts["target"] = { "new" }
        assertEquals("new", vm.execute(root))
        hosts.remove("target")
        assertFailsWith<VMException.VariableNotFound> { vm.execute(root) }
    }

    @Test
    fun indexed_host_calls_observe_authority_replacement_inside_a_nested_call() {
        val hosts = mutableMapOf<String, (List<Any?>) -> Any?>("target" to { "old" })
        hosts["change"] = { hosts["target"] = { "new" }; null }
        val child = Bytecode.fromInstructions(listOf(
            Instruction.CALL_HOST(0, "change", 0), Instruction.RETURN,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), child)), Instruction.CALL_LAMBDA(0),
            Instruction.POP, Instruction.CALL_HOST(0, "target", 0),
        ))
        assertEquals("new", VM(hosts).execute(root))
    }

    @Test
    fun host_indices_remain_program_local_across_nested_calls_and_resume() {
        val leaf = Bytecode.fromInstructions(listOf(
            Instruction.CALL_HOST(0, "inner", 0), Instruction.SUSPEND, Instruction.RETURN_VALUE,
        ))
        val middle = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), leaf)), Instruction.CALL_LAMBDA(0),
            Instruction.POP,
            Instruction.CALL_HOST(0, "middle", 0), Instruction.RETURN_VALUE,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), middle)), Instruction.CALL_LAMBDA(0),
            Instruction.CALL_HOST(0, "outer", 0), Instruction.MAKE_ARRAY(2),
        ))
        val effects = mutableListOf<String>()
        val hosts = listOf("inner", "middle", "outer").associateWith { name ->
            { _: List<Any?> -> effects.add(name); name }
        }
        val payload = VM(hosts).execute(root) as Map<*, *>
        assertEquals(listOf("inner"), effects)
        val resumed = VM.restoreFromSnapshot(payload["snapshot"] as String, hosts)
        assertTrue(resumed.resume())
        assertEquals(listOf("middle", "outer"), resumed.resultOrNull())
        assertEquals(listOf("inner", "middle", "outer"), effects)
    }

    @Test
    fun empty_comprehension_builder_can_resume_and_append() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.ARRAY_BUILDER_INIT, Instruction.SUSPEND, Instruction.PUSH(7),
            Instruction.ARRAY_BUILDER_ADD, Instruction.ARRAY_BUILDER_FINISH,
        ))
        val payload = VM().execute(code) as Map<*, *>
        val resumed = VM.restoreFromSnapshot(payload["snapshot"] as String)
        assertTrue(resumed.resume())
        assertEquals(listOf(7L), resumed.resultOrNull())
    }

    @Test
    fun snapshot_preserves_operand_order() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(10), Instruction.PUSH(3), Instruction.SUSPEND, Instruction.SUB,
        ))
        val payload = VM().execute(code) as Map<*, *>
        val resumed = VM.restoreFromSnapshot(payload["snapshot"] as String)
        assertTrue(resumed.resume())
        assertEquals(7L, resumed.resultOrNull())
    }

    @Test
    fun live_closure_capture_and_shadowing_match_interpreter_after_repeated_calls() {
        val source = """
            TRANSFORM T {
                LET value = 1
                LET read = () -> value
                LET shadow = (value) -> value
                LET first = read()
                LET value = 9
                LET second = read()
                OUTPUT { first: first, second: second, shadow: shadow(42), outer: value }
            }
        """.trimIndent()
        val transform = Parser(Lexer(source).lex()).parse().decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(emptyMap(), StdLib.fns).compile(transform.body.statements)
        val expected = Exec(ir, hostFns = StdLib.fns).run(mutableMapOf())
        val actual = VM(StdLib.fns).execute(Compiler(hostFns = StdLib.fns, useLocals = false).compile(ir))
        assertEquals(mapOf("first" to 1, "second" to 9, "shadow" to 42, "outer" to 9), expected)
        assertEquals(expected, actual)
    }

    @Test
    fun host_mutations_remain_visible_after_lambda_return() {
        val env = mutableMapOf<String, Any?>("value" to 1)
        var effects = 0
        val failure = IllegalStateException("host failed")
        val hosts = mapOf<String, (List<Any?>) -> Any?>(
            "change" to { effects++; env["value"] = 9; null },
            "fail" to { effects++; throw failure },
        )
        val lambda = Bytecode.fromInstructions(listOf(
            Instruction.CALL_HOST(0, "change", 0), Instruction.RETURN,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), lambda)), Instruction.CALL_LAMBDA(0),
            Instruction.POP, Instruction.LOAD_VAR("value"),
        ))
        val vm = VM(hosts)
        assertEquals(9, vm.execute(root, env))
        assertEquals(1, effects)
        val failing = Bytecode.fromInstructions(listOf(Instruction.CALL_HOST(0, "fail", 0)))
        assertSame(failure, assertFailsWith<IllegalStateException> { vm.execute(failing, env) })
        assertEquals(2, effects)
    }

    @Test
    fun host_can_catch_a_callback_error_and_continue_in_the_callers_program() {
        val failure = IllegalStateException("callback failed")
        var effects = 0
        val leaf = Bytecode.fromInstructions(listOf(
            Instruction.CALL_HOST(0, "fail", 0), Instruction.RETURN_VALUE,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), leaf)), Instruction.CALL_HOST(0, "invoke", 1),
            Instruction.CALL_HOST(1, "after", 0), Instruction.MAKE_ARRAY(2),
        ))
        val hosts = mapOf<String, (List<Any?>) -> Any?>(
            "fail" to { effects++; throw failure },
            "invoke" to { args ->
                @Suppress("UNCHECKED_CAST")
                val callback = args.single() as (List<Any?>) -> Any?
                assertSame(failure, assertFailsWith<IllegalStateException> { callback(emptyList()) })
                "recovered"
            },
            "after" to { effects++; "after" },
        )
        assertEquals(listOf("recovered", "after"), VM(hosts).execute(root))
        assertEquals(2, effects)
    }

    @Test
    fun caller_try_handles_callback_failure_only_after_host_call_propagates() {
        val failure = IllegalStateException("callback failed")
        var effects = 0
        val leaf = Bytecode.fromInstructions(listOf(
            Instruction.CALL_HOST(0, "fail", 0), Instruction.RETURN_VALUE,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.TRY_START(6), Instruction.PUSH(LambdaTemplate(emptyList(), leaf)),
            Instruction.CALL_HOST(0, "invoke", 1), Instruction.TRY_END,
            Instruction.PUSH("ok"), Instruction.RETURN_VALUE,
            Instruction.CATCH("error", 0), Instruction.PUSH("fallback"), Instruction.RETURN_VALUE,
        ))
        val hosts = mapOf<String, (List<Any?>) -> Any?>(
            "fail" to { effects++; throw failure },
            "invoke" to { args ->
                @Suppress("UNCHECKED_CAST")
                val callback = args.single() as (List<Any?>) -> Any?
                repeat(2) { callback(emptyList()) }
                "unexpected"
            },
        )
        assertEquals("fallback", VM(hosts).execute(root))
        assertEquals(1, effects)
    }

    private fun retryProgram(): Bytecode = Bytecode.fromInstructions(listOf(
        Instruction.TRY_START(6), Instruction.CALL_FN("nested", 0), Instruction.POP,
        Instruction.TRY_END, Instruction.PUSH("ok"), Instruction.RETURN_VALUE,
        Instruction.CATCH("error", 2), Instruction.PUSH("fallback"), Instruction.RETURN_VALUE,
    ))

    private fun nestedFunction(suspend: Boolean): Map<String, FuncDecl> {
        val suspension = if (suspend) "SUSPEND 0;" else ""
        val source = "FUNC nested() { $suspension LET ignored = fail(); RETURN 7; }"
        return Parser(Lexer(source).lex()).parse().decls.filterIsInstance<FuncDecl>().associateBy { it.name }
    }

    @Test
    fun outer_try_retries_a_throwing_function_in_the_callers_program() {
        var attempts = 0
        val hosts = mapOf<String, (List<Any?>) -> Any?>("fail" to {
            attempts++
            if (attempts < 3) error("retry")
            null
        })
        assertEquals("ok", VM(hosts, nestedFunction(false)).execute(retryProgram()))
        assertEquals(3, attempts)
        val alwaysFail = mapOf<String, (List<Any?>) -> Any?>("fail" to { error("exhausted") })
        assertEquals("fallback", VM(alwaysFail, nestedFunction(false)).execute(retryProgram()))
    }

    @Test
    fun cross_call_try_and_retry_survive_suspension() {
        var attempts = 0
        val hosts = mapOf<String, (List<Any?>) -> Any?>("fail" to {
            attempts++
            if (attempts < 3) error("retry")
            null
        })
        val funcs = nestedFunction(true)
        var vm = VM(hosts, funcs)
        var snapshot = (vm.execute(retryProgram()) as Map<*, *>)["snapshot"] as String
        var finished = false
        repeat(4) {
            if (!finished) {
                vm = VM.restoreFromSnapshot(snapshot, hosts, funcs)
                finished = vm.resume()
                if (!finished) snapshot = vm.suspendedSnapshotOrNull() ?: error("Expected retry suspension")
            }
        }
        assertTrue(finished)
        assertEquals("ok", vm.resultOrNull())
        assertEquals(3, attempts)
    }

    @Test
    fun loop_local_binding_is_removed_from_lambda_environment() {
        val callee = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(listOf(1)), Instruction.INIT_FOREACH("item", 4),
            Instruction.NOP, Instruction.NEXT_FOREACH(2, 4),
            Instruction.LOAD_VAR("item"), Instruction.RETURN_VALUE,
        ))
        val root = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(LambdaTemplate(emptyList(), callee)), Instruction.CALL_LAMBDA(0),
        ))
        assertFailsWith<VMException.VariableNotFound> { VM().execute(root) }
    }
}
