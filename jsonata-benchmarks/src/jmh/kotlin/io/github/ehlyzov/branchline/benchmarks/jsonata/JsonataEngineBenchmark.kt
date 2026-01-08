package io.github.ehlyzov.branchline.benchmarks.jsonata

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

private const val DEFAULT_CASE_FILTER: String = "all"
private const val CASE_FILTER_PROPERTY: String = "jsonata.caseFilter"

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public open class JsonataEngineBenchmark {
    @Param(
        ENGINE_DASHJOIN,
        ENGINE_IBM,
    )
    public lateinit var engineId: String

    @Param(DEFAULT_CASE_FILTER)
    public lateinit var caseId: String

    private lateinit var engine: JsonataEngine
    private lateinit var preparedCases: List<JsonataPreparedCase>
    private var timeoutRunner: BenchmarkTimeoutRunner? = null

    @Setup(Level.Trial)
    public fun setup() {
        engine = JsonataEngines.require(engineId)
        val root = JsonataTestSuite.resolveRoot()
        val filter = resolveCaseFilter(caseId)
        val caseIds = filterCaseIds(JsonataTestSuite.listCaseIds(root), filter)
        val prepared = ArrayList<JsonataPreparedCase>(caseIds.size)
        for (caseId in caseIds) {
            val testCase = try {
                JsonataTestSuite.loadCase(root, caseId)
            } catch (ex: Throwable) {
                BenchmarkErrorReporter.record(engineId, caseId, "load-case", ex)
                prepared.add(JsonataPreparedCase(caseId, loadError = ex.message ?: ex::class.simpleName))
                continue
            }
            val input = engine.prepareInput(testCase.inputJson)
            val expectedFailure = testSuiteFailureReasons(testCase).isNotEmpty()
            val compiled = try {
                engine.compile(testCase.expression)
            } catch (ex: Throwable) {
                BenchmarkErrorReporter.record(engineId, testCase.id, "compile", ex)
                prepared.add(
                    JsonataPreparedCase(
                        id = testCase.id,
                        expression = testCase.expression,
                        input = input,
                        compiled = null,
                        compileError = ex.message ?: ex::class.simpleName,
                        expectedFailure = expectedFailure,
                    ),
                )
                continue
            }
            prepared.add(
                JsonataPreparedCase(
                    id = testCase.id,
                    expression = testCase.expression,
                    input = input,
                    compiled = compiled,
                    expectedFailure = expectedFailure,
                ),
            )
        }
        preparedCases = prepared
        val timeoutMs = resolveBenchmarkTimeoutMs()
        timeoutRunner = if (timeoutMs == null) null else BenchmarkTimeoutRunner(timeoutMs)
    }

    @TearDown(Level.Trial)
    public fun teardown() {
        timeoutRunner?.close()
    }

    @Benchmark
    public fun parse(blackhole: Blackhole) {
        for (prepared in preparedCases) {
            if (prepared.loadError != null) {
                blackhole.consume(prepared.loadError)
                continue
            }
            if (prepared.compileError != null) {
                blackhole.consume(prepared.compileError)
                continue
            }
            val disabledMessage = prepared.parseDisabledMessage
            if (disabledMessage != null) {
                blackhole.consume(disabledMessage)
                continue
            }
            val result = try {
                val timeoutRunner = timeoutRunner
                if (timeoutRunner == null) {
                    engine.compile(prepared.expression)
                } else {
                    timeoutRunner.run { engine.compile(prepared.expression) }
                }
            } catch (ex: Throwable) {
                BenchmarkErrorReporter.record(engineId, prepared.id, "parse", ex)
                prepared.disableParse(ex.message ?: ex::class.simpleName ?: "error")
                ex
            }
            blackhole.consume(result)
        }
    }

    @Benchmark
    public fun evaluate(blackhole: Blackhole) {
        for (prepared in preparedCases) {
            if (prepared.loadError != null) {
                blackhole.consume(prepared.loadError)
                continue
            }
            if (prepared.compileError != null) {
                blackhole.consume(prepared.compileError)
                continue
            }
            val disabledMessage = prepared.evaluateDisabledMessage
            if (disabledMessage != null) {
                blackhole.consume(disabledMessage)
                continue
            }
            val result = try {
                val timeoutRunner = timeoutRunner
                if (timeoutRunner == null) {
                    engine.evaluate(requireNotNull(prepared.compiled), prepared.input)
                } else {
                    timeoutRunner.run { engine.evaluate(requireNotNull(prepared.compiled), prepared.input) }
                }
            } catch (ex: Throwable) {
                BenchmarkErrorReporter.record(engineId, prepared.id, "evaluate", ex)
                prepared.disableEvaluate(ex.message ?: ex::class.simpleName ?: "error")
                ex
            }
            blackhole.consume(result)
        }
    }
}

private class JsonataPreparedCase(
    val id: String,
    val expression: String = "",
    val input: Any? = null,
    val compiled: Any? = null,
    val compileError: String? = null,
    val expectedFailure: Boolean = false,
    val loadError: String? = null,
) {
    var parseDisabledMessage: String? = null
        private set
    var evaluateDisabledMessage: String? = null
        private set

    fun disableParse(message: String) {
        if (parseDisabledMessage == null) {
            parseDisabledMessage = message
        }
    }

    fun disableEvaluate(message: String) {
        if (evaluateDisabledMessage == null) {
            evaluateDisabledMessage = message
        }
    }
}

private fun resolveCaseFilter(paramFilter: String): String {
    val propertyFilter = System.getProperty(CASE_FILTER_PROPERTY)
    return if (propertyFilter.isNullOrBlank()) paramFilter else propertyFilter
}

private fun filterCaseIds(
    caseIds: List<String>,
    filter: String,
): List<String> {
    val trimmed = filter.trim()
    if (trimmed.isEmpty() || trimmed == DEFAULT_CASE_FILTER) return caseIds
    val ids = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    require(ids.isNotEmpty()) { "Case filter must not be empty." }
    val filtered = caseIds.filter { ids.contains(it) }
    require(filtered.isNotEmpty()) { "No cases matched filter: $filter" }
    return filtered
}
