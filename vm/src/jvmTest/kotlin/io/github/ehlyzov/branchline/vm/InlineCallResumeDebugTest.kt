package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.Test

class InlineCallResumeDebugTest {
    @Test
    fun debug_inline_call_suspend_resume() {
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
        SemanticAnalyzer(hostFns.keys).analyze(prog)
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)
        var vm = VM(hostFns, funcs)
        var payload = vm.execute(bytecode, mutableMapOf()) as Map<*, *>
        var snap = payload["snapshot"] as String
        while (true) {
            vm = VM.restoreFromSnapshot(snap, hostFns, funcs)
            val finished = vm.step(1000)
            if (finished) break
            val next = vm.suspendedSnapshotOrNull()
            require(next != null)
            snap = next
        }
        val out = vm.resultOrNull()
        println("InlineCallResumeDebugTest out=$out")
    }
}

