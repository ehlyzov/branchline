package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.contract.ContractJsonRenderer
import io.github.ehlyzov.branchline.contract.GuaranteeNodeV2
import io.github.ehlyzov.branchline.contract.RequirementNodeV2
import io.github.ehlyzov.branchline.contract.TransformContractV2
import io.github.ehlyzov.branchline.contract.ValueShape
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ContractInferenceQualityGateTest {
    private val json = Json
    private val curatedExamples = listOf(
        "contract-deep-composition",
        "error-handling-try-catch",
        "junit-badge-summary",
        "stdlib-core-append-prepend",
        "stdlib-core-listify-get",
        "stdlib-strings-casts",
        "stdlib-strings-text",
        "xml-input-output-roundtrip",
    )

    @Test
    fun curated_examples_unknown_ratio_stays_under_gate() {
        val stats = collectStats(curatedExamples)
        assertTrue(
            stats.ratio <= 0.35,
            "Unknown ratio gate failed: ratio=${stats.ratio}, unknown=${stats.unknown}, total=${stats.total}, details=${stats.perExample}",
        )
    }

    @Test
    fun junit_badge_summary_unknown_ratio_stays_under_gate() {
        val stats = collectStats(listOf("junit-badge-summary"))
        assertTrue(
            stats.ratio <= 0.20,
            "junit-badge-summary gate failed: ratio=${stats.ratio}, unknown=${stats.unknown}, total=${stats.total}",
        )
    }

    @Test
    fun v2_json_has_no_duplicate_shape_schema_fields() {
        val contract = contractForExample("junit-badge-summary")
        val outputJson = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV2(contract.output, includeSpans = false, pretty = true),
        )
        assertNoSchemaKey(outputJson)
    }

    @Test
    fun v2_json_origin_is_debug_only() {
        val contract = contractForExample("customer-profile")
        val standard = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV2(contract.output, includeSpans = false, pretty = true),
        ).jsonObject
        val debug = Json.parseToJsonElement(
            ContractJsonRenderer.renderSchemaGuaranteeV2(contract.output, includeSpans = true, pretty = true),
        ).jsonObject
        val standardRoot = standard["root"]?.jsonObject ?: error("missing standard root")
        val debugRoot = debug["root"]?.jsonObject ?: error("missing debug root")
        assertTrue(!standardRoot.containsKey("origin"), "origin must be absent in default contracts JSON")
        assertEquals("OUTPUT", debugRoot["origin"]?.toString()?.trim('"'))
    }

    private fun collectStats(exampleNames: List<String>): AggregateStats {
        val examplesDir = resolveExamplesDir()
        var unknown = 0
        var total = 0
        val perExample = LinkedHashMap<String, Double>()
        for (name in exampleNames) {
            val contract = contractForExample(name)
            val stats = contractStats(contract)
            unknown += stats.unknown
            total += stats.total
            perExample[name] = stats.ratio
        }
        return AggregateStats(
            unknown = unknown,
            total = total,
            perExample = perExample,
        )
    }

    private fun parseProgram(payload: JsonObject): String {
        val program = payload["program"] ?: error("Example payload is missing program")
        return when (program) {
            is JsonPrimitive -> program.content
            is JsonArray -> program.joinToString("\n") { line ->
                (line as? JsonPrimitive)?.content ?: line.toString()
            }
            else -> error("Unsupported program payload: $program")
        }
    }

    private fun contractForExample(name: String): TransformContractV2 {
        val examplesDir = resolveExamplesDir()
        val file = examplesDir.resolve("$name.json")
        require(Files.exists(file)) { "Missing playground example: $file" }
        val payload = json.parseToJsonElement(Files.readString(file)).jsonObject
        val program = parseProgram(payload)
        val runtime = BranchlineProgram(wrapProgramIfNeeded(program))
        val transform = runtime.selectTransform(null)
        return runtime.contractV2ForTransform(transform)
    }

    private fun wrapProgramIfNeeded(program: String): String {
        if (program.contains("TRANSFORM")) return program
        return buildString {
            appendLine("TRANSFORM Playground {")
            appendLine(program)
            appendLine("}")
        }
    }

    private fun contractStats(contract: TransformContractV2): ShapeStats {
        val input = requirementNodeStats(contract.input.root)
        val output = guaranteeNodeStats(contract.output.root)
        return input + output
    }

    private fun requirementNodeStats(node: RequirementNodeV2): ShapeStats {
        var stats = shapeStats(node.shape)
        for (child in node.children.values) {
            stats += requirementNodeStats(child)
        }
        return stats
    }

    private fun guaranteeNodeStats(node: GuaranteeNodeV2): ShapeStats {
        var stats = shapeStats(node.shape)
        for (child in node.children.values) {
            stats += guaranteeNodeStats(child)
        }
        return stats
    }

    private fun shapeStats(shape: ValueShape): ShapeStats = when (shape) {
        ValueShape.Unknown -> ShapeStats(unknown = 1, total = 1)
        ValueShape.Null,
        ValueShape.BooleanShape,
        ValueShape.NumberShape,
        ValueShape.Bytes,
        ValueShape.TextShape,
        -> ShapeStats(unknown = 0, total = 1)
        is ValueShape.ArrayShape -> ShapeStats(unknown = 0, total = 1) + shapeStats(shape.element)
        is ValueShape.SetShape -> ShapeStats(unknown = 0, total = 1) + shapeStats(shape.element)
        is ValueShape.ObjectShape -> {
            var stats = ShapeStats(unknown = 0, total = 1)
            for (field in shape.schema.fields.values) {
                stats += shapeStats(field.shape)
            }
            stats
        }
        is ValueShape.Union -> {
            var stats = ShapeStats(unknown = 0, total = 1)
            for (option in shape.options) {
                stats += shapeStats(option)
            }
            stats
        }
    }

    private fun resolveExamplesDir(): Path {
        val candidates = listOf(
            Path.of("playground/examples"),
            Path.of("../playground/examples"),
            Path.of("../../playground/examples"),
        )
        return candidates.firstOrNull { candidate -> Files.isDirectory(candidate) }
            ?: error("Unable to locate playground/examples from ${Path.of("").toAbsolutePath()}")
    }

    private fun assertNoSchemaKey(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                assertTrue(!element.containsKey("schema"), "Found deprecated duplicate object schema key in JSON node: $element")
                element.values.forEach(::assertNoSchemaKey)
            }
            is JsonArray -> element.forEach(::assertNoSchemaKey)
            is JsonPrimitive -> Unit
        }
    }

    private data class ShapeStats(
        val unknown: Int,
        val total: Int,
    ) {
        val ratio: Double
            get() = if (total == 0) 0.0 else unknown.toDouble() / total.toDouble()

        operator fun plus(other: ShapeStats): ShapeStats = ShapeStats(
            unknown = unknown + other.unknown,
            total = total + other.total,
        )
    }

    private data class AggregateStats(
        val unknown: Int,
        val total: Int,
        val perExample: Map<String, Double>,
    ) {
        val ratio: Double
            get() = if (total == 0) 0.0 else unknown.toDouble() / total.toDouble()
    }
}
