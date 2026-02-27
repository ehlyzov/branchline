package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.ConstraintExpr
import io.github.ehlyzov.branchline.contract.ContractEnforcer
import io.github.ehlyzov.branchline.contract.ContractObligation
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.ContractViolationException
import io.github.ehlyzov.branchline.contract.ContractViolationKind
import io.github.ehlyzov.branchline.contract.GuaranteeSchema
import io.github.ehlyzov.branchline.contract.Node
import io.github.ehlyzov.branchline.contract.NodeKind
import io.github.ehlyzov.branchline.contract.RequirementSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConformContractValidationTest {

    @Test
    fun warn_mode_reports_missing_required_fields() {
        val requirement = RequirementSchema(
            root = Node(
                required = true,
                kind = NodeKind.OBJECT,
                open = true,
                children = linkedMapOf(
                    "id" to Node(required = true, kind = NodeKind.NUMBER),
                ),
            ),
            obligations = emptyList(),
            opaqueRegions = emptyList(),
        )
        val violations = ContractEnforcer.enforceInput(ContractValidationMode.WARN, requirement, emptyMap<String, Any?>())
        assertTrue(violations.isNotEmpty())
        assertEquals(ContractViolationKind.MISSING_REQUIRED_PATH, violations.first().kind)
    }

    @Test
    fun warn_mode_reports_missing_required_any_of_group() {
        val requirement = RequirementSchema(
            root = Node(
                required = true,
                kind = NodeKind.OBJECT,
                open = true,
                children = linkedMapOf(
                    "testsuites" to Node(required = false, kind = NodeKind.ANY),
                    "testsuite" to Node(required = false, kind = NodeKind.ANY),
                ),
            ),
            obligations = listOf(
                ContractObligation(
                    expr = ConstraintExpr.OneOf(
                        listOf(
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                        ),
                    ),
                    ruleId = "required-any-of",
                ),
            ),
            opaqueRegions = emptyList(),
        )
        val violations = ContractEnforcer.enforceInput(ContractValidationMode.WARN, requirement, emptyMap<String, Any?>())
        assertEquals(1, violations.size)
        assertEquals(ContractViolationKind.MISSING_CONDITIONAL_GROUP, violations.first().kind)
    }

    @Test
    fun strict_mode_throws_when_required_any_of_group_is_missing() {
        val requirement = RequirementSchema(
            root = Node(
                required = true,
                kind = NodeKind.OBJECT,
                open = true,
                children = linkedMapOf(
                    "testsuites" to Node(required = false, kind = NodeKind.ANY),
                    "testsuite" to Node(required = false, kind = NodeKind.ANY),
                ),
            ),
            obligations = listOf(
                ContractObligation(
                    expr = ConstraintExpr.OneOf(
                        listOf(
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                        ),
                    ),
                    ruleId = "required-any-of",
                ),
            ),
            opaqueRegions = emptyList(),
        )
        assertFailsWith<ContractViolationException> {
            ContractEnforcer.enforceInput(ContractValidationMode.STRICT, requirement, emptyMap<String, Any?>())
        }
    }

    @Test
    fun required_any_of_group_accepts_present_non_null_alternative() {
        val requirement = RequirementSchema(
            root = Node(
                required = true,
                kind = NodeKind.OBJECT,
                open = true,
                children = linkedMapOf(
                    "testsuites" to Node(required = false, kind = NodeKind.ANY),
                    "testsuite" to Node(required = false, kind = NodeKind.ANY),
                ),
            ),
            obligations = listOf(
                ContractObligation(
                    expr = ConstraintExpr.OneOf(
                        listOf(
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuites")))),
                            ConstraintExpr.PathNonNull(AccessPath(listOf(AccessSegment.Field("testsuite")))),
                        ),
                    ),
                    ruleId = "required-any-of",
                ),
            ),
            opaqueRegions = emptyList(),
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
        val guarantee = GuaranteeSchema(
            root = Node(
                required = true,
                kind = NodeKind.OBJECT,
                open = false,
                children = linkedMapOf(
                    "status" to Node(required = true, kind = NodeKind.TEXT),
                ),
            ),
            obligations = emptyList(),
            mayEmitNull = false,
            opaqueRegions = emptyList(),
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
        val guarantee = GuaranteeSchema(
            root = Node(
                required = true,
                kind = NodeKind.ARRAY,
                element = Node(
                    required = true,
                    kind = NodeKind.OBJECT,
                    open = false,
                    children = linkedMapOf(
                        "ok" to Node(required = true, kind = NodeKind.BOOLEAN),
                    ),
                ),
            ),
            obligations = emptyList(),
            mayEmitNull = false,
            opaqueRegions = emptyList(),
        )
        val payload = listOf(
            mapOf("ok" to true),
            mapOf("ok" to false),
        )
        val violations = ContractEnforcer.enforceOutput(ContractValidationMode.WARN, guarantee, payload)
        assertTrue(violations.isEmpty())
    }
}
