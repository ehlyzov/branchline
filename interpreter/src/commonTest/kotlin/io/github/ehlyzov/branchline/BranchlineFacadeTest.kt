package io.github.ehlyzov.branchline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFails
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

public class BranchlineFacadeTest {
    @Test
    public fun inspectReturnsContractsAndFeatureSummary() {
        val program = """
            TRANSFORM Main {
                LET greeting = "Hello, " + input.name;
                OUTPUT { greeting: greeting }
            }
        """.trimIndent()

        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = program,
                includeWitness = true,
            )
        )

        assertTrue(result.success)
        assertTrue(result.diagnostics.isEmpty())
        assertEquals(1, result.transforms.size)
        assertEquals("Main", result.transforms.single().name)
        assertEquals("inferred", result.transforms.single().contractSource)
        assertNotNull(result.transforms.single().inferredContract)
        assertNotNull(result.transforms.single().mergedContract)
        assertNotNull(result.transforms.single().witness)
        assertTrue(result.featureUsage.features.contains("let"))
        assertTrue(result.featureUsage.features.contains("output"))
        assertEquals(BranchlineSubsetCompatibility.COMPATIBLE, result.subsetCompatibility)
        assertNull(result.normalizedSource)
    }

    @Test
    public fun inspectMarksSharedProgramsAsIncompatibleWithAiSubset() {
        val program = """
            SHARED cache SINGLE

            TRANSFORM Main {
                LET value = AWAIT cache.user;
                OUTPUT { value: value }
            }
        """.trimIndent()

        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(programText = program)
        )

        assertTrue(result.success)
        assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, result.subsetCompatibility)
        val unsupported = result.diagnostics.filter { it.code == "unsupported_in_ai_subset" }
        assertTrue(unsupported.isNotEmpty())
        assertTrue(unsupported.any { it.message.contains("SHARED") })
        assertTrue(unsupported.any { it.message.contains("AWAIT") })
        assertTrue(unsupported.all { it.category == BranchlineDiagnosticCategory.UNSUPPORTED_SUBSET })
        assertTrue(unsupported.all { it.payload?.operation == "ai-subset-check" })
    }

    @Test
    public fun inspectSelectsNamedTransform() {
        val program = """
            TRANSFORM First {
                OUTPUT { value: input.first }
            }

            TRANSFORM Second {
                OUTPUT { value: input.second }
            }
        """.trimIndent()

        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = program,
                transformName = "Second",
            )
        )

        assertTrue(result.success)
        assertEquals(listOf("Second"), result.transforms.map { it.name })
    }

    @Test
    public fun inspectReportsParseDiagnosticsWithSpanAndCode() {
        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = "TRANSFORM Main { OUTPUT { greeting: } }",
            )
        )

        assertTrue(!result.success)
        assertEquals(1, result.diagnostics.size)
        val diagnostic = result.diagnostics.single()
        assertEquals("parse_error", diagnostic.code)
        assertEquals(BranchlineDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(BranchlineDiagnosticCategory.SYNTAX, diagnostic.category)
        assertEquals("parse", diagnostic.payload?.operation)
        assertNotNull(diagnostic.span)
        assertTrue(diagnostic.message.isNotBlank())
    }

    @Test
    public fun inspectReportsSemanticDiagnosticsWithSpanAndCode() {
        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = """
                    TRANSFORM Main {
                        OUTPUT { greeting: unknownName }
                    }
                """.trimIndent(),
            )
        )

        assertTrue(!result.success)
        assertEquals(1, result.diagnostics.size)
        val diagnostic = result.diagnostics.single()
        assertEquals("semantic_error", diagnostic.code)
        assertEquals(BranchlineDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(BranchlineDiagnosticCategory.SEMANTIC, diagnostic.category)
        assertEquals("analyze", diagnostic.payload?.operation)
        assertNotNull(diagnostic.span)
        assertTrue(diagnostic.message.contains("unknownName"))
    }

    @Test
    public fun inspectIncludesDebugMetadataOnlyWhenRequested() {
        val program = """
            TRANSFORM Main {
                OUTPUT { greeting: "hi " + input.name }
            }
        """.trimIndent()

        val standard = BranchlineFacade.inspect(
            BranchlineInspectRequest(programText = program)
        )
        val debug = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = program,
                includeDebugMetadata = true,
            )
        )

        assertTrue(standard.success)
        assertTrue(debug.success)

        val standardOutputRoot = standard.transforms.single().mergedContract
            .jsonObject["output"]!!.jsonObject["root"]!!.jsonObject
        val debugOutputRoot = debug.transforms.single().mergedContract
            .jsonObject["output"]!!.jsonObject["root"]!!.jsonObject

        assertTrue(!standardOutputRoot.containsKey("origin"))
        assertEquals("OUTPUT", debugOutputRoot["origin"]?.jsonPrimitive?.content)
    }

    @Test
    public fun inspectCanRenderCanonicalJsonPayloadForCli() {
        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = """
                    TRANSFORM Main {
                        OUTPUT { greeting: "hi " + input.name }
                    }
                """.trimIndent(),
                includeWitness = true,
            )
        )

        assertTrue(result.success)
        val payload = Json.parseToJsonElement(result.contractsJson()).jsonObject
        assertEquals("Main", payload["name"]?.jsonPrimitive?.content)
        val witness = payload["witness"]?.jsonObject
        assertNotNull(witness)
        assertTrue(witness["input"] != null)
        assertTrue(witness["output"] != null)
    }

    @Test
    public fun inspectRendersMultipleTransformsPayloadWhenSelectionIsBroad() {
        val result = BranchlineFacade.inspect(
            BranchlineInspectRequest(
                programText = """
                    TRANSFORM First { OUTPUT { value: input.first } }
                    TRANSFORM Second { OUTPUT { value: input.second } }
                """.trimIndent(),
            )
        )

        assertTrue(result.success)
        val payload = Json.parseToJsonElement(result.contractsJson()).jsonObject
        val transforms = payload["transforms"]?.jsonArray
        assertNotNull(transforms)
        assertEquals(2, transforms.size)
    }

    @Test
    public fun inspectJsonSerializesDiagnosticTaxonomyAndPayloads() {
        val result = BranchlineInspectResult(
            success = false,
            transforms = emptyList(),
            diagnostics = listOf(
                BranchlineDiagnostic(
                    code = "normalization_unsupported_node",
                    message = "Cannot canonicalize node",
                    severity = BranchlineDiagnosticSeverity.WARNING,
                    category = BranchlineDiagnosticCategory.NORMALIZATION,
                    payload = BranchlineDiagnosticPayload(
                        operation = "normalize",
                        actualKind = "OutputDecl",
                        hint = "Keep original source.",
                    ),
                ),
                BranchlineDiagnostic(
                    code = "mutation_target_missing",
                    message = "'+=' variable 'items' not found",
                    severity = BranchlineDiagnosticSeverity.ERROR,
                    category = BranchlineDiagnosticCategory.RUNTIME,
                    payload = BranchlineDiagnosticPayload(
                        operation = "+=",
                        targetPath = "items",
                        expectedKind = "declared local list, number, or string",
                        actualKind = "missing",
                        hint = "Declare the accumulator with LET first.",
                    ),
                ),
                BranchlineDiagnostic(
                    code = "contract_output_mismatch",
                    message = "Output contract mismatch",
                    severity = BranchlineDiagnosticSeverity.ERROR,
                    category = BranchlineDiagnosticCategory.CONTRACT,
                    payload = BranchlineDiagnosticPayload(
                        operation = "validate-output-contract",
                        targetPath = "root.total",
                        expectedKind = "number",
                        actualKind = "text",
                        expected = buildJsonObject { put("kind", JsonPrimitive("number")) },
                        actual = buildJsonObject { put("kind", JsonPrimitive("text")) },
                        hint = "Convert the value with NUMBER(...) or update the contract.",
                    ),
                ),
            ),
            warnings = emptyList(),
            featureUsage = BranchlineFeatureUsageSummary(emptyList()),
            subsetCompatibility = BranchlineSubsetCompatibility.UNKNOWN,
            normalizedSource = null,
        )

        val diagnostics = Json.parseToJsonElement(result.inspectJson()).jsonObject["diagnostics"]!!.jsonArray
        val normalization = diagnostics[0].jsonObject
        assertEquals("normalization", normalization["category"]?.jsonPrimitive?.content)
        assertEquals("normalize", normalization["payload"]?.jsonObject?.get("operation")?.jsonPrimitive?.content)

        val runtime = diagnostics[1].jsonObject
        assertEquals("runtime", runtime["category"]?.jsonPrimitive?.content)
        assertEquals("+=", runtime["payload"]?.jsonObject?.get("operation")?.jsonPrimitive?.content)
        assertEquals("items", runtime["payload"]?.jsonObject?.get("targetPath")?.jsonPrimitive?.content)

        val contract = diagnostics[2].jsonObject
        assertEquals("contract", contract["category"]?.jsonPrimitive?.content)
        assertEquals(
            "number",
            contract["payload"]?.jsonObject?.get("expected")?.jsonObject?.get("kind")?.jsonPrimitive?.content,
        )
        assertEquals(
            "text",
            contract["payload"]?.jsonObject?.get("actual")?.jsonObject?.get("kind")?.jsonPrimitive?.content,
        )
    }
    @Test
    public fun inspectRejectsAmbientCallsThroughFunctionWrappersAndDynamicKeys() {
        val examples = listOf(
            "TRANSFORM Main { OUTPUT { value: NOW() } }" to "NOW",
            "TRANSFORM Main { OUTPUT { value: RANDOM() } }" to "RANDOM",
            "FUNC clock() = NOW(); FUNC wrapped() = clock(); TRANSFORM Main { OUTPUT { value: wrapped() } }" to "NOW",
            "FUNC roll() { RETURN RANDOM(); } TRANSFORM Main { OUTPUT { value: roll() } }" to "RANDOM",
            "TRANSFORM Main { LET f = () -> RANDOM(); OUTPUT { value: f() } }" to "RANDOM",
            "TRANSFORM Main { OUTPUT { value: input[NOW()] } }" to "NOW",
        )
        for ((source, feature) in examples) {
            val result = BranchlineFacade.inspect(BranchlineInspectRequest(source, includeNormalizedSource = true))
            assertTrue(result.success, result.diagnostics.toString())
            assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, result.subsetCompatibility, source)
            assertNull(result.normalizedSource)
            val blocker = result.diagnostics.single { it.code == "unsupported_in_ai_subset" }
            assertEquals(feature, blocker.payload?.actualKind)
            assertEquals("ai-subset-check", blocker.payload?.operation)
            assertEquals(BranchlineDiagnosticCategory.UNSUPPORTED_SUBSET, blocker.category)
            assertNotNull(blocker.span)
        }
    }

    @Test
    public fun inspectAllowsPureShadowsAndUnreachableAmbientFunctions() {
        val source = """
            FUNC impure() = NOW();
            FUNC unused() { LET impure = 0; SET impure = 1; RETURN impure; }
            FUNC pure(impure) = impure();
            TRANSFORM Main {
                LET NOW = () -> 7;
                LET object = { RANDOM: () -> 9 };
                OUTPUT { first: pure(NOW), second: object.RANDOM() }
            }
        """.trimIndent()
        val result = BranchlineFacade.inspect(BranchlineInspectRequest(source))
        assertTrue(result.success, result.diagnostics.toString())
        assertEquals(BranchlineSubsetCompatibility.COMPATIBLE, result.subsetCompatibility)
        assertEquals(mapOf("first" to 7, "second" to 9), buildRunnerFromProgramMP(source)(emptyMap()))
    }

    @Test
    public fun inspectDoesNotTreatBareDeclaredOrHostNamesAsFunctionValues() {
        // Named FUNC and host names use direct-call dispatch; identifier evaluation only reads locals.
        for (body in listOf(
            "LET f = stamp; OUTPUT { value: f() }",
            "OUTPUT { value: MAP([1], stamp) }",
            "LET f = NOW; OUTPUT { value: f() }",
            "OUTPUT { value: MAP([1], RANDOM) }",
        )) {
            val source = "FUNC stamp() = NOW(); TRANSFORM Main { $body }"
            val result = BranchlineFacade.inspect(BranchlineInspectRequest(source))
            assertTrue(result.success, result.diagnostics.toString())
            assertEquals(BranchlineSubsetCompatibility.COMPATIBLE, result.subsetCompatibility)
            assertFails { buildRunnerFromProgramMP(source)(emptyMap()) }
        }
        val closure = "FUNC stamp() = NOW(); TRANSFORM Main { OUTPUT { value: MAP([1], (x) -> stamp()) } }"
        val result = BranchlineFacade.inspect(BranchlineInspectRequest(closure))
        assertTrue(result.success, result.diagnostics.toString())
        assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, result.subsetCompatibility)
    }

    @Test
    public fun inspectDirectAmbientCallsFollowHostFirstRuntimeResolution() {
        for (declaration in listOf("FUNC NOW() = 7;", "")) {
            val source = "$declaration TRANSFORM Main { LET NOW = () -> 8; OUTPUT { value: NOW() } }"
            val result = BranchlineFacade.inspect(BranchlineInspectRequest(source))
            assertTrue(result.success, result.diagnostics.toString())
            assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, result.subsetCompatibility)
            assertEquals("NOW", result.diagnostics.single { it.code == "unsupported_in_ai_subset" }.payload?.actualKind)
            val output = buildRunnerFromProgramMP(source)(emptyMap()) as Map<*, *>
            assertTrue(output["value"] is String)
        }
    }

    @Test
    public fun inspectChecksFunctionFallbackForScalarAndMutableShadows() {
        val programs = listOf(
            "FUNC roll() = RANDOM(); TRANSFORM Main { LET roll = 0; OUTPUT { value: roll() } }",
            "FUNC roll() = RANDOM(); TRANSFORM Main { LET roll = () -> 7; IF TRUE THEN { SET roll = 0; } OUTPUT { value: roll() } }",
            "FUNC impure() = NOW(); TRANSFORM Main { LET impure = 0; SET impure = () -> 7; IF TRUE THEN { SET impure = 0; } ELSE {} OUTPUT { value: impure() } }",
            "FUNC roll() = RANDOM(); FUNC invoke(roll) = roll(); TRANSFORM Main { LET first = invoke(() -> 7); OUTPUT { value: invoke(0) } }",
        )
        for (source in programs) {
            val result = BranchlineFacade.inspect(BranchlineInspectRequest(source))
            assertTrue(result.success, result.diagnostics.toString())
            assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, result.subsetCompatibility)
            val output = buildRunnerFromProgramMP(source)(emptyMap()) as Map<*, *>
            assertTrue(output["value"] is Number || output["value"] is String)
        }
    }

    @Test
    public fun inspectTraversesRecursiveFunctionsOnceAndHonorsTransformSelection() {
        val source = """
            FUNC recurse(x) = IF x THEN recurse(FALSE) ELSE RANDOM();
            TRANSFORM Pure { OUTPUT { value: input.value } }
            TRANSFORM Impure { OUTPUT { value: recurse(TRUE) } }
        """.trimIndent()
        val pure = BranchlineFacade.inspect(BranchlineInspectRequest(source, transformName = "Pure"))
        assertTrue(pure.success, pure.diagnostics.toString())
        assertEquals(BranchlineSubsetCompatibility.COMPATIBLE, pure.subsetCompatibility)
        val impure = BranchlineFacade.inspect(BranchlineInspectRequest(source, transformName = "Impure"))
        assertTrue(impure.success, impure.diagnostics.toString())
        assertEquals(1, impure.diagnostics.count { it.code == "unsupported_in_ai_subset" })
    }

}
