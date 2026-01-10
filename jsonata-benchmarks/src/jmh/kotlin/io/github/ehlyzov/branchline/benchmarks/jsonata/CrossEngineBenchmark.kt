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

private const val DEFAULT_CASE_ID: String = "all"
private const val CASE_FILTER_PROPERTY: String = "jsonata.caseFilter"

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public open class CrossEngineBenchmark {
    @Param(
        ENGINE_KOTLIN,
        ENGINE_BRANCHLINE_INTERPRETER,
        ENGINE_BRANCHLINE_VM,
        ENGINE_DASHJOIN,
        ENGINE_IBM,
    )
    public lateinit var engineId: String

    @Param(DEFAULT_CASE_ID)
    public lateinit var caseId: String

    private lateinit var preparedCases: List<PreparedCase>
    private var timeoutRunner: BenchmarkTimeoutRunner? = null

    @Setup(Level.Trial)
    public fun setup() {
        val filter = resolveCaseFilter(caseId)
        val cases = filterCases(CrossEngineCases.loadAll(), filter)
        preparedCases = cases.map { case ->
            val inputs = buildInputs(case)
            prepareCase(case, inputs, engineId)
        }
        val timeoutMs = resolveBenchmarkTimeoutMs()
        timeoutRunner = if (timeoutMs == null) null else BenchmarkTimeoutRunner(timeoutMs)
    }

    @TearDown(Level.Trial)
    public fun teardown() {
        timeoutRunner?.close()
    }

    @Benchmark
    public fun evaluate(blackhole: Blackhole) {
        for (prepared in preparedCases) {
            val errorMessage = prepared.errorMessage
            if (errorMessage != null) {
                blackhole.consume(errorMessage)
                continue
            }
            val disabledMessage = prepared.disabledMessage
            if (disabledMessage != null) {
                blackhole.consume(disabledMessage)
                continue
            }
            val runner = prepared.execute ?: continue
            val result = try {
                val timeoutRunner = timeoutRunner
                if (timeoutRunner == null) {
                    runner()
                } else {
                    timeoutRunner.run { runner() }
                }
            } catch (ex: Throwable) {
                BenchmarkErrorReporter.record(prepared.engineId, prepared.id, "evaluate", ex)
                prepared.disable(ex.message ?: ex::class.simpleName ?: "error")
                ex
            }
            blackhole.consume(result)
        }
    }

    private fun prepareCase(
        case: CrossEngineCase,
        inputs: CrossEngineInput,
        engineId: String,
    ): PreparedCase {
        if (case.loadError != null) {
            BenchmarkErrorReporter.recordMessage(engineId, case.id, "load-case", case.loadError)
            return PreparedCase(case.id, engineId, case.loadError)
        }
        return when (engineId) {
            ENGINE_KOTLIN -> prepareKotlin(case, inputs)
            ENGINE_BRANCHLINE_INTERPRETER, ENGINE_BRANCHLINE_VM -> prepareBranchline(case, inputs, engineId)
            ENGINE_DASHJOIN -> prepareJsonata(case, inputs, ENGINE_DASHJOIN)
            ENGINE_IBM -> prepareJsonata(case, inputs, ENGINE_IBM)
            else -> error("Unknown engine: $engineId")
        }
    }
}

private class PreparedCase(
    val id: String,
    val engineId: String,
    val errorMessage: String? = null,
    val execute: (() -> Any?)? = null,
) {
    var disabledMessage: String? = null
        private set

    fun disable(message: String) {
        if (disabledMessage == null) {
            disabledMessage = message
        }
    }
}

private fun buildInputs(case: CrossEngineCase): CrossEngineInput {
    val kotlinInput = JsonataInputs.parseKotlinValue(case.inputJson)
    val branchlineInput = kotlinInput
    val ibmInput = JsonataInputs.parseJsonNode(case.inputJson)
    return CrossEngineInput(
        kotlinInput = kotlinInput,
        branchlineInput = branchlineInput,
        dashjoinInput = kotlinInput,
        ibmInput = ibmInput,
    )
}

private fun prepareKotlin(case: CrossEngineCase, inputs: CrossEngineInput): PreparedCase {
    val evalId = case.kotlinEvalId
    if (evalId == null) {
        val message = "Missing kotlin eval for ${case.id}."
        BenchmarkErrorReporter.recordMessage(ENGINE_KOTLIN, case.id, "compile", message)
        return PreparedCase(case.id, ENGINE_KOTLIN, message)
    }
    val evaluator = KotlinEvaluators.resolve(evalId)
    return PreparedCase(case.id, ENGINE_KOTLIN) {
        evaluator(inputs.kotlinInput)
    }
}

private fun prepareBranchline(
    case: CrossEngineCase,
    inputs: CrossEngineInput,
    engineId: String,
): PreparedCase {
    val program = case.branchlineProgram
    if (program == null) {
        val message = "Missing Branchline analog for ${case.id}."
        BenchmarkErrorReporter.recordMessage(engineId, case.id, "compile", message)
        return PreparedCase(case.id, engineId, message)
    }
    val runner = try {
        if (engineId == ENGINE_BRANCHLINE_INTERPRETER) {
            BranchlineCompiler.compileInterpreter(program)
        } else {
            BranchlineCompiler.compileVm(program)
        }
    } catch (ex: Throwable) {
        BenchmarkErrorReporter.record(engineId, case.id, "compile", ex)
        return PreparedCase(case.id, engineId, ex.message ?: ex::class.simpleName)
    }
    return PreparedCase(case.id, engineId) {
        runner(inputs.branchlineInput)
    }
}

private fun prepareJsonata(
    case: CrossEngineCase,
    inputs: CrossEngineInput,
    engineId: String,
): PreparedCase {
    val engine = try {
        JsonataEngines.require(engineId)
    } catch (ex: Throwable) {
        BenchmarkErrorReporter.record(engineId, case.id, "engine", ex)
        return PreparedCase(case.id, engineId, ex.message ?: ex::class.simpleName)
    }
    val compiled = try {
        engine.compile(case.jsonataExpression)
    } catch (ex: Throwable) {
        BenchmarkErrorReporter.record(engineId, case.id, "compile", ex)
        return PreparedCase(case.id, engineId, ex.message ?: ex::class.simpleName)
    }
    return PreparedCase(case.id, engineId) {
        val input = if (engineId == ENGINE_DASHJOIN) inputs.dashjoinInput else inputs.ibmInput
        engine.evaluate(compiled, input)
    }
}

private fun resolveCaseFilter(paramFilter: String): String {
    val propertyFilter = System.getProperty(CASE_FILTER_PROPERTY)
    return if (propertyFilter.isNullOrBlank()) paramFilter else propertyFilter
}

private fun filterCases(
    cases: List<CrossEngineCase>,
    filter: String,
): List<CrossEngineCase> {
    val trimmed = filter.trim()
    if (trimmed.isEmpty() || trimmed == DEFAULT_CASE_ID) return cases
    val ids = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    require(ids.isNotEmpty()) { "Case filter must not be empty." }
    val filtered = cases.filter { ids.contains(it.id) }
    require(filtered.isNotEmpty()) { "No cases matched filter: $filter" }
    return filtered
}
