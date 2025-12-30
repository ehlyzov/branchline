package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import v2.Lexer
import v2.Parser
import v2.Program
import v2.sema.SemanticAnalyzer
import v2.sema.SemanticException

public class ConformDiagnosticsTest {

    @Test
    public fun contract_mismatches_emit_warnings() {
        val program = """
            TRANSFORM T(input: { id: number }) -> { id: number, status: string } {
                OUTPUT { id: input.id }
            }
        """.trimIndent()
        val parsed = parseProgram(program)
        val analyzer = SemanticAnalyzer()

        analyzer.analyze(parsed)

        val warnings = analyzer.warnings
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].message.contains("Output contract is missing required field 'status'"))
    }

    @Test
    public fun strict_contract_mismatch_raises_error() {
        val program = """
            TRANSFORM T(input: { id: number }) -> { id: number, status: string } {
                OUTPUT { id: input.id }
            }
        """.trimIndent()
        val parsed = parseProgram(program)
        val analyzer = SemanticAnalyzer(strictContracts = true)

        val error = assertFailsWith<SemanticException> {
            analyzer.analyze(parsed)
        }

        assertTrue(error.message?.contains("Output contract is missing required field 'status'") == true)
    }

    @Test
    public fun unknown_types_raise_semantic_error() {
        val program = """
            TRANSFORM T(input: Customer) -> { id: number } {
                OUTPUT { id: input.id }
            }
        """.trimIndent()
        val parsed = parseProgram(program)

        val error = assertFailsWith<SemanticException> {
            SemanticAnalyzer().analyze(parsed)
        }

        assertTrue(error.message?.contains("Unknown type 'Customer'") == true)
    }
}

private fun parseProgram(program: String): Program {
    val tokens = Lexer(program).lex()
    return Parser(tokens, program).parse()
}
