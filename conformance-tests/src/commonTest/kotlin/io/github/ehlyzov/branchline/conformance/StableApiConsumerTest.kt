package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.BranchlineDiagnostic
import io.github.ehlyzov.branchline.BranchlineDiagnosticCategory
import io.github.ehlyzov.branchline.BranchlineDiagnosticPayload
import io.github.ehlyzov.branchline.BranchlineDiagnosticSeverity
import io.github.ehlyzov.branchline.BranchlineFacade
import io.github.ehlyzov.branchline.BranchlineFeatureUsageSummary
import io.github.ehlyzov.branchline.BranchlineInspectRequest
import io.github.ehlyzov.branchline.BranchlineInspectResult
import io.github.ehlyzov.branchline.BranchlineInspectTransform
import io.github.ehlyzov.branchline.BranchlineSourceSpan
import io.github.ehlyzov.branchline.BranchlineSubsetCompatibility
import io.github.ehlyzov.branchline.cli.BranchlineCli
import io.github.ehlyzov.branchline.cli.CliCommand
import io.github.ehlyzov.branchline.cli.ExitCode
import io.github.ehlyzov.branchline.cli.PlatformKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import playground.PlaygroundFacade
import playground.PlaygroundInspectResult
import playground.PlaygroundResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A source consumer of module dependencies, with no AST/IR/VM test helpers. */
class StableApiConsumerTest {
    private val program = """
        TRANSFORM Main {
            OUTPUT { greeting: "Hello, " + input.name, total: input.count + 1 }
        }
    """.trimIndent()
    private val input = """{"name":"Ada","count":2}"""
    private val expected = Json.parseToJsonElement("""{"greeting":"Hello, Ada","total":3}""")

    @Test
    fun executionUsesTheDocumentedPackageDefaultsAndIndependentExpectedValue() {
        val result: PlaygroundResult = PlaygroundFacade.run(program = program, inputJson = input)
        assertTrue(result.success, result.errorMessage)
        assertEquals(expected, Json.parseToJsonElement(assertNotNull(result.outputJson)))
        assertNull(result.errorMessage)
        assertNull(result.line)
        assertNull(result.column)
        assertNull(result.explainJson)
        assertNull(result.explainHuman)
        assertNull(result.inputContractJson)
        assertNull(result.outputContractJson)
        assertNull(result.contractSource)
        assertNull(result.contractWarnings)
        val body = "OUTPUT { greeting: \"Hello, \" + input.name, total: input.count + 1 }"
        assertTrue(PlaygroundFacade.isBodyOnlyProgram(program = body))
        assertFalse(PlaygroundFacade.isBodyOnlyProgram(program = program))
        assertEquals(expected, Json.parseToJsonElement(assertNotNull(PlaygroundFacade.run(body, input).outputJson)))
    }

    @Test
    fun contractAndOutputModesHaveDocumentedDefaultsAliasesAndFallbacks() {
        fun run(mode: String, format: String): PlaygroundResult = PlaygroundFacade.runWithContracts(
            program = program,
            inputJson = input,
            enableTracing = false,
            includeContracts = true,
            contractsMode = mode,
            includeContractSpans = false,
            outputFormat = format,
        )
        for (mode in listOf("off", "warn", "strict", " WARNING ", "error", "", "unrecognized-mode")) {
            val result = run(mode, "json-compact")
            assertTrue(result.success, "$mode: ${result.errorMessage}")
            assertEquals(expected, Json.parseToJsonElement(assertNotNull(result.outputJson)))
            assertNotNull(result.inputContractJson)
            assertNotNull(result.outputContractJson)
            assertEquals("inferred", result.contractSource)
            assertNull(result.contractWarnings)
        }
        assertEquals(run("off", "json").outputJson, run("off", "unknown-format").outputJson)
        assertEquals(run("off", "json-compact").outputJson, run("off", "JSON_COMPACT").outputJson)
        assertEquals(run("off", "json-canonical").outputJson, run("off", "JSON_CANONICAL").outputJson)
        // Explicit input validation is independent of the executable output.
        val contractProgram = "TRANSFORM Main(input: { required: string }) -> { ok: boolean } { OUTPUT { ok: true } }"
        fun missing(mode: String) = PlaygroundFacade.runWithContracts(
            contractProgram, "{}", false, true, mode, false, outputFormat = "json",
        )
        assertFalse(missing("strict").success)
        val warning = missing("warn")
        assertTrue(warning.success, warning.errorMessage)
        assertNotNull(warning.contractWarnings)
        assertTrue(missing("invalid-mode").success)
        assertNull(missing("invalid-mode").contractWarnings)
    }

