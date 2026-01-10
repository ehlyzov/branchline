package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.IROutput
import io.github.ehlyzov.branchline.ir.buildRunnerFromIRMP

class InterpSmokeJsTest {
    @Test
    fun output_literal_object() {
        val tok = Token(TokenType.RETURN, "", 1, 1)
        val fields = listOf(
            LiteralProperty(
                ObjKey.Name("v"),
                NumberLiteral(I32(1), tok)
            )
        )
        val ir = listOf(IROutput(fields))
        val runner = buildRunnerFromIRMP(ir)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 1), out)
    }
}

