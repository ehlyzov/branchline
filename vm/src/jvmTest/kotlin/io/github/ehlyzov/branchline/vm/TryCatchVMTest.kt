package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.compileAndRunUnchecked
import kotlin.test.Test
import kotlin.test.assertEquals

class TryCatchVMTest {
    @Test
    fun try_succeeds_no_fallback() {
        val out = compileAndRunUnchecked(
            """
            TRY row.ok
            CATCH(e) RETRY 2 TIMES -> {err:"failed"}
            OUTPUT { res : "done" }
            """.trimIndent(),
            row = mapOf("ok" to 1),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("res" to "done"), out)
    }

    @Test
    fun try_fails_then_fallback_object() {
        val out = compileAndRunUnchecked(
            """
            TRY fail
            CATCH(e) RETRY 2 TIMES -> {err:"failed"}
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("err" to "failed"), out)
    }

    @Test
    fun retry_counter_works() {
        val out = compileAndRunUnchecked(
            """
            TRY fail
            CATCH(e) RETRY 0 TIMES -> {err:"no retry"}
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("err" to "no retry"), out)
    }
}

