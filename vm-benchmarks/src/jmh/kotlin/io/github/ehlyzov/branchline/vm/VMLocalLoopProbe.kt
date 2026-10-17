package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.cli.formatJson
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.lang.ref.WeakReference

/** Untimed correctness and fresh-process heap/non-heap/class-loading observations. */
public object VMLocalLoopProbe {
    @JvmStatic public fun main(args: Array<String>) {
        require(args.isNotEmpty()) { usage }
        when (args[0]) {
            "validate" -> {
                require(args.size == 1 || args.size == 4) { usage }
                val engines = if (args.size == 4) listOf(args[1]) else VMLocalLoopHarness.engines
                val workloads = if (args.size == 4) listOf(args[2]) else VMLocalLoopHarness.primaryWorkloads + "fallbackClosure"
                val datasets = if (args.size == 4) listOf(args[3]) else listOf("small", "large")
                for (engine in engines) for (workload in workloads) for (dataset in datasets) {
                    val fixture = VMLocalLoopFixture(workload, dataset)
                    println(formatJson(fixture.validate(fixture.prepare(engine)) + mapOf("operation" to "validate", "passed" to true), pretty = false))
                }
            }
            "memory" -> memory(args)
            else -> error(usage)
        }
    }

    /** Custom raw fixtures remain separate from the preregistered benchmark matrix. */
    @JvmStatic public fun validateSource(source: String, input: Map<String, Any?>, expected: Any?, engine: String): Map<String, Any?> {
        val pristine = copyTree(input)
        val executable = VMLocalLoopHarness.prepareSource(source, engine)
        repeat(3) {
            val actual = executable.execute(BenchDatasets.buildEnv(input))
            check(actual == expected) { "Custom $engine expected=$expected actual=$actual" }
            check(input == pristine) { "Custom $engine input mutated" }
        }
        return executable.metadata() + mapOf("operation" to "validateCustom", "validatedExecutions" to 3, "passed" to true)
    }

    private fun copyTree(value: Any?): Any? = when (value) {
        is Map<*, *> -> LinkedHashMap<Any?, Any?>().also { copy -> for ((key, element) in value) copy[key] = copyTree(element) }
        is List<*> -> value.map { copyTree(it) }
        else -> value
    }

    private fun memory(args: Array<String>) {
        require(args.size == 6) { usage }
        val engine = args[1]
        val lifetime = args[4]
        require(lifetime == "reuse" || lifetime == "many") { usage }
        val count = args[5].toInt()
        require(count > 0)
        // Preallocate snapshot storage and load MXBean/metric classes before the first sample.
        val samples = List(5) { LocalLoopMemorySample() }
        LocalLoopMemoryRoots.initialize(engine, args[2], args[3])
        for (sample in samples) sample.capture()
        samples[0].captureAfterGc()
        LocalLoopMemoryRoots.prepare(engine, lifetime, count)
        samples[1].captureAfterGc()
        LocalLoopMemoryRoots.execute(lifetime, count)
        samples[2].captureAfterGc()
        LocalLoopMemoryRoots.releaseExecutables()
        samples[3].captureAfterGc()
        val collected = LocalLoopMemoryRoots.collectedWrappers()
        LocalLoopMemoryRoots.assertOutput()
        LocalLoopMemoryRoots.releaseOutputAndWeakReferences()
        samples[4].captureAfterGc()
        println(formatJson(LocalLoopMemoryRoots.metadata() + linkedMapOf(
            "operation" to "memory", "engine" to engine, "workload" to args[2], "dataset" to args[3],
            "lifetime" to lifetime, "executions" to count,
            "independentlyPreparedExecutorCount" to if (lifetime == "reuse") 1 else count,
            "stages" to linkedMapOf(
                "fixtureOnlyAfterPriming" to samples[0].values(),
                "fixturePreparedUnexecutedExecutorsAndWeakReferences" to samples[1].values(),
                "fixtureExecutedExecutorsWeakReferencesAndLastOutput" to samples[2].values(),
                "fixtureWeakReferencesAndLastOutput" to samples[3].values(),
                "fixtureOnlyAfterRelease" to samples[4].values(),
            ),
            "approximateHeapDeltasBytes" to linkedMapOf(
                "preparedUnexecutedMinusFixture" to samples[1].heapUsed - samples[0].heapUsed,
                "executedMinusFixture" to samples[2].heapUsed - samples[0].heapUsed,
                "executedMinusPreparedUnexecuted" to samples[2].heapUsed - samples[1].heapUsed,
                "afterExecutorsReleaseMinusFixture" to samples[3].heapUsed - samples[0].heapUsed,
                "afterAllReleaseMinusFixture" to samples[4].heapUsed - samples[0].heapUsed,
            ),
            "collectedExecutorWrappers" to collected,
            "weakReferenceScope" to "wrapper collection only; not exact graph size or generated-class unloading",
            "classLoadingScope" to "process-wide; generated code can unload separately from wrapper collection",
            "afterExecutionScope" to "includes engine runtime state and retained result trees; not plan memory",
            "jvmArguments" to ManagementFactory.getRuntimeMXBean().inputArguments,
            "heapMaxBytes" to ManagementFactory.getMemoryMXBean().heapMemoryUsage.max,
            "peakRssSource" to "external /usr/bin/time -l; distinct from heap/nonheap/B-op",
        ), pretty = false))
    }

