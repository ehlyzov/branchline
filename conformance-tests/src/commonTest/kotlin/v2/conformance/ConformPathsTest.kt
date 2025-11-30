package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformPathsTest {

    @Test
    fun set_and_append_inside_object() {
        val program = """
            TRANSFORM T { LET obj = {};
                SET obj.x = 1;
                APPEND TO obj.items 2 INIT [];
                OUTPUT { obj: obj }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap()) as Map<String, Any?>
        assertEquals(mapOf("x" to 1, "items" to listOf(2)), result["obj"])
    }

    @Test
    fun modify_root_object() {
        val program = """
            TRANSFORM T { LET o = { a: 1, b: 2 };
                MODIFY o { b: 3, c: 4 }
                OUTPUT { o: o }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap()) as Map<String, Any?>
        assertEquals(mapOf("a" to 1, "b" to 3, "c" to 4), result["o"])
    }
}
