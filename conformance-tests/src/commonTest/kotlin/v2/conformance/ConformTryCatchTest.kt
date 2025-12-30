package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformTryCatchTest {

    @Test
    fun try_catch_expression_success() {
        val program = """
            TRANSFORM T { OUTPUT { value: TRY 1 + 1 CATCH(err) -> 0 }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("value" to 2), out)
    }

    @Test
    fun try_catch_expression_binds_error() {
        val program = """
            TRANSFORM T { OUTPUT { message: TRY ASSERT(false, "boom") CATCH(err) -> err.message }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("message" to "boom"), out)
    }
}