    private const val usage = """Usage:
  VMLocalLoopProbe validate [<interpreter|baseline|loopBody|localLoop> <workload> <small|large>]
  VMLocalLoopProbe memory <engine> <workload> <small|large> <reuse|many> <count>
Use holdoutProjection to measure eligible generated plans. Run each memory command in a fresh JVM;
capture peak RSS externally with /usr/bin/time -l."""
}

private object LocalLoopMemoryMetrics {
    val memory = ManagementFactory.getMemoryMXBean()
    val classes = ManagementFactory.getClassLoadingMXBean()
    val pools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.NON_HEAP }
}

/** Scalar/preallocated storage avoids retaining measurement maps between heap samples. */
private class LocalLoopMemorySample {
    var heapUsed = 0L
    private var loadedClasses = 0
    private var totalLoadedClasses = 0L
    private var unloadedClasses = 0L
    private val poolUsed = LongArray(LocalLoopMemoryMetrics.pools.size)
    private val poolCommitted = LongArray(LocalLoopMemoryMetrics.pools.size)

    fun captureAfterGc() {
        repeat(3) { System.gc(); System.runFinalization() }
        capture()
    }

    fun capture() {
        heapUsed = LocalLoopMemoryMetrics.memory.heapMemoryUsage.used
        loadedClasses = LocalLoopMemoryMetrics.classes.loadedClassCount
        totalLoadedClasses = LocalLoopMemoryMetrics.classes.totalLoadedClassCount
        unloadedClasses = LocalLoopMemoryMetrics.classes.unloadedClassCount
        for ((index, pool) in LocalLoopMemoryMetrics.pools.withIndex()) {
            val usage = pool.usage
            poolUsed[index] = usage?.used ?: -1L
            poolCommitted[index] = usage?.committed ?: -1L
        }
    }

    fun values(): Map<String, Any?> = linkedMapOf(
        "heapUsedBytes" to heapUsed,
        "loadedClasses" to loadedClasses, "totalLoadedClasses" to totalLoadedClasses, "unloadedClasses" to unloadedClasses,
        "nonHeapPoolsBytes" to LocalLoopMemoryMetrics.pools.mapIndexed { index, pool ->
            pool.name to mapOf("used" to poolUsed[index], "committed" to poolCommitted[index])
        }.toMap(),
    )
}

/** Each method returns before the caller samples GC; retained graphs have explicit roots. */
private object LocalLoopMemoryRoots {
    @Volatile private var fixture: VMLocalLoopFixture? = null
    @Volatile private var executables: MutableList<VMLocalLoopExecutable>? = null
    @Volatile private var lastOutput: Any? = null
    @Volatile private var weakExecutables: List<WeakReference<VMLocalLoopExecutable>>? = null
    private var validationMetadata: Map<String, Any?> = emptyMap()
    private var generatedClassTotal = 0
    private var generatedBytecodeBytesTotal = 0L

    fun initialize(engine: String, workload: String, dataset: String) {
        fixture = VMLocalLoopFixture(workload, dataset)
        val executable = fixture!!.prepare(engine)
        validationMetadata = fixture!!.validate(executable)
        repeat(10) { fixture!!.assertOutput(executable.execute(fixture!!.freshEnv())) }
        fixture!!.assertPristine()
    }

    fun prepare(engine: String, lifetime: String, count: Int) {
        val roots = ArrayList<VMLocalLoopExecutable>(if (lifetime == "reuse") 1 else count)
        generatedClassTotal = 0
        generatedBytecodeBytesTotal = 0L
        repeat(if (lifetime == "reuse") 1 else count) {
            val executable = fixture!!.prepare(engine)
            roots.add(executable)
            generatedClassTotal += executable.generatedClassCount
            generatedBytecodeBytesTotal += executable.generatedBytecodeBytes
        }
        executables = roots
        weakExecutables = roots.map { WeakReference(it) }
    }

    fun execute(lifetime: String, count: Int) {
        val f = fixture!!
        val roots = executables!!
        if (lifetime == "reuse") repeat(count) { lastOutput = roots[0].execute(f.freshEnv()); f.assertOutput(lastOutput) }
        else for (executable in roots) { lastOutput = executable.execute(f.freshEnv()); f.assertOutput(lastOutput) }
        f.assertPristine()
        f.assertMode(roots.last().metadata())
    }

    fun metadata(): Map<String, Any?> = mapOf(
        "untimedValidation" to validationMetadata,
        "preparedGeneratedClassCountSum" to generatedClassTotal,
        "preparedGeneratedClassfileBytesSum" to generatedBytecodeBytesTotal,
        "generatedBytecodeScope" to "classfile byte lengths, not loaded metaspace or heap size",
    )
    fun releaseExecutables() { executables = null }
    fun collectedWrappers(): Int = weakExecutables!!.count { it.get() == null }
    fun assertOutput() { fixture!!.assertOutput(lastOutput) }
    fun releaseOutputAndWeakReferences() { lastOutput = null; weakExecutables = null }
}
