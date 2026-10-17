@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.ir.IRLet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ExperimentalFusionObservationTest {
    private val options = listOf(
        VMExperimentalOptions(slots = false, fusion = true),
        VMExperimentalOptions(slots = true, fusion = true),
    )

    private fun code(key: ObjKey): Bytecode {
        val node = IRLet("access", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        return Bytecode.fromInstructions(listOf(
            Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("before"), Instruction.STORE_LOCAL(0),
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("nested")),
            Instruction.ACCESS_STATIC(key), Instruction.POP,
            Instruction.LOAD_LOCAL(0), Instruction.STORE_VAR("after"),
            Instruction.LOAD_VAR("before"),
        ), traceMetadata = TraceMetadata(
            enterEvents = mapOf(6 to listOf(node)),
            exitEvents = mapOf(6 to listOf(node)),
        ))
    }

    private data class Observation(
        val result: Any?, val writes: List<Any?>, val events: List<Any>, val counts: Map<String, Long>,
        val before: Any?, val after: Any?,
    )

    private fun observe(experimental: VMExperimentalOptions?, list: Boolean): Observation {
        val trace = CollectingTracer(TraceOptions(step = true))
        val writes = mutableListOf<Any?>()
        val env = mutableMapOf<String, Any?>()
        val activate = {
            writes.add(env["before"])
            env["before"] = 11
            Debug.tracer = trace
        }
        val nested: Any = if (list) object : AbstractList<Any?>() {
            override val size: Int get() = 1
            override fun get(index: Int): Any? { activate(); return 3 }
        } else object : Map<String, Any?> by mapOf("value" to 3) {
            override fun get(key: String): Any? { activate(); return 3 }
        }
        env["input"] = mutableMapOf("nested" to nested)
        val bytecode = code(if (list) I32(0) else ObjKey.Name("value"))
        val vm = VM()
        val result = if (experimental == null) vm.execute(bytecode, env)
            else vm.executeExperimental(vm.prepareExperimental(bytecode, experimental), env)
        return Observation(result, writes.toList(), trace.events.map { it.event }, trace.instructionCounts.toMap(),
            env["before"], env["after"])
    }

    @Test
    fun custom_nested_access_observes_flushed_stores_and_can_enable_trace_without_replay() {
        val previous = Debug.tracer
        try {
            for (list in listOf(false, true)) {
                Debug.tracer = null
                val expected = observe(null, list)
                assertEquals(11, expected.result)
                assertEquals(listOf(7), expected.writes)
                assertEquals(7, expected.after)
                for (option in options) {
                    Debug.tracer = null
                    assertEquals(expected, observe(option, list))
                }
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun failing_custom_get_keeps_prior_store_and_throws_the_original_failure_once() {
        val previous = Debug.tracer
        Debug.tracer = null
        try {
            for (option in options) {
                val failure = IllegalStateException("nested get failed")
                var calls = 0
                val nested = object : Map<String, Any?> by emptyMap() {
                    override fun get(key: String): Any? { calls++; throw failure }
                }
                val env = mutableMapOf<String, Any?>("input" to mapOf("nested" to nested))
                val bytecode = code(ObjKey.Name("value"))
                val vm = VM()
                assertSame(failure, assertFailsWith<IllegalStateException> {
                    vm.executeExperimental(vm.prepareExperimental(bytecode, option), env)
                })
                assertEquals(1, calls)
                assertEquals(7, env["before"])
                assertEquals(false, env.containsKey("after"))
            }
        } finally {
            Debug.tracer = previous
        }
    }
}
