package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.IROutput
import kotlin.test.Test
import kotlin.test.assertTrue

class OutputSpecializationCompilerTest {

    @Test
    fun `compiler emits OUTPUT_1 for single-field outputs`() {
        val node = IROutput(listOf(LiteralProperty(ObjKey.Name("a"), NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 0, 0)))))
        val bc = Compiler().compile(listOf(node))
        assertTrue(bc.instructions.any { it === Instruction.OUTPUT_1 })
    }

    @Test
    fun `compiler emits OUTPUT_2 for two-field outputs`() {
        val node = IROutput(
            listOf(
                LiteralProperty(ObjKey.Name("a"), NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 0, 0))),
                LiteralProperty(ObjKey.Name("b"), NumberLiteral(I32(2), Token(TokenType.NUMBER, "2", 0, 1))),
            )
        )
        val bc = Compiler().compile(listOf(node))
        assertTrue(bc.instructions.any { it === Instruction.OUTPUT_2 })
    }
}
