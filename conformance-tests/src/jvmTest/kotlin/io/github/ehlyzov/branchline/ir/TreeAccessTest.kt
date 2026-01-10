package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun run(body: String, row: Map<String, Any?> = emptyMap(), engine: ExecutionEngine): Any? =
    compileAndRun(body, row, engine = engine)

class TreeAccessTest {

    @EngineTest
    fun `dot field access - nested`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { v : row.user.name }""",
            mapOf("user" to mapOf("name" to "Ann")),
            engine,
        )
        assertEquals(mapOf("v" to "Ann"), out)
    }

    @EngineTest
    fun `bracket index into array`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { second : row.items[1] }""",
            mapOf("items" to listOf("a", "b", "c")),
            engine,
        )
        assertEquals(mapOf("second" to "b"), out)
    }

    @EngineTest
    fun `dot-number path into array`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { v : row.items.2 }""",
            mapOf("items" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("v" to 30), out)
    }

    @EngineTest
    fun `array index out of bounds by bracket throws`(engine: ExecutionEngine) {
        val ex = assertThrows<IllegalArgumentException> {
            run("""OUTPUT row.items[5]""", mapOf("items" to listOf(1, 2)), engine)
        }
        assertTrue(ex.message!!.contains("out of bounds"))
    }

    @EngineTest
    fun `computed property in object literal`(engine: ExecutionEngine) {
        val out = run(
            """
            LET k = "x";
            LET o = { [k] : 42 };
            OUTPUT { v : o.x }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("v" to 42), out)
    }

    @EngineTest
    fun `dot bracket with explicit path inside - numeric key`(engine: ExecutionEngine) {
        val out = run(
            """
            LET template = { value : 10 };
            LET count    = { 10 : "test" };
            OUTPUT { res : count.[template.value] }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("res" to "test"), out)
    }

    @EngineTest
    fun `object index missing key wrapped yields null field`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { v : row.obj[1] }""",
            mapOf("obj" to mapOf("a" to 1)),
            engine,
        )
        assertEquals(mapOf("v" to null), out)
    }

    @EngineTest
    fun `object index missing key with bare OUTPUT triggers No OUTPUT`(engine: ExecutionEngine) {
        val ex = assertThrows<IllegalStateException> {
            run("""OUTPUT row.obj[1]""", mapOf("obj" to mapOf("a" to 1)), engine)
        }
        assertTrue(ex.message!!.contains("No OUTPUT"))
    }

    @EngineTest
    fun `KEYS on array returns indices`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { k : KEYS(row.xs) }""",
            mapOf("xs" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("k" to listOf(0, 1, 2)), out)
    }

    @EngineTest
    fun `KEYS on object returns fields in insertion order`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { k : KEYS(row.obj) }""",
            mapOf("obj" to linkedMapOf("a" to 1, "b" to 2)),
            engine,
        )
        assertEquals(mapOf("k" to listOf("a", "b")), out)
    }

    @EngineTest
    fun `PUT inserts new field into object by string key`(engine: ExecutionEngine) {
        val out = run(
            """
            LET obj = { a: 1 };
            OUTPUT { obj : PUT(obj, "b", 2) }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("obj" to mapOf("a" to 1, "b" to 2)), out)
    }

    @EngineTest
    fun `PUT replaces existing field`(engine: ExecutionEngine) {
        val out = run(
            """
            LET obj = { a: 1, b: 2 };
            OUTPUT { obj : PUT(obj, "b", 3) }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("obj" to mapOf("a" to 1, "b" to 3)), out)
    }

    @EngineTest
    fun `PUT with numeric object key coerces to string`(engine: ExecutionEngine) {
        val out = run(
            """
            LET obj = { 10 : "x" };
            OUTPUT { obj2 : PUT(obj, 20, "y") }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("obj2" to mapOf(10 to "x", 20 to "y")), out)
    }

    @EngineTest
    fun `PUT into array replaces by index`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { xs : PUT(row.xs, 1, 99) }""",
            mapOf("xs" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("xs" to listOf(10, 99, 30)), out)
    }

    @EngineTest
    fun `PUT into array appends when index equals size`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { xs : PUT(row.xs, 3, 40) }""",
            mapOf("xs" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("xs" to listOf(10, 20, 30, 40)), out)
    }

    @EngineTest
    fun `PUT into array out of bounds`(engine: ExecutionEngine) {
        val ex = assertThrows<RuntimeException> {
            run("""OUTPUT PUT(row.xs, 5, 99)""", mapOf("xs" to listOf(1, 2)), engine)
        }
        assertTrue(ex.message!!.contains("out of bounds"))
    }

    @EngineTest
    fun `DELETE object by string key`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { obj : DELETE(row.obj, "b") }""",
            mapOf("obj" to linkedMapOf("a" to 1, "b" to 2, "c" to 3)),
            engine,
        )
        assertEquals(mapOf("obj" to mapOf("a" to 1, "c" to 3)), out)
    }

    @EngineTest
    fun `DELETE object by numeric key coerced`(engine: ExecutionEngine) {
        val out = run(
            """
            LET m = { 10 : "x", 20 : "y" };
            OUTPUT { obj : DELETE(m, 10) }
            """.trimIndent(),
            engine = engine,
        )
        assertEquals(mapOf("obj" to mapOf(20 to "y")), out)
    }

    @EngineTest
    fun `DELETE array by index`(engine: ExecutionEngine) {
        val out = run(
            """OUTPUT { xs : DELETE(row.xs, 1) }""",
            mapOf("xs" to listOf(10, 20, 30)),
            engine,
        )
        assertEquals(mapOf("xs" to listOf(10, 30)), out)
    }
}
