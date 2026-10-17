package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.cli.formatJson
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference

/** Untimed validation and fresh-JVM approximate heap stages; no benchmark processes are spawned here. */
public object VMPreparedBlockProbe {
    @JvmStatic public fun main(args: Array<String>) {
        require(args.isNotEmpty()) { usage }
        when (args[0]) {
            "validate" -> {
                require(args.size == 1 || args.size == 4) { usage }
                val engines = if (args.size == 4) listOf(args[1]) else VMPreparedBlockHarness.engines
                val workloads = if (args.size == 4) listOf(args[2]) else VMPreparedBlockHarness.primaryWorkloads + "fallbackClosure"
                val datasets = if (args.size == 4) listOf(args[3]) else listOf("small", "large")
                for (engine in engines) for (workload in workloads) for (dataset in datasets) {
                    val fixture = VMPreparedBlockFixture(workload, dataset)
                    println(formatJson(fixture.validate(fixture.prepare(engine)) + mapOf("operation" to "validate", "passed" to true), pretty = false))
                }
            }
            "memory" -> memory(args)
            else -> error(usage)
        }
    }

    /** Parent may supply independent raw fixtures without adding them to the primary benchmark matrix. */
    @JvmStatic public fun validateSource(source: String, input: Map<String, Any?>, expected: Any?, engine: String): Map<String, Any?> {
        val pristine = copyTree(input)
        val executable = VMPreparedBlockHarness.prepareSource(source, engine)
        repeat(3) {
            val result = executable.execute(BenchDatasets.buildEnv(input))
            check(result == expected) { "Custom $engine expected=$expected actual=$result" }
            check(input == pristine) { "Custom $engine mutated its input" }
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
        PreparedBlockMemoryRoots.initialize(engine, args[2], args[3])
        val baseline = heapUsedAfterGc()
        PreparedBlockMemoryRoots.populate(engine, lifetime, count)
        val metadata = PreparedBlockMemoryRoots.metadata()
        val retained = heapUsedAfterGc()
        PreparedBlockMemoryRoots.releaseExecutables()
        val outputOnly = heapUsedAfterGc()
        val collected = PreparedBlockMemoryRoots.collectedWrappers()
        PreparedBlockMemoryRoots.assertOutput()
        PreparedBlockMemoryRoots.releaseOutputAndWeakReferences()
        val released = heapUsedAfterGc()
        println(formatJson(metadata + linkedMapOf(
            "operation" to "memory", "engine" to engine, "workload" to args[2], "dataset" to args[3],
            "lifetime" to lifetime, "executions" to count,
            "independentlyPreparedExecutorCount" to if (lifetime == "reuse") 1 else count,
            "heapUsedBytes" to linkedMapOf(
                "fixtureOnlyAfterPriming" to baseline,
                "fixtureExecutorsWeakReferencesAndLastOutput" to retained,
                "fixtureWeakReferencesAndLastOutput" to outputOnly,
                "fixtureOnlyAfterRelease" to released,
            ),
            "approximateHeapDeltasBytes" to linkedMapOf(
                "executorsAndOutputMinusBaseline" to retained - baseline,
                "afterExecutorsReleaseMinusBaseline" to outputOnly - baseline,
                "afterAllReleaseMinusBaseline" to released - baseline,
            ),
            "collectedExecutorWrappers" to collected,
            "weakReferenceScope" to "wrapper collection only; no exact retained graph claim",
            "jvmArguments" to ManagementFactory.getRuntimeMXBean().inputArguments,
            "heapMaxBytes" to ManagementFactory.getMemoryMXBean().heapMemoryUsage.max,
            "peakRssSource" to "external /usr/bin/time -l; distinct from heap-used and allocation B/op",
        ), pretty = false))
    }

    private fun heapUsedAfterGc(): Long {
        repeat(3) { System.gc(); System.runFinalization() }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }

    private const val usage = """Usage:
  VMPreparedBlockProbe validate [<interpreter|baseline|preparedControl|preparedOptimized> <workload> <small|large>]
  VMPreparedBlockProbe memory <engine> <workload> <small|large> <reuse|many> <count>
Run each memory command in a fresh JVM; capture peak RSS externally with /usr/bin/time -l."""
}

/** Explicit static roots; each preparation/execution/release method returns before forced GC. */
private object PreparedBlockMemoryRoots {
    @Volatile private var fixture: VMPreparedBlockFixture? = null
    @Volatile private var executables: MutableList<VMPreparedBlockExecutable>? = null
    @Volatile private var lastOutput: Any? = null
    @Volatile private var weakExecutables: List<WeakReference<VMPreparedBlockExecutable>>? = null
    private var validationMetadata: Map<String, Any?> = emptyMap()

    fun initialize(engine: String, workload: String, dataset: String) {
        fixture = VMPreparedBlockFixture(workload, dataset)
        val prepared = fixture!!.prepare(engine)
        validationMetadata = fixture!!.validate(prepared)
        repeat(10) { fixture!!.assertOutput(prepared.execute(fixture!!.freshEnv())) }
        fixture!!.assertPristine()
    }

    fun populate(engine: String, lifetime: String, count: Int) {
        val f = fixture!!
        val roots = ArrayList<VMPreparedBlockExecutable>(if (lifetime == "reuse") 1 else count)
        if (lifetime == "reuse") {
            val prepared = f.prepare(engine)
            roots.add(prepared)
            repeat(count) { lastOutput = prepared.execute(f.freshEnv()); f.assertOutput(lastOutput) }
        } else {
            repeat(count) {
                val prepared = f.prepare(engine)
                roots.add(prepared)
                lastOutput = prepared.execute(f.freshEnv())
                f.assertOutput(lastOutput)
            }
        }
        f.assertPristine()
        executables = roots
        weakExecutables = roots.map { WeakReference(it) }
    }

    fun metadata(): Map<String, Any?> = executables!!.last().metadata() + mapOf("untimedValidation" to validationMetadata)
    fun releaseExecutables() { executables = null }
    fun collectedWrappers(): Int = weakExecutables!!.count { it.get() == null }
    fun assertOutput() { fixture!!.assertOutput(lastOutput) }
    fun releaseOutputAndWeakReferences() { lastOutput = null; weakExecutables = null }
}
