@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExperimentalHostBoundaryAcceptanceTest {
    private fun modes() = listOf(false, true).flatMap { slots ->
        listOf(false, true).flatMap { fusion ->
            listOf(false, true).map { diagnostics -> VMExperimentalOptions(slots, fusion, diagnostics) }
        }
    }

    private fun mirror(value: Any?) = listOf(
        Instruction.PUSH(value), Instruction.DUP, Instruction.STORE_VAR("x"), Instruction.STORE_LOCAL(0),
    )

    private data class HostObservation(
        val result: Any?, val keys: List<String>, val snapshots: List<JsonElement>,
        val observedKeys: List<List<String>>, val environment: Map<String, Any?>,
    )

    @Test
    fun two_hosts_observe_null_presence_key_order_and_new_mirrors_after_alias_detachment() {
        val bytecode = Bytecode.fromInstructions(mirror(1) + listOf(
            Instruction.CALL_HOST(0, "first", 0), Instruction.POP,
            Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(2),
            Instruction.STORE_VAR("during"),
        ) + mirror(null) + listOf(
            Instruction.CALL_HOST(1, "second", 0), Instruction.POP,
            Instruction.LOAD_LOCAL(0), Instruction.LOAD_SCOPE("x"), Instruction.MAKE_ARRAY(2),
        ))
        fun execute(mode: VMExperimentalOptions?): HostObservation {
            val env = linkedMapOf<String, Any?>("seed" to 0)
            val snapshots = mutableListOf<JsonElement>()
            val keys = mutableListOf<List<String>>()
            lateinit var vm: VM
            vm = VM(hostFns = mapOf(
                "first" to {
                    assertEquals(1, env["x"])
                    keys.add(env.keys.toList())
                    snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                    env.remove("x")
                    env["middle"] = null
                    env["x"] = 2
                    null
                },
                "second" to {
                    assertTrue(env.containsKey("x"))
                    assertEquals(null, env["x"])
                    assertEquals(listOf(1, 2), env["during"])
                    keys.add(env.keys.toList())
                    snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                    env.remove("x")
                    null
                },
            ))
            val result = if (mode == null) vm.execute(bytecode, env)
                else vm.executeExperimental(vm.prepareExperimental(bytecode, mode), env)
            assertEquals(listOf(null, null), result)
            return HostObservation(result, env.keys.toList(), snapshots, keys, env.toMap())
        }
        val expected = execute(null)
        assertEquals(listOf("seed", "middle", "during"), expected.keys)
        for (mode in modes()) assertEquals(expected, execute(mode), "$mode host boundary parity")
    }

    @Test
    fun effective_treatments_are_subsets_of_requested_flags_and_diagnostics_are_opt_in() {
        val bytecode = Bytecode.fromInstructions(mirror(1) + listOf(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(io.github.ehlyzov.branchline.ObjKey.Name("value")),
        ))
        for (mode in modes()) {
            val vm = VM()
            assertEquals(3, vm.executeExperimental(vm.prepareExperimental(bytecode, mode),
                mutableMapOf("input" to mutableMapOf("value" to 3))))
            val report = vm.experimentalReport
            assertEquals(mode, report.requested)
            assertEquals(mode.slots, report.effective.slots)
            assertEquals(mode.fusion, report.effective.fusion)
            if (!mode.slots) {
                assertEquals(0L, report.slotReads)
                assertEquals(0L, report.slotWrites)
                assertEquals(0L, report.mirroredStoresElided)
            }
            if (!mode.fusion) assertEquals(0L, report.fusedDispatches)
            if (!mode.collectDiagnostics) {
                assertEquals(0L, report.slotReads + report.slotWrites + report.loopSlotWrites +
                    report.mirroredStoresElided + report.fusedDispatches + report.instructionsElided + report.hostBarriers)
            }
        }
    }

    @Test
    fun unsupported_programs_report_fallback_without_claiming_either_treatment() {
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(listOf(1)), Instruction.PUSH(listOf(2)), Instruction.CONCAT,
        ))
        for (mode in modes()) {
            val vm = VM()
            val plan = vm.prepareExperimental(bytecode, mode)
            assertEquals(0, plan.slotNameCount)
            assertEquals(0, plan.fusedSites)
            assertEquals(listOf(1, 2), vm.executeExperimental(plan))
            val report = vm.experimentalReport
            assertNotNull(report.fallbackReason)
            assertFalse(report.effective.slots)
            assertFalse(report.effective.fusion)
            assertEquals(0L, report.instructionsElided)
        }
    }
}
