package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.ContractEnforcerV2
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.ContractViolationExceptionV2
import io.github.ehlyzov.branchline.contract.ContractViolationKindV2
import io.github.ehlyzov.branchline.contract.GuaranteeNodeV2
import io.github.ehlyzov.branchline.contract.GuaranteeSchemaV2
import io.github.ehlyzov.branchline.contract.OriginKind
import io.github.ehlyzov.branchline.contract.RequirementExprV2
import io.github.ehlyzov.branchline.contract.RequirementNodeV2
import io.github.ehlyzov.branchline.contract.RequirementSchemaV2
import io.github.ehlyzov.branchline.contract.ValueShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConformContractValidationV2Test {
    @Test
    fun warn_mode_reports_missing_nested_required_path() {
        val requirement = RequirementSchemaV2(
            root = RequirementNodeV2(
                required = true,
                shape = ValueShape.ObjectShape(emptyGuarantee(), closed = false),
                open = true,
                children = linkedMapOf(
                    "user" to RequirementNodeV2(
                        required = true,
                        shape = ValueShape.ObjectShape(emptyGuarantee(), closed = false),
                        open = true,
                        children = linkedMapOf(
                            "name" to RequirementNodeV2(
                                required = true,
                                shape = ValueShape.TextShape,
                                open = true,
                                children = linkedMapOf(),
                                evidence = emptyList(),
                            ),
                        ),
                        evidence = emptyList(),
                    ),
                ),
                evidence = emptyList(),
            ),
            requirements = emptyList(),
            opaqueRegions = emptyList(),
        )
        val violations = ContractEnforcerV2.enforceInput(
            ContractValidationMode.WARN,
            requirement,
            mapOf("user" to emptyMap<String, Any?>()),
        )
        assertTrue(violations.isNotEmpty())
        assertEquals(ContractViolationKindV2.MISSING_REQUIRED_PATH, violations.first().kind)
    }

    @Test
    fun strict_mode_throws_for_failed_any_of_requirement() {
        val requirement = RequirementSchemaV2(
            root = RequirementNodeV2(
                required = true,
                shape = ValueShape.ObjectShape(emptyGuarantee(), closed = false),
                open = true,
                children = linkedMapOf(),
                evidence = emptyList(),
            ),
            requirements = listOf(
                RequirementExprV2.AnyOf(
                    children = listOf(
                        RequirementExprV2.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                        RequirementExprV2.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                    ),
                ),
            ),
            opaqueRegions = emptyList(),
        )
        assertFailsWith<ContractViolationExceptionV2> {
            ContractEnforcerV2.enforceInput(
                ContractValidationMode.STRICT,
                requirement,
                emptyMap<String, Any?>(),
            )
        }
    }

    @Test
    fun output_shape_mismatch_reports_v2_kind() {
        val guarantee = GuaranteeSchemaV2(
            root = GuaranteeNodeV2(
                required = true,
                shape = ValueShape.ObjectShape(
                    schema = io.github.ehlyzov.branchline.contract.SchemaGuarantee(
                        fields = linkedMapOf(
                            "status" to io.github.ehlyzov.branchline.contract.FieldShape(
                                required = true,
                                shape = ValueShape.TextShape,
                                origin = OriginKind.OUTPUT,
                            ),
                        ),
                        mayEmitNull = false,
                        dynamicFields = emptyList(),
                    ),
                    closed = false,
                ),
                open = true,
                origin = OriginKind.OUTPUT,
                children = linkedMapOf(
                    "status" to GuaranteeNodeV2(
                        required = true,
                        shape = ValueShape.TextShape,
                        open = true,
                        origin = OriginKind.OUTPUT,
                        children = linkedMapOf(),
                        evidence = emptyList(),
                    ),
                ),
                evidence = emptyList(),
            ),
            mayEmitNull = false,
            opaqueRegions = emptyList(),
        )
        val violations = ContractEnforcerV2.enforceOutput(
            ContractValidationMode.WARN,
            guarantee,
            mapOf("status" to 123),
        )
        assertTrue(violations.any { violation -> violation.kind == ContractViolationKindV2.SHAPE_MISMATCH })
    }

    private fun emptyGuarantee() = io.github.ehlyzov.branchline.contract.SchemaGuarantee(
        fields = linkedMapOf(),
        mayEmitNull = false,
        dynamicFields = emptyList(),
    )
}

