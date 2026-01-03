package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotCompletenessVMTest {

    @Test
    fun resume_inside_try_block() {
        val program = """
                TRANSFORM T { TRY SUSPEND 0
                    CATCH(e) RETRY 0 TIMES -> { err: "x" }
                    OUTPUT { ok: true }
                }
            """.trimIndent()
        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = StdLib.fns
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)
        var vm = VM(hostFns, funcs)
        val payload = vm.execute(bytecode, mutableMapOf()) as Map<*, *>
        val snap = payload["snapshot"] as String
        vm = VM.restoreFromSnapshot(snap, hostFns, funcs)
        vm.step()
        val out = vm.resultOrNull() as Map<*, *>
        assertEquals(true, out["ok"])
    }

    @Test
    fun resume_in_middle_of_loop() {
        val program = """
                TRANSFORM T { LET xs = [1,2,3];
                    LET acc = { ys: [] };
                    FOR EACH i IN xs {
                        APPEND TO acc.ys i INIT [];
                        SUSPEND 0;
                    }
                    OUTPUT acc
                }
            """.trimIndent()
        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = StdLib.fns
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)
        var vm = VM(hostFns, funcs)
        // First run returns suspension
        var payload = vm.execute(bytecode, mutableMapOf()) as Map<*, *>
        var snap = payload["snapshot"] as String
        while (true) {
            vm = VM.restoreFromSnapshot(snap, hostFns, funcs)
            val finished = vm.step(1000)
            if (finished) break
            val next = vm.suspendedSnapshotOrNull()
            require(next != null) { "Expected suspension snapshot" }
            snap = next
        }
        val out = vm.resultOrNull() as Map<*, *>

        @Suppress("UNCHECKED_CAST")
        val ys = ((out["ys"]) as List<Any?>).map { (it as Number).toInt() }
        assertEquals(listOf(1, 2, 3), ys)
    }

    @Test
    fun resume_inside_function_call() {
        val program = """
                FUNC f() {
                    LET a = 1;
                    SUSPEND 0;
                    RETURN a + 1;
                }
                TRANSFORM T { OUTPUT { y: f() }
                }
            """.trimIndent()
        val tokens = Lexer(program).lex()
        val prog = Parser(tokens, program).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = StdLib.fns
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)
        var vm = VM(hostFns, funcs)
        var payload = vm.execute(bytecode, mutableMapOf()) as Map<*, *>
        var snap = payload["snapshot"] as String
        // Verify callStack presence and then resume to completion with a hard iteration cap
        assertTrue(snap.contains("\"calls\":"))
        val maxResumes = 16
        var finished = false
        var iter = 0
        while (iter++ < maxResumes) {
            vm = VM.restoreFromSnapshot(snap, hostFns, funcs)
            finished = vm.resume()
            if (finished) break
            val next = vm.suspendedSnapshotOrNull() ?: break
            // If next equals previous snapshot, break to avoid infinite loop
            if (next == snap) break
            snap = next
        }
        assertTrue(finished, "Function-call resume did not finish within $maxResumes iterations")
        val out = vm.resultOrNull() as Map<*, *>
        assertEquals(2, (out["y"] as Number).toInt())
    }
}
