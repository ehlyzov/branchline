package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.compileStream
import io.github.ehlyzov.branchline.json.formatCanonicalJson
import io.github.ehlyzov.branchline.json.parseJsonObjectInput
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.VMExecutionPath
import io.github.ehlyzov.branchline.vm.VMExecutionReport
import io.github.ehlyzov.branchline.vm.VMFallbackPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// Read the checked-out guide on each target. Programs are never copied into tests.
internal expect fun readGettingStartedGuide(): String

class GettingStartedExamplesTest {
    // These business expectations are independent of the guide's advertised output
    // and of either engine's result. Do not derive an oracle from another engine.
    private val expectedExamples = linkedMapOf(
        "hello" to """{"greeting":"Hello, Ada"}""",
        "defaults" to """{"name":"Guest","nickname":null,"active":false,"attempts":0}""",
        "available-items" to """{"items":[{"sku":"B","total":14},{"sku":"C","total":3}]}""",
    )

    @Test
    fun documented_examples_match_independent_outputs_in_interpreter() {
        checkExamples(readGettingStartedGuide(), ExecutionEngine.INTERPRETER)
    }

    @Test
    fun documented_examples_match_independent_outputs_in_strict_vm() {
        checkExamples(readGettingStartedGuide(), ExecutionEngine.VM)
    }

    @Test
    fun defaults_preserve_empty_strings_and_handle_missing_fields() {
        val program = examples(readGettingStartedGuide()).getValue("defaults").program
        for (engine in ExecutionEngine.entries) {
            checkRun(program, "{}",
                """{"name":"Guest","nickname":null,"active":true,"attempts":3}""", engine)
            checkRun(program, """{"name":"","nickname":"Ace","active":true,"attempts":2}""",
                """{"name":"","nickname":"Ace","active":true,"attempts":2}""", engine)
            checkRun(program, """{"active":null,"attempts":null}""",
                """{"name":"Guest","nickname":null,"active":true,"attempts":3}""", engine)
        }
    }

    @Test
    fun filter_handles_missing_null_empty_and_fully_filtered_arrays() {
        val program = examples(readGettingStartedGuide()).getValue("available-items").program
        for (engine in ExecutionEngine.entries) {
            for (input in listOf("{}", """{"items":null}""", """{"items":[]}""",
                """{"items":[{"sku":"A","qty":0,"price":10},{"sku":"B","qty":-1,"price":7}]}""")) {
                checkRun(program, input, """{"items":[]}""", engine)
            }
        }
    }

    @Test
    fun wrong_program_output_is_rejected_by_the_execution_gate() {
        val original = readGettingStartedGuide()
        val corrupted = original.replace("\"Hello, \" + input.name", "\"Goodbye, \" + input.name")
        assertNotEquals(original, corrupted, "Negative control must change the program")
        for (engine in ExecutionEngine.entries) {
            assertFailsWith<AssertionError> { checkExamples(corrupted, engine) }
        }
    }

    @Test
    fun stale_documented_output_is_rejected_by_the_same_gate() {
        val original = readGettingStartedGuide()
        val corrupted = original.replace("{\"greeting\":\"Hello, Ada\"}", "{\"greeting\":\"Hello, Grace\"}")
        assertNotEquals(original, corrupted, "Negative control must change the documented output")
        assertFailsWith<AssertionError> { checkExamples(corrupted, ExecutionEngine.INTERPRETER) }
    }

    @Test
    fun an_unmarked_program_or_removed_marker_is_rejected() {
        val original = readGettingStartedGuide()
        assertFailsWith<AssertionError> {
            examples(original + "\n```branchline\nTRANSFORM Unchecked { OUTPUT {} }\n```\n")
        }
        assertFailsWith<AssertionError> {
            examples(original.replace("<!-- onboarding:hello:program -->", ""))
        }
    }

    private data class Example(val program: String, val input: String, val output: String)

    private fun examples(markdown: String): Map<String, Example> {
        val blocks = Regex(
            "<!-- onboarding:([a-z-]+):(program|input|output) -->\\s*\n```([a-z]+)\n([\\s\\S]*?)\n```",
        ).findAll(markdown).toList()
        val expectedKeys = expectedExamples.keys.flatMap { id ->
            listOf("$id:program", "$id:input", "$id:output")
        }.toSet()
        assertEquals(expectedKeys.size, blocks.size, "Every guide fixture must have exactly three marked blocks")
        val byKey = blocks.associate { match ->
            val (id, kind, language, body) = match.destructured
            assertEquals(if (kind == "program") "branchline" else "json", language, "$id:$kind language")
            "$id:$kind" to body
        }
        assertEquals(expectedKeys, byKey.keys, "Missing, duplicate, or unknown onboarding marker")
        assertEquals(blocks.size, Regex("<!-- onboarding:").findAll(markdown).count(), "Malformed marker")
        assertEquals(expectedExamples.size, Regex("^```branchline\\s*$", RegexOption.MULTILINE).findAll(markdown).count(),
            "Every Branchline block in the guide must belong to this gate")
        return expectedExamples.keys.associateWith { id ->
            Example(byKey.getValue("$id:program"), byKey.getValue("$id:input"), byKey.getValue("$id:output"))
        }
    }

    private fun checkExamples(markdown: String, engine: ExecutionEngine) {
        for ((id, example) in examples(markdown)) {
            val expected = expectedExamples.getValue(id)
            assertJsonEquals(expected, parseJsonObjectInput(example.output), "$id documented output")
            checkRun(example.program, example.input, expected, engine)
        }
    }

    private fun checkRun(program: String, input: String, expected: String, engine: ExecutionEngine) {
        val parsed = Parser(Lexer(program).lex(), program).parse()
        SemanticAnalyzer(StdLib.fns.keys).analyze(parsed)
        val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
        val reports = mutableListOf<VMExecutionReport>()
        val runner = compileStream(
            transform,
            hostFns = StdLib.fns,
            hostFnMeta = StdLib.meta,
            engine = engine,
            vmFallbackPolicy = VMFallbackPolicy.NONE,
            onVMExecution = reports::add,
        )
        val actual = runner(parseJsonObjectInput(input))
        assertJsonEquals(expected, actual, "$engine program output for input $input")
        if (engine == ExecutionEngine.VM) {
            assertEquals(listOf(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true)), reports,
                "A matching interpreter fallback result is not VM coverage")
        } else {
            assertTrue(reports.isEmpty(), "Interpreter run must not report bytecode execution")
        }
    }

    private fun assertJsonEquals(expected: String, actual: Any?, message: String) {
        assertEquals(formatCanonicalJson(parseJsonObjectInput(expected)), formatCanonicalJson(actual), message)
    }
}
