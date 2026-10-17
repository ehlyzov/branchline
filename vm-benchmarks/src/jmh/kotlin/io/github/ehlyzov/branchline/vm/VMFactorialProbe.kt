package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.cli.formatJson
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference

/** Validation and approximate post-GC heap stages. Run each memory command in a fresh JVM. */
public object VMFactorialProbe {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.isNotEmpty()) { usage }
        when (args[0]) {
            "validate" -> {
                require(args.size == 1 || args.size == 4) { usage }
                val engines = if (args.size == 4) listOf(args[1]) else VMFactorialFixture.engines
                val workloads = if (args.size == 4) listOf(args[2]) else VMFactorialFixture.primaryWorkloads + "fallbackClosure"
                val datasets = if (args.size == 4) listOf(args[3]) else listOf("small", "large")
                for (engine in engines) for (workload in workloads) for (dataset in datasets) {
                    val fixture = VMFactorialFixture(workload, dataset)
                    val prepared = fixture.prepare(engine, collectDiagnostics = true)
                    println(formatJson(fixture.validate(prepared) + mapOf("operation" to "validate", "engine" to engine, "passed" to true), pretty = false))
                }
            }
            "memory" -> memory(args)
            else -> error(usage)
        }
    }

    private fun memory(args: Array<String>) {
        require(args.size == 6) { usage }
        val engine = args[1]
        val lifetime = args[4]
        require(lifetime == "reuse" || lifetime == "many") { usage }
        val count = args[5].toInt()
        require(count > 0)
        FactorialMemoryRoots.initialize(engine, args[2], args[3])
        val baseline = heapUsedAfterGc()
        FactorialMemoryRoots.populate(engine, lifetime, count)
        val metadata = FactorialMemoryRoots.metadata()
        val retained = heapUsedAfterGc()
        FactorialMemoryRoots.releaseExecutables()
        val outputOnly = heapUsedAfterGc()
        val collectedWrappers = FactorialMemoryRoots.collectedWrappers()
        FactorialMemoryRoots.assertOutput()
        FactorialMemoryRoots.releaseOutputAndWeakReferences()
        val released = heapUsedAfterGc()
        println(formatJson(metadata + linkedMapOf(
            "operation" to "memory", "engine" to engine, "lifetime" to lifetime,
            "workload" to args[2], "dataset" to args[3], "executions" to count,
            "preparedExecutorCount" to if (lifetime == "reuse") 1 else count,
            "heapUsedBytes" to linkedMapOf(
                "fixtureOnlyAfterPriming" to baseline,
                "fixtureExecutorsWeakReferencesAndLastOutput" to retained,
                "fixtureWeakReferencesAndLastOutput" to outputOnly,
                "fixtureOnlyAfterRelease" to released,
            ),
            "approximateHeapDeltasBytes" to linkedMapOf(
                "preparedAndLastOutputMinusBaseline" to retained - baseline,
                "afterExecutorsReleaseMinusBaseline" to outputOnly - baseline,
                "afterAllReleaseMinusBaseline" to released - baseline,
            ),
            "collectedExecutorWrappers" to collectedWrappers,
            "weakReferenceScope" to "executor wrappers only; no assertion about exact reachable graphs",
            "jvmArguments" to ManagementFactory.getRuntimeMXBean().inputArguments,
            "heapMaxBytes" to ManagementFactory.getMemoryMXBean().heapMemoryUsage.max,
            "peakRssSource" to "external /usr/bin/time -l; not a heap-used or B/op value",
        ), pretty = false))
    }

    private fun heapUsedAfterGc(): Long {
        repeat(3) { System.gc(); System.runFinalization() }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }

    private const val usage = """Usage:
  VMFactorialProbe validate [<interpreter|baseline|slots|fusion|both> <workload> <small|large>]
  VMFactorialProbe memory <engine> <workload> <small|large> <reuse|many> <count>
Memory invocations must use a fresh process. Capture peak RSS externally with /usr/bin/time -l."""
}

/** Strong roots and separate method frames make stage lifetimes explicit. */
private object FactorialMemoryRoots {
    @Volatile private var fixture: VMFactorialFixture? = null
    @Volatile private var executables: MutableList<VMFactorialExecutable>? = null
    @Volatile private var lastOutput: Any? = null
    @Volatile private var weakExecutables: List<WeakReference<VMFactorialExecutable>>? = null
    private var diagnosticMetadata: Map<String, Any?> = emptyMap()

    fun initialize(engine: String, workload: String, dataset: String) {
        fixture = VMFactorialFixture(workload, dataset)
        diagnosticMetadata = fixture!!.validate(fixture!!.prepare(engine, collectDiagnostics = true))
        // Prime each target's parser/compiler/execution classes before the fixture-only sample.
        val prepared = fixture!!.prepare(engine)
        fixture!!.validate(prepared)
        repeat(10) { fixture!!.assertOutput(prepared.execute(fixture!!.freshEnv())) }
        fixture!!.assertPristine()
    }

    fun populate(engine: String, lifetime: String, count: Int) {
        val f = fixture!!
        val roots = ArrayList<VMFactorialExecutable>(if (lifetime == "reuse") 1 else count)
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

    fun metadata(): Map<String, Any?> = executables!!.last().metadata() + mapOf("untimedDiagnostics" to diagnosticMetadata)
    fun releaseExecutables() { executables = null }
    fun collectedWrappers(): Int = weakExecutables!!.count { it.get() == null }
    fun assertOutput() { fixture!!.assertOutput(lastOutput) }
    fun releaseOutputAndWeakReferences() { lastOutput = null; weakExecutables = null }
}
