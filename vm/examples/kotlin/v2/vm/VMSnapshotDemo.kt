package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.std.StdLib

/**
 * Demo utility: compile a small program, run partially, snapshot, restore, and finish.
 */
object VMSnapshotDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        // Simple program that increments a variable in steps; no OUTPUT to avoid unimplemented op
        val program = """
            LET total = 0;
            SET total = total + 1;
            SET total = total + 2;
            SET total = total + 3;
        """.trimIndent()

        // Parse → IR
        val code = """
            TRANSFORM __T { $program
            }
        """.trimIndent()

        val tokens = Lexer(code).lex()
        val prog = Parser(tokens, code).parse()
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = StdLib.fns
        SemanticAnalyzer(hostFns.keys).analyze(prog)
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)

        // Compile IR → bytecode
        val compiler = Compiler(funcs, hostFns)
        val bytecode = compiler.compile(ir)

        // Start VM and run a few steps
        val vm = VM(hostFns, funcs)
        val env = mutableMapOf<String, Any?>(
            io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS to emptyMap<String, Any?>()
        ).apply {
            for (alias in io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES) this[alias] = emptyMap<String, Any?>()
        }
        vm.begin(bytecode, env)
        vm.step(3) // run partially

        // Snapshot current state
        val snapshot = vm.snapshotJson()
        // Persist snapshot to file snapshot_{timestamp}.txt
        val ts = System.currentTimeMillis()
        val outFile = java.io.File("snapshot_${ts}.txt")
        outFile.writeText(snapshot)
        println("Saved snapshot JSON to ${outFile.absolutePath}")
        println("\n--- Snapshot (pretty) ---\n" + VM.prettySnapshot(snapshot))

        // Simulate saving and restoring elsewhere
        val vm2 = VM.restoreFromSnapshot(snapshot, hostFns, funcs)
        val finished = vm2.step(Int.MAX_VALUE)
        val result = vm2.resultOrNull()
        println("\nFinished: $finished, env[total]=${vm2SnapshotTotal(vm2)} result=$result")
    }

    private fun vm2SnapshotTotal(vm: VM): Any? = try {
        val f = VM::class.java.getDeclaredField("environment")
        f.isAccessible = true
        val env = f.get(vm) as MutableMap<*, *>
        env["total"]
    } catch (_: Exception) { null }
}
