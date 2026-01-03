package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunnerFromProgram

private fun exec(src: String, engine: ExecutionEngine): (Map<String, Any?>) -> Any? =
    buildRunnerFromProgram(src, engine = engine)

class FuncBlockBodyTest {

    @EngineTest
    fun `factorial via block body`(engine: ExecutionEngine) {
        val bl = """
            FUNC fact(n) {
                IF n == 0 THEN { RETURN 1 ; }
                RETURN n * fact(n - 1) ;
            }
            TRANSFORM T { OUTPUT { f : fact(input.n) }
            }
        """.trimIndent()

        assertEquals(mapOf("f" to 6), exec(bl, engine)(mapOf("n" to 3)))
    }

    @EngineTest
    fun `return optional`(engine: ExecutionEngine) {
        val bl = """
            FUNC maybe(x) { IF x ?? false THEN { RETURN "yes" ; } }
            TRANSFORM T { OUTPUT { v : maybe(input.flag) } }
        """.trimIndent()
        assertEquals(mapOf("v" to "yes"), exec(bl, engine)(mapOf("flag" to true)))
        assertEquals(mapOf("v" to null), exec(bl, engine)(mapOf("flag" to false)))
    }

    @EngineTest
    fun `output inside func forbidden`(engine: ExecutionEngine) {
        val bad = """
            FUNC wrong() { OUTPUT {x:1} }
            TRANSFORM T { OUTPUT { v: wrong() } }
        """.trimIndent()
        assertThrows<IllegalStateException> { exec(bad, engine)(emptyMap()) }
    }
}
