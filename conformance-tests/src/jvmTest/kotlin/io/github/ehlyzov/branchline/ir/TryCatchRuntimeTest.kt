package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunnerUnchecked

private fun execBody(body: String, engine: ExecutionEngine) = buildRunnerUnchecked(body, engine = engine)

class TryCatchRuntimeTest {

    @EngineTest
    fun `try succeeds no fallback`(engine: ExecutionEngine) {
        val f = execBody(
            """
            TRY row.ok
            CATCH(e) RETRY 2 TIMES -> {err:"failed"}
            OUTPUT { res : "done" }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("res" to "done"), f(mapOf("ok" to 1)))
    }

    @EngineTest
    fun `try fails then fallback object`(engine: ExecutionEngine) {
        val f = execBody(
            """
            TRY fail
            CATCH(e) RETRY 2 TIMES -> {err:"failed"}
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("err" to "failed"), f(emptyMap()))
    }

    @EngineTest
    fun `retry counter works`(engine: ExecutionEngine) {
        val f = execBody(
            """
            TRY fail
            CATCH(e) RETRY 0 TIMES -> {err:"no retry"}
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("err" to "no retry"), f(emptyMap()))
    }
}
