package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformRuntimeFeaturesTest {

    @Test
    fun try_catch_fallback_outputs_value() {
        val program = """
            TRANSFORM T {
                TRY row.items[5] CATCH (err) -> { v: "fallback" };
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(mapOf("items" to listOf(1, 2, 3)))
        assertEquals(mapOf("v" to "fallback"), result)
    }

    @Test
    fun shared_declaration_allows_execution() {
        val program = """
            SHARED cache SINGLE;
            TRANSFORM T { OUTPUT { ok: true } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap())
        assertEquals(mapOf("ok" to true), result)
    }

    @Test
    fun host_call_with_multiple_args() {
        val host = mapOf(
            "SUM" to { args: List<Any?> ->
                val a = (args[0] as? Number)?.toInt() ?: 0
                val b = (args[1] as? Number)?.toInt() ?: 0
                a + b
            }
        )
        val program = """
            TRANSFORM T { OUTPUT { v: SUM(20, 22) } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program, hostFns = host)
        val result = runner(emptyMap())
        assertEquals(mapOf("v" to 42), result)
    }

    @Test
    fun type_declaration_runs_with_semantic_validation() {
        val program = """
            TYPE Id = union String | Number;
            TRANSFORM T { OUTPUT { v: "ok" } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program, runSema = true)
        val result = runner(emptyMap())
        assertEquals(mapOf("v" to "ok"), result)
    }

    @Test
    fun tracing_captures_execution_steps() {
        val tracer = CollectingTracer(TraceOptions(step = true))
        val program = """
            TRANSFORM T { OUTPUT { v: 1 + 1 } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program, tracer = tracer)
        val result = runner(emptyMap())
        assertEquals(mapOf("v" to 2), result)
        assertTrue(tracer.events.any { it.event is TraceEvent.Enter })
    }
}
