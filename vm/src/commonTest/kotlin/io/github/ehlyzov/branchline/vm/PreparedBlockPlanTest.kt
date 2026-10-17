@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PreparedBlockPlanTest {
    private fun plan(vararg instructions: Instruction, optimize: Boolean = true) =
        PreparedBlockPlan.prepare(Bytecode.fromInstructions(instructions.toList()), optimize)

    @Test
    fun control_resolves_all_operands_without_lowering_bindings_or_paths() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(7), Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("rows")),
            Instruction.STORE_VAR("saved"), Instruction.LOAD_LOCAL(3), Instruction.STORE_LOCAL(5),
            Instruction.CALL_HOST(2, "host", 4), Instruction.INIT_FOREACH("row", 10),
            Instruction.NEXT_FOREACH(7, 10), Instruction.OUTPUT(2), Instruction.RETURN_VALUE,
        ))
        val plan = PreparedBlockPlan.prepare(code, false)
        assertNull(plan.fallbackReason)
        assertFalse(plan.optimize)
        assertEquals(0, plan.slotNameCount)
        assertEquals(0, plan.directPathCount)
        assertEquals(0, plan.mirrorStoreCount)
        assertTrue(plan.instructions.all { it != null })
        assertEquals(7, plan.instructions[0]!!.value)
        assertEquals("input", plan.instructions[1]!!.name)
        assertEquals(ObjKey.Name("rows"), plan.instructions[2]!!.key)
        assertEquals(PreparedBlockOp.STORE_VAR, plan.instructions[3]!!.kind)
        assertEquals(3, plan.instructions[4]!!.a)
        assertEquals(5, plan.instructions[5]!!.a)
        assertEquals(2, plan.instructions[6]!!.a)
        assertEquals(4, plan.instructions[6]!!.b)
        assertEquals("host", plan.instructions[6]!!.name)
        assertEquals(10, plan.instructions[7]!!.a)
        assertEquals("row", plan.instructions[7]!!.name)
        assertEquals(7, plan.instructions[8]!!.a)
        assertEquals(10, plan.instructions[8]!!.b)
        assertEquals(2, plan.instructions[9]!!.a)
        for (pc in plan.instructions.indices) {
            assertEquals(pc, plan.instructions[pc]!!.sourcePc)
            assertEquals(pc + 1, plan.instructions[pc]!!.nextPc)
        }
    }

    @Test
    fun basic_blocks_and_successors_are_precomputed_from_source_control_flow() {
        val plan = plan(
            Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("ok")),
            Instruction.JUMP_IF_FALSE(5), Instruction.PUSH(7), Instruction.JUMP(7),
            Instruction.PUSH(9), Instruction.STORE_VAR("x"), Instruction.LOAD_VAR("x"), Instruction.RETURN_VALUE,
        )
        assertEquals(listOf(0 to 3, 3 to 5, 5 to 7, 7 to 9), plan.blocks.map { it.startPc to it.endPc })
        assertEquals(listOf(listOf(5, 3), listOf(7), listOf(7), emptyList()), plan.blocks.map { it.successors })
        assertEquals(listOf(1, 2), plan.blocks[0].observationPcs)
        assertEquals(PreparedBlockOp.STATIC_PATH, plan.instructions[0]!!.kind)
        assertNull(plan.instructions[1])
        assertEquals(2, plan.instructions[0]!!.nextPc)
        assertEquals(PreparedBlockOp.JUMP_IF_FALSE, plan.instructions[2]!!.kind)
    }

    @Test
    fun direct_paths_never_consume_a_control_flow_entry() {
        val branches: List<(Int) -> Instruction> = listOf(
            { Instruction.JUMP(it) }, { Instruction.JUMP_IF_TRUE(it) },
            { Instruction.JUMP_IF_FALSE(it) }, { Instruction.JUMP_IF_NULL(it) },
            { Instruction.INIT_FOREACH("row", it) },
            { Instruction.NEXT_FOREACH(it, 5) }, { Instruction.NEXT_FOREACH(5, it) },
        )
        for (branch in branches) {
            fun source(target: Int) = Bytecode.fromInstructions(listOf(
                Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("a")),
                Instruction.ACCESS_STATIC(ObjKey.Name("b")), Instruction.ACCESS_STATIC(ObjKey.Name("c")),
                branch(target), Instruction.RETURN,
            ))
            val first = PreparedBlockPlan.prepare(source(1), true)
            assertEquals(PreparedBlockOp.SLOT_LOAD, first.instructions[0]!!.kind)
            assertNotNull(first.instructions[1])
            val later = PreparedBlockPlan.prepare(source(2), true)
            assertEquals(2, later.instructions[0]!!.nextPc)
            assertNotNull(later.instructions[2])
        }
    }

    @Test
    fun alias_mirrors_and_path_loads_carry_the_complete_prepared_frame_coordinates() {
        val plan = plan(
            Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("sum"), Instruction.STORE_LOCAL(2),
            Instruction.LOAD_LOCAL(2), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
            Instruction.LOAD_SCOPE("optional"), Instruction.INIT_FOREACH("row", 9), Instruction.NEXT_FOREACH(8, 9),
        )
        assertEquals(listOf("sum", "optional", "row"), plan.names)
        assertEquals(listOf(-1, -1, 0), plan.aliases.toList())
        assertEquals(1, plan.mirrorStoreCount)
        val mirror = plan.instructions[1]!!
        assertEquals(PreparedBlockOp.MIRROR_STORE, mirror.kind)
        assertEquals(0, mirror.a)
        assertEquals(2, mirror.b)
        assertEquals(4, mirror.nextPc)
        assertNull(plan.instructions[2])
        assertNull(plan.instructions[3])
        val path = plan.instructions[4]!!
        assertEquals(PreparedBlockOp.STATIC_PATH, path.kind)
        assertEquals(PreparedBlockOp.ALIAS_LOAD, path.load!!.kind)
        assertEquals(2, path.load!!.a)
        assertEquals(0, path.load!!.b)
        assertEquals(listOf(ObjKey.Name("value")), path.keys)
        assertEquals(2, plan.instructions[7]!!.b)
    }

    @Test
    fun alias_elimination_is_rejected_for_loop_bindings_independent_writes_or_interior_targets() {
        val mirror = listOf(Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("x"), Instruction.STORE_LOCAL(0))
        val tails = listOf(
            listOf(Instruction.INIT_FOREACH("x", 5)),
            listOf(Instruction.PUSH(9), Instruction.STORE_VAR("x")),
            listOf(Instruction.PUSH(9), Instruction.STORE_LOCAL(0)),
            listOf(Instruction.JUMP(2)),
            listOf(Instruction.JUMP(3)),
        )
        for (tail in tails) {
            val plan = PreparedBlockPlan.prepare(Bytecode.fromInstructions(mirror + tail), true)
            assertEquals(0, plan.mirrorStoreCount)
            assertEquals(-1, plan.aliases[0])
            assertEquals(PreparedBlockOp.DUP, plan.instructions[1]!!.kind)
        }
    }

    @Test
    fun source_structure_is_owned_but_constant_value_identity_is_preserved() {
        val constant = mutableMapOf("value" to 7)
        val debug = mutableMapOf(0 to "source")
        val source = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(constant), Instruction.LOAD_VAR("input"), Instruction.ACCESS_STATIC(ObjKey.Name("value")),
        ), debugInfo = debug)
        val plan = PreparedBlockPlan.prepare(source, true)
        source.opcodes[0] = Opcode.POP.ordinal
        source.stringOperands[0] = "changed"
        source.objKeyOperands[0] = ObjKey.Name("changed")
        source.constOperands[0] = 99
        debug[0] = "changed"
        assertEquals(Opcode.PUSH.ordinal, plan.bytecode.getOpcode(0))
        assertSame(constant, plan.instructions[0]!!.value)
        assertEquals("input", plan.instructions[1]!!.load!!.name)
        assertEquals(listOf(ObjKey.Name("value")), plan.instructions[1]!!.keys)
        assertEquals("source", plan.bytecode.debugInfo[0])
    }

    @Test
    fun unsupported_and_malformed_bytecode_fall_back_without_a_partial_plan() {
        val programs = listOf(
            Bytecode.fromInstructions(listOf(Instruction.PUSH(7), Instruction.STORE_VAR("before"), Instruction.SUSPEND)),
            Bytecode.fromInstructions(listOf(Instruction.CONCAT)),
            Bytecode.fromInstructions(listOf(Instruction.PUSH(LambdaTemplate(emptyList(), Bytecode.fromInstructions(emptyList()))))),
            Bytecode(opcodes = intArrayOf(Opcode.LOAD_VAR.ordinal)),
            Bytecode(opcodes = intArrayOf(Int.MAX_VALUE)),
            Bytecode.fromInstructions(listOf(Instruction.PUSH(7))).copy(constOperands = intArrayOf(9)),
            Bytecode.fromInstructions(listOf(Instruction.LOAD_VAR("input"))).copy(stringOperandOffsets = intArrayOf(Int.MAX_VALUE)),
            Bytecode.fromInstructions(listOf(Instruction.CALL_HOST(0, "host", 1))).copy(intOperands = intArrayOf(0)),
        )
        for (source in programs) for (optimize in listOf(false, true)) {
            val plan = PreparedBlockPlan.prepare(source, optimize)
            assertNotNull(plan.fallbackReason)
            assertTrue(plan.instructions.all { it == null })
            assertEquals(0, plan.blockCount)
            assertEquals(0, plan.slotNameCount)
            assertEquals(0, plan.directPathCount + plan.mirrorStoreCount)
        }
    }

    @Test
    fun preparation_never_hashes_formats_or_compares_unused_constants() {
        val inert = object {
            override fun hashCode(): Int = error("hashed an inert constant")
            override fun equals(other: Any?): Boolean = error("compared an inert constant")
            override fun toString(): String = error("formatted an inert constant")
        }
        val plan = plan(Instruction.JUMP(2), Instruction.PUSH(inert), Instruction.RETURN)
        assertNull(plan.fallbackReason)
        assertSame(inert, plan.instructions[1]!!.value)
    }

    @Test
    fun empty_program_has_no_phantom_block_or_transformations() {
        val plan = plan()
        assertNull(plan.fallbackReason)
        assertEquals(0, plan.blockCount)
        assertEquals(0, plan.sourceInstructionCount)
        assertEquals(0, plan.directPathCount + plan.mirrorStoreCount)
    }

    @Test
    fun truth_conversions_are_observation_sites_because_jvm_bignum_subclasses_can_callback() {
        val plan = plan(Instruction.AND, Instruction.OR, Instruction.NOT,
            Instruction.JUMP_IF_TRUE(5), Instruction.JUMP_IF_FALSE(5), optimize = false)
        assertEquals(listOf(PreparedBlockObservation.SCALAR_PAIR, PreparedBlockObservation.SCALAR_PAIR,
            PreparedBlockObservation.SCALAR_ONE, PreparedBlockObservation.SCALAR_ONE,
            PreparedBlockObservation.SCALAR_ONE), plan.instructions.map { it!!.observation })
    }
}
