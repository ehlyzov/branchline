package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CompilerAccessTest {
    private fun token(
        type: TokenType,
        lexeme: String = "",
        line: Int = 0,
        col: Int = 0
    ) = Token(type, lexeme, line, col)

    @Test
    fun `literal key emits ACCESS_STATIC`() {
        val baseTok = token(TokenType.IDENTIFIER, "obj")
        val keyTok = token(TokenType.STRING, "\"name\"")
        val expr = AccessExpr(
            base = IdentifierExpr("obj", baseTok),
            segs = listOf(AccessSeg.Dynamic(StringExpr("name", keyTok))),
            token = baseTok
        )
        val ir = IRExprStmt(expr)
        val compiler = Compiler()
        val bytecode = compiler.compile(listOf(ir))
        val instrs = bytecode.instructions
        assertEquals(3, instrs.size)
        assertEquals(Instruction.LOAD_VAR("obj"), instrs[0])
        assertEquals(Instruction.ACCESS_STATIC(ObjKey.Name("name")), instrs[1])
        assertEquals(Instruction.POP, instrs[2])
    }

    @Test
    fun `dynamic key emits ACCESS_DYNAMIC`() {
        val baseTok = token(TokenType.IDENTIFIER, "obj")
        val keyTok = token(TokenType.IDENTIFIER, "k")
        val expr = AccessExpr(
            base = IdentifierExpr("obj", baseTok),
            segs = listOf(AccessSeg.Dynamic(IdentifierExpr("k", keyTok))),
            token = baseTok
        )
        val ir = IRExprStmt(expr)
        val compiler = Compiler()
        val bytecode = compiler.compile(listOf(ir))
        val instrs = bytecode.instructions
        assertEquals(
            listOf(
                Instruction.LOAD_VAR("obj"),
                Instruction.LOAD_VAR("k"),
                Instruction.ACCESS_DYNAMIC,
                Instruction.POP
            ),
            instrs
        )
    }
}
