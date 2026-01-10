package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.Test
import kotlin.test.assertEquals

class SuspendVMTest {

    @Test
    fun suspend_and_resume_simple() {
        val program = """
            TRANSFORM T { SUSPEND 1;
                OUTPUT { done: true }
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
        val vm = VM(hostFns, funcs)

        val value = vm.execute(bytecode, mutableMapOf())
        val suspended = value as Map<*, *>
        assertEquals(true, suspended["__suspended"])
        val snapshot = suspended["snapshot"] as String

        val vm2 = VM.restoreFromSnapshot(snapshot, hostFns, funcs)
        vm2.step() // run to completion
        val out = vm2.resultOrNull() as Map<*, *>
        assertEquals(true, out["done"])
    }

    @Test
    fun suspend_preserves_locals_across_resume() {
        val program = """
            TRANSFORM T { LET a = 5;
                SUSPEND 1;
                OUTPUT { v: a }
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
        val vm = VM(hostFns, funcs)

        val value = vm.execute(bytecode, mutableMapOf())
        val snapshot = (value as Map<*, *>) ["snapshot"] as String
        val vm2 = VM.restoreFromSnapshot(snapshot, hostFns, funcs)
        vm2.step()
        val out = vm2.resultOrNull() as Map<*, *>
        val v = out["v"] as Number
        assertEquals(5, v.toInt())
    }

    // Multiple suspension points can be tested similarly by resuming from each returned snapshot.
}
