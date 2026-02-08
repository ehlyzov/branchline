package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.sema.TransformShapeSynthesizer

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

    @Test
    fun coalesce_fallbacks_emit_required_any_of_group() {
        val program = """
            TRANSFORM T {
                LET root = input.testsuites ?? input.testsuite ?? {};
                OUTPUT { name: root["@name"] ?? "none" }
            }
        """.trimIndent()
        val contract = synthesizeContract(program)

        val testsuites = contract.input.fields["testsuites"]
        val testsuite = contract.input.fields["testsuite"]
        assertNotNull(testsuites)
        assertNotNull(testsuite)
        assertEquals(false, testsuites.required)
        assertEquals(false, testsuite.required)
        assertEquals(1, contract.input.requiredAnyOf.size)

        val alternatives = contract.input.requiredAnyOf.first().alternatives
        assertEquals(2, alternatives.size)
        val first = alternatives[0].segments.singleOrNull() as? AccessSegment.Field
        val second = alternatives[1].segments.singleOrNull() as? AccessSegment.Field
        assertEquals("testsuites", first?.name)
        assertEquals("testsuite", second?.name)
    }

    private fun synthesizeContract(program: String) = TransformShapeSynthesizer().synthesize(parseTransform(program))

    private fun parseTransform(program: String): TransformDecl {
        val tokens = Lexer(program).lex()
        val parsed = Parser(tokens, program).parse()
        return parsed.decls.filterIsInstance<TransformDecl>().single()
    }
}
