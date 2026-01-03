package io.github.ehlyzov.branchline.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP

class ConformBasicsTest {

    @Test
    fun arithmetic_addition() {
        val program = """
            TRANSFORM T { OUTPUT { v: 1 + 2 }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 3), out)
    }

    @Test
    fun if_else_expression() {
        val program = """
            TRANSFORM T { OUTPUT { v: IF true THEN 1 ELSE 2 }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 1), out)
    }

    @Test
    fun array_comprehension() {
        val program = """
            TRANSFORM T { OUTPUT { arr: [x FOR EACH x IN input.items] }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(mapOf("items" to listOf(1, 2, 3)))
        assertEquals(mapOf("arr" to listOf(1, 2, 3)), out)
    }

    @Test
    fun function_return_statement() {
        val program = """
            FUNC addOne(x) { RETURN x + 1; }
            TRANSFORM T { OUTPUT { v: addOne(2) }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to 3), out)
    }
}
