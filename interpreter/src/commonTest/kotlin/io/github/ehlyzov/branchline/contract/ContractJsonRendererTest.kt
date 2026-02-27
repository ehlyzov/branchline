package io.github.ehlyzov.branchline.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContractJsonRendererTest {
    @Test
    fun json_is_deterministic_for_same_contract() {
        val contract = sampleContract()
        val first = ContractJsonRenderer.renderSchemaGuarantee(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        val second = ContractJsonRenderer.renderSchemaGuarantee(
            guarantee = contract.output,
            includeSpans = false,
            pretty = true,
        )
        assertEquals(first, second)
        val parsed = Json.parseToJsonElement(first).jsonObject
        assertTrue(!parsed.containsKey("version"))
    }

    @Test
    fun json_encodes_array_element_object_children_explicitly() {
        val contract = sampleContract()
        val rendered = ContractJsonRenderer.renderSchemaGuarantee(
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
    fun json_gates_origin_and_spans_to_debug_output() {
        val contract = sampleContract()
        val standard = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuarantee(contract.output, includeSpans = false, pretty = true),
        ).jsonObject
        val debug = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuarantee(contract.output, includeSpans = true, pretty = true),
        ).jsonObject
        val standardRoot = standard["root"]?.jsonObject ?: error("missing standard root")
        val debugRoot = debug["root"]?.jsonObject ?: error("missing debug root")
        assertFalse(standardRoot.containsKey("origin"))
        assertEquals("OUTPUT", debugRoot["origin"]?.toString()?.trim('"'))
    }

    @Test
    fun json_renders_obligations_and_domains() {
        val contract = sampleContract()
        val rendered = ContractJsonRenderer.renderSchemaGuarantee(
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
    fun json_hides_obligation_metadata_without_debug() {
        val contract = sampleContract()
        val standard = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuarantee(contract.output, includeSpans = false, pretty = true),
        ).jsonObject
        val debug = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuarantee(contract.output, includeSpans = true, pretty = true),
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

    private fun sampleContract(): TransformContract {
        val inputRoot = Node(
            required = true,
            kind = NodeKind.OBJECT,
            open = true,
            children = linkedMapOf(
                "testsuites" to Node(required = false, kind = NodeKind.ANY),
                "testsuite" to Node(required = false, kind = NodeKind.ANY),
            ),
        )
        val outputRoot = Node(
            required = true,
            kind = NodeKind.OBJECT,
            open = false,
            origin = OriginKind.OUTPUT,
            children = linkedMapOf(
                "status" to Node(
                    required = true,
                    kind = NodeKind.TEXT,
                    origin = OriginKind.OUTPUT,
                    domains = listOf(ValueDomain.EnumText(listOf("error", "passing", "failing"))),
                ),
                "suites" to Node(
                    required = true,
                    kind = NodeKind.ARRAY,
                    origin = OriginKind.OUTPUT,
                    element = Node(
                        required = true,
                        kind = NodeKind.OBJECT,
                        open = false,
                        children = linkedMapOf(
                            "name" to Node(required = true, kind = NodeKind.TEXT, origin = OriginKind.OUTPUT),
                            "tests" to Node(required = true, kind = NodeKind.NUMBER, origin = OriginKind.OUTPUT),
                            "failures" to Node(required = true, kind = NodeKind.NUMBER, origin = OriginKind.OUTPUT),
                            "errors" to Node(required = true, kind = NodeKind.NUMBER, origin = OriginKind.OUTPUT),
                            "skipped" to Node(required = true, kind = NodeKind.NUMBER, origin = OriginKind.OUTPUT),
                        ),
                        origin = OriginKind.OUTPUT,
                    ),
                ),
            ),
        )
        return TransformContract(
            input = RequirementSchema(
                root = inputRoot,
                obligations = listOf(
                    ContractObligation(
                        expr = ConstraintExpr.OneOf(
                            listOf(
                                ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                                ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                            ),
                        ),
                    ),
                ),
                opaqueRegions = emptyList(),
            ),
            output = GuaranteeSchema(
                root = outputRoot,
                obligations = listOf(
                    ContractObligation(
                        expr = ConstraintExpr.ForAll(
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
