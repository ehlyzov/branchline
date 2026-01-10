package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.buildRunnerFromProgram
import kotlin.test.Test
import kotlin.test.assertEquals

class FuncVMTest {

    @Test
    fun expr_body_func_call() {
        val runner = buildRunnerFromProgram(
            """
            FUNC inc(x) = x + 1;
            TRANSFORM T { OUTPUT { y: inc(41) }
            }
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("y" to 42), runner(emptyMap()))
    }

    @Test
    fun block_body_func_with_return() {
        val runner = buildRunnerFromProgram(
            """
            FUNC sum(a,b) {
                LET s = a + b;
                RETURN s;
            }
            TRANSFORM T { OUTPUT { z: sum(3,4) }
            }
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("z" to 7), runner(emptyMap()))
    }

    @Test
    fun func_reads_outer_env_disallowed() {
        val runner = buildRunnerFromProgram(
            """
            FUNC addK(x) = x + k;
            TRANSFORM T { LET k = 10;
                OUTPUT { v: addK(5) }
            }
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        try {
            runner(emptyMap())
        } catch (e: Exception) {
            // expected: function must not capture outer LET
            return
        }
        throw AssertionError("Function unexpectedly captured outer environment")
    }
}
