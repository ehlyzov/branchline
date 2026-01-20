package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.runtime.isBigInt
import kotlin.test.Test
import kotlin.test.assertTrue

class NumericJsTest {
    @Test
    fun promotes_to_bigint_on_safe_integer_overflow() {
        val program = """
            TRANSFORM T {
                OUTPUT { v: 9007199254740991 + 1 }
            }
        """.trimIndent()
        val run = buildRunnerFromProgramMP(program)
        val result = run(emptyMap()) as Map<*, *>
        assertTrue(isBigInt(result["v"]))
    }
}
