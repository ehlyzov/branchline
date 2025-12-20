package v2.std

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import v2.testutils.compileAndRun

class StdCoreStringsRuntimeTest {

    @Test
    fun `LISTIFY returns list as-is`() {
        val list = listOf(1, 2, 3)
        val out = compileAndRun(
            """OUTPUT { xs: LISTIFY(row.xs) }""",
            mapOf("xs" to list)
        ) as Map<*, *>
        assertSame(list, out["xs"])
    }

    @Test
    fun `LISTIFY null becomes empty list`() {
        val out = compileAndRun("""OUTPUT { xs: LISTIFY(NULL) }""")
        assertEquals(mapOf("xs" to emptyList<Any?>()), out)
    }

    @Test
    fun `GET returns default only when missing`() {
        val out = compileAndRun(
            """
            OUTPUT {
                hit: GET(row.obj, "a", "fallback"),
                miss: GET(row.obj, "b", "fallback")
            };
            """.trimIndent(),
            mapOf("obj" to mapOf("a" to null))
        ) as Map<*, *>
        assertEquals(null, out["hit"])
        assertEquals("fallback", out["miss"])
    }

    @Test
    fun `INT parses strings strictly`() {
        val out = compileAndRun("""OUTPUT { v: INT("0012") }""")
        assertEquals(mapOf("v" to 12), out)
    }

    @Test
    fun `INT rejects fractional values`() {
        assertThrows<IllegalArgumentException> {
            compileAndRun("""OUTPUT INT(1.5)""")
        }
    }

    @Test
    fun `PARSE_INT uses digits and default`() {
        val out = compileAndRun(
            """
            OUTPUT {
                a: PARSE_INT("x12y"),
                b: PARSE_INT("nope", 5),
                c: PARSE_INT(NULL, 7)
            };
            """.trimIndent()
        )
        assertEquals(mapOf("a" to 12, "b" to 5, "c" to 7), out)
    }

    @Test
    fun `PARSE_INT rejects fractional values`() {
        assertThrows<IllegalArgumentException> {
            compileAndRun("""OUTPUT PARSE_INT(2.75)""")
        }
    }
}
