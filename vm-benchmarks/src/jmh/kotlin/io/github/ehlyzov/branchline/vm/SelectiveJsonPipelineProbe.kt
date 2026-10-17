package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.cli.ExperimentalJsonPipeline
import io.github.ehlyzov.branchline.cli.formatJson
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Standalone fixture/validation/live-heap probe; never run inside timed JMH. */
public object SelectiveJsonPipelineProbe {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.isNotEmpty()) { usage }
        when (args[0]) {
            "generate" -> generate(args)
            "validate", "memory" -> {
                require(args.size in 6..7) { usage }
                val path = Path.of(args[1])
                val fixture = SelectivePipelineFixture(args[2].toInt(), args[3], args[4].toInt(), args.getOrElse(6) { "static" })
                require(Files.size(path) == fixture.actualDocumentBytes.toLong()) { "Fixture size does not match the declared shape/size" }
                if (args[0] == "memory") memory(path, fixture, args[5]) else validate(path, fixture, args[5])
            }
            else -> error(usage)
        }
    }

    private fun generate(args: Array<String>) {
        require(args.size == 4) { usage }
        val path = Path.of(args[1]).toAbsolutePath()
        val fixture = SelectivePipelineFixture(args[2].toInt(), args[3], 1, "static")
        path.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { fixture.writeTo(it) }
        check(Files.size(path) == fixture.actualDocumentBytes.toLong())
        println(formatJson(mapOf(
            "operation" to "generate", "input" to path.toString(),
            "inputMiB" to fixture.inputMiB, "shape" to fixture.shape,
            "actualInputBytes" to Files.size(path), "inputSha256" to sha256(path),
        ), pretty = false))
    }

    private fun validate(path: Path, fixture: SelectivePipelineFixture, engineMode: String) {
        val pipeline = fixture.prepare(engineMode)
        fixture.assertPlan(pipeline, engineMode)
        val text = Files.readString(path, StandardCharsets.UTF_8)
        fixture.assertMaterialized(pipeline.materialize(text))
        val output = pipeline.execute(text)
        fixture.assertOutput(output)
        println(formatJson(fixture.metadata(engineMode, pipeline) + mapOf(
            "operation" to "validate", "input" to path.toAbsolutePath().toString(),
            "inputSha256" to sha256(path), "output" to output, "passed" to true,
        ), pretty = false))
    }

    private fun memory(path: Path, fixture: SelectivePipelineFixture, engineMode: String) {
        val digest = sha256(path)
        MemoryState.initialize(path, fixture, engineMode)
        val metadata = MemoryState.metadata(fixture, engineMode)
        val baseline = MemoryState.heapUsedAfterGc()
        MemoryState.materializeAndValidate(fixture)
        val withMaterialized = MemoryState.heapUsedAfterGc()
        MemoryState.releaseMaterialized()
        val afterMaterializedRelease = MemoryState.heapUsedAfterGc()
        MemoryState.executeAndValidate(fixture)
        val withReusableVm = MemoryState.heapUsedAfterGc()
        MemoryState.releaseRawAndPipeline()
        val detachedOutputOnly = MemoryState.heapUsedAfterGc()
        check(MemoryState.rawCollected()) { "Raw text remained reachable after its owner was dropped" }
        check(MemoryState.pipelineCollected()) { "Pipeline remained reachable after its owner was dropped" }
        fixture.assertOutput(MemoryState.output())
        println(formatJson(metadata + mapOf(
            "operation" to "memory", "input" to path.toAbsolutePath().toString(),
            "inputSha256" to digest,
            "heapUsedBytes" to linkedMapOf(
                "rawAndPreparedVm" to baseline,
                "rawPreparedAndMaterialized" to withMaterialized,
                "rawPreparedAfterMaterializedRelease" to afterMaterializedRelease,
                "rawAndReusableVmLastEnvironment" to withReusableVm,
                "detachedOutputOnly" to detachedOutputOnly,
            ),
            "heapDeltasBytes" to linkedMapOf(
                "materializedMinusBaseline" to withMaterialized - baseline,
                "reusableVmMinusBaseline" to withReusableVm - baseline,
                "releaseMaterialized" to withMaterialized - afterMaterializedRelease,
                "releaseRawAndPipeline" to withReusableVm - detachedOutputOnly,
            ),
            "rawTextCollected" to MemoryState.rawCollected(),
            "pipelineCollected" to MemoryState.pipelineCollected(),
            "output" to MemoryState.output(),
            "jvmArguments" to ManagementFactory.getRuntimeMXBean().inputArguments,
            "heapMaxBytes" to ManagementFactory.getMemoryMXBean().heapMemoryUsage.max,
        ), pretty = false))
        MemoryState.releaseOutput()
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
    }

    private const val usage = """Usage:
  SelectiveJsonPipelineProbe generate <input.json> <1|16|64> <strings|records>
  SelectiveJsonPipelineProbe validate <input.json> <1|16|64> <strings|records> <1|10|100> <eager|selective> [static|dynamic]
  SelectiveJsonPipelineProbe memory <input.json> <1|16|64> <strings|records> <1|10|100> <eager|selective> [static|dynamic]
Run each memory invocation in a fresh JVM. Generate fixtures in separate processes beforehand."""
}

