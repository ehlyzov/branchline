package io.github.ehlyzov.branchline.std

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.github.ehlyzov.branchline.testutils.compileAndRun

class StdLibWalkCollectTest {

    @Test
    fun `WALK over object with string and numeric keys - internal nodes included`() {
        val out = compileAndRun(
            """
            LET obj = { a: "one", 10: "ten", b: { c: "two" } };
            OUTPUT { entries: COLLECT(WALK(obj)) }
            """.trimIndent()
        )

        val expected = mapOf(
            "entries" to listOf(
                // root (object)
                mapOf(
                    "path" to emptyList<Any>(),
                    "key" to null,
                    "value" to mapOf("a" to "one", 10 to "ten", "b" to mapOf("c" to "two")),
                    "depth" to 0,
                    "isLeaf" to false
                ),
                // 'a' leaf
                mapOf(
                    "path" to listOf<Any>("a"),
                    "key" to "a",
                    "value" to "one",
                    "depth" to 1,
                    "isLeaf" to true
                ),
                // 10 (numeric key) leaf
                mapOf(
                    "path" to listOf<Any>(10),
                    "key" to 10,
                    "value" to "ten",
                    "depth" to 1,
                    "isLeaf" to true
                ),
                // 'b' internal
                mapOf(
                    "path" to listOf<Any>("b"),
                    "key" to "b",
                    "value" to mapOf("c" to "two"),
                    "depth" to 1,
                    "isLeaf" to false
                ),
                // 'b.c' leaf
                mapOf(
                    "path" to listOf<Any>("b", "c"),
                    "key" to "c",
                    "value" to "two",
                    "depth" to 2,
                    "isLeaf" to true
                ),
            )
        )
        assertEquals(expected, out)
    }

    @Test
    fun `WALK over array with nested and empty lists - internal nodes included`() {
        val out = compileAndRun(
            """
            LET arr = ["x", ["y"], []];
            OUTPUT { entries: COLLECT(WALK(arr)) }
            """.trimIndent()
        )

        val expected = mapOf(
            "entries" to listOf(
                // root (array)
                mapOf(
                    "path" to emptyList<Any>(),
                    "key" to null,
                    "value" to listOf<Any>("x", listOf("y"), emptyList<Any>()),
                    "depth" to 0,
                    "isLeaf" to false
                ),
                // [0] leaf
                mapOf(
                    "path" to listOf<Any>(0),
                    "key" to 0,
                    "value" to "x",
                    "depth" to 1,
                    "isLeaf" to true
                ),
                // [1] internal
                mapOf(
                    "path" to listOf<Any>(1),
                    "key" to 1,
                    "value" to listOf("y"),
                    "depth" to 1,
                    "isLeaf" to false
                ),
                // [1,0] leaf
                mapOf(
                    "path" to listOf<Any>(1, 0),
                    "key" to 0,
                    "value" to "y",
                    "depth" to 2,
                    "isLeaf" to true
                ),
                // [2] empty list — leaf
                mapOf(
                    "path" to listOf<Any>(2),
                    "key" to 2,
                    "value" to emptyList<Any?>(),
                    "depth" to 1,
                    "isLeaf" to true
                ),
            )
        )
        assertEquals(expected, out)
    }

    @Test
    fun `WALK preserves key types - string 1 vs numeric 1 (with root)`() {
        val out = compileAndRun(
            """
            LET obj = { 1: "num", "1": "str" };
            OUTPUT { entries: COLLECT(WALK(obj)) }
            """.trimIndent()
        )

        val expected = mapOf(
            "entries" to listOf(
                // root
                mapOf(
                    "path" to emptyList<Any>(),
                    "key" to null,
                    "value" to mapOf(1 to "num", "1" to "str"),
                    "depth" to 0,
                    "isLeaf" to false
                ),
                // numeric 1
                mapOf(
                    "path" to listOf<Any>(1),
                    "key" to 1,
                    "value" to "num",
                    "depth" to 1,
                    "isLeaf" to true
                ),
                // string "1"
                mapOf(
                    "path" to listOf<Any>("1"),
                    "key" to "1",
                    "value" to "str",
                    "depth" to 1,
                    "isLeaf" to true
                ),
            )
        )
        assertEquals(expected, out)
    }

    @Test
    fun `WALK of empty object and empty array - root is leaf`() {
        val outObj = compileAndRun("""OUTPUT { entries: COLLECT(WALK({})) }""")
        val outArr = compileAndRun("""OUTPUT { entries: COLLECT(WALK([])) }""")

        assertEquals(
            mapOf(
                "entries" to listOf(
                    mapOf(
                        "path" to emptyList<Any>(),
                        "key" to null,
                        "value" to emptyMap<Any, Any>(),
                        "depth" to 0,
                        "isLeaf" to true
                    )
                )
            ),
            outObj
        )

        assertEquals(
            mapOf(
                "entries" to listOf(
                    mapOf(
                        "path" to emptyList<Any>(),
                        "key" to null,
                        "value" to emptyList<Any?>(),
                        "depth" to 0,
                        "isLeaf" to true
                    )
                )
            ),
            outArr
        )
    }

    @Test
    fun `WALK sees null as leaf - with root and fields`() {
        val out = compileAndRun(
            """
            LET obj = { a: null, b: "B" };
            OUTPUT { entries: COLLECT(WALK(obj)) }
            """.trimIndent()
        )
        val expected = mapOf(
            "entries" to listOf(
                // root object
                mapOf(
                    "path" to emptyList<Any>(),
                    "key" to null,
                    "value" to mapOf("a" to null, "b" to "B"),
                    "depth" to 0,
                    "isLeaf" to false
                ),
                // a = null leaf
                mapOf(
                    "path" to listOf<Any>("a"),
                    "key" to "a",
                    "value" to null,
                    "depth" to 1,
                    "isLeaf" to true
                ),
                // b = "B" leaf
                mapOf(
                    "path" to listOf<Any>("b"),
                    "key" to "b",
                    "value" to "B",
                    "depth" to 1,
                    "isLeaf" to true
                ),
            )
        )
        assertEquals(expected, out)
    }

    @Test
    fun `COLLECT materializes sequence from WALK`() {
        val out = compileAndRun(
            """
            LET obj = { x: [1] };
            OUTPUT { list: COLLECT(WALK(obj)) }
            """.trimIndent()
        )
        val expected = mapOf(
            "list" to listOf(
                mapOf(
                    "path" to emptyList<Any>(),
                    "key" to null,
                    "value" to mapOf("x" to listOf(1)),
                    "depth" to 0,
                    "isLeaf" to false
                ),
                mapOf(
                    "path" to listOf<Any>("x"),
                    "key" to "x",
                    "value" to listOf(1),
                    "depth" to 1,
                    "isLeaf" to false
                ),
                mapOf(
                    "path" to listOf<Any>("x", 0),
                    "key" to 0,
                    "value" to 1,
                    "depth" to 2,
                    "isLeaf" to true
                ),
            )
        )
        assertEquals(expected, out)
    }
}
