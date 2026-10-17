package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class VMNumericSiteOverflowTest {
    @Test
    fun cached_site_preserves_exact_long_overflow_and_returns_to_small_integers() {
        val code = Bytecode.fromInstructions(listOf(
            Instruction.LOAD_VAR("a"), Instruction.LOAD_VAR("b"), Instruction.ADD,
        ))
        val vm = VM()
        assertEquals(3, vm.execute(code, mutableMapOf("a" to 1, "b" to 2)))
        val exactOverflow = blBigIntOfLong(Long.MAX_VALUE) + blBigIntOfLong(1)
        assertEquals(exactOverflow, vm.execute(code, mutableMapOf("a" to Long.MAX_VALUE, "b" to 1)))
        assertEquals(7, vm.execute(code, mutableMapOf("a" to 3, "b" to 4)))
    }
}
