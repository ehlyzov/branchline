@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.cli.formatJson
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Fork(value = 2, jvmArgsAppend = ["-Xms256m", "-Xmx768m"])
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public open class VMLocalLoopBenchmark {
    @Param("interpreter", "baseline", "loopBody", "localLoop") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMLocalLoopFixture
    private lateinit var executable: VMLocalLoopExecutable

    @Setup(Level.Trial) public fun setup() {
        fixture = VMLocalLoopFixture(workload, dataset)
        executable = fixture.prepare(engine)
        println(formatJson(fixture.validate(executable) + mapOf("boundary" to "fresh-env-plus-reusable-execution"), pretty = false))
    }
    @Benchmark public fun execute(): Any? = executable.execute(fixture.freshEnv())
}

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Fork(value = 2, jvmArgsAppend = ["-Xms256m", "-Xmx768m"])
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public open class VMLocalLoopPreparationBenchmark {
    @Param("interpreter", "baseline", "loopBody", "localLoop") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMLocalLoopFixture

    @Setup(Level.Trial) public fun setup() {
        fixture = VMLocalLoopFixture(workload, dataset)
        println(formatJson(fixture.validate(fixture.prepare(engine)) + mapOf("boundary" to "source-to-executor-including-class-generation"), pretty = false))
    }
    @Benchmark public fun prepare(): VMLocalLoopExecutable = fixture.prepare(engine)
}

public interface VMLocalLoopExecutable {
    public fun execute(env: MutableMap<String, Any?>): Any?
    public fun metadata(): Map<String, Any?>
    public val generatedClassCount: Int get() = 0
    public val generatedBytecodeBytes: Int get() = 0
}

public object VMLocalLoopHarness {
    public val engines: List<String> = listOf("interpreter", "baseline", "loopBody", "localLoop")
    public val primaryWorkloads: List<String> = listOf("paths", "reduction", "holdoutProjection")

    @JvmStatic public fun prepareSource(source: String, engine: String): VMLocalLoopExecutable {
        require(engine in engines) { "Unknown engine: $engine" }
        return if (engine == "localLoop") GeneratedLocalLoopFactory.prepare(source)
        else LegacyLocalLoopAdapter(VMLoopBodyHarness.prepareSource(source, engine))
    }
}

public class VMLocalLoopFixture(public val workload: String, public val dataset: String) {
    private val native = VMFactorialFixture(workload, dataset)
    public val source: String get() = native.source
    public val input: Map<String, Any?> get() = native.input
    public val expected: Any get() = native.expected
    public fun freshEnv(): MutableMap<String, Any?> = native.freshEnv()
    public fun assertOutput(actual: Any?) { native.assertOutput(actual) }
    public fun assertPristine() { native.assertPristine() }
    public fun prepare(engine: String): VMLocalLoopExecutable = VMLocalLoopHarness.prepareSource(source, engine)

    public fun assertMode(metadata: Map<String, Any?>) {
        if (metadata["engine"] == "loopBody") {
            if (workload == "holdoutProjection") check(metadata["loopBodyExecuted"] == true && (metadata["loopBodyEntries"] as Int) > 0 && metadata["fallbackReason"] == null) { "Prior loop control did not enter its kernel: $metadata" }
            if (workload == "paths" || workload == "fallbackClosure") check(metadata["loopBodyExecuted"] == false && metadata["loopBodyEntries"] == 0 && metadata["fallbackReason"] is String) { "Prior loop fallback control claimed kernel execution: $metadata" }
        }
        if (metadata["engine"] != "localLoop") return
        val entered = metadata["localLoopExecuted"] == true
        val entries = metadata["localLoopEntries"] as Int
        if (entered) check(entries > 0 && (metadata["loweredLoopCount"] as Int) > 0 && (metadata["generatedClassCount"] as Int) == 1 && (metadata["localValueCount"] as Int) > 0) { "Local execution lacks positive lowering mechanisms: $metadata" }
        else check(entries == 0 && metadata["fallbackReason"] is String) { "Nonexecution lacked explicit fallback: $metadata" }
        if (workload == "holdoutProjection") {
            check(entered && metadata["localLoopPrimitiveUsed"] == true && metadata["fallbackReason"] == null &&
                (metadata["eligibleLoopCount"] as Int) > 0 && (metadata["primitiveSiteCount"] as Int) > 0 &&
                (metadata["bodyInstructionCount"] as Int) > 0) { "Projection did not execute actual local/primitive lowering without fallback: $metadata" }
        }
        if (workload == "paths" || workload == "fallbackClosure") check(!entered && entries == 0 && metadata["localLoopPrimitiveUsed"] == false && metadata["fallbackReason"] is String) { "Control claimed local execution: $metadata" }
    }

    public fun validate(executable: VMLocalLoopExecutable): Map<String, Any?> {
        repeat(3) { assertOutput(executable.execute(freshEnv())); assertPristine(); assertMode(executable.metadata()) }
        return mapOf(
            "workload" to workload, "dataset" to dataset, "validatedExecutions" to 3,
            "sourceSha256" to MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
            "expectedOutputHash" to expected.hashCode(),
        ) + executable.metadata()
    }
}

private class LegacyLocalLoopAdapter(private val delegate: VMLoopBodyExecutable) : VMLocalLoopExecutable {
    override val generatedClassCount: Int get() = delegate.generatedClassCount
    override val generatedBytecodeBytes: Int get() = delegate.generatedBytecodeBytes
    override fun execute(env: MutableMap<String, Any?>): Any? = delegate.execute(env)
    override fun metadata(): Map<String, Any?> = delegate.metadata() + mapOf("localLoopExecuted" to false, "localLoopEntries" to 0, "localLoopPrimitiveUsed" to false)
}

/** New local-runtime types are resolved only after choosing the new engine branch. */
private object GeneratedLocalLoopFactory {
    fun prepare(source: String): VMLocalLoopExecutable {
        val funcs: Map<String, FuncDecl> = emptyMap()
        val parsed = Parser(Lexer(source).lex(), source).parse()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, StdLib.fns).compile(transform.body.statements)
        val bytecode = Compiler(funcs, StdLib.fns).compile(ir)
        return GeneratedLocalLoopMachine(VM(StdLib.fns, funcs), JvmLocalLoopCompiler.prepare(bytecode))
    }
}

private class GeneratedLocalLoopMachine(private val vm: VM, private val plan: LocalLoopPlan) : VMLocalLoopExecutable {
    override val generatedClassCount: Int get() = plan.generatedClassCount
    override val generatedBytecodeBytes: Int get() = plan.generatedBytecodeBytes
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.executeLocalLoops(plan, env)
    override fun metadata(): Map<String, Any?> = linkedMapOf(
        "engine" to "localLoop", "localLoopExecuted" to vm.localLoopExecuted,
        "localLoopEntries" to vm.localLoopEntries, "localLoopPrimitiveUsed" to vm.localLoopPrimitiveUsed,
        "fallbackReason" to vm.localLoopFallbackReason, "planFallbackReason" to plan.fallbackReason,
        "eligibleLoopCount" to plan.eligibleLoopCount, "loweredLoopCount" to plan.loweredLoopCount,
        "generatedClassCount" to plan.generatedClassCount, "bodyInstructionCount" to plan.bodyInstructionCount,
        "generatedBytecodeBytes" to plan.generatedBytecodeBytes, "localValueCount" to plan.localValueCount,
        "primitiveSiteCount" to plan.primitiveSiteCount, "spillSiteCount" to plan.spillSiteCount,
        "guardSiteCount" to plan.guardSiteCount,
    )
}
