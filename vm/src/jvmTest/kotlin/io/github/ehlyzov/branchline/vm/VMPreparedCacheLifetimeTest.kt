package io.github.ehlyzov.branchline.vm

import kotlin.test.*

class VMPreparedCacheLifetimeTest {
    private fun prepared(vm: VM): List<*> = VM::class.java.getDeclaredField("preparedPrograms")
        .apply { isAccessible = true }.get(vm) as List<*>

    @Test
    fun fresh_root_programs_release_previous_numeric_and_host_state() {
        val vm = VM()
        repeat(1000) {
            val code = Bytecode.fromInstructions(listOf(
                Instruction.PUSH(1), Instruction.PUSH(2), Instruction.ADD,
            ))
            assertEquals(3, vm.execute(code))
            assertEquals(1, prepared(vm).size)
        }
    }

    @Test
    fun replacing_an_interrupted_program_releases_loop_state() {
        val interrupted = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(listOf(1, 2, 3)), Instruction.INIT_FOREACH("item", 3),
            Instruction.SUSPEND,
        ))
        val vm = VM()
        vm.execute(interrupted)
        val loops = VM::class.java.getDeclaredField("loopStack").apply { isAccessible = true }
        assertEquals(1, (loops.get(vm) as Collection<*>).size)
        vm.begin(Bytecode.fromInstructions(listOf(Instruction.PUSH(7))))
        assertEquals(0, (loops.get(vm) as Collection<*>).size)
        assertTrue(vm.resume())
        assertEquals(7, vm.resultOrNull())
    }

    @Test
    fun transient_callees_cannot_evict_the_current_roots_prepared_state() {
        val root = Bytecode.fromInstructions(buildList {
            add(Instruction.JUMP(8193))
            repeat(8192) { add(Instruction.NOP) }
            add(Instruction.PUSH(1))
            add(Instruction.PUSH(2))
            add(Instruction.ADD)
            add(Instruction.POP)
            add(Instruction.LOAD_VAR("fn"))
            add(Instruction.CALL_LAMBDA(0))
        })
        val vm = VM()
        vm.begin(root)
        val rootState = prepared(vm).single()
        val activeState = VM::class.java.getDeclaredField("preparedProgram").apply { isAccessible = true }
        repeat(1000) { value ->
            val lambda = LambdaValue(emptyList(), Bytecode.fromInstructions(listOf(
                Instruction.PUSH(value), Instruction.RETURN_VALUE,
            )), emptyMap())
            assertEquals(value, vm.execute(root, mutableMapOf("fn" to lambda)))
            assertSame(rootState, activeState.get(vm))
            assertTrue(prepared(vm).any { it === rootState })
            assertTrue(prepared(vm).size <= 64)
        }
    }

    @Test
    fun one_root_cannot_retain_unbounded_transient_lambda_programs() {
        val root = Bytecode.fromInstructions(listOf(Instruction.LOAD_VAR("fn"), Instruction.CALL_LAMBDA(0)))
        val vm = VM()
        repeat(1000) { value ->
            val lambda = LambdaValue(emptyList(), Bytecode.fromInstructions(listOf(
                Instruction.PUSH(value), Instruction.RETURN_VALUE,
            )), emptyMap())
            assertEquals(value, vm.execute(root, mutableMapOf("fn" to lambda)))
            assertTrue(prepared(vm).size <= 64)
        }
    }
}
