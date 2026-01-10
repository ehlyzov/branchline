package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner

private fun execBody(body: String, engine: ExecutionEngine) = buildRunner(body, engine = engine)

class OperatorRuntimeTest {

    @EngineTest
    fun `equality numbers`(engine: ExecutionEngine) {
        val f = execBody("OUTPUT { ok : row.x == row.y }", engine)
        assertEquals(mapOf("ok" to true), f(mapOf("x" to 5, "y" to 5)))
        assertEquals(mapOf("ok" to false), f(mapOf("x" to 5, "y" to 6)))
    }

    @EngineTest
    fun `less greater`(engine: ExecutionEngine) {
        val f = execBody("OUTPUT { lt : row.a < 10 , ge : row.a >= 10 }", engine)
        assertEquals(mapOf("lt" to true, "ge" to false), f(mapOf("a" to 3)))
    }

    @EngineTest
    fun `logical and or`(engine: ExecutionEngine) {
        val f = execBody("OUTPUT { ok : row.a > 0 && row.b < 10 || false }", engine)
        assertEquals(mapOf("ok" to true), f(mapOf("a" to 1, "b" to 5)))
    }

    @EngineTest
    fun `coalesce operator`(engine: ExecutionEngine) {
        val f = execBody("OUTPUT { v : row.opt ?? 42 }", engine)
        assertEquals(mapOf("v" to 42), f(emptyMap()))
        assertEquals(mapOf("v" to 99), f(mapOf("opt" to 99)))
    }

    @EngineTest
    fun `unary not`(engine: ExecutionEngine) {
        val f = execBody("OUTPUT { flag : !row.on }", engine)
        assertEquals(mapOf("flag" to true), f(mapOf("on" to false)))
        assertEquals(mapOf("flag" to false), f(mapOf("on" to true)))
    }
}
