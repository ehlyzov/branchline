package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VMTest {

    @Test
    fun `test basic arithmetic operations`() {
        // Test: 5 + 3 = 8
        val instructions = listOf(
            Instruction.PUSH(5),
            Instruction.PUSH(3),
            Instruction.ADD
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()
        val result = vm.execute(bytecode)

        assertEquals(8, result)
    }

    @Test
    fun `test variable operations`() {
        // Test: let x = 10; x + 5
        val instructions = listOf(
            Instruction.PUSH(10),
            Instruction.STORE_VAR("x"),
            Instruction.LOAD_VAR("x"),
            Instruction.PUSH(5),
            Instruction.ADD
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()
        val env = mutableMapOf<String, Any?>()
        val result = vm.execute(bytecode, env)

        assertEquals(15, result)
        assertEquals(10, env["x"])
    }

    @Test
    fun `test object creation`() {
        // Test: create object with key-value pairs
        val instructions = listOf(
            Instruction.PUSH("value1"), // value for key1
            Instruction.PUSH("key1"), // key1
            Instruction.PUSH("value2"), // value for key2
            Instruction.PUSH("key2"), // key2
            Instruction.MAKE_OBJECT(2) // 2 key-value pairs
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()
        val result = vm.execute(bytecode) as Map<*, *>

        assertEquals("value1", result["key1"])
        assertEquals("value2", result["key2"])
        assertEquals(2, result.size)
    }

    @Test
    fun `test array creation`() {
        // Test: create array [1, 2, 3]
        val instructions = listOf(
            Instruction.PUSH(1),
            Instruction.PUSH(2),
            Instruction.PUSH(3),
            Instruction.MAKE_ARRAY(3)
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()
        val result = vm.execute(bytecode) as List<*>

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `test conditional jump`() {
        // Test: if true then push 1 else push 0
        val instructions = listOf(
            Instruction.PUSH(true),
            Instruction.JUMP_IF_FALSE(6), // Jump to instruction 6 if false
            Instruction.PUSH(1), // instruction 2
            Instruction.JUMP(7), // instruction 3: Jump to end
            Instruction.PUSH(0) // instruction 4-5: else branch
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()
        val result = vm.execute(bytecode)

        assertEquals(1, result)
    }

    @Test
    fun `test compiler with simple IR`() {
        // Test compilation of simple LET statement
        val expr = NumberLiteral(I32(42), Token(TokenType.NUMBER, "42", 0, 0))
        val letNode = IRLet("x", expr)
        val outputNode = IRExprOutput(IdentifierExpr("x", Token(TokenType.IDENTIFIER, "x", 0, 0)))

        val compiler = Compiler()
        val bytecode = compiler.compile(listOf(letNode, outputNode))

        assertTrue(bytecode.instructions.isNotEmpty())
        assertTrue(bytecode.instructions.any { it is Instruction.PUSH })
        assertTrue(bytecode.instructions.any { it is Instruction.STORE_VAR })
    }

    @Test
    fun `test VM integration with existing system`() {
        // Test that VMExec can fallback to original implementation
        val expr = NumberLiteral(I32(100), Token(TokenType.NUMBER, "100", 0, 0))
        val letNode = IRLet("result", expr)

        val eval: (Expr, MutableMap<String, Any?>) -> Any? = { expr, env ->
            when (expr) {
                is NumberLiteral -> {
                    val value = expr.value
                    when (value) {
                        is I32 -> value.v
                        else -> 0
                    }
                }
                is IdentifierExpr -> env[expr.name]
                else -> null
            }
        }

        val vmExec = VMExec(listOf(letNode), eval)
        val env = mutableMapOf<String, Any?>()

        // This should either work with VM or fallback to original
        val result = vmExec.run(env)

        // Either way, the variable should be set
        assertEquals(100, env["result"])
    }

    @Test
    fun `test VMUtil validation`() {
        // Test IR validation for VM compatibility
        val simpleExpr = NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 0, 0))
        val letNode = IRLet("x", simpleExpr)

        val validation = VMUtil.validateIRForVM(listOf(letNode))
        assertTrue(validation.isValid)
        assertTrue(validation.unsupportedFeatures.isEmpty())
    }

    @Test
    fun `test performance estimation`() {
        // Test performance estimation heuristics
        val expr = NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 0, 0))
        val ifNode = IRIf(
            BoolExpr(true, Token(TokenType.TRUE, "true", 0, 0)),
            listOf(IRLet("x", expr)),
            null
        )

        val estimate = VMUtil.estimatePerformanceBenefit(listOf(ifNode))
        assertTrue(estimate.expectedSpeedup >= 1.0)
        assertTrue(estimate.confidence >= 0.0 && estimate.confidence <= 1.0)
    }

    @Test
    fun `test stack overflow protection`() {
        // Test that stack overflow is properly handled
        val manyPushInstructions = (1..10001).map { Instruction.PUSH(it) }
        val bytecode = Bytecode.fromInstructions(manyPushInstructions)
        val vm = VM()

        assertFailsWith<VMException.StackOverflow> { vm.execute(bytecode) }
    }

    @Test
    fun `test VM exception handling`() {
        // Test division by zero
        val instructions = listOf(
            Instruction.PUSH(5),
            Instruction.PUSH(0),
            Instruction.DIV
        )
        val bytecode = Bytecode.fromInstructions(instructions)
        val vm = VM()

        assertFailsWith<VMException.DivisionByZero> { vm.execute(bytecode) }
    }

    @Test
    fun `array comprehension evaluates correctly`() {
        // Parse expression and compile
        fun parseExpr(src: String): Expr {
            val program = Parser(Lexer("FUNC f() = $src ;").lex()).parse()
            val func = program.decls[0] as FuncDecl
            return (func.body as ExprBody).expr
        }

        val expr = parseExpr("[x FOR EACH x IN list WHERE x > 1]")
        val ir = IRExprStmt(expr)
        val compiler = Compiler()
        val bytecodeFull = compiler.compile(listOf(ir))
        // Drop trailing POP from ExprStmt to get expression result
        val bytecode = Bytecode.fromInstructions(bytecodeFull.instructions.dropLast(1), bytecodeFull.constants, bytecodeFull.debugInfo)

        val vm = VM()
        val env = mutableMapOf<String, Any?>("list" to listOf(0, 2, 3))
        val result = vm.execute(bytecode, env)

        assertEquals(listOf(2, 3), result)
    }
}
