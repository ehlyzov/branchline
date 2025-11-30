package v2.vm

import v2.*
import v2.ir.ToIR
import v2.sema.SemanticAnalyzer
import v2.std.StdLib
import kotlin.test.Test

class InlineCallBytecodeInspectTest {
    @Test
    fun dump_inline_call_bytecode() {
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
        println(BytecodeDumper.format(bytecode))
        println("CALL_FN argc via getter: " + bytecode.getIntOperand(1, 0))

        // Step through with tracer to observe stack effects across CALL_FN/RETURN
        v2.debug.Debug.tracer = v2.debug.CollectingTracer(v2.debug.TraceOptions(step = true, includeCalls = true))
        val vm = VM(hostFns, funcs)
        vm.begin(bytecode, mutableMapOf())
        // Step 1: PUSH "y"
        vm.step(1)
        println("\n-- After PUSH, snapshot --\n" + VM.prettySnapshot(vm.snapshotJson()))
        // Step 2: CALL_FN f -> switch into f bytecode
        vm.step(1)
        println("\n-- After CALL_FN switch, snapshot --\n" + VM.prettySnapshot(vm.snapshotJson()))
        // Now continue until either suspend or finish
        var result = try { vm.step(1000); vm.resultOrNull() } catch (e: Exception) { if (e is v2.vm.VMException.Abort) mapOf("__suspended" to true, "snapshot" to e.value) else throw e }
        if (result is Map<*, *> && result["__suspended"] == true) {
            val snap = result["snapshot"] as String
            println("\n-- Snapshot (pretty) --\n" + VM.prettySnapshot(snap))
            val vm2 = VM.restoreFromSnapshot(snap, hostFns, funcs)
            vm2.step(1000)
            println("\n-- After resume, result=${vm2.resultOrNull()} --")
        }
        v2.debug.Debug.tracer = null

        // Also compile FUNC f() body to inspect RETURN/SUSPEND shape
        val f = funcs["f"]!!
        val bodyIR: List<v2.ir.IRNode> = when (val b = f.body) {
            is v2.BlockBody -> v2.ir.ToIR(funcs, hostFns).compile(b.block.statements)
            is v2.ExprBody -> listOf(v2.ir.IRReturn(b.expr))
        }
        val fbc = Compiler(funcs, hostFns, useLocals = false).compile(bodyIR)
        println("\n-- FUNC f bytecode --\n" + BytecodeDumper.format(fbc))
    }
}
