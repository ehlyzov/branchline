package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals

class AppendToOpRuntimeTest {

    @EngineTest
    fun `append to missing key with INIT seed`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
                LET o = {};
                APPEND TO o.x 1 INIT [0];
                OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("x" to listOf(0, 1)), out)
    }

    @EngineTest
    fun `append to existing null with INIT`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
                LET o = { x: null };
                APPEND TO o.x 7 INIT [1,2];
                OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("x" to listOf(1, 2, 7)), out)
    }

    @EngineTest
    fun `append ignores INIT when list exists`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
                LET o = { x: [10] };
                APPEND TO o.x 11 INIT [0,0];
                OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("x" to listOf(10, 11)), out)
    }

    @EngineTest
    fun `append to non-list even with INIT - error`(engine: ExecutionEngine) {
        assertThrows<IllegalStateException> {
            compileAndRun(
                """
                    LET o = { x: {a:1} };
                    APPEND TO o.x 2 INIT [];
                    OUTPUT o;
                """.trimIndent(),
                engine = engine,
            )
        }
    }
}
