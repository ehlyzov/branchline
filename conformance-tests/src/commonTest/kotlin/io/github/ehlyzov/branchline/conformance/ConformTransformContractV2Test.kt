package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.ContractSource
import io.github.ehlyzov.branchline.contract.ContractFitterV2
import io.github.ehlyzov.branchline.contract.RequirementExprV2
import io.github.ehlyzov.branchline.contract.RuntimeContractExampleV2
import io.github.ehlyzov.branchline.contract.RuntimeFitResultV2
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.contract.TransformContractV2
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.sema.BinaryTypeEvalResult
import io.github.ehlyzov.branchline.sema.BinaryTypeEvalRule
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
                LET payload = input.payload;
                IF payload != NULL THEN {
                    OUTPUT { name: TEXT(payload.name) }
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

    @Test
    fun cast_functions_push_expected_shape_to_input_provenance() {
        val program = """
            TRANSFORM T {
                LET total = NUMBER(input.metrics.total ?? 0);
                OUTPUT { total: total }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val metrics = contract.input.root.children["metrics"]
        assertNotNull(metrics)
        val total = metrics.children["total"]
        assertNotNull(total)
        assertEquals(ValueShape.NumberShape, total.shape)
    }

    @Test
    fun set_updates_local_object_shape_used_in_output() {
        val program = """
            TRANSFORM T {
                LET summary = {};
                SET summary.total = NUMBER(input.total ?? 0);
                OUTPUT { summary: summary }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val summary = contract.output.root.children["summary"]
        assertNotNull(summary)
        val total = summary.children["total"]
        assertNotNull(total)
        assertEquals(ValueShape.NumberShape, total.shape)
    }

    @Test
    fun listify_and_append_summaries_produce_array_element_shape() {
        val program = """
            TRANSFORM T {
                LET base = LISTIFY(input.payload);
                LET out = APPEND(base, { id: "x" });
                OUTPUT { out: out }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val out = contract.output.root.children["out"]
        assertNotNull(out)
        val arrayShape = out.shape as? ValueShape.ArrayShape
        assertNotNull(arrayShape)
        assertTrue(arrayShape.element is ValueShape.ObjectShape || arrayShape.element == ValueShape.Unknown)
    }

    @Test
    fun get_summary_tracks_static_key_path() {
        val program = """
            TRANSFORM T {
                LET count = NUMBER(GET(input.metrics, "count", 0));
                OUTPUT { count: count }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val metrics = contract.input.root.children["metrics"]
        assertNotNull(metrics)
        val count = metrics.children["count"]
        assertNotNull(count)
        assertEquals(ValueShape.NumberShape, count.shape)
    }

    @Test
    fun wildcard_output_signature_uses_hybrid_seeded_inference() {
        val program = """
            TRANSFORM T(input: { payload?: { name: text } }) -> _ {
                LET payload = input.payload;
                IF payload != NULL THEN {
                    OUTPUT { name: payload.name }
                } ELSE {
                    OUTPUT { name: "none" }
                }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        assertEquals(ContractSource.INFERRED, contract.source)
        assertTrue(contract.metadata.inference.inputTypeSeeded)
        val name = contract.output.root.children["name"]
        assertNotNull(name)
        assertEquals(ValueShape.TextShape, name.shape)
    }

    @Test
    fun wildcard_output_alias_resolving_to_any_uses_hybrid_routing() {
        val program = """
            TYPE AnyOutput = UNION any
            TRANSFORM T(input: { value: number }) -> AnyOutput {
                OUTPUT { value: input.value }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        assertEquals(ContractSource.INFERRED, contract.source)
        assertTrue(contract.metadata.inference.inputTypeSeeded)
        val value = contract.output.root.children["value"]
        assertNotNull(value)
        assertEquals(ValueShape.NumberShape, value.shape)
    }

    @Test
    fun wildcard_hybrid_mode_uses_typed_seed_for_get_static_key() {
        val program = """
            TRANSFORM T(input: { metrics: { count: number } }) -> _ {
                OUTPUT { count: GET(input.metrics, "count", 0) }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val count = contract.output.root.children["count"]
        assertNotNull(count)
        assertEquals(ValueShape.NumberShape, count.shape)
    }

    @Test
    fun non_wildcard_output_signature_stays_explicit() {
        val program = """
            TRANSFORM T(input: { payload?: { name: text } }) -> { name: text } {
                OUTPUT { name: input.payload.name ?? "none" }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        assertEquals(ContractSource.EXPLICIT, contract.source)
        assertTrue(!contract.metadata.inference.inputTypeSeeded)
    }

    @Test
    fun wildcard_seeded_inference_keeps_dynamic_access_conservative() {
        val program = """
            TRANSFORM T(input: { payload: { name: text } }) -> _ {
                LET key = "name";
                OUTPUT { value: input.payload[key] }
            }
        """.trimIndent()
        val contract = synthesizeV2(program)
        val value = contract.output.root.children["value"]
        assertNotNull(value)
        assertEquals(ValueShape.Unknown, value.shape)
        assertTrue(
            contract.input.opaqueRegions.any { region ->
                region.path.segments.size == 2 &&
                    (region.path.segments[0] as? AccessSegment.Field)?.name == "payload" &&
                    region.path.segments[1] == AccessSegment.Dynamic
            },
        )
    }

    @Test
    fun custom_binary_type_eval_rule_can_shape_expression_outputs() {
        val program = """
            TRANSFORM T {
                LET sum = input.left + input.right;
                OUTPUT { sum: sum }
            }
        """.trimIndent()
        val builder = TransformContractBuilder(
            typeResolver = TypeResolver(emptyList()),
            binaryTypeEvalRules = listOf(
                BinaryTypeEvalRule { input ->
                    if (input.operator != TokenType.PLUS) return@BinaryTypeEvalRule null
                    BinaryTypeEvalResult(
                        shape = ValueShape.NumberShape,
                        ruleId = "custom-plus",
                        enforceOperandShape = ValueShape.NumberShape,
                    )
                },
            ),
        )
        val contract = builder.buildV2(parseTransform(program))
        val sum = contract.output.root.children["sum"]
        assertNotNull(sum)
        assertEquals(ValueShape.NumberShape, sum.shape)
    }

    @Test
    fun runtime_fit_extension_point_accepts_observed_examples() {
        val program = """
            TRANSFORM T {
                OUTPUT { value: input.value }
            }
        """.trimIndent()
        val builder = TransformContractBuilder(
            typeResolver = TypeResolver(emptyList()),
            contractFitter = ContractFitterV2 { staticContract, examples ->
                RuntimeFitResultV2(
                    contract = staticContract.copy(
                        metadata = staticContract.metadata.copy(
                            runtimeFit = staticContract.metadata.runtimeFit.copy(
                                strategy = "fitted-examples-${examples.size}",
                            ),
                        ),
                    ),
                )
            },
        )
        val contract = builder.buildV2(
            transform = parseTransform(program),
            runtimeExamples = listOf(
                RuntimeContractExampleV2(
                    input = mapOf("value" to 1),
                    output = mapOf("value" to 1),
                    label = "seed",
                ),
            ),
        )
        assertEquals("fitted-examples-1", contract.metadata.runtimeFit.strategy)
    }

    private fun synthesizeV2(program: String) =
        buildV2Contract(program)

    private fun parseTransform(program: String): TransformDecl = parseProgram(program)
        .decls
        .filterIsInstance<TransformDecl>()
        .single()

    private fun buildV2Contract(program: String): TransformContractV2 {
        val parsed = parseProgram(program)
        val typeDecls = parsed.decls.filterIsInstance<TypeDecl>()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        return TransformContractBuilder(TypeResolver(typeDecls)).buildV2(transform)
    }

    private fun parseProgram(program: String): io.github.ehlyzov.branchline.Program {
        val tokens = Lexer(program).lex()
        return Parser(tokens, program).parse()
    }
}
