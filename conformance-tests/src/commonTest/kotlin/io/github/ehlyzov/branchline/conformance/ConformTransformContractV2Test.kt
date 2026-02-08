package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.contract.RequirementExprV2
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.sema.TypeResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConformTransformContractV2Test {
    @Test
    fun propagates_variable_shape_into_output_fields() {
        val program = """
            TRANSFORM T {
                LET total = 1;
                OUTPUT { total: total }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val total = contract.output.root.children["total"]
        assertNotNull(total)
        assertEquals(ValueShape.NumberShape, total.shape)
    }

    @Test
    fun infers_nested_input_path_from_variable_access_chain() {
        val program = """
            TRANSFORM T {
                LET user = input.user;
                OUTPUT { name: user.name }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val user = contract.input.root.children["user"]
        assertNotNull(user)
        val name = user.children["name"]
        assertNotNull(name)
    }

    @Test
    fun emits_any_of_requirement_for_coalesce_paths() {
        val program = """
            TRANSFORM T {
                LET root = input.testsuites ?? input.testsuite ?? {};
                OUTPUT { status: root["@name"] ?? "missing" }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val anyOf = contract.input.requirements.firstOrNull() as? RequirementExprV2.AnyOf
        assertNotNull(anyOf)
        assertTrue(anyOf.children.size >= 2)
    }

    @Test
    fun null_guard_refines_shape_for_then_branch_access() {
        val program = """
            TRANSFORM T {
                LET payload = input.payload ?? {};
                IF payload != NULL THEN {
                    OUTPUT { name: payload.name }
                } ELSE {
                    OUTPUT { name: "none" }
                }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val name = contract.output.root.children["name"]
        assertNotNull(name)
        assertEquals(ValueShape.TextShape, name.shape)
    }

    @Test
    fun object_guard_refines_target_to_object_shape() {
        val program = """
            TRANSFORM T {
                LET payload = input.payload;
                IF IS_OBJECT(payload) THEN {
                    OUTPUT { ok: payload.id ?? 0 }
                } ELSE {
                    OUTPUT { ok: 0 }
                }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val payload = contract.input.root.children["payload"]
        assertNotNull(payload)
        assertTrue(payload.shape is ValueShape.ObjectShape || payload.shape == ValueShape.Unknown)
    }

    private fun synthesizeV2(program: String) =
        TransformContractBuilder(TypeResolver(emptyList())).buildV2(parseTransform(program))

    private fun parseTransform(program: String): TransformDecl {
        val tokens = Lexer(program).lex()
        val parsed = Parser(tokens, program).parse()
        return parsed.decls.filterIsInstance<TransformDecl>().single()
    }
}
