package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.cli.ExperimentalJsonPipeline
import io.github.ehlyzov.branchline.cli.ExperimentalJsonPipelineOptions
import io.github.ehlyzov.branchline.cli.parseJsonInput
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/** Text -> complete JSON validation/materialization -> env -> strict VM -> compact JSON. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public open class SelectiveJsonPipelineBenchmark {
    @Param("eager", "selective")
    public lateinit var engineMode: String

    @Param("1", "16", "64")
    public var inputMiB: Int = 0

    @Param("1", "10", "100")
    public var selectedPercent: Int = 0

    @Param("strings", "records")
    public lateinit var shape: String

    // Keep the primary matrix static. Select -p accessPattern=dynamic for the
    // separate variable-key eager-fallback control with the same tiny output.
    @Param("static")
    public lateinit var accessPattern: String

    private lateinit var text: String
    private lateinit var pipeline: ExperimentalJsonPipeline

    @Setup(Level.Trial)
    public fun prepare() {
        val fixture = SelectivePipelineFixture(inputMiB, shape, selectedPercent, accessPattern)
        text = fixture.text()
        pipeline = fixture.prepare(engineMode)
        fixture.assertPlan(pipeline, engineMode)
        fixture.assertOutput(pipeline.execute(text))
    }

    @Benchmark
    public fun fullPipeline(blackhole: Blackhole) {
        blackhole.consume(pipeline.execute(text))
    }
}

/** Shared deterministic fixture specification; construction is never timed. */
internal class SelectivePipelineFixture(
    val inputMiB: Int,
    val shape: String,
    val selectedPercent: Int,
    val accessPattern: String,
) {
    init {
        require(inputMiB in listOf(1, 16, 64)) { "inputMiB must be 1, 16 or 64" }
        require(shape in listOf("strings", "records")) { "shape must be strings or records" }
        require(selectedPercent in listOf(1, 10, 100)) { "selectedPercent must be 1, 10 or 100" }
        require(accessPattern in listOf("static", "dynamic")) { "accessPattern must be static or dynamic" }
    }

    private val targetBytes = inputMiB * 1024 * 1024
    private val emptyValue = if (shape == "strings") "\"\"" else "[]"
    private val emptyDocumentBytes = 2 + (0 until FIELD_COUNT).sumOf { "\"f$it\":$emptyValue".length } + FIELD_COUNT - 1
    private val stringPayloadBytes = targetBytes - emptyDocumentBytes
    private val record = "{\"id\":7,\"payload\":\"" + "x".repeat(64) + "\"}"
    private val recordsPerField = (targetBytes - emptyDocumentBytes + FIELD_COUNT) / (FIELD_COUNT * (record.length + 1))

    val source: String = buildString {
        append("TRANSFORM Main {\n")
        if (accessPattern == "dynamic") {
            repeat(selectedPercent) { append("LET k$it = \"f$it\";\n") }
        }
        append("OUTPUT { total: ")
        repeat(selectedPercent) { index ->
            if (index > 0) append(" + ")
            append(if (accessPattern == "static") "LENGTH(input.f$index)" else "LENGTH(input[k$index])")
        }
        append(" };\n}")
    }

    val expectedTotal: Long = if (shape == "strings") {
        (0 until selectedPercent).sumOf { stringLength(it).toLong() }
    } else {
        selectedPercent.toLong() * recordsPerField
    }

    val actualDocumentBytes: Int = if (shape == "strings") targetBytes else
        emptyDocumentBytes + FIELD_COUNT * (recordsPerField * (record.length + 1) - 1)

    fun text(): String = buildString(actualDocumentBytes) { writeTo(this) }

    fun writeTo(out: Appendable) {
        out.append('{')
        repeat(FIELD_COUNT) { field ->
            if (field > 0) out.append(',')
            out.append("\"f$field\":")
            if (shape == "strings") {
                out.append('"')
                var remaining = stringLength(field)
                val chunk = ('a' + field % 26).toString().repeat(minOf(8192, remaining))
                while (remaining > 0) {
                    val count = minOf(remaining, chunk.length)
                    out.append(chunk, 0, count)
                    remaining -= count
                }
                out.append('"')
            } else {
                out.append('[')
                repeat(recordsPerField) { index ->
                    if (index > 0) out.append(',')
                    out.append(record)
                }
                out.append(']')
            }
        }
        out.append('}')
    }

    fun prepare(engineMode: String): ExperimentalJsonPipeline {
        require(engineMode in listOf("eager", "selective")) { "engineMode must be eager or selective" }
        return ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(
            forceEager = engineMode == "eager",
            contractsMode = ContractValidationMode.OFF,
        ))
    }

    fun assertPlan(pipeline: ExperimentalJsonPipeline, engineMode: String) {
        val expectedMode = if (engineMode == "selective" && accessPattern == "static") "SELECTIVE" else "EAGER"
        check(pipeline.report.effectiveMode.name == expectedMode) {
            "Expected $expectedMode; got ${pipeline.report}"
        }
        if (expectedMode == "SELECTIVE") {
            // The planner also preserves a real top-level "input" field because
            // it would shadow the default alias. Our 100-field fixture has none.
            val expectedKeys = (0 until selectedPercent).map { "f$it" }.toSet() + DEFAULT_INPUT_ALIAS
            check(pipeline.report.selectedRootKeys == expectedKeys) {
                "Expected selected keys $expectedKeys; got ${pipeline.report.selectedRootKeys}"
            }
        }
        check(pipeline.report.reason.isNotBlank())
    }

    fun assertOutput(output: String) {
        val parsed = parseJsonInput(output)
        check(parsed.keys == setOf("total")) { "Unexpected output fields: $output" }
        val total = parsed["total"] as? Number ?: error("Expected numeric total: $output")
        check(total.toDouble() == expectedTotal.toDouble()) { "Expected $expectedTotal; got $output" }
    }

    fun assertMaterialized(input: Map<String, Any?>) {
        val total = (0 until selectedPercent).sumOf { field ->
            when (val value = input["f$field"]) {
                is String -> value.length.toLong()
                is List<*> -> value.size.toLong()
                else -> error("Unexpected materialized field f$field: ${value?.javaClass?.name}")
            }
        }
        check(total == expectedTotal) { "Expected materialized length $expectedTotal; got $total" }
    }

    fun metadata(engineMode: String, pipeline: ExperimentalJsonPipeline): Map<String, Any?> = linkedMapOf(
        "engineMode" to engineMode,
        "inputMiB" to inputMiB,
        "actualInputBytes" to actualDocumentBytes,
        "shape" to shape,
        "selectedPercent" to selectedPercent,
        "accessPattern" to accessPattern,
        "expectedTotal" to expectedTotal,
        "effectiveMode" to pipeline.report.effectiveMode.name,
        "reason" to pipeline.report.reason,
        "selectedRootKeys" to pipeline.report.selectedRootKeys.sorted(),
    )

    private fun stringLength(field: Int): Int = stringPayloadBytes / FIELD_COUNT +
        if (field < stringPayloadBytes % FIELD_COUNT) 1 else 0

    companion object {
        private const val FIELD_COUNT = 100
    }
}
