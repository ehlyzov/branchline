package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformHostFnsTest {

    @Test
    fun call_host_function() {
        val host = mapOf(
            "ADD1" to { args: List<Any?> -> ((args[0] as? Int) ?: (args[0] as Number).toInt()) + 1 }
        )
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { v: ADD1(41) }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program, hostFns = host)
        val res = runner(emptyMap())
        assertEquals(mapOf("v" to 42), res)
    }
}
