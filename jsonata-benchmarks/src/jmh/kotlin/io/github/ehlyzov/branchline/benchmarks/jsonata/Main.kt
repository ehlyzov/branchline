package io.github.ehlyzov.branchline.benchmarks.jsonata.test

import io.github.ehlyzov.branchline.benchmarks.jsonata.BenchmarkErrorReporter
import io.github.ehlyzov.branchline.benchmarks.jsonata.BranchlineCompiler
import io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineCase
import io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineCases
import io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineInput
import io.github.ehlyzov.branchline.benchmarks.jsonata.ENGINE_BRANCHLINE_INTERPRETER
import io.github.ehlyzov.branchline.benchmarks.jsonata.ENGINE_BRANCHLINE_VM
import io.github.ehlyzov.branchline.benchmarks.jsonata.ENGINE_DASHJOIN
import io.github.ehlyzov.branchline.benchmarks.jsonata.ENGINE_IBM
import io.github.ehlyzov.branchline.benchmarks.jsonata.ENGINE_KOTLIN
import io.github.ehlyzov.branchline.benchmarks.jsonata.JsonataEngines
import io.github.ehlyzov.branchline.benchmarks.jsonata.JsonataInputs
import io.github.ehlyzov.branchline.benchmarks.jsonata.KotlinEvaluators
import io.github.ehlyzov.branchline.benchmarks.jsonata.test.Loader.buildInputs
import io.github.ehlyzov.branchline.benchmarks.jsonata.test.Loader.prepareCase

object Loader {
    fun prepareCase(
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

    class PreparedCase(
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

    fun buildInputs(case: CrossEngineCase): CrossEngineInput {
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

    fun prepareKotlin(case: CrossEngineCase, inputs: CrossEngineInput): PreparedCase {
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
            if (engineId == ENGINE_BRANCHLINE_VM) {
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

    private fun filterCases(
        cases: List<CrossEngineCase>,
        filter: String,
    ): List<CrossEngineCase> {
        val trimmed = filter.trim()
        if (trimmed.isEmpty()) return cases
        val ids = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        require(ids.isNotEmpty()) { "Case filter must not be empty." }
        val filtered = cases.filter { ids.contains(it.id) }
        require(filtered.isNotEmpty()) { "No cases matched filter: $filter" }
        return filtered
    }

}

fun main(vararg args: String) {
    val cases = CrossEngineCases.loadAll()
    val engineIds = listOf(
        ENGINE_KOTLIN,
        ENGINE_BRANCHLINE_INTERPRETER,
        ENGINE_BRANCHLINE_VM,
        ENGINE_DASHJOIN,
        ENGINE_IBM,
    )

    val preparedCases = cases.forEach { case ->
        println(case.id)
        val inputs = buildInputs(case)
        val case = prepareCase(case, inputs, ENGINE_BRANCHLINE_INTERPRETER)
        if (case.errorMessage != null) {
            println(case.errorMessage)
            return@forEach
        }
        val runner = case.execute
        runner!!.invoke()
    }

}