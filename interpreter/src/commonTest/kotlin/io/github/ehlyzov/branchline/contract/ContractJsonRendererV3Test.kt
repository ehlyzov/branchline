package io.github.ehlyzov.branchline.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContractJsonRendererV3Test {
    @Test
    fun v3_json_is_deterministic_for_same_contract() {
        val contract = sampleContractV3()
        val first = ContractJsonRenderer.renderSchemaGuaranteeV3(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        val second = ContractJsonRenderer.renderSchemaGuaranteeV3(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        assertEquals(first, second)
        val parsed = Json.parseToJsonElement(first).jsonObject
        assertEquals("v3", parsed["version"]?.toString()?.trim('"'))
    }

    @Test
    fun v3_json_encodes_array_element_object_children_explicitly() {
        val contract = sampleContractV3()
        val rendered = ContractJsonRenderer.renderSchemaGuaranteeV3(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        val json = Json.parseToJsonElement(rendered).jsonObject
        val root = json["root"]?.jsonObject ?: error("missing root")
        val suites = root["children"]?.jsonObject?.get("suites")?.jsonObject ?: error("missing suites")
        assertEquals("array", suites["kind"]?.toString()?.trim('"'))
        val element = suites["element"]?.jsonObject ?: error("missing element")
        assertEquals("object", element["kind"]?.toString()?.trim('"'))
        val elementChildren = element["children"]?.jsonObject ?: error("missing element children")
        assertTrue(elementChildren.containsKey("name"))
        assertTrue(elementChildren.containsKey("tests"))
        assertTrue(elementChildren.containsKey("failures"))
        assertTrue(elementChildren.containsKey("errors"))
        assertTrue(elementChildren.containsKey("skipped"))
    }

    @Test
    fun v3_json_gates_origin_and_spans_to_debug_output() {
        val contract = sampleContractV3()
        val standard = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV3(contract.output, includeSpans = false, pretty = true),
        ).jsonObject
        val debug = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV3(contract.output, includeSpans = true, pretty = true),
        ).jsonObject
        val standardRoot = standard["root"]?.jsonObject ?: error("missing standard root")
        val debugRoot = debug["root"]?.jsonObject ?: error("missing debug root")
        assertFalse(standardRoot.containsKey("origin"))
        assertEquals("OUTPUT", debugRoot["origin"]?.toString()?.trim('"'))
    }

    @Test
    fun v3_json_renders_obligations_and_domains() {
        val contract = sampleContractV3()
        val rendered = ContractJsonRenderer.renderSchemaGuaranteeV3(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        val json = Json.parseToJsonElement(rendered).jsonObject
        val obligations = json["obligations"]?.jsonArray ?: error("missing obligations")
        assertTrue(obligations.isNotEmpty())
        val firstExpr = obligations.first().jsonObject["expr"]?.jsonObject ?: error("missing expr")
        assertNotNull(firstExpr["kind"])
    }

    @Test
    fun v3_json_hides_obligation_metadata_without_debug() {
        val contract = sampleContractV3()
        val standard = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV3(contract.output, includeSpans = false, pretty = true),
        ).jsonObject
        val debug = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV3(contract.output, includeSpans = true, pretty = true),
        ).jsonObject

        val standardObligation = standard["obligations"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("missing standard obligation")
        val debugObligation = debug["obligations"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("missing debug obligation")

        assertTrue(!standardObligation.containsKey("confidence"))
        assertTrue(!standardObligation.containsKey("ruleId"))
        assertTrue(!standardObligation.containsKey("heuristic"))
        assertTrue(debugObligation.containsKey("confidence"))
        assertTrue(debugObligation.containsKey("ruleId"))
        assertTrue(debugObligation.containsKey("heuristic"))
    }

    private fun sampleContractV3(): TransformContractV3 {
        val inputRoot = NodeV3(
            required = true,
            kind = NodeKindV3.OBJECT,
            open = true,
            children = linkedMapOf(
                "testsuites" to NodeV3(required = false, kind = NodeKindV3.ANY),
                "testsuite" to NodeV3(required = false, kind = NodeKindV3.ANY),
            ),
        )
        val outputRoot = NodeV3(
            required = true,
            kind = NodeKindV3.OBJECT,
            open = false,
            origin = OriginKind.OUTPUT,
            children = linkedMapOf(
                "status" to NodeV3(
                    required = true,
                    kind = NodeKindV3.TEXT,
                    origin = OriginKind.OUTPUT,
                    domains = listOf(ValueDomainV3.EnumText(listOf("error", "passing", "failing"))),
                ),
                "suites" to NodeV3(
                    required = true,
                    kind = NodeKindV3.ARRAY,
                    origin = OriginKind.OUTPUT,
                    element = NodeV3(
                        required = true,
                        kind = NodeKindV3.OBJECT,
                        open = false,
                        children = linkedMapOf(
                            "name" to NodeV3(required = true, kind = NodeKindV3.TEXT, origin = OriginKind.OUTPUT),
                            "tests" to NodeV3(required = true, kind = NodeKindV3.NUMBER, origin = OriginKind.OUTPUT),
                            "failures" to NodeV3(required = true, kind = NodeKindV3.NUMBER, origin = OriginKind.OUTPUT),
                            "errors" to NodeV3(required = true, kind = NodeKindV3.NUMBER, origin = OriginKind.OUTPUT),
                            "skipped" to NodeV3(required = true, kind = NodeKindV3.NUMBER, origin = OriginKind.OUTPUT),
                        ),
                        origin = OriginKind.OUTPUT,
                    ),
                ),
            ),
        )
        return TransformContractV3(
            input = RequirementSchemaV3(
                root = inputRoot,
                obligations = listOf(
                    ContractObligationV3(
                        expr = ConstraintExprV3.OneOf(
                            listOf(
                                ConstraintExprV3.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                                ConstraintExprV3.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                            ),
                        ),
                    ),
                ),
                opaqueRegions = emptyList(),
            ),
            output = GuaranteeSchemaV3(
                root = outputRoot,
                obligations = listOf(
                    ContractObligationV3(
                        expr = ConstraintExprV3.ForAll(
                            path = AccessPath(listOf(AccessSegment.Field("suites"))),
                            requiredFields = listOf("name", "tests", "failures", "errors", "skipped"),
                        ),
                    ),
                ),
                mayEmitNull = false,
                opaqueRegions = emptyList(),
            ),
            source = ContractSource.INFERRED,
        )
    }
}
