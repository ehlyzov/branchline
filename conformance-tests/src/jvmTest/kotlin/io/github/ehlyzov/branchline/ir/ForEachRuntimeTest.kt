package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.sema.SemanticException
import org.junit.jupiter.api.Assumptions.assumeTrue
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun ExecutionEngine.exec(body: String) = buildRunner(body, engine = this)

class ForEachRuntimeTest {

    @EngineTest
    fun `simple projection`(engine: ExecutionEngine) {
        val exec = engine.exec(
            """
            FOR EACH it IN row.items {
                OUTPUT { q : it.qty }
            }
            """.trimIndent(),
        )
        val input = mapOf(
            "items" to listOf(
                mapOf("qty" to 2), mapOf("qty" to 5),
            ),
        )
        assertEquals(listOf(mapOf("q" to 2), mapOf("q" to 5)), exec(input))
    }

    @EngineTest
    fun `nested let inside loop`(engine: ExecutionEngine) {
        val exec = engine.exec(
            """
            LET base = 10;
            FOR EACH x IN row.nums {
                LET sum = base + x ;
                OUTPUT { s : sum }
            }
            """.trimIndent(),
        )
        val out = exec(mapOf("nums" to listOf(1, 2)))
        assertEquals(listOf(mapOf("s" to 11), mapOf("s" to 12)), out)
    }

    @EngineTest
    fun `iterable not array throws`(engine: ExecutionEngine) {
        val exec = engine.exec(
            """
            FOR EACH x IN row.notArray { OUTPUT {x:x} }
            """.trimIndent(),
        )
        assertThrows<IllegalStateException> {
            exec(mapOf("notArray" to 123))
        }
    }

    @EngineTest
    fun `for-each with WHERE filters outputs`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            FOR EACH it IN row.items WHERE it.qty > 2 {
                OUTPUT { sku: it.sku }
            }
            """.trimIndent(),
        )
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 1),
                mapOf("sku" to "B", "qty" to 5),
            ),
        )
        assertEquals(mapOf("sku" to "B"), f(inRow))
    }

    @EngineTest
    fun `accumulates across iterations in single loop`(engine: ExecutionEngine) {
        val program = """
            LET acc = {};
            FOR i IN [1, 2, 3] {
                APPEND TO acc.values i INIT [];
            }
            OUTPUT acc;
        """.trimIndent()

        val expected = mapOf("values" to listOf(1, 2, 3))
        val res = compileAndRun(program, engine = engine)
        assertEquals(expected, res)
    }

    @EngineTest
    fun `preserves accumulators across multiple loops`(engine: ExecutionEngine) {
        val program = """
            LET acc = {};
            FOR i IN [1, 2] {
                APPEND TO acc.a i INIT [];
            }
            FOR j IN [10, 20] {
                APPEND TO acc.b j INIT [];
            }
            OUTPUT acc;
        """.trimIndent()

        val expected = mapOf(
            "a" to listOf(1, 2),
            "b" to listOf(10, 20),
        )
        val res = compileAndRun(program, engine = engine)
        assertEquals(expected, res)
    }

    @EngineTest
    fun `nested loops accumulate by dynamic key`(engine: ExecutionEngine) {
        val program = """
            LET acc = {};
            FOR prod IN row.products {
                FOR attr IN prod.characteristics {
                    APPEND TO acc.[attr] prod INIT [];
                }
            }
            OUTPUT acc;
        """.trimIndent()

        val product1 = mapOf("id" to 1, "price" to 100, "characteristics" to listOf("red", "large", "metal"))
        val product2 = mapOf("id" to 2, "price" to 150, "characteristics" to listOf("blue", "small", "plastic"))
        val product3 = mapOf("id" to 3, "price" to 200, "characteristics" to listOf("red", "small", "plastic"))
        val data = listOf(product1, product2, product3)

        val expected = mapOf(
            "red" to listOf(product1, product3),
            "large" to listOf(product1),
            "metal" to listOf(product1),
            "blue" to listOf(product2),
            "small" to listOf(product2, product3),
            "plastic" to listOf(product2, product3),
        )
        val res = compileAndRun(program, mapOf("products" to data), engine = engine)
        assertEquals(expected, res)
    }

    @EngineTest
    fun `loop variable does not leak after loop - semantic error`(engine: ExecutionEngine) {
        val program = """
            LET acc = 0;
            FOR x IN [42] {
                LET tmp = 7;
            }
            OUTPUT { acc: acc, x: x };
        """.trimIndent()

        assertThrows<SemanticException> {
            compileAndRun(program, engine = engine)
        }
    }

    @EngineTest
    fun `loop variable shadowing does not overwrite outer binding`(engine: ExecutionEngine) {
        val program = """
            LET x = 99;
            FOR x IN [1, 2, 3] {
            }
            OUTPUT { x: x };
        """.trimIndent()

        val res = compileAndRun(program, engine = engine)
        assertEquals(mapOf("x" to 99), res)
    }

    @EngineTest
    fun for_each_restores_outer_loop_var_only(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
              LET k = "keep";
              FOR k IN [1,2] { }
              OUTPUT { after: k };
            """.trimIndent(),
            engine = engine,
        ) as Map<*, *>
        assertEquals("keep", out["after"])
    }

    @EngineTest
    fun for_each_persists_mutations_sum_and_append(engine: ExecutionEngine) {
        val out = compileAndRun(
            """
              LET sum = 0;
              LET acc = [];
              FOR n IN [1,2,3] {
                SET sum = sum + n;
                APPEND TO acc n INIT [];
              }
              OUTPUT { sum: sum, acc: acc };
            """.trimIndent(),
            engine = engine,
        ) as Map<*, *>
        assertEquals(6, out["sum"])
        assertEquals(listOf(1, 2, 3), out["acc"])
    }
}