/**
 * Strong volatile roots make every retained stage explicit. Separate methods
 * return before GC so caller stack locals cannot accidentally keep old owners.
 */
private object MemoryState {
    @Volatile private var raw: String? = null
    @Volatile private var pipeline: ExperimentalJsonPipeline? = null
    @Volatile private var materialized: Map<String, Any?>? = null
    @Volatile private var tinyOutput: String? = null
    private var rawWeak: WeakReference<String>? = null
    private var pipelineWeak: WeakReference<ExperimentalJsonPipeline>? = null

    fun initialize(path: Path, fixture: SelectivePipelineFixture, engineMode: String) {
        raw = Files.readString(path, StandardCharsets.UTF_8)
        pipeline = fixture.prepare(engineMode)
        fixture.assertPlan(pipeline!!, engineMode)
        // Prime VM/parser/serializer classes with a tiny matching shape. This
        // baseline includes reusable VM preparation, but no full-size parsed graph.
        val tinyInput = (0 until 100).joinToString(",", "{", "}") { field ->
            "\"f$field\":" + if (fixture.shape == "strings") "\"x\"" else "[{}]"
        }
        val primedOutput = pipeline!!.execute(tinyInput)
        check(io.github.ehlyzov.branchline.cli.parseJsonInput(primedOutput)["total"] is Number)
    }

    fun metadata(fixture: SelectivePipelineFixture, engineMode: String): Map<String, Any?> =
        fixture.metadata(engineMode, pipeline!!)

    fun materializeAndValidate(fixture: SelectivePipelineFixture) {
        materialized = pipeline!!.materialize(raw!!)
        fixture.assertMaterialized(materialized!!)
    }

    fun releaseMaterialized() { materialized = null }

    fun executeAndValidate(fixture: SelectivePipelineFixture) {
        tinyOutput = pipeline!!.execute(raw!!)
        fixture.assertOutput(tinyOutput!!)
    }

    fun releaseRawAndPipeline() {
        rawWeak = WeakReference(raw!!)
        pipelineWeak = WeakReference(pipeline!!)
        raw = null
        pipeline = null
    }

    fun rawCollected(): Boolean = rawWeak?.get() == null
    fun pipelineCollected(): Boolean = pipelineWeak?.get() == null
    fun output(): String = checkNotNull(tinyOutput)
    fun releaseOutput() { tinyOutput = null }

    fun heapUsedAfterGc(): Long {
        System.gc()
        System.runFinalization()
        System.gc()
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }
}
