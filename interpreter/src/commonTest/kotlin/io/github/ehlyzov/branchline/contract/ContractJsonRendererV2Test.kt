package io.github.ehlyzov.branchline.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ContractJsonRendererV2Test {
    @Test
    fun v2_json_is_deterministic_for_same_contract() {
        val contract = TransformContractV2Adapter.fromV1(sampleContract())
        val first = ContractJsonRenderer.renderSchemaRequirementV2(
            requirement = contract.input,
            includeSpans = false,
            pretty = true,
        )
        val second = ContractJsonRenderer.renderSchemaRequirementV2(
            requirement = contract.input,
            includeSpans = false,
            pretty = true,
        )
        assertEquals(first, second)
        val parsed = Json.parseToJsonElement(first).jsonObject
        assertEquals("v2", parsed["version"]?.toString()?.trim('"'))
    }

    @Test
    fun v1_to_v2_round_trip_preserves_overlapping_fields() {
        val original = sampleContract()
        val v2 = TransformContractV2Adapter.fromV1(original)
        val roundTrip = TransformContractV2Adapter.toV1(v2)
        assertEquals(original.input.fields.keys, roundTrip.input.fields.keys)
        assertEquals(original.output.fields.keys, roundTrip.output.fields.keys)
        assertEquals(original.input.requiredAnyOf.size, roundTrip.input.requiredAnyOf.size)
        assertTrue(roundTrip.input.open)
    }

    private fun sampleContract(): TransformContract {
        val input = SchemaRequirement(
            fields = linkedMapOf(
                "testsuites" to FieldConstraint(
                    required = false,
                    shape = ValueShape.ObjectShape(
                        schema = SchemaGuarantee(
                            fields = linkedMapOf(
                                "testsuite" to FieldShape(
                                    required = false,
                                    shape = ValueShape.ArrayShape(ValueShape.Unknown),
                                    origin = OriginKind.OUTPUT,
                                ),
                            ),
                            mayEmitNull = false,
                            dynamicFields = emptyList(),
                        ),
                        closed = false,
                    ),
                    sourceSpans = emptyList(),
                ),
                "testsuite" to FieldConstraint(
                    required = false,
                    shape = ValueShape.ObjectShape(
                        schema = SchemaGuarantee(
                            fields = linkedMapOf(),
                            mayEmitNull = false,
                            dynamicFields = emptyList(),
                        ),
                        closed = false,
                    ),
                    sourceSpans = emptyList(),
                ),
            ),
            open = true,
            dynamicAccess = emptyList(),
            requiredAnyOf = listOf(
                RequiredAnyOfGroup(
                    alternatives = listOf(
                        AccessPath(listOf(AccessSegment.Field("testsuites"))),
                        AccessPath(listOf(AccessSegment.Field("testsuite"))),
                    ),
                ),
            ),
        )
        val output = SchemaGuarantee(
            fields = linkedMapOf(
                "status" to FieldShape(
                    required = true,
                    shape = ValueShape.TextShape,
                    origin = OriginKind.OUTPUT,
                ),
            ),
            mayEmitNull = false,
            dynamicFields = emptyList(),
        )
        return TransformContract(input = input, output = output, source = ContractSource.INFERRED)
    }
}
