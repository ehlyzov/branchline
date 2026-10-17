package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.cli.formatJson
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
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Fork(value = 2, jvmArgsAppend = ["-Xms256m", "-Xmx768m"])
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public open class VMFactorialBenchmark {
    @Param("interpreter", "baseline", "slots", "fusion", "both") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "comprehension", "reduction", "holdoutProjection", "holdoutDeepPaths") public lateinit var workload: String
    private lateinit var fixture: VMFactorialFixture
    private lateinit var executable: VMFactorialExecutable

    @Setup(Level.Trial)
    public fun setup() {
        fixture = VMFactorialFixture(workload, dataset)
        println(formatJson(fixture.validate(fixture.prepare(engine, collectDiagnostics = true)) + mapOf("engine" to engine, "boundary" to "fresh-env-plus-prepared-execution", "timedDiagnostics" to false), pretty = false))
        executable = fixture.prepare(engine)
    }

    /** JMH consumes the returned raw result; environment creation is timed for every engine. */
    @Benchmark public fun execute(): Any? = executable.execute(fixture.freshEnv())
}

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Fork(value = 2, jvmArgsAppend = ["-Xms256m", "-Xmx768m"])
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public open class VMFactorialPreparationBenchmark {
    @Param("interpreter", "baseline", "slots", "fusion", "both") public lateinit var engine: String
    @Param("small", "large") public lateinit var dataset: String
    @Param("paths", "comprehension", "reduction", "holdoutProjection", "holdoutDeepPaths") public lateinit var workload: String
    private lateinit var fixture: VMFactorialFixture

    @Setup(Level.Trial) public fun setup() {
        fixture = VMFactorialFixture(workload, dataset)
        println(formatJson(fixture.validate(fixture.prepare(engine, collectDiagnostics = true)) + mapOf("engine" to engine, "boundary" to "source-to-prepared-executor", "timedDiagnostics" to false), pretty = false))
    }

    @Benchmark public fun prepare(): VMFactorialExecutable = fixture.prepare(engine)
}
