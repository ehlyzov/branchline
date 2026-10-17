@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.Tracer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreparedBlockRuntimeStateTest {
    private val modes = listOf(false, true)

    private fun mirrored(name: String, index: Int, value: Any?) = listOf(
        Instruction.PUSH(value), Instruction.DUP, Instruction.STORE_VAR(name), Instruction.STORE_LOCAL(index),
    )

    private fun assertStateParity(instructions: List<Instruction>, env: Map<String, Any?> = emptyMap()) {
        val bytecode = Bytecode.fromInstructions(instructions)
        val baselineEnv = env.toMutableMap()
        val baseline = VM()
        val expected = baseline.execute(bytecode, baselineEnv)
        val expectedSnapshot = Json.parseToJsonElement(baseline.snapshotJson())
        for (mode in modes) {
            val actualEnv = env.toMutableMap()
            val vm = VM()
            assertEquals(expected, vm.executeBlocks(vm.prepareBlocks(bytecode, mode), actualEnv), "$mode result")
            assertEquals(baselineEnv, actualEnv, "$mode environment")
            assertEquals(baselineEnv.keys.toList(), actualEnv.keys.toList(), "$mode key order")
            assertEquals(expectedSnapshot, Json.parseToJsonElement(vm.snapshotJson()), "$mode snapshot")
        }
    }

    @Test
    fun final_snapshot_preserves_geometric_local_capacity_and_uninitialized_locals() {
        assertStateParity(mirrored("a", 0, 3) + mirrored("b", 5, 8) + Instruction.LOAD_LOCAL(5))
        assertStateParity(mirrored("a", 0, 3) + listOf(Instruction.PUSH(9), Instruction.STORE_LOCAL(5), Instruction.LOAD_LOCAL(0)))
        assertStateParity(listOf(Instruction.LOAD_LOCAL(20)))
        assertStateParity(listOf(Instruction.PUSH(false), Instruction.JUMP_IF_FALSE(6)) + mirrored("a", 9, 8) + Instruction.LOAD_LOCAL(9), mapOf("a" to 41))
    }

    @Test
    fun host_environment_mutation_preserves_stale_local_and_live_variable() {
        val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + listOf(
            Instruction.CALL_HOST(0, "mutate", 0), Instruction.POP,
            Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(2),
        ))
        fun execute(mode: Boolean?): Pair<Any?, String> {
            val env = mutableMapOf<String, Any?>()
            val vm = VM(hostFns = mapOf("mutate" to {
                assertEquals(7, env["x"])
                env["x"] = 11
                null
            }))
            val result = if (mode == null) vm.execute(bytecode, env)
                else vm.executeBlocks(vm.prepareBlocks(bytecode, mode), env)
            assertEquals(11, env["x"])
            assertEquals(listOf(7, 11), result)
            return result to vm.snapshotJson()
        }
        val expected = execute(null)
        for (mode in modes) {
            val actual = execute(mode)
            assertEquals(expected.first, actual.first)
            assertEquals(Json.parseToJsonElement(expected.second), Json.parseToJsonElement(actual.second))
        }
    }

    @Test
    fun host_enabling_trace_then_throwing_keeps_exact_failure_and_partial_state_once() {
        val previous = Debug.tracer
        val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + listOf(
            Instruction.CALL_HOST(0, "fail", 0), Instruction.STORE_VAR("after"),
        ))
        var expectedSnapshot: Any? = null
        try {
            for (mode in listOf<Boolean?>(null) + modes) {
                Debug.tracer = null
                val trace = CollectingTracer(TraceOptions(step = true))
                val env = mutableMapOf<String, Any?>()
                val failure = IllegalStateException("host failure")
                var calls = 0
                val vm = VM(hostFns = mapOf("fail" to {
                    calls++
                    assertEquals(7, env["x"])
                    env["x"] = 13
                    Debug.tracer = trace
                    throw failure
                }))
                assertSame(failure, assertFailsWith<IllegalStateException> {
                    if (mode == null) vm.execute(bytecode, env)
                    else vm.executeBlocks(vm.prepareBlocks(bytecode, mode), env)
                })
                assertEquals(1, calls)
                assertEquals(mapOf<String, Any?>("x" to 13), env)
                assertTrue(trace.events.isEmpty())
                assertTrue(trace.instructionCounts.isEmpty())
                val snapshot = Json.parseToJsonElement(vm.snapshotJson())
                if (mode == null) expectedSnapshot = snapshot else assertEquals(expectedSnapshot, snapshot)
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun arithmetic_failure_preserves_prior_writes_and_snapshot_without_replay() {
        val bytecode = Bytecode.fromInstructions(mirrored("x", 5, 7) + listOf(
            Instruction.PUSH(1), Instruction.PUSH(0), Instruction.DIV, Instruction.STORE_VAR("after"),
        ))
        val expectedEnv = mutableMapOf<String, Any?>()
        val baseline = VM()
        assertFailsWith<VMException.DivisionByZero> { baseline.execute(bytecode, expectedEnv) }
        val expectedSnapshot = Json.parseToJsonElement(baseline.snapshotJson())
        for (mode in modes) {
            val env = mutableMapOf<String, Any?>()
            val vm = VM()
            assertFailsWith<VMException.DivisionByZero> { vm.executeBlocks(vm.prepareBlocks(bytecode, mode), env) }
            assertEquals(expectedEnv, env)
            assertEquals(expectedSnapshot, Json.parseToJsonElement(vm.snapshotJson()))
        }
    }

    @Test
    fun loop_shadowing_and_removed_then_reinserted_key_order_match_baseline() {
        assertStateParity(listOf(
            Instruction.PUSH(listOf(1, 2)), Instruction.INIT_FOREACH("i", 6),
            Instruction.PUSH(7), Instruction.STORE_VAR("b"), Instruction.NEXT_FOREACH(2, 6), Instruction.PUSH(null),
            Instruction.PUSH(9), Instruction.STORE_VAR("i"), Instruction.LOAD_VAR("b"),
        ))
        assertStateParity(listOf(
            Instruction.PUSH(listOf(1, 2)), Instruction.INIT_FOREACH("x", 10),
            Instruction.PUSH(listOf(10, 20)), Instruction.INIT_FOREACH("x", 7),
            Instruction.LOAD_VAR("x"), Instruction.STORE_VAR("seen"), Instruction.NEXT_FOREACH(4, 7),
            Instruction.LOAD_VAR("x"), Instruction.STORE_VAR("outerSeen"), Instruction.NEXT_FOREACH(2, 10),
            Instruction.LOAD_VAR("outerSeen"),
        ), mapOf("x" to "original"))
    }

    @Test
    fun actual_prepared_status_is_scalar_and_identifies_optimized_execution() {
        val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + Instruction.LOAD_LOCAL(0))
        val vm = VM()
        assertEquals(7, vm.executeBlocks(vm.prepareBlocks(bytecode, true)))
        assertTrue(vm.preparedBlockExecuted)
        assertTrue(vm.preparedBlockOptimized)
        assertEquals(null, vm.preparedBlockFallbackReason)
    }

    @Test
    fun reused_frame_clears_bindings_after_success_failure_and_environment_replacement() {
        val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + listOf(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            Instruction.STORE_VAR("output"), Instruction.LOAD_VAR("output"),
        ))
        for (mode in modes) {
            val vm = VM()
            val plan = vm.prepareBlocks(bytecode, mode)
            val first = mutableMapOf<String, Any?>("input" to mutableMapOf("value" to 1))
            assertEquals(1, vm.executeBlocks(plan, first))
            val failed = mutableMapOf<String, Any?>()
            assertFailsWith<VMException.VariableNotFound> { vm.executeBlocks(plan, failed) }
            assertEquals(mapOf<String, Any?>("x" to 7), failed)
            val second = mutableMapOf<String, Any?>("input" to mutableMapOf("value" to 3))
            assertEquals(3, vm.executeBlocks(plan, second))
            assertEquals(1, first["output"])
            assertEquals(3, second["output"])
            val baselineEnv = mutableMapOf<String, Any?>("input" to mutableMapOf("value" to 3))
            val baseline = VM()
            baseline.execute(bytecode, baselineEnv)
            assertEquals(Json.parseToJsonElement(baseline.snapshotJson()), Json.parseToJsonElement(vm.snapshotJson()))
        }
    }

    @Test
    fun reentrant_execution_is_rejected_before_outer_state_changes_including_fallback() {
        val nested = Bytecode.fromInstructions(listOf(Instruction.PUSH(99)))
        for (mode in modes) {
            for (fallback in listOf(false, true)) {
                lateinit var vm: VM
                var calls = 0
                vm = VM(hostFns = mapOf("observe" to {
                    calls++
                    assertFailsWith<IllegalStateException> { vm.execute(nested) }
                    assertFailsWith<IllegalStateException> { vm.begin(nested) }
                    assertFailsWith<IllegalStateException> { vm.step() }
                    assertFailsWith<IllegalStateException> { vm.executeBlocks(vm.prepareBlocks(nested, mode)) }
                    null
                }))
                val prefix = if (fallback) listOf(Instruction.PUSH(emptyList<Any?>()), Instruction.PUSH(emptyList<Any?>()), Instruction.CONCAT, Instruction.POP) else emptyList()
                val bytecode = Bytecode.fromInstructions(prefix + mirrored("x", 0, 7) + listOf(
                    Instruction.CALL_HOST(0, "observe", 0), Instruction.POP, Instruction.LOAD_LOCAL(0),
                ))
                val env = mutableMapOf<String, Any?>()
                assertEquals(7, vm.executeBlocks(vm.prepareBlocks(bytecode, mode), env))
                assertEquals(mapOf<String, Any?>("x" to 7), env)
                assertEquals(1, calls)
                assertEquals(!fallback, vm.preparedBlockExecuted)
            }
        }
    }

    @Test
    fun tracer_close_stays_inside_guard_and_close_failure_allows_later_reuse() {
        val previous = Debug.tracer
        try {
            for (mode in modes) {
                Debug.tracer = null
                lateinit var vm: VM
                var closes = 0
                var failClose = true
                val closeFailure = IllegalStateException("close failure")
                val nested = Bytecode.fromInstructions(listOf(Instruction.PUSH(99)))
                val closingTracer = object : Tracer, AutoCloseable {
                    override val opts = TraceOptions()
                    override fun on(event: TraceEvent) = Unit
                    override fun close() {
                        closes++
                        Debug.tracer = null
                        assertFailsWith<IllegalStateException> { vm.execute(nested) }
                        assertFailsWith<IllegalStateException> { vm.executeBlocks(vm.prepareBlocks(nested, mode)) }
                        if (failClose) throw closeFailure
                    }
                }
                vm = VM(hostFns = mapOf("activate" to { Debug.tracer = closingTracer; null }))
                val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + listOf(
                    Instruction.CALL_HOST(0, "activate", 0), Instruction.POP, Instruction.LOAD_LOCAL(0),
                ))
                val plan = vm.prepareBlocks(bytecode, mode)
                assertSame(closeFailure, assertFailsWith<IllegalStateException> { vm.executeBlocks(plan) })
                failClose = false
                assertEquals(7, vm.executeBlocks(plan))
                assertEquals(2, closes)
            }
        } finally {
            Debug.tracer = previous
        }
    }
}