    @Test
    fun executionFailureUsesResultEnvelopeAndNeverPretendsToProduceOutput() {
        val malformed = PlaygroundFacade.run("TRANSFORM Main { OUTPUT { value: } }", "{}")
        assertFalse(malformed.success)
        assertNull(malformed.outputJson)
        assertNotNull(malformed.errorMessage)
        assertNotNull(malformed.line)
        assertNotNull(malformed.column)
        val invalidInput = PlaygroundFacade.run(program, "[]")
        assertFalse(invalidInput.success)
        assertEquals("Input JSON must be an object at the top level.", invalidInput.errorMessage)
        val failedHost = PlaygroundFacade.run("OUTPUT { value: ASSERT(false) }", "{}")
        assertFalse(failedHost.success)
        assertNull(failedHost.outputJson)
        assertNotNull(failedHost.errorMessage)
    }

    @Test
    fun inspectionDefaultsAndNamedOptionsExposeStructuredContracts() {
        val request = BranchlineInspectRequest(programText = program)
        assertNull(request.transformName)
        assertFalse(request.includeDebugMetadata)
        assertFalse(request.includeWitness)
        assertFalse(request.includeNormalizedSource)
        val defaults: BranchlineInspectResult = BranchlineFacade.inspect(request = request)
        assertTrue(defaults.success)
        assertEquals(BranchlineSubsetCompatibility.COMPATIBLE, defaults.subsetCompatibility)
        assertTrue(defaults.diagnostics.isEmpty())
        assertTrue(defaults.warnings.isEmpty())
        assertNull(defaults.normalizedSource)
        val transform: BranchlineInspectTransform = defaults.transforms.single()
        assertEquals("Main", transform.name)
        assertEquals("inferred", transform.contractSource)
        assertNull(transform.explicitContract)
        assertNull(transform.witness)
        assertTrue(transform.inferredContract.containsKey("input"))
        assertEquals(transform.mergedContract, Json.parseToJsonElement(defaults.contractsJson(pretty = false)))
        assertEquals(defaults.inspectJsonPayload(), Json.parseToJsonElement(defaults.inspectJson(pretty = false)))
        val allOptions = BranchlineFacade.inspect(BranchlineInspectRequest(
            programText = program,
            transformName = "Main",
            includeDebugMetadata = true,
            includeWitness = true,
            includeNormalizedSource = true,
        ))
        assertNotNull(allOptions.normalizedSource)
        assertNotNull(allOptions.transforms.single().witness)
        assertTrue(allOptions.featureUsage.features.contains("output"))
        val wrapped: PlaygroundInspectResult = PlaygroundFacade.inspect(program = "OUTPUT { ok: true }")
        assertTrue(wrapped.success, wrapped.errorMessage)
        assertEquals("COMPATIBLE", wrapped.subsetCompatibility)
        assertNotNull(wrapped.normalizedSource)
        assertNotNull(wrapped.inspectJson)
        assertNull(wrapped.errorMessage)
        assertEquals(Json.parseToJsonElement("[]"), Json.parseToJsonElement(wrapped.diagnosticsJson))
        assertEquals(Json.parseToJsonElement("[]"), Json.parseToJsonElement(wrapped.warningsJson))
        assertTrue(Json.parseToJsonElement(wrapped.featureUsageJson).jsonObject.containsKey("features"))
        assertEquals(1, Json.parseToJsonElement(wrapped.transformsJson).jsonArray.size)
    }

