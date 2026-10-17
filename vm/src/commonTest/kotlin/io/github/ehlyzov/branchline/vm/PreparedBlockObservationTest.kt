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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreparedBlockObservationTest {
    @Test
    fun a_custom_list_can_activate_tracing_only_when_next_foreach_reads_its_second_element() {
        val node = IRLet("next", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        data class Observation(val result: Any?, val operations: List<String>, val events: List<Any>, val counts: Map<String, Long>)
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("input"), Instruction.INIT_FOREACH("item", 5),
            Instruction.LOAD_VAR("item"), Instruction.POP, Instruction.NEXT_FOREACH(2, 5),
            Instruction.PUSH(7),
        ), traceMetadata = TraceMetadata(enterEvents = mapOf(4 to listOf(node)), exitEvents = mapOf(4 to listOf(node))))
        fun execute(optimize: Boolean?): Observation {
            val trace = CollectingTracer(TraceOptions(step = true))
            val operations = mutableListOf<String>()
            val input = object : AbstractList<Any?>() {
                override val size: Int get() { operations.add("size"); return 2 }
                override fun get(index: Int): Any? {
                    operations.add("get:$index")
                    if (index == 1) Debug.tracer = trace
                    return index
                }
            }
            val vm = VM()
            val env = mutableMapOf<String, Any?>("input" to input)
            val result = if (optimize == null) vm.execute(bytecode, env)
                else vm.executeBlocks(vm.prepareBlocks(bytecode, optimize), env)
            return Observation(result, operations.toList(), trace.events.map { it.event }, trace.instructionCounts.toMap())
        }
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val expected = execute(null)
            assertEquals(7, expected.result)
            assertTrue(expected.operations.contains("get:1"))
            for (optimize in listOf(false, true)) {
                Debug.tracer = null
                assertEquals(expected, execute(optimize))
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun direct_path_failures_preserve_each_original_pc_and_pop_effect() {
        val cases = listOf(
            mutableMapOf<String, Any?>("input" to 3),
            mutableMapOf<String, Any?>("input" to mutableMapOf("a" to null)),
            mutableMapOf<String, Any?>("input" to mutableMapOf("a" to mutableMapOf("b" to emptyList<Any?>()))),
            mutableMapOf<String, Any?>(),
        )
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(99), Instruction.LOAD_VAR("input"),
            Instruction.ACCESS_STATIC(ObjKey.Name("a")), Instruction.ACCESS_STATIC(ObjKey.Name("b")),
            Instruction.ACCESS_STATIC(I32(2)), Instruction.STORE_VAR("after"),
        ))
        for (initial in cases) {
            val baseline = VM()
            val expectedEnv = initial.toMutableMap()
            val expectedError = assertFails { baseline.execute(bytecode, expectedEnv) }
            val expectedSnapshot = Json.parseToJsonElement(baseline.snapshotJson())
            for (optimize in listOf(false, true)) {
                val vm = VM()
                val env = initial.toMutableMap()
                val error = assertFails { vm.executeBlocks(vm.prepareBlocks(bytecode, optimize), env) }
                assertEquals(expectedError::class, error::class)
                assertEquals(expectedError.message, error.message)
                assertEquals(expectedEnv, env)
                assertEquals(expectedSnapshot, Json.parseToJsonElement(vm.snapshotJson()))
            }
        }
    }

    @Test
    fun observer_in_a_direct_path_sees_materialized_state_and_enables_trace_at_its_source_pc() {
        val node = IRLet("observed", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("x"), Instruction.STORE_LOCAL(0),
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("nested")),
            Instruction.ACCESS_STATIC(ObjKey.Name("child")), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(3),
        ), traceMetadata = TraceMetadata(enterEvents = mapOf(6 to listOf(node)), exitEvents = mapOf(6 to listOf(node))))
        data class Observation(val result: Any?, val calls: Int, val events: List<Any>, val counts: Map<String, Long>, val x: Any?)
        fun execute(optimize: Boolean?): Observation {
            val trace = CollectingTracer(TraceOptions(step = true))
            val env = mutableMapOf<String, Any?>()
            var calls = 0
            val nested = object : Map<String, Any?> by mapOf("child" to mutableMapOf("value" to 3)) {
                override fun get(key: String): Any? {
                    calls++
                    assertEquals(7, env["x"])
                    env["x"] = 11
                    Debug.tracer = trace
                    return mutableMapOf("value" to 3)
                }
            }
            env["input"] = mutableMapOf("nested" to nested)
            val vm = VM()
            val result = if (optimize == null) vm.execute(bytecode, env)
                else vm.executeBlocks(vm.prepareBlocks(bytecode, optimize), env)
            return Observation(result, calls, trace.events.map { it.event }, trace.instructionCounts.toMap(), env["x"])
        }
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val expected = execute(null)
            assertEquals(listOf(3, 7, 11), expected.result)
            assertEquals(1, expected.calls)
            for (optimize in listOf(false, true)) {
                Debug.tracer = null
                assertEquals(expected, execute(optimize))
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun both_runners_use_the_owned_source_snapshot_after_caller_rewrites_bytecode_arrays() {
        val source = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
        ))
        val vm = VM()
        val plans = listOf(vm.prepareBlocks(source, false), vm.prepareBlocks(source, true))
        source.opcodes[0] = Opcode.POP.ordinal
        source.stringOperands[0] = "missing"
        source.objKeyOperands[0] = ObjKey.Name("missing")
        for (plan in plans) {
            assertEquals(7, vm.executeBlocks(plan, mutableMapOf("input" to mutableMapOf("value" to 7))))
            assertTrue(vm.preparedBlockExecuted)
            assertNull(vm.preparedBlockFallbackReason)
        }
    }
}
