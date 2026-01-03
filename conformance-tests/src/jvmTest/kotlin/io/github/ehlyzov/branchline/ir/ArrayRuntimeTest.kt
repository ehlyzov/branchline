package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner

private fun ExecutionEngine.exec(body: String) = buildRunner(body, engine = this)

class ArrayRuntimeTest {

    @EngineTest
    fun `array literal`(engine: ExecutionEngine) {
        val f = engine.exec("OUTPUT { arr : [1, 2, row.x] }")
        assertEquals(mapOf("arr" to listOf(1, 2, 3)), f(mapOf("x" to 3)))
    }

    @EngineTest
    fun `array comprehension`(engine: ExecutionEngine) {
        val f = engine.exec(
            "OUTPUT { qties : [ item.qty FOR EACH item IN row.items ] }"
        )
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 2),
                mapOf("sku" to "B", "qty" to 5)
            )
        )
        assertEquals(mapOf("qties" to listOf(2, 5)), f(inRow))
    }

    @EngineTest
    fun `empty array literal`(engine: ExecutionEngine) {
        val f = engine.exec("OUTPUT { arr : [] }")
        assertEquals(mapOf("arr" to emptyList<Any?>()), f(emptyMap()))
    }

    @EngineTest
    fun `heterogeneous array literal`(engine: ExecutionEngine) {
        val f = engine.exec("""OUTPUT { arr : ["a", 1, TRUE] }""")
        assertEquals(mapOf("arr" to listOf("a", 1, true)), f(emptyMap()))
    }

    @EngineTest
    fun `array of object literals`(engine: ExecutionEngine) {
        val f = engine.exec("""OUTPUT { arr : [ {a:1}, {b:2} ] }""")
        assertEquals(
            mapOf("arr" to listOf(mapOf("a" to 1), mapOf("b" to 2))),
            f(emptyMap())
        )
    }

    @EngineTest
    fun `nested array literal`(engine: ExecutionEngine) {
        val f = engine.exec("""OUTPUT { arr : [ [1,2], [3] ] }""")
        assertEquals(
            mapOf("arr" to listOf(listOf(1, 2), listOf(3))),
            f(emptyMap())
        )
    }

    @EngineTest
    fun `array literal in LET and reuse`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            LET xs = [1,2,3];
            OUTPUT { arr : xs }
            """.trimIndent()
        )
        assertEquals(mapOf("arr" to listOf(1, 2, 3)), f(emptyMap()))
    }

    @EngineTest
    fun `comprehension with arithmetic and outer LET`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            LET base = 10;
            OUTPUT { sums : [ base + n FOR EACH n IN row.nums ] }
            """.trimIndent()
        )
        val inRow = mapOf("nums" to listOf(1, 2))
        assertEquals(mapOf("sums" to listOf(11, 12)), f(inRow))
    }

    @EngineTest
    fun `comprehension mapping to objects`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            OUTPUT { items : [
                { sku: item.sku, qty: item.qty * 2 }
                FOR EACH item IN row.items
            ] }
            """.trimIndent()
        )
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 2),
                mapOf("sku" to "B", "qty" to 5)
            )
        )
        assertEquals(
            mapOf(
                "items" to listOf(
                    mapOf("sku" to "A", "qty" to 4),
                    mapOf("sku" to "B", "qty" to 10)
                )
            ),
            f(inRow)
        )
    }

    @EngineTest
    fun `array comprehension with WHERE`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
        OUTPUT { q: [ it.qty FOR EACH it IN row.items WHERE it.qty > 2 ] }
            """.trimIndent()
        )
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 1),
                mapOf("sku" to "B", "qty" to 5)
            )
        )
        assertEquals(mapOf("q" to listOf(5)), f(inRow))
    }

    @EngineTest
    fun `comprehension with computed property in object`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            OUTPUT { arr : [
                { [ item.sku ] : item.qty }
                FOR EACH item IN row.items
            ] }
            """.trimIndent()
        )
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 2),
                mapOf("sku" to "B", "qty" to 5)
            )
        )
        assertEquals(
            mapOf(
                "arr" to listOf(
                    mapOf("A" to 2),
                    mapOf("B" to 5)
                )
            ),
            f(inRow)
        )
    }

    @EngineTest
    fun `comprehension over empty list`(engine: ExecutionEngine) {
        val f = engine.exec("""OUTPUT { xs : [ x FOR EACH x IN row.list ] }""")
        assertEquals(mapOf("xs" to emptyList<Any?>()), f(mapOf("list" to emptyList<Any?>())))
    }

    @EngineTest
    fun `comprehension source is not a list - error`(engine: ExecutionEngine) {
        val f = engine.exec("""OUTPUT { xs : [ x FOR EACH x IN row.notList ] }""")
        assertThrows<IllegalStateException> {
            f(mapOf("notList" to 42))
        }
    }

    @EngineTest
    fun `nested comprehension - clone matrix`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            OUTPUT { matrix :
              [ [ y FOR EACH y IN xs ] FOR EACH xs IN row.matrix ]
            }
            """.trimIndent()
        )
        val inRow = mapOf(
            "matrix" to listOf(
                listOf(1, 2),
                listOf(3)
            )
        )
        assertEquals(
            mapOf(
                "matrix" to listOf(
                    listOf(1, 2),
                    listOf(3)
                )
            ),
            f(inRow)
        )
    }

    @EngineTest
    fun `nested comprehension with arithmetic inside inner`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            OUTPUT { matrix :
              [ [ y * 10 FOR EACH y IN xs ] FOR EACH xs IN row.matrix ]
            }
            """.trimIndent()
        )
        val inRow = mapOf(
            "matrix" to listOf(
                listOf(1, 2),
                listOf(3)
            )
        )
        assertEquals(
            mapOf(
                "matrix" to listOf(
                    listOf(10, 20),
                    listOf(30)
                )
            ),
            f(inRow)
        )
    }
}
