package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.ConstraintExprV3
import io.github.ehlyzov.branchline.contract.ContractEnforcerV3
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.ContractValidatorV3
import io.github.ehlyzov.branchline.contract.ContractViolationExceptionV3
import io.github.ehlyzov.branchline.contract.ContractWitnessGeneratorV3
import io.github.ehlyzov.branchline.contract.NodeKindV3
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.contract.TransformContractV3
import io.github.ehlyzov.branchline.contract.ValueDomainV3
import io.github.ehlyzov.branchline.sema.TypeResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConformTransformContractV3Test {
    @Test
    fun junit_badge_summary_v3_has_strict_suites_shape_and_status_enum() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        val suites = contract.output.root.children["suites"]
        assertNotNull(suites)
        assertEquals(NodeKindV3.ARRAY, suites.kind)
        val element = suites.element
        assertNotNull(element)
        assertEquals(NodeKindV3.OBJECT, element.kind)
        assertTrue(element.children.containsKey("name"))
        assertTrue(element.children.containsKey("tests"))
        assertTrue(element.children.containsKey("failures"))
        assertTrue(element.children.containsKey("errors"))
        assertTrue(element.children.containsKey("skipped"))
        assertTrue(element.children.values.all { child -> child.required })

        val status = contract.output.root.children["status"]
        assertNotNull(status)
        val enumDomain = status.domains.filterIsInstance<ValueDomainV3.EnumText>().firstOrNull()
        assertNotNull(enumDomain)
        assertEquals(setOf("error", "passing", "failing"), enumDomain.values.toSet())

        val hasDuplicatedForAll = contract.output.obligations.any { obligation ->
            obligation.expr is ConstraintExprV3.ForAll
        }
        val hasDuplicatedStatusDomain = contract.output.obligations.any { obligation ->
            val valueDomain = obligation.expr as? ConstraintExprV3.ValueDomain ?: return@any false
            valueDomain.path.segments == listOf(AccessSegment.Field("status"))
        }
        assertTrue(!hasDuplicatedForAll)
        assertTrue(!hasDuplicatedStatusDomain)
    }

    @Test
    fun junit_badge_summary_v3_does_not_leak_loop_local_suite_into_input_requirements() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        val allPaths = collectRequirementPaths(contract)
        val leaked = allPaths.any { path ->
            val first = path.segments.firstOrNull() as? AccessSegment.Field
            first?.name == "suite"
        }
        assertTrue(!leaked, "loop-local variable 'suite' must not appear in input obligations")
    }

    @Test
    fun junit_badge_summary_real_input_and_output_pass_v3_strict_validation() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        val input = junitBadgeSummaryInput()
        val output = mapOf(
            "status" to "failing",
            "totals" to mapOf(
                "tests" to 12,
                "failures" to 1,
                "errors" to 0,
                "skipped" to 2,
            ),
            "suites" to listOf(
                mapOf(
                    "name" to "api.tests.UserTest",
                    "tests" to 5,
                    "failures" to 1,
                    "errors" to 0,
                    "skipped" to 1,
                ),
                mapOf(
                    "name" to "web.tests.CartTest",
                    "tests" to 7,
                    "failures" to 0,
                    "errors" to 0,
                    "skipped" to 1,
                ),
            ),
        )

        val inputViolations = ContractEnforcerV3.enforceInput(ContractValidationMode.STRICT, contract.input, input)
        val outputViolations = ContractEnforcerV3.enforceOutput(ContractValidationMode.STRICT, contract.output, output)
        assertTrue(inputViolations.isEmpty())
        assertTrue(outputViolations.isEmpty())
    }

    @Test
    fun v3_strict_rejects_missing_testsuites_and_testsuite_input() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        assertFailsWith<ContractViolationExceptionV3> {
            ContractEnforcerV3.enforceInput(
                mode = ContractValidationMode.STRICT,
                requirement = contract.input,
                value = emptyMap<String, Any?>(),
            )
        }
    }

    @Test
    fun v3_strict_rejects_invalid_status_enum_and_empty_suite_elements() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        assertFailsWith<ContractViolationExceptionV3> {
            ContractEnforcerV3.enforceOutput(
                mode = ContractValidationMode.STRICT,
                guarantee = contract.output,
                value = mapOf(
                    "status" to "synthetic-status",
                    "totals" to mapOf(
                        "tests" to 0,
                        "failures" to 0,
                        "errors" to 0,
                        "skipped" to 0,
                    ),
                    "suites" to listOf(
                        emptyMap<String, Any?>(),
                    ),
                ),
            )
        }
    }

    @Test
    fun v3_witness_generator_produces_contract_valid_samples() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        val witness = ContractWitnessGeneratorV3.generate(contract)
        val validator = ContractValidatorV3()
        val inputViolations = validator.validateInput(contract.input, witness.input).violations
        val outputViolations = validator.validateOutput(contract.output, witness.output).violations
        assertTrue(inputViolations.isEmpty())
        assertTrue(outputViolations.isEmpty())
    }

    @Test
    fun junit_badge_summary_inferred_input_fields_are_optional_and_discoverable() {
        val contract = synthesizeV3(junitBadgeSummaryProgram())
        val testsuites = contract.input.root.children["testsuites"]
        val testsuite = contract.input.root.children["testsuite"]
        assertNotNull(testsuites)
        assertNotNull(testsuite)
        assertTrue(!testsuites.required)
        assertTrue(!testsuite.required)

        val testsuitesSuite = testsuites.children["testsuite"]
        assertNotNull(testsuitesSuite)
        assertTrue(!testsuitesSuite.required)
        assertTrue(testsuitesSuite.children.containsKey("@tests"))
        assertTrue(testsuitesSuite.children.containsKey("@failures"))
        assertTrue(testsuitesSuite.children.containsKey("@errors"))
        assertTrue(testsuitesSuite.children.containsKey("@skipped"))
        assertTrue(testsuitesSuite.children.containsKey("@name"))
        assertTrue(testsuitesSuite.children.containsKey("@package"))
        assertTrue(testsuitesSuite.children.values.all { child -> !child.required })
    }

    private fun synthesizeV3(program: String): TransformContractV3 {
        val parsed = parseProgram(program)
        val typeDecls = parsed.decls.filterIsInstance<TypeDecl>()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        return TransformContractBuilder(TypeResolver(typeDecls)).buildV3(transform)
    }

    private fun parseProgram(program: String): io.github.ehlyzov.branchline.Program {
        val tokens = Lexer(program).lex()
        return Parser(tokens, program).parse()
    }

    private fun collectRequirementPaths(contract: TransformContractV3): List<AccessPath> {
        val paths = mutableListOf<AccessPath>()
        for (obligation in contract.input.obligations) {
            collectPathsFromExpr(obligation.expr, paths)
        }
        return paths
    }

    private fun collectPathsFromExpr(expr: ConstraintExprV3, out: MutableList<AccessPath>) {
        when (expr) {
            is ConstraintExprV3.PathPresent -> out += expr.path
            is ConstraintExprV3.PathNonNull -> out += expr.path
            is ConstraintExprV3.OneOf -> expr.children.forEach { child -> collectPathsFromExpr(child, out) }
            is ConstraintExprV3.AllOf -> expr.children.forEach { child -> collectPathsFromExpr(child, out) }
            is ConstraintExprV3.ForAll -> out += expr.path
            is ConstraintExprV3.Exists -> out += expr.path
            is ConstraintExprV3.ValueDomain -> out += expr.path
        }
    }

    private fun junitBadgeSummaryProgram(): String = """
        TRANSFORM JunitBadgeSummary {
            LET root = input.testsuites ?? input.testsuite ?? {};
            LET suitesRaw = root.testsuite ?? root;
            LET suites = IF suitesRaw == NULL THEN []
                ELSE IF IS_OBJECT(suitesRaw) THEN [suitesRaw]
                ELSE suitesRaw;

            LET totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
            LET normalizedSuites = [];

            FOR suite IN suites {
                IF suite != NULL THEN {
                    LET stats = {
                        name: suite["@name"] ?? suite["@package"] ?? "unnamed suite",
                        tests: NUMBER(suite["@tests"] ?? 0),
                        failures: NUMBER(suite["@failures"] ?? 0),
                        errors: NUMBER(suite["@errors"] ?? 0),
                        skipped: NUMBER(suite["@skipped"] ?? 0)
                    };

                    SET totals.tests = totals.tests + stats.tests;
                    SET totals.failures = totals.failures + stats.failures;
                    SET totals.errors = totals.errors + stats.errors;
                    SET totals.skipped = totals.skipped + stats.skipped;

                    SET normalizedSuites = APPEND(normalizedSuites, stats);
                }
            }

            LET failed = totals.failures + totals.errors;
            LET status = IF totals.tests == 0 THEN "error"
                ELSE IF failed == 0 THEN "passing"
                ELSE "failing";

            OUTPUT {
                status: status,
                totals: totals,
                suites: normalizedSuites
            }
        }
    """.trimIndent()

    private fun junitBadgeSummaryInput(): Map<String, Any?> = mapOf(
        "testsuites" to mapOf(
            "@name" to "All",
            "@tests" to 12,
            "@failures" to 1,
            "@errors" to 0,
            "@skipped" to 2,
            "testsuite" to listOf(
                mapOf(
                    "@name" to "api.tests.UserTest",
                    "@tests" to 5,
                    "@failures" to 1,
                    "@errors" to 0,
                    "@skipped" to 1,
                ),
                mapOf(
                    "@name" to "web.tests.CartTest",
                    "@tests" to 7,
                    "@failures" to 0,
                    "@errors" to 0,
                    "@skipped" to 1,
                ),
            ),
        ),
    )
}
