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
public open class VMLoopBodyBenchmark {
    @Param("interpreter", "baseline", "preparedOptimized", "loopBody") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMLoopBodyFixture
    private lateinit var executable: VMLoopBodyExecutable

    @Setup(Level.Trial) public fun setup() {
        fixture = VMLoopBodyFixture(workload, dataset)
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
public open class VMLoopBodyPreparationBenchmark {
    @Param("interpreter", "baseline", "preparedOptimized", "loopBody") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMLoopBodyFixture

    @Setup(Level.Trial) public fun setup() {
        fixture = VMLoopBodyFixture(workload, dataset)
        println(formatJson(fixture.validate(fixture.prepare(engine)) + mapOf("boundary" to "source-to-executor-including-class-generation"), pretty = false))
    }
    @Benchmark public fun prepare(): VMLoopBodyExecutable = fixture.prepare(engine)
}

public interface VMLoopBodyExecutable {
    public fun execute(env: MutableMap<String, Any?>): Any?
    public fun metadata(): Map<String, Any?>
    public val generatedClassCount: Int get() = 0
    public val generatedBytecodeBytes: Int get() = 0
}

public object VMLoopBodyHarness {
    public val engines: List<String> = listOf("interpreter", "baseline", "preparedOptimized", "loopBody")
    public val primaryWorkloads: List<String> = listOf("paths", "reduction", "holdoutProjection")

    @JvmStatic public fun prepareSource(source: String, engine: String): VMLoopBodyExecutable {
        require(engine in engines) { "Unknown engine: $engine" }
        // Keep references to newly added runtime symbols out of every legacy branch.
        return if (engine == "loopBody") GeneratedLoopBodyFactory.prepare(source)
        else LegacyLoopBodyAdapter(VMPreparedBlockHarness.prepareSource(source, engine))
    }
}

public class VMLoopBodyFixture(public val workload: String, public val dataset: String) {
    private val native = VMFactorialFixture(workload, dataset)
    public val source: String get() = native.source
    public val input: Map<String, Any?> get() = native.input
    public val expected: Any get() = native.expected
    public fun freshEnv(): MutableMap<String, Any?> = native.freshEnv()
    public fun assertOutput(actual: Any?) { native.assertOutput(actual) }
    public fun assertPristine() { native.assertPristine() }
    public fun prepare(engine: String): VMLoopBodyExecutable = VMLoopBodyHarness.prepareSource(source, engine)

    public fun assertMode(metadata: Map<String, Any?>) {
        when (metadata["engine"]) {
            "preparedOptimized" -> if (workload in VMLoopBodyHarness.primaryWorkloads) {
                check(metadata["preparedExecuted"] == true && metadata["optimizedExecuted"] == true && metadata["fallbackReason"] == null) { "Prior prepared control did not execute its actual mode: $metadata" }
            } else if (workload == "fallbackClosure") {
                check(metadata["preparedExecuted"] == false && metadata["optimizedExecuted"] == false && metadata["fallbackReason"] is String) { "Prior prepared closure control failed explicit fallback: $metadata" }
            }
            "loopBody" -> {
                val entered = metadata["loopBodyExecuted"] == true
                val entries = metadata["loopBodyEntries"] as Int
                if (entered) {
                    check(entries > 0 && (metadata["loweredLoopCount"] as Int) > 0 && (metadata["generatedClassCount"] as Int) == 1) { "Generated execution without a positive mechanism: $metadata" }
                } else check(entries == 0 && metadata["fallbackReason"] is String) { "Nonexecution lacked explicit fallback: $metadata" }
                if (workload == "holdoutProjection") {
                    check(entered && metadata["fallbackReason"] == null && (metadata["eligibleLoopCount"] as Int) > 0 && (metadata["bodyInstructionCount"] as Int) > 0) { "Projection did not execute a generated body without fallback: $metadata" }
                }
                if (workload == "paths" || workload == "fallbackClosure") {
                    check(!entered && entries == 0 && metadata["fallbackReason"] is String) { "Non-loop/closure control claimed generated execution: $metadata" }
                }
            }
        }
    }

    public fun validate(executable: VMLoopBodyExecutable): Map<String, Any?> {
        repeat(3) { assertOutput(executable.execute(freshEnv())); assertPristine(); assertMode(executable.metadata()) }
        return mapOf(
            "workload" to workload, "dataset" to dataset, "validatedExecutions" to 3,
            "sourceSha256" to MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
            "expectedOutputHash" to expected.hashCode(),
        ) + executable.metadata()
    }
}

private class LegacyLoopBodyAdapter(private val delegate: VMPreparedBlockExecutable) : VMLoopBodyExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = delegate.execute(env)
    override fun metadata(): Map<String, Any?> = delegate.metadata() + mapOf("loopBodyExecuted" to false, "loopBodyEntries" to 0, "generatedClassCount" to 0)
}

/** Separate class ensures baseline loading/execution does not resolve new loop-runtime symbols. */
private object GeneratedLoopBodyFactory {
    fun prepare(source: String): VMLoopBodyExecutable {
        val funcs: Map<String, FuncDecl> = emptyMap()
        val parsed = Parser(Lexer(source).lex(), source).parse()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, StdLib.fns).compile(transform.body.statements)
        val bytecode = Compiler(funcs, StdLib.fns).compile(ir)
        return GeneratedLoopBodyMachine(VM(StdLib.fns, funcs), JvmLoopCompiler.prepare(bytecode))
    }
}

private class GeneratedLoopBodyMachine(private val vm: VM, private val plan: LoopBodyPlan) : VMLoopBodyExecutable {
    override val generatedClassCount: Int get() = plan.generatedClassCount
    override val generatedBytecodeBytes: Int get() = plan.generatedBytecodeBytes
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.executeLoopBodies(plan, env)
    override fun metadata(): Map<String, Any?> = linkedMapOf(
        "engine" to "loopBody", "loopBodyExecuted" to vm.loopBodyExecuted, "loopBodyEntries" to vm.loopBodyEntries,
        "fallbackReason" to vm.loopBodyFallbackReason, "planFallbackReason" to plan.fallbackReason,
        "eligibleLoopCount" to plan.eligibleLoopCount, "loweredLoopCount" to plan.loweredLoopCount,
        "generatedClassCount" to plan.generatedClassCount, "bodyInstructionCount" to plan.bodyInstructionCount,
        "generatedBytecodeBytes" to plan.generatedBytecodeBytes,
    )
}
