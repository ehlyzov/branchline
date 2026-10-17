@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.cli.formatJson
import io.github.ehlyzov.branchline.ir.Exec
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
public open class VMPreparedBlockBenchmark {
    @Param("interpreter", "baseline", "preparedControl", "preparedOptimized") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMPreparedBlockFixture
    private lateinit var executable: VMPreparedBlockExecutable

    @Setup(Level.Trial) public fun setup() {
        fixture = VMPreparedBlockFixture(workload, dataset)
        executable = fixture.prepare(engine)
        println(formatJson(fixture.validate(executable) + mapOf("engine" to engine, "boundary" to "fresh-env-plus-prepared-execution"), pretty = false))
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
public open class VMPreparedBlockPreparationBenchmark {
    @Param("interpreter", "baseline", "preparedControl", "preparedOptimized") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "reduction", "holdoutProjection") public lateinit var workload: String
    private lateinit var fixture: VMPreparedBlockFixture

    @Setup(Level.Trial) public fun setup() {
        fixture = VMPreparedBlockFixture(workload, dataset)
        println(formatJson(fixture.validate(fixture.prepare(engine)) + mapOf("engine" to engine, "boundary" to "source-to-prepared-executor"), pretty = false))
    }

    @Benchmark public fun prepare(): VMPreparedBlockExecutable = fixture.prepare(engine)
}

public interface VMPreparedBlockExecutable {
    public fun execute(env: MutableMap<String, Any?>): Any?
    /** Detached scalar metadata only; never returns plans, environments or outputs. */
    public fun metadata(): Map<String, Any?>
}

/** Public helper for independent parent probes; the baseline branch uses the original VM API. */
public object VMPreparedBlockHarness {
    public val engines: List<String> = listOf("interpreter", "baseline", "preparedControl", "preparedOptimized")
    public val primaryWorkloads: List<String> = listOf("paths", "reduction", "holdoutProjection")

    @JvmStatic public fun prepareSource(source: String, engine: String): VMPreparedBlockExecutable {
        require(engine in engines) { "Unknown engine: $engine" }
        val funcs: Map<String, FuncDecl> = emptyMap()
        val parsed = Parser(Lexer(source).lex(), source).parse()
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        val ir = ToIR(funcs, StdLib.fns).compile(transform.body.statements)
        if (engine == "interpreter") return PreparedBlockInterpreter(Exec(ir, hostFns = StdLib.fns, hostFnMeta = StdLib.meta))
        val bytecode = Compiler(funcs, StdLib.fns).compile(ir)
        val vm = VM(StdLib.fns, funcs)
        if (engine == "baseline") return PreparedBlockBaseline(vm, bytecode)
        return PreparedBlockMachine(vm, vm.prepareBlocks(bytecode, optimize = engine == "preparedOptimized"), engine)
    }
}

public class VMPreparedBlockFixture(public val workload: String, public val dataset: String) {
    private val native = VMFactorialFixture(workload, dataset)
    public val source: String get() = native.source
    public val input: Map<String, Any?> get() = native.input
    public val expected: Any get() = native.expected
    public fun freshEnv(): MutableMap<String, Any?> = native.freshEnv()
    public fun assertOutput(actual: Any?) { native.assertOutput(actual) }
    public fun assertPristine() { native.assertPristine() }
    public fun prepare(engine: String): VMPreparedBlockExecutable = VMPreparedBlockHarness.prepareSource(source, engine)

    public fun validate(executable: VMPreparedBlockExecutable): Map<String, Any?> {
        repeat(3) { assertOutput(executable.execute(freshEnv())); assertPristine() }
        val metadata = executable.metadata()
        val prepared = metadata["engine"] == "preparedControl" || metadata["engine"] == "preparedOptimized"
        if (workload in VMPreparedBlockHarness.primaryWorkloads && prepared) {
            check(metadata["preparedExecuted"] == true && metadata["fallbackReason"] == null) { "Primary workload did not run prepared dispatch: $metadata" }
            val optimized = metadata["engine"] == "preparedOptimized"
            check(metadata["optimizedExecuted"] == optimized) { "Incorrect actual optimization mode: $metadata" }
            if (optimized) check((metadata["directPathSites"] as Int) + (metadata["mirrorStoreSites"] as Int) > 0) { "Optimized execution has no prepared mechanism sites: $metadata" }
        }
        if (workload == "fallbackClosure" && prepared) {
            check(metadata["fallbackReason"] is String && metadata["preparedExecuted"] == false && metadata["optimizedExecuted"] == false) { "Closure control failed explicit ordinary-VM fallback: $metadata" }
        }
        return mapOf(
            "workload" to workload, "dataset" to dataset, "validatedExecutions" to 3,
            "sourceSha256" to MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
            "expectedOutputHash" to expected.hashCode(),
        ) + metadata
    }
}

private class PreparedBlockInterpreter(private val exec: Exec) : VMPreparedBlockExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = exec.run(env)
    override fun metadata(): Map<String, Any?> = mapOf("engine" to "interpreter", "preparedExecuted" to false, "optimizedExecuted" to false, "fallbackReason" to null)
}

private class PreparedBlockBaseline(private val vm: VM, private val bytecode: Bytecode) : VMPreparedBlockExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.execute(bytecode, env)
    override fun metadata(): Map<String, Any?> = mapOf("engine" to "baseline", "executionApi" to "VM.execute", "preparedExecuted" to false, "optimizedExecuted" to false, "fallbackReason" to null)
}

private class PreparedBlockMachine(private val vm: VM, private val plan: PreparedBlockPlan, private val engine: String) : VMPreparedBlockExecutable {
    override fun execute(env: MutableMap<String, Any?>): Any? = vm.executeBlocks(plan, env)
    override fun metadata(): Map<String, Any?> = linkedMapOf(
        "engine" to engine, "requestedOptimized" to plan.optimize,
        "preparedExecuted" to vm.preparedBlockExecuted, "optimizedExecuted" to vm.preparedBlockOptimized,
        "fallbackReason" to vm.preparedBlockFallbackReason, "planFallbackReason" to plan.fallbackReason,
        "sourceInstructionCount" to plan.sourceInstructionCount, "blockCount" to plan.blockCount,
        "directPathSites" to plan.directPathCount, "mirrorStoreSites" to plan.mirrorStoreCount,
    )
}
