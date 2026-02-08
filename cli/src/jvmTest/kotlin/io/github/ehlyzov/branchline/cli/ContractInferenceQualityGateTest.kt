package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.contract.GuaranteeNodeV2
import io.github.ehlyzov.branchline.contract.RequirementNodeV2
import io.github.ehlyzov.branchline.contract.TransformContractV2
import io.github.ehlyzov.branchline.contract.ValueShape
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

    private fun collectStats(exampleNames: List<String>): AggregateStats {
        val examplesDir = resolveExamplesDir()
        var unknown = 0
        var total = 0
        val perExample = LinkedHashMap<String, Double>()
        for (name in exampleNames) {
            val file = examplesDir.resolve("$name.json")
            require(Files.exists(file)) { "Missing playground example: $file" }
            val payload = json.parseToJsonElement(Files.readString(file)).jsonObject
            val program = parseProgram(payload)
            val runtime = BranchlineProgram(wrapProgramIfNeeded(program))
            val transform = runtime.selectTransform(null)
            val contract = runtime.contractV2ForTransform(transform)
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
