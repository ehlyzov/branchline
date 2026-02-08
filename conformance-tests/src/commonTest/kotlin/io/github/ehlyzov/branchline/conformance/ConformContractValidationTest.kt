package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.ehlyzov.branchline.contract.ContractEnforcer
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.ContractViolationException
import io.github.ehlyzov.branchline.contract.ContractViolationKind
import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.FieldConstraint
import io.github.ehlyzov.branchline.contract.FieldShape
import io.github.ehlyzov.branchline.contract.RequiredAnyOfGroup
import io.github.ehlyzov.branchline.contract.SchemaGuarantee
import io.github.ehlyzov.branchline.contract.SchemaRequirement
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.contract.OriginKind

class ConformContractValidationTest {

    @Test
    fun warn_mode_reports_missing_required_fields() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "id" to FieldConstraint(
                    required = true,
                    shape = ValueShape.NumberShape,
                    sourceSpans = emptyList(),
                ),
            ),
            open = true,
            dynamicAccess = emptyList(),
            requiredAnyOf = emptyList(),
        )
        val violations = ContractEnforcer.enforceInput(ContractValidationMode.WARN, requirement, emptyMap<String, Any?>())
        assertTrue(violations.isNotEmpty())
        assertEquals(ContractViolationKind.MISSING_FIELD, violations.first().kind)
    }

    @Test
    fun warn_mode_reports_missing_required_any_of_group() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "testsuites" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
                    sourceSpans = emptyList(),
                ),
                "testsuite" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
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
        val violations = ContractEnforcer.enforceInput(ContractValidationMode.WARN, requirement, emptyMap<String, Any?>())
        assertEquals(1, violations.size)
        assertEquals(ContractViolationKind.MISSING_ANY_OF_GROUP, violations.first().kind)
    }

    @Test
    fun strict_mode_throws_when_required_any_of_group_is_missing() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "testsuites" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
                    sourceSpans = emptyList(),
                ),
                "testsuite" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
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
        assertFailsWith<ContractViolationException> {
            ContractEnforcer.enforceInput(ContractValidationMode.STRICT, requirement, emptyMap<String, Any?>())
        }
    }

    @Test
    fun required_any_of_group_accepts_present_non_null_alternative() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "testsuites" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
                    sourceSpans = emptyList(),
                ),
                "testsuite" to FieldConstraint(
                    required = false,
                    shape = ValueShape.Unknown,
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
        val violations = ContractEnforcer.enforceInput(
            ContractValidationMode.WARN,
            requirement,
            mapOf("testsuites" to mapOf("name" to "suite")),
        )
        assertTrue(violations.isEmpty())
    }

    @Test
    fun strict_mode_throws_on_type_mismatch() {
        val guarantee = SchemaGuarantee(
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
        assertFailsWith<ContractViolationException> {
            ContractEnforcer.enforceOutput(
                ContractValidationMode.STRICT,
                guarantee,
                mapOf("status" to 123),
            )
        }
    }

    @Test
    fun validates_list_of_outputs() {
        val guarantee = SchemaGuarantee(
            fields = linkedMapOf(
                "ok" to FieldShape(
                    required = true,
                    shape = ValueShape.BooleanShape,
                    origin = OriginKind.OUTPUT,
                ),
            ),
            mayEmitNull = false,
            dynamicFields = emptyList(),
        )
        val payload = listOf(
            mapOf("ok" to true),
            mapOf("ok" to false),
        )
        val violations = ContractEnforcer.enforceOutput(ContractValidationMode.WARN, guarantee, payload)
        assertTrue(violations.isEmpty())
    }
}