    @Test
    fun inspectSelectionSubsetAndErrorSemanticsAreDistinct() {
        val multiple = "TRANSFORM First { OUTPUT { id: 1 } } TRANSFORM Second { OUTPUT { id: 2 } }"
        val all = BranchlineFacade.inspect(BranchlineInspectRequest(multiple))
        assertEquals(listOf("First", "Second"), all.transforms.map { it.name })
        assertEquals(2, Json.parseToJsonElement(all.contractsJson()).jsonObject.getValue("transforms").jsonArray.size)
        val selected = BranchlineFacade.inspect(BranchlineInspectRequest(multiple, transformName = "Second"))
        assertEquals(listOf("Second"), selected.transforms.map { it.name })
        val absent = BranchlineFacade.inspect(BranchlineInspectRequest(multiple, transformName = "Absent"))
        assertFalse(absent.success)
        assertEquals("transform_not_found", absent.diagnostics.single().code)
        assertEquals(BranchlineSubsetCompatibility.UNKNOWN, absent.subsetCompatibility)
        val invalid = BranchlineFacade.inspect(BranchlineInspectRequest("TRANSFORM Bad { OUTPUT { x: } }"))
        val diagnostic: BranchlineDiagnostic = invalid.diagnostics.single()
        assertFalse(invalid.success)
        assertEquals("parse_error", diagnostic.code)
        assertEquals(BranchlineDiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(BranchlineDiagnosticCategory.SYNTAX, diagnostic.category)
        val span: BranchlineSourceSpan = assertNotNull(diagnostic.span)
        assertTrue(span.startLine >= 1 && span.startColumn >= 1)
        assertTrue(span.endLine >= span.startLine && span.endColumn >= span.startColumn)
        assertEquals("parse", assertNotNull(diagnostic.payload).operation)
        val outsideSubset = BranchlineFacade.inspect(BranchlineInspectRequest(
            "SHARED cache SINGLE; TRANSFORM Main { OUTPUT { ok: true } }",
        ))
        assertTrue(outsideSubset.success)
        assertEquals(BranchlineSubsetCompatibility.INCOMPATIBLE, outsideSubset.subsetCompatibility)
        assertFailsWith<IllegalArgumentException> { BranchlineFacade.inspect(BranchlineInspectRequest("@")) }
        assertFalse(PlaygroundFacade.inspect(program = "@").success)
    }

    @Test
    fun consumerCanConstructResultDtosAndObserveConstructorDefaults() {
        val payload = BranchlineDiagnosticPayload()
        assertEquals(listOf(null, null, null, null, null, null, null), listOf(
            payload.operation, payload.targetPath, payload.expectedKind, payload.actualKind,
            payload.expected, payload.actual, payload.hint,
        ))
        val diagnostic = BranchlineDiagnostic("custom", "Message", BranchlineDiagnosticSeverity.WARNING)
        assertNull(diagnostic.span)
        assertNull(diagnostic.payload)
        assertEquals(BranchlineDiagnosticCategory.UNKNOWN, diagnostic.category)
        val objectValue = JsonObject(emptyMap())
        val transform = BranchlineInspectTransform("Main", "inferred", null, objectValue, objectValue, null)
        val inspect = BranchlineInspectResult(true, listOf(transform), emptyList(), emptyList(),
            BranchlineFeatureUsageSummary(emptyList()), BranchlineSubsetCompatibility.COMPATIBLE, null)
        assertEquals(objectValue, Json.parseToJsonElement(inspect.contractsJson()))
        assertEquals(BranchlineSourceSpan(1, 2, 1, 3), BranchlineSourceSpan(1, 2, 1, 3))
        val run = PlaygroundResult(true, "{}", null, null, null, null, null, null, null, null, null)
        assertTrue(run.success)
        val wrapped = PlaygroundInspectResult(true, null, "UNKNOWN", "[]", "[]", "{}", "[]", null, null)
        assertNull(wrapped.normalizedSource)
    }

    @Test
    fun cliInvalidContractModeIsUsageErrorRatherThanPlaygroundFallback() {
        assertEquals(64, BranchlineCli.run(
            args = listOf("run", "unused.bl", "--contracts", "unrecognized-mode"),
            platform = PlatformKind.JVM,
        ))
        assertEquals(ExitCode.USAGE.code, BranchlineCli.run(
            args = emptyList(), platform = PlatformKind.JS, defaultCommand = CliCommand.RUN,
        ))
        assertEquals(listOf(0, 64, 65, 74, 70), ExitCode.entries.map { it.code })
    }
}
