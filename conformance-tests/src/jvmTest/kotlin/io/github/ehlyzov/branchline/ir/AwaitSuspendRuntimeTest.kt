package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunnerUnchecked

private fun buildExec(body: String, engine: ExecutionEngine): (Map<String, Any?>) -> Any? =
    buildRunnerUnchecked(body, engine = engine)

class AwaitSuspendRuntimeTest {

    @EngineTest
    fun `await passes through`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            OUTPUT { id : await row["id"] }
            """.trimIndent(),
            engine,
        )
        assertEquals(mapOf("id" to 42), exec(mapOf("id" to 42)))
    }

    @EngineTest
    fun `suspend behavior`(engine: ExecutionEngine) {
        val exec = buildExec(
            """
            OUTPUT { x : suspend row.id }
            """.trimIndent(),
            engine,
        )
        val attempt = runCatching { exec(mapOf("id" to 1)) }
        if (engine == ExecutionEngine.INTERPRETER) {
            val ex = attempt.exceptionOrNull()
            assertTrue(ex is UnsupportedOperationException)
        } else {
            val result = attempt.getOrNull()
            @Suppress("UNCHECKED_CAST")
            val snapshot = result as Map<String, Any?>
            assertTrue(snapshot["__suspended"] == true)
        }
    }
}
