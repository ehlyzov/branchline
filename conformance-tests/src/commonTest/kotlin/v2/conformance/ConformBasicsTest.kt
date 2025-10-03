package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformBasicsTest {

    @Test
    fun arithmetic_addition() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { v: 1 + 2 }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 3), out)
    }

    @Test
    fun if_else_expression() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { v: IF true THEN 1 ELSE 2 }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 1), out)
    }

    @Test
    fun array_comprehension() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                OUTPUT { arr: [x FOR EACH x IN row.items] }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(mapOf("items" to listOf(1, 2, 3)))
        assertEquals(mapOf("arr" to listOf(1, 2, 3)), out)
    }
}
