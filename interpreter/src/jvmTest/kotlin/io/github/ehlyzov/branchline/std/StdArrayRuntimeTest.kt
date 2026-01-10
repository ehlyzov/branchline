package io.github.ehlyzov.branchline.std

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.testutils.compileAndRun

class StdArrayRuntimeTest {

    // ---------- KEYS ----------

    @Test
    fun `KEYS on array returns indices`() {
        val out = compileAndRun(
            """OUTPUT { k : KEYS(row.xs) }""",
            mapOf("xs" to listOf(10, 20, 30))
        )
        assertEquals(mapOf("k" to listOf(0, 1, 2)), out)
    }

    @Test
    fun `KEYS on object returns its fields`() {
        val out = compileAndRun(
            """OUTPUT { k : KEYS(row.obj) }""",
            mapOf("obj" to linkedMapOf("a" to 1, "b" to 2))
        )
        // Порядок вставки: ["a","b"]
        assertEquals(mapOf("k" to listOf("a", "b")), out)
    }

    @Test
    fun `KEYS on scalar - error`() {
        assertThrows(IllegalStateException::class.java) {
            compileAndRun("""OUTPUT KEYS(42)""")
        }
    }

    // ---------- APPEND / PREPEND ----------

    @Test
    fun `APPEND adds to end`() {
        val out = compileAndRun(
            """OUTPUT { xs : APPEND(row.xs, 99) }""",
            mapOf("xs" to listOf(1, 2))
        )
        assertEquals(mapOf("xs" to listOf(1, 2, 99)), out)
    }

    @Test
    fun `PREPEND adds to start`() {
        val out = compileAndRun(
            """OUTPUT { xs : PREPEND(row.xs, 0) }""",
            mapOf("xs" to listOf(1, 2))
        )
        assertEquals(mapOf("xs" to listOf(0, 1, 2)), out)
    }

    @Test
    fun `APPEND - first arg not a list`() {
        assertThrows<IllegalStateException> {
            compileAndRun("""OUTPUT APPEND(123, 5)""")
        }
    }

    // ---------- DELETE ----------

    @Test
    fun `DELETE from array by index`() {
        val out = compileAndRun(
            """OUTPUT { xs : DELETE(row.xs, 1) }""",
            mapOf("xs" to listOf(10, 20, 30))
        )
        assertEquals(mapOf("xs" to listOf(10, 30)), out)
    }

    @Test
    fun `DELETE from object by key`() {
        val out = compileAndRun(
            """OUTPUT { obj : DELETE(row.obj, "b") }""",
            mapOf("obj" to linkedMapOf("a" to 1, "b" to 2, "c" to 3))
        )
        assertEquals(mapOf("obj" to mapOf("a" to 1, "c" to 3)), out)
    }

    @Test
    fun `DELETE array - index out of bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            compileAndRun(
                """OUTPUT DELETE(row.xs, 5)""",
                mapOf("xs" to listOf(1, 2))
            )
        }
    }

    @Test
    fun `DELETE object - key is index`() {
        val out = compileAndRun(
            """OUTPUT DELETE(row.obj, 1)""",
            mapOf("obj" to mapOf(1 to 1))
        )
        assertEquals(emptyMap<Any, Any>(), out)
    }

    // ---------- CONCAT (++) ----------

    @Test
    fun `CONCAT on arrays with ++`() {
        val out = compileAndRun(
            """OUTPUT { xs : row.xs ++ [3,4] }""",
            mapOf("xs" to listOf(1, 2))
        )
        assertEquals(mapOf("xs" to listOf(1, 2, 3, 4)), out)
    }

    @Test
    fun `CONCAT chaining - left assoc`() {
        val out = compileAndRun(
            """OUTPUT { xs : [0] ++ row.xs ++ [3] }""",
            mapOf("xs" to listOf(1, 2))
        )
        assertEquals(mapOf("xs" to listOf(0, 1, 2, 3)), out)
    }

    @Test
    fun `REVERSE list`() {
        val out = compileAndRun(
            """OUTPUT { list: REVERSE(input.list) }""",
            mapOf("list" to listOf(1, 2, 3))
        )
        assertEquals(mapOf("list" to listOf(3, 2, 1)), out)
    }

    // ---------- Универсальный паттерн удаления без GET/INDICES ----------

    @Test
    fun `rebuild array by KEYS and bracket access`() {
        val row = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 1),
                mapOf("sku" to "B", "qty" to 2),
                mapOf("sku" to "A", "qty" to 3)
            )
        )
        val out = compileAndRun(
            """
            LET keep = [
              row.items[k]
              FOR EACH k IN KEYS(row.items)
              WHERE row.items[k].sku != "A"
            ];
            OUTPUT { items : keep }
            """.trimIndent(),
            row
        )
        assertEquals(
            mapOf(
                "items" to listOf(
                    mapOf("sku" to "B", "qty" to 2)
                )
            ),
            out
        )
    }

    @Test
    fun `delete single by key from array using KEYS and DELETE`() {
        val row = mapOf(
            "items" to listOf(
                mapOf("sku" to "X"), mapOf("sku" to "Y"), mapOf("sku" to "Z")
            )
        )
        val out = compileAndRun(
            """
            LET ks = KEYS(row.items);
            LET head = ks.0;             // 0
            OUTPUT { items : DELETE(row.items, head) }
            """.trimIndent(),
            row
        )
        assertEquals(
            mapOf(
                "items" to listOf(
                    mapOf("sku" to "Y"), mapOf("sku" to "Z")
                )
            ),
            out
        )
    }
}
