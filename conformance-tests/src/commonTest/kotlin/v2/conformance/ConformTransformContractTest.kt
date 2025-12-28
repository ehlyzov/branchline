package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import v2.Lexer
import v2.Parser
import v2.TransformDecl
import v2.contract.ValueShape
import v2.sema.TransformShapeSynthesizer

class ConformTransformContractTest {

    @Test
    fun infers_input_and_output_fields() {
        val program = """
            TRANSFORM T {
                OUTPUT { name: input.user.name, age: input.user.age }
            }
        """.trimIndent()
        val contract = synthesizeContract(program)
        val userField = contract.input.fields["user"]
        assertNotNull(userField)
        assertTrue(userField.required)

        val nameField = contract.output.fields["name"]
        val ageField = contract.output.fields["age"]
        assertNotNull(nameField)
        assertNotNull(ageField)
        assertTrue(nameField.required)
        assertTrue(ageField.required)
    }

    @Test
    fun infers_union_shapes_across_branches() {
        val program = """
            TRANSFORM T {
                IF input.flag THEN {
                    OUTPUT { value: 1 }
                } ELSE {
                    OUTPUT { value: "one" }
                }
            }
        """.trimIndent()
        val contract = synthesizeContract(program)
        val field = contract.output.fields["value"]
        assertNotNull(field)
        val shape = field.shape
        assertTrue(shape is ValueShape.Union)
        val options = shape.options.toSet()
        assertTrue(options.contains(ValueShape.NumberShape))
        assertTrue(options.contains(ValueShape.TextShape))
    }

    @Test
    fun infers_optional_output_field_when_missing_in_branch() {
        val program = """
            TRANSFORM T {
                IF input.flag THEN {
                    OUTPUT { value: 1 }
                } ELSE {
                    OUTPUT { }
                }
            }
        """.trimIndent()
        val contract = synthesizeContract(program)
        val field = contract.output.fields["value"]
        assertNotNull(field)
        assertEquals(false, field.required)
    }

    @Test
    fun records_dynamic_input_accesses() {
        val program = """
            TRANSFORM T {
                LET key = "id";
                OUTPUT { value: input[key] }
            }
        """.trimIndent()
        val contract = synthesizeContract(program)
        assertTrue(contract.input.dynamicAccess.isNotEmpty())
    }

    private fun synthesizeContract(program: String) = TransformShapeSynthesizer().synthesize(parseTransform(program))

    private fun parseTransform(program: String): TransformDecl {
        val tokens = Lexer(program).lex()
        val parsed = Parser(tokens, program).parse()
        return parsed.decls.filterIsInstance<TransformDecl>().single()
    }
}
