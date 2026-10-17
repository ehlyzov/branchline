package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.json.JsonKeyMode
import io.github.ehlyzov.branchline.json.JsonParseOptions
import kotlin.test.*

class ExperimentalJsonPipelineTest {
    private val source = """
        TRANSFORM Test {
            LET size = LENGTH(input.payload);
            OUTPUT { id: row.id, size: size, amount: input.price.amount ?? 0 };
        }
    """.trimIndent()

    @Test
    fun automaticPlanUsesSameStrictVmPipelineAsEagerAndFreshValues() {
        val selective = ExperimentalJsonPipeline.prepare(source)
        val eager = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(forceEager = true))
        assertEquals(JsonMaterializationMode.SELECTIVE, selective.report.effectiveMode)
        assertEquals(JsonMaterializationMode.EAGER, eager.report.effectiveMode)
        assertEquals("FORCE_EAGER", eager.report.reason)
        assertEquals(setOf("input", "payload", "id", "price"), selective.report.selectedRootKeys)
        val text = """{"unused":[{"big":"unused"}],"id":"x","payload":"abcd","price":{"amount":3}}"""
        assertEquals("""{"id":"x","size":4,"amount":3}""", selective.execute(text))
        assertEquals(eager.execute(text), selective.execute(text))
        val first = selective.materialize(text)
        assertFalse(first.containsKey("unused"))
        assertNotSame(first, selective.materialize(text))
        (selective.report.selectedRootKeys as MutableSet<String>).clear()
        assertEquals(eager.execute(text), selective.execute(text))
        assertEquals(eager.execute("""{"id":null,"payload":null,"price":{}}"""),
            selective.execute("""{"id":null,"payload":null,"price":{}}"""))
    }

    @Test
    fun inputAliasCollisionAndSkippedErrorsMatchEager() {
        val selective = ExperimentalJsonPipeline.prepare(source)
        val eager = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(forceEager = true))
        for (text in listOf(
            """{"id":1,"payload":"outer","price":{},"input":{"payload":"inner","price":{"amount":9}}}""",
            """{"id":1,"payload":"outer","price":{},"inp\u0075t":null}""",
            """{"id":1,"payload":"outer","price":{},"input":3}""",
            """{"id":1,"unused":{"a":1,"a":2}}""",
            """{"id":1,"unused":"\uD800"}""",
            """{"id":1,"payload":1,"price":{}}""",
            " ", "{}",
        )) {
            val expected = runCatching { eager.execute(text) }
            val actual = runCatching { selective.execute(text) }
            assertEquals(expected.exceptionOrNull()?.message, actual.exceptionOrNull()?.message, text)
            if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), text)
        }
    }

    @Test
    fun fallbackIsExplicitAndPreservesWholeInputAndDynamicReads() {
        for (body in listOf("OUTPUT input;", "LET key = \"a\"; OUTPUT input[key];", "OUTPUT LENGTH(input);")) {
            val program = "TRANSFORM Test { $body }"
            val fallback = ExperimentalJsonPipeline.prepare(program)
            val eager = ExperimentalJsonPipeline.prepare(program, options = ExperimentalJsonPipelineOptions(forceEager = true))
            assertEquals(JsonMaterializationMode.EAGER, fallback.report.effectiveMode)
            assertEquals("UNSUPPORTED_SYNTAX", fallback.report.reason)
            assertEquals(eager.execute("""{"a":1,"b":2}"""), fallback.execute("""{"a":1,"b":2}"""))
        }
        for (mode in listOf(ContractValidationMode.WARN, ContractValidationMode.STRICT)) {
            val fallback = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(contractsMode = mode))
            val eager = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(forceEager = true, contractsMode = mode))
            assertEquals("CONTRACTS_ENABLED", fallback.report.reason)
            val text = """{"id":"x","payload":"abcd","price":{"amount":3},"unused":true}"""
            assertEquals(eager.execute(text), fallback.execute(text))
            assertEquals(
                runCatching { eager.execute("{}") }.exceptionOrNull()?.message,
                runCatching { fallback.execute("{}") }.exceptionOrNull()?.message,
            )
        }
        val numeric = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(
            jsonOptions = JsonParseOptions(keyMode = JsonKeyMode.NUMERIC)))
        assertEquals("NUMERIC_KEYS", numeric.report.reason)
        assertTrue(numeric.materialize("""{"unused":{"0":1}}""").containsKey("unused"))
    }

    @Test
    fun suspensionUsesEagerInputAndPreservesCompleteSnapshotOutput() {
        for (body in listOf("OUTPUT SUSPEND 0;", "LET suspended = SUSPEND 0; OUTPUT suspended;")) {
            val program = "TRANSFORM Test { $body }"
            val fallback = ExperimentalJsonPipeline.prepare(program)
            val eager = ExperimentalJsonPipeline.prepare(program, options = ExperimentalJsonPipelineOptions(forceEager = true))
            assertEquals(JsonMaterializationMode.EAGER, fallback.report.effectiveMode)
            assertEquals("UNSUPPORTED_SYNTAX", fallback.report.reason)
            val text = """{"a":1,"unused":2}"""
            val output = fallback.execute(text)
            assertEquals(eager.execute(text), output)
            assertTrue(output.contains("__suspended"))
            assertTrue(output.contains("unused"))
            assertTrue(fallback.materialize(text).containsKey("unused"))
        }
    }

    @Test
    fun ambientTracerEnabledAfterPreparationObservesCompleteInput() {
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val pipeline = ExperimentalJsonPipeline.prepare("TRANSFORM Test { OUTPUT input.id; }")
            val preparedSnapshot = pipeline.report
            val text = """{"id":1,"unused":{"nested":[2,3]}}"""
            assertEquals(JsonMaterializationMode.SELECTIVE, pipeline.report.effectiveMode)
            assertFalse(pipeline.materialize(text).containsKey("unused"))
            val observedInputs = mutableListOf<Map<*, *>>()
            Debug.tracer = object : Tracer {
                override val opts = TraceOptions(watch = setOf("input"))
                override fun on(event: TraceEvent) {
                    if (event is TraceEvent.Read && event.name == "input") {
                        observedInputs.add(event.value as Map<*, *>)
                    }
                }
            }
            val tracedSnapshot = pipeline.report
            assertEquals(JsonMaterializationMode.EAGER, tracedSnapshot.effectiveMode)
            assertEquals("AMBIENT_TRACING", tracedSnapshot.reason)
            assertEquals(emptySet(), tracedSnapshot.selectedRootKeys)
            assertTrue(pipeline.materialize(text).containsKey("unused"))
            assertEquals("1", pipeline.execute(text))
            assertTrue(observedInputs.isNotEmpty())
            assertTrue(observedInputs.all { it["unused"] == mapOf("nested" to listOf(2L, 3L)) })
            // Preparation under tracing is also reversible; force-eager keeps its own reason.
            val preparedUnderTracing = ExperimentalJsonPipeline.prepare("TRANSFORM Test { OUTPUT input.id; }")
            assertEquals("AMBIENT_TRACING", preparedUnderTracing.report.reason)
            val forced = ExperimentalJsonPipeline.prepare(source, options = ExperimentalJsonPipelineOptions(forceEager = true))
            assertEquals("FORCE_EAGER", forced.report.reason)
            Debug.tracer = null
            assertEquals(JsonMaterializationMode.SELECTIVE, pipeline.report.effectiveMode)
            assertEquals(JsonMaterializationMode.SELECTIVE, preparedUnderTracing.report.effectiveMode)
            assertFalse(pipeline.materialize(text).containsKey("unused"))
            assertEquals("1", pipeline.execute(text))
            assertEquals(JsonMaterializationMode.SELECTIVE, preparedSnapshot.effectiveMode)
            assertEquals(JsonMaterializationMode.EAGER, tracedSnapshot.effectiveMode)
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun userFunctionsAndSharedDeclarationsForceEagerInput() {
        val functionSource = "FUNC LENGTH(x) = 7; TRANSFORM Test { OUTPUT LENGTH(input.a); }"
        val function = ExperimentalJsonPipeline.prepare(functionSource)
        assertEquals("USER_FUNCTIONS", function.report.reason)
        assertEquals("7", function.execute("""{"a":"abcd","unused":1}"""))
        assertTrue(function.materialize("""{"unused":1}""").containsKey("unused"))
        val shared = ExperimentalJsonPipeline.prepare("SHARED cache SINGLE; TRANSFORM Test { OUTPUT input.a; }")
        assertEquals("SHARED_RESOURCES", shared.report.reason)
        val shadowedBuiltin = ExperimentalJsonPipeline.prepare("TRANSFORM Test { LET LENGTH = 3; OUTPUT LENGTH(input.a); }")
        assertEquals("UNSUPPORTED_SYNTAX", shadowedBuiltin.report.reason)
    }
}
