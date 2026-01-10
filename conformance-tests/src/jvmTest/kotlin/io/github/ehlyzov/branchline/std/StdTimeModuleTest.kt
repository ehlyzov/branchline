package io.github.ehlyzov.branchline.std

import java.time.Instant
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StdTimeModuleTest {

    @EngineTest
    fun now_returns_iso_timestamp(engine: ExecutionEngine) {
        val out = compileAndRun("OUTPUT { t: NOW() }", engine = engine)
        val t = (out as Map<String, Any?>)["t"] as String
        val parsed = Instant.parse(t)
        assertEquals(parsed.toString(), t)
    }

    @EngineTest
    fun now_throws_on_arguments(engine: ExecutionEngine) {
        val ex = assertFailsWith<IllegalArgumentException> {
            compileAndRun(
                """
                LET t = NOW("bad") ;
                OUTPUT { t: t }
                """.trimIndent(),
                engine = engine,
            )
        }
        assertTrue(ex.message?.contains("NOW()") == true)
    }
}
