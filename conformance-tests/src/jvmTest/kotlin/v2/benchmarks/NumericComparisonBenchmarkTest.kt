package v2.benchmarks

import kotlin.test.Test
import v2.ExecutionEngine
import v2.testutils.buildRunnerFromProgram
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class NumericComparisonBenchmarkTest {
    private val program = """
        TRANSFORM Compare {
            LET eq = input.a == input.b;
            LET lt = input.a < input.c;
            OUTPUT { eq: eq, lt: lt };
        }
    """.trimIndent()

    private val interpreterRunner = buildRunnerFromProgram(program, engine = ExecutionEngine.INTERPRETER)
    private val vmRunner = buildRunnerFromProgram(program, engine = ExecutionEngine.VM)

    @Test
    @Suppress("KotlinJunit5IgnoredFunction") // mark as bench; opt-in when needed
    fun interpreterEqualityMicrobenchmark_disabled() {
        val input = mapOf("a" to 1, "b" to 1.0, "c" to 2)
        val iterations = 500
        val elapsed = measureTimeMillis {
            repeat(iterations) { interpreterRunner(input) }
        }
        // Sanity: ensure comparisons still evaluate correctly
        val result = interpreterRunner(input) as Map<*, *>
        assertTrue(result["eq"] == true && result["lt"] == true)
        println("[bench-disabled] Interpreter equality (" + iterations + " iterations): " + elapsed + "ms")
    }

    @Test
    @Suppress("KotlinJunit5IgnoredFunction") // mark as bench; opt-in when needed
    fun vmEqualityMicrobenchmark_disabled() {
        val input = mapOf("a" to 1, "b" to 1.0, "c" to 2)
        val iterations = 2000
        val elapsed = measureTimeMillis {
            repeat(iterations) { vmRunner(input) }
        }
        val result = vmRunner(input) as Map<*, *>
        assertTrue(result["eq"] == true && result["lt"] == true)
        println("[bench-disabled] VM equality (" + iterations + " iterations): " + elapsed + "ms")
    }
}
