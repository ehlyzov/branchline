package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.runtime.bignum.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class ConformNumericFunctionsTest {
    private fun evaluate(expression: String, input: Map<String, Any?> = emptyMap()): Any? =
        (buildRunnerFromProgramMP("TRANSFORM Main { OUTPUT { value: $expression } }")(input) as Map<*, *>)["value"]

    @Test
    fun ordinaryPowersAndRootsHaveFiniteFloatingResults() {
        assertEquals(8.0, evaluate("POWER(2, 3)"))
        assertEquals(-8.0, evaluate("POWER(-2, 3)"))
        assertEquals(0.25, evaluate("POWER(2, -2)"))
        assertEquals(3.0, evaluate("POWER(9, 0.5)"))
        assertEquals(1.0, evaluate("POWER(0, 0)"))
        assertEquals(0.0, evaluate("SQRT(0)"))
        assertEquals(3.0, evaluate("SQRT(9)"))
        assertEquals(kotlin.math.sqrt(2.0), evaluate("SQRT(2)"))
        assertEquals(null, evaluate("POWER(NULL, 1)"))
        assertEquals(null, evaluate("POWER(1, NULL)"))
        assertEquals(null, evaluate("SQRT(NULL)"))
    }

    @Test
    fun explicitNumbersStayInTheirDeclaredDomain() {
        val integer = evaluate("POWER(DEC(3), 20)")
        assertTrue(integer is BLBigInt)
        assertEquals("3486784401", integer.toString())
        val decimal = evaluate("POWER(DEC(\"1.25\"), 3)") as BLBigDec
        assertEquals(0, decimal.compareTo(blBigDecParse("1.953125")))
        val reciprocal = evaluate("POWER(DEC(2), -3)") as BLBigDec
        assertEquals(0, reciprocal.compareTo(blBigDecParse("0.125")))
        val root = evaluate("SQRT(DEC(\"2.25\"))") as BLBigDec
        assertEquals(0, root.compareTo(blBigDecParse("1.5")))
        assertEquals("1", evaluate("POWER(DEC(0), 0)").toString())
        assertEquals("1", evaluate("POWER(DEC(1), DEC(999999999))").toString())
    }

    @Test
    fun invalidTypesDomainsAndNonFiniteResultsFail() {
        for (expression in listOf(
            "POWER(1)", "POWER(1, 2, 3)", "SQRT()", "SQRT(1, 2)",
            "POWER(\"2\", 3)", "POWER(2, TRUE)", "SQRT(\"9\")",
            "POWER(-2, 0.5)", "POWER(0, -1)", "SQRT(-1)",
            "POWER(DEC(0), -1)", "SQRT(DEC(-1))",
            "POWER(DEC(2), 0.5)", "POWER(2.5, DEC(3))",
            "POWER(DEC(2), DEC(\"0.5\"))", "POWER(DEC(2), DEC(1000000000))",
            "POWER(DEC(2), DEC(-1000000000))", "POWER(10, 1000)",
        )) {
            assertFails(expression) { evaluate(expression) }
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFails { evaluate("POWER(input.value, 2)", mapOf("value" to value)) }
            assertFails { evaluate("POWER(2, input.value)", mapOf("value" to value)) }
            assertFails { evaluate("SQRT(input.value)", mapOf("value" to value)) }
        }
    }
}
