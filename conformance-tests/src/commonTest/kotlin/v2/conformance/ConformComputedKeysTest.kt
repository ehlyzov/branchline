package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformComputedKeysTest {
    @Test
    fun computed_string_key_in_object() {
        val program = """
            TRANSFORM T { OUTPUT { obj: { [input.key] : 1 } }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(mapOf("key" to "a")) as Map<String, Any?>
        assertEquals(mapOf("a" to 1), out["obj"])
    }
}
