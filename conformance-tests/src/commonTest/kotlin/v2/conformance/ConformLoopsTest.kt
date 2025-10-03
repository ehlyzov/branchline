package v2.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.ir.buildRunnerFromProgramMP

class ConformLoopsTest {

    @Test
    fun foreach_with_where_and_output() {
        val program = """
            SOURCE row;
            TRANSFORM T { stream } {
                FOR EACH it IN row.items WHERE it.qty > 2 {
                    OUTPUT { sku: it.sku }
                }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val inRow = mapOf(
            "items" to listOf(
                mapOf("sku" to "A", "qty" to 1),
                mapOf("sku" to "B", "qty" to 5)
            )
        )
        val out = runner(inRow)
        assertEquals(mapOf("sku" to "B"), out)
    }
}
