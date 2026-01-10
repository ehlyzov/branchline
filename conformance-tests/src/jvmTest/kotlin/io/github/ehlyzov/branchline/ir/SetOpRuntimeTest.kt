package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals

class SetOpRuntimeTest {

    @EngineTest
    fun `SET object insert and replace`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET o = { a: 1 };
            SET o.b = 2;
            SET o.a = 3;
            OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("a" to 3, "b" to 2), out)
    }

    @EngineTest
    fun `SET list replace only`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET xs = [1,2,3];
            SET xs[1] = 42;
            OUTPUT { xs: xs }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("xs" to listOf(1, 42, 3)), out)
    }

    @EngineTest
    fun `SET dynamic path`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET k = "x";
            LET o = {};
            SET o.[k] = 10;
            OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("x" to 10), out)
    }

    @EngineTest
    fun `SET into nested`(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
            LET o = { a: { b: [0,0] } };
            SET o.a.b[1] = 7;
            OUTPUT o;
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("a" to mapOf("b" to listOf(0, 7))), out)
    }

    @EngineTest
    fun `SET fails on missing mid path`(engine: ExecutionEngine) {
        assertThrows<IllegalStateException> {
            compileAndRun(
                """
                LET o = {};
                SET o.a.b = 1;
                OUTPUT o;
                """.trimIndent(),
                engine = engine,
            )
        }
    }
}
