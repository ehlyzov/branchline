package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.*

class CallSuspendBytecodeTest {

    private fun parse(program: String): Program {
        val tokens = Lexer(program).lex()
        return Parser(tokens, program).parse()
    }

    @Test
    fun func_body_ir_and_bytecode_contains_suspend() {
        val program = """
            FUNC f() {
                LET a = 1;
                SUSPEND 0;
                RETURN a + 1;
            }
        """.trimIndent()

        val prog = parse(program)
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = emptyMap<String, (List<Any?>) -> Any?>()

        val f = funcs.getValue("f")
        val body = (f.body as BlockBody).block.statements

        // IR inspection
        val ir = ToIR(funcs, hostFns).compile(body)
        assertTrue(ir.any { it is IRLet && it.name == "a" })
        assertTrue(ir.any { it is IRExprStmt && (it.expr as? UnaryExpr)?.token?.type == TokenType.SUSPEND })
        assertTrue(ir.any { it is IRReturn })

        // Bytecode inspection
        val bc = Compiler(funcs, hostFns, useLocals = false).compile(ir)
        val ins = bc.instructions
        val hasSuspend = ins.any { it is Instruction.SUSPEND }
        val hasReturnValue = ins.any { it is Instruction.RETURN_VALUE }
        assertTrue(hasSuspend, "Function bytecode must contain SUSPEND")
        assertTrue(hasReturnValue, "Function bytecode must contain RETURN_VALUE")
        val idxSuspend = ins.indexOfFirst { it is Instruction.SUSPEND }
        val idxReturn = ins.indexOfFirst { it is Instruction.RETURN_VALUE }
        assertTrue(idxSuspend in 0 until idxReturn, "SUSPEND should precede RETURN_VALUE in function body")
    }

    @Test
    fun transform_calls_func_and_emits_output() {
        val program = """
            FUNC f() { RETURN 7; }
            TRANSFORM T { OUTPUT { y: f() }
            }
        """.trimIndent()

        val prog = parse(program)
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = emptyMap<String, (List<Any?>) -> Any?>()
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()

        val tir = ToIR(funcs, hostFns).compile(transform.body.statements)
        // Expect a single OUTPUT with one field
        assertTrue(tir.any { it is IROutput })

        val bc = Compiler(funcs, hostFns).compile(tir)
        val ins = bc.instructions

        // Expect: PUSH "y"; CALL_FN f 0; OUTPUT_1
        val idxPush = ins.indexOfFirst { it is Instruction.PUSH }
        val idxCallFn = ins.indexOfFirst { it is Instruction.CALL_FN && it.name == "f" }
        val idxOutput1 = ins.indexOfFirst { it === Instruction.OUTPUT_1 }
        assertTrue(idxPush >= 0, "Expected PUSH of key 'y'")
        assertTrue(idxCallFn > idxPush, "CALL_FN should follow key PUSH")
        assertTrue(idxOutput1 > idxCallFn, "OUTPUT_1 should follow CALL_FN")
    }

    @Test
    fun suspend_in_transform_ir_and_bytecode() {
        val program = """
            TRANSFORM T { SUSPEND 0;
                OUTPUT { ok: true }
            }
        """.trimIndent()

        val prog = parse(program)
        val funcs = prog.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val hostFns = emptyMap<String, (List<Any?>) -> Any?>()
        val transform = prog.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)

        // IR should contain an IRExprStmt with UnaryExpr(SUSPEND)
        assertTrue(ir.any { it is IRExprStmt && (it.expr as? UnaryExpr)?.token?.type == TokenType.SUSPEND })

        val bc = Compiler(funcs, hostFns).compile(ir)
        val ins = bc.instructions
        val names = ins.map {
            when (it) {
                is Instruction.PUSH -> "PUSH"
                is Instruction.CALL_FN -> "CALL_FN"
                Instruction.OUTPUT_1 -> "OUTPUT_1"
                is Instruction.OUTPUT -> "OUTPUT ${it.fieldCount}"
                Instruction.SUSPEND -> "SUSPEND"
                else -> it::class.simpleName ?: "?"
            }
        }
        assertTrue(names.contains("SUSPEND"), "Transform bytecode must contain SUSPEND")
        // Ensure OUTPUT appears after SUSPEND
        val iS = names.indexOf("SUSPEND")
        val iO = names.indexOfFirst { it.startsWith("OUTPUT") }
        assertTrue(iO > iS, "OUTPUT should come after SUSPEND in transform")
    }
}

