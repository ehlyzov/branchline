@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceOptions
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExperimentalRuntimeStateTest {
    private val modes = listOf(
        VMExperimentalOptions(slots = true, collectDiagnostics = true),
        VMExperimentalOptions(fusion = true, collectDiagnostics = true),
        VMExperimentalOptions(slots = true, fusion = true, collectDiagnostics = true),
    )

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
            assertEquals(expected, vm.executeExperimental(vm.prepareExperimental(bytecode, mode), actualEnv), "$mode result")
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
        fun execute(mode: VMExperimentalOptions?): Pair<Any?, String> {
            val env = mutableMapOf<String, Any?>()
            val vm = VM(hostFns = mapOf("mutate" to {
                assertEquals(7, env["x"])
                env["x"] = 11
                null
            }))
            val result = if (mode == null) vm.execute(bytecode, env)
                else vm.executeExperimental(vm.prepareExperimental(bytecode, mode), env)
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
            for (mode in listOf<VMExperimentalOptions?>(null) + modes) {
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
                    else vm.executeExperimental(vm.prepareExperimental(bytecode, mode), env)
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
            assertFailsWith<VMException.DivisionByZero> { vm.executeExperimental(vm.prepareExperimental(bytecode, mode), env) }
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
    fun disabled_diagnostics_preserve_results_without_claiming_mechanism_counts() {
        val bytecode = Bytecode.fromInstructions(mirrored("x", 0, 7) + Instruction.LOAD_LOCAL(0))
        val vm = VM()
        assertEquals(7, vm.executeExperimental(vm.prepareExperimental(bytecode, VMExperimentalOptions(slots = true))))
        assertTrue(vm.experimentalReport.effective.slots)
        assertEquals(0L, vm.experimentalReport.slotWrites)
        assertEquals(0L, vm.experimentalReport.mirroredStoresElided)
    }
}
