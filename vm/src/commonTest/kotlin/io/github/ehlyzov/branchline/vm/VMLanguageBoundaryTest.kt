package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.IRExprOutput
import io.github.ehlyzov.branchline.ir.compileStream
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.*

class VMLanguageBoundaryTest {
    private fun runner(
        source: String,
        hostFns: Map<String, (List<Any?>) -> Any?> = StdLib.fns,
        onExecution: ((VMExecutionReport) -> Unit)? = null,
    ): (Map<String, Any?>) -> Any? {
        val program = Parser(Lexer(source).lex()).parse()
        return compileStream(
            program.decls.filterIsInstance<TransformDecl>().single(),
            funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name },
            hostFns = hostFns, engine = ExecutionEngine.VM,
            vmFallbackPolicy = VMFallbackPolicy.NONE, onVMExecution = onExecution,
        )
    }

    @Test
    fun present_null_and_missing_variables_remain_distinct_in_low_level_vm() {
        val bytecode = Bytecode.fromInstructions(listOf(Instruction.LOAD_VAR("value")))
        assertNull(VM().execute(bytecode, mutableMapOf("value" to null)))
        assertFailsWith<VMException.VariableNotFound> { VM().execute(bytecode, mutableMapOf()) }
    }

    @Test
    fun nullable_loop_binding_can_be_filtered_without_replay() {
        val reports = mutableListOf<VMExecutionReport>()
        val run = runner("""
            TRANSFORM T {
                LET values = []
                FOR EACH value IN input.values WHERE value != NULL { values += value }
                OUTPUT { values: values }
            }
        """.trimIndent(), onExecution = reports::add)
        assertEquals(mapOf("values" to listOf(1, 2)), run(mapOf("values" to listOf(1, null, 2))))
        assertEquals(listOf(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true)), reports)
    }

    @Test
    fun null_function_and_lambda_parameters_shadow_captured_values() {
        val run = runner("""
            FUNC identity(value) = value;
            TRANSFORM T {
                LET value = 42
                LET closure = (value) -> value
                OUTPUT { functionValue: identity(NULL), lambdaValue: closure(NULL) }
            }
        """.trimIndent())
        assertEquals(mapOf("functionValue" to null, "lambdaValue" to null), run(emptyMap()))
    }

    @Test
    fun implicit_return_resumes_after_call_without_repeating_host_effect() {
        var effects = 0
        val run = runner("""
            FUNC maybe(flag) { LET ignored = tick(); IF flag ?? FALSE THEN { RETURN "yes"; } }
            TRANSFORM T { OUTPUT { value: maybe(input.flag) } }
        """.trimIndent(), hostFns = mapOf("tick" to { effects++; null }))
        assertEquals(mapOf("value" to "yes"), run(mapOf("flag" to true)))
        assertEquals(mapOf("value" to null), run(mapOf("flag" to false)))
        assertEquals(mapOf("value" to null), run(mapOf("flag" to null)))
        assertEquals(3, effects)
    }

    @Test
    fun nested_implicit_returns_restore_the_outer_call_once() {
        val run = runner("""
            FUNC inner() { LET value = 1; }
            FUNC outer() { LET result = inner(); }
            TRANSFORM T { OUTPUT { value: outer() } }
        """.trimIndent())
        assertEquals(mapOf("value" to null), run(emptyMap()))
    }

    @Test
    fun host_callback_implicit_returns_resume_host_without_running_caller_bytecode() {
        val run = runner("""
            TRANSFORM T { OUTPUT { values: MAP([1, 2], (value) -> { LET ignored = value; }) } }
        """.trimIndent())
        assertEquals(mapOf("values" to listOf(null, null)), run(emptyMap()))
    }

    @Test
    fun adapters_preserve_language_error_categories_and_raw_vm_cause() {
        for (body in listOf(
            "OUTPUT { value: missing() }",
            "OUTPUT { values: [x FOR EACH x IN 1] }",
            "FOR EACH x IN 1 { OUTPUT { value: x } }",
            "LET value = {}; SET value.a.b = 1; OUTPUT value",
        )) {
            val reports = mutableListOf<VMExecutionReport>()
            val failure = assertFailsWith<IllegalStateException>(body) {
                runner("TRANSFORM T { $body }", onExecution = reports::add)(emptyMap())
            }
            assertTrue(failure.cause is VMException, body)
            assertSame(failure, reports.single().failure)
            assertEquals(VMExecutionPath.VM_BYTECODE, reports.single().path)
        }
        val failure = assertFailsWith<IllegalArgumentException> {
            runner("TRANSFORM T { OUTPUT { value: [1][9] } }")(emptyMap())
        }
        assertTrue(failure.cause is VMException.IndexOutOfBounds)
        val token = Token(TokenType.IDENTIFIER, "missing", 1, 1)
        val expr = CallExpr(IdentifierExpr("missing", token), emptyList(), token)
        val eval = VMEval(fallbackPolicy = VMFallbackPolicy.NONE)
        val evalFailure = assertFailsWith<IllegalStateException> { eval.eval(expr, mutableMapOf()) }
        assertTrue(evalFailure.cause is VMException.VariableNotFound)
        assertSame(evalFailure, eval.lastExecution?.failure)
    }

    @Test
    fun host_thrown_vm_errors_keep_identity_and_execute_once_in_both_adapters() {
        for (failure in listOf(
            VMException.VariableNotFound("host-owned"),
            VMException.TypeMismatch("host-owned", "value"),
            VMException.IndexOutOfBounds(9, 1),
        )) {
            var effects = 0
            val hosts = mapOf<String, (List<Any?>) -> Any?>("fail" to { effects++; throw failure })
            val token = Token(TokenType.IDENTIFIER, "fail", 1, 1)
            val expr = CallExpr(IdentifierExpr("fail", token), emptyList(), token)
            val exec = VMExec(listOf(IRExprOutput(expr)), hostFns = hosts, fallbackPolicy = VMFallbackPolicy.NONE)
            assertSame(failure, assertFails { exec.run(mutableMapOf()) })
            assertSame(failure, exec.lastExecution?.failure)
            val eval = VMEval(hostFns = hosts, fallbackPolicy = VMFallbackPolicy.NONE)
            assertSame(failure, assertFails { eval.eval(expr, mutableMapOf()) })
            assertSame(failure, eval.lastExecution?.failure)
            assertEquals(2, effects)
        }
    }

    @Test
    fun unused_function_does_not_compile_until_called_and_failure_is_reported() {
        val reports = mutableListOf<VMExecutionReport>()
        val run = runner("""
            FUNC invalid() { OUTPUT { forbidden: TRUE } }
            TRANSFORM T { OUTPUT { value: IF input.call THEN invalid() ELSE 7 } }
        """.trimIndent(), onExecution = reports::add)
        assertEquals(mapOf("value" to 7), run(mapOf("call" to false)))
        assertFailsWith<IllegalStateException> { run(mapOf("call" to true)) }
        assertEquals(listOf(true, false), reports.map { it.succeeded })
        assertTrue(reports.all { it.path == VMExecutionPath.VM_BYTECODE && it.compilationFailure == null })
    }
}
