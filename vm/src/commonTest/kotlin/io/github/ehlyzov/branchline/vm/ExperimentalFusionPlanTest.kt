package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.I32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentalFusionPlanTest {
    @Test
    fun static_paths_retain_each_key_and_the_original_load_kind() {
        val loads = listOf(
            Instruction.LOAD_VAR("input") to Opcode.LOAD_VAR,
            Instruction.LOAD_SCOPE("input") to Opcode.LOAD_SCOPE,
            Instruction.LOAD_LOCAL(2) to Opcode.LOAD_LOCAL,
        )
        for ((load, opcode) in loads) {
            val code = Bytecode.fromInstructions(listOf(
                load, Instruction.ACCESS_STATIC(ObjKey.Name("rows")),
                Instruction.ACCESS_STATIC(I32(0)),
                Instruction.ACCESS_STATIC(ObjKey.Name("price")), Instruction.RETURN_VALUE,
            ))
            val plan = ExperimentalFusionPlan.prepare(code)
            val path = assertIs<ExperimentalFusion.StaticPath>(plan.byPc[0])
            assertEquals(opcode, path.load)
            assertEquals(if (opcode == Opcode.LOAD_LOCAL) null else "input", path.name)
            assertEquals(if (opcode == Opcode.LOAD_LOCAL) 2 else null, path.localIndex)
            assertEquals(listOf(ObjKey.Name("rows"), I32(0), ObjKey.Name("price")), path.keys)
            assertEquals(4, path.span)
            assertTrue(plan.byPc.drop(1).all { it == null })
        }
    }

    @Test
    fun mirrored_store_preserves_named_then_local_store_order() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(7), Instruction.DUP,
            Instruction.STORE_VAR("sum"), Instruction.STORE_LOCAL(3), Instruction.RETURN,
        ))
        val plan = ExperimentalFusionPlan.prepare(code)
        assertEquals(ExperimentalFusion.MirroredStore(1, "sum", 3), plan.byPc[1])
        assertNull(plan.byPc[2])
        assertNull(plan.byPc[3])
    }

    @Test
    fun every_control_flow_entry_stops_fusion_before_its_target() {
        val branches: List<(Int) -> Instruction> = listOf(
            { Instruction.JUMP(it) }, { Instruction.JUMP_IF_TRUE(it) },
            { Instruction.JUMP_IF_FALSE(it) }, { Instruction.JUMP_IF_NULL(it) },
            { Instruction.INIT_FOREACH("row", it) }, { Instruction.TRY_START(it) },
            { Instruction.NEXT_FOREACH(it, 5) }, { Instruction.NEXT_FOREACH(5, it) },
        )
        for (branch in branches) {
            val prefix = listOf(
                Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("a")),
                Instruction.ACCESS_STATIC(ObjKey.Name("b")), Instruction.ACCESS_STATIC(ObjKey.Name("c")),
            )
            val code = Bytecode.fromInstructions(prefix + branch(2) + Instruction.RETURN)
            val path = assertIs<ExperimentalFusion.StaticPath>(ExperimentalFusionPlan.prepare(code).byPc[0])
            assertEquals(2, path.span)

            val firstAccessTarget = Bytecode.fromInstructions(prefix + branch(1) + Instruction.RETURN)
            assertNull(ExperimentalFusionPlan.prepare(firstAccessTarget).byPc[0])

            val storeCode = Bytecode.fromInstructions(listOf(
                Instruction.DUP, Instruction.STORE_VAR("v"), Instruction.STORE_LOCAL(0),
                branch(1), Instruction.RETURN,
            ))
            assertNull(ExperimentalFusionPlan.prepare(storeCode).byPc[0])
            val localTarget = Bytecode.fromInstructions(listOf(
                Instruction.DUP, Instruction.STORE_VAR("v"), Instruction.STORE_LOCAL(0),
                branch(2), Instruction.RETURN,
            ))
            assertNull(ExperimentalFusionPlan.prepare(localTarget).byPc[0])
        }
    }

    @Test
    fun a_loop_can_reenter_the_first_instruction_of_a_pattern() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            Instruction.POP, Instruction.JUMP(0),
        ))
        assertIs<ExperimentalFusion.StaticPath>(ExperimentalFusionPlan.prepare(code).byPc[0])
    }

    @Test
    fun no_pattern_crosses_an_observer_or_dynamic_access_instruction() {
        val barriers = listOf(
            Instruction.TRACE("event"), Instruction.BREAKPOINT("pause"), Instruction.LINE(8),
            Instruction.SUSPEND, Instruction.CALL_HOST(0, "host", 0),
            Instruction.ACCESS_DYNAMIC, Instruction.TRY_END, Instruction.NOP,
        )
        for (barrier in barriers) {
            val code = Bytecode.fromInstructions(listOf(
                Instruction.LOAD_VAR("input"), barrier, Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            ))
            assertTrue(ExperimentalFusionPlan.prepare(code).byPc.all { it == null })
        }
    }

    @Test
    fun preparing_does_not_rewrite_the_program_or_inspect_constant_values() {
        val inert = object {
            override fun hashCode(): Int = error("Fusion preparation hashed an unexecuted constant")
            override fun equals(other: Any?): Boolean = error("Fusion preparation compared an unexecuted constant")
            override fun toString(): String = error("Fusion preparation formatted an unexecuted constant")
        }
        val code = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            Instruction.DUP, Instruction.STORE_VAR("v"), Instruction.STORE_LOCAL(1),
            Instruction.PUSH(inert),
        ))
        val opcodes = code.opcodes.copyOf()
        val ints = code.intOperands.copyOf()
        val strings = code.stringOperands.copyOf()
        val keys = code.objKeyOperands.copyOf()
        val consts = code.constOperands.copyOf()
        val plan = ExperimentalFusionPlan.prepare(code)
        assertIs<ExperimentalFusion.StaticPath>(plan.byPc[0])
        assertIs<ExperimentalFusion.MirroredStore>(plan.byPc[2])
        assertContentEquals(opcodes, code.opcodes)
        assertContentEquals(ints, code.intOperands)
        assertContentEquals(strings, code.stringOperands)
        assertContentEquals(keys, code.objKeyOperands)
        assertContentEquals(consts, code.constOperands)
    }
}
