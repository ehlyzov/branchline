package io.github.ehlyzov.branchline.ir

import org.junit.jupiter.api.Assertions.assertEquals
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.buildRunner

private fun ExecutionEngine.exec(body: String) = buildRunner(body, engine = this)

class NestedForEachTest {

    @EngineTest
    fun `two-level for each flattens`(engine: ExecutionEngine) {
        val f = engine.exec(
            """
            FOR EACH outer IN row.orders {
                FOR EACH item IN outer.lines {
                    OUTPUT { id : outer.id , sku : item.sku }
                }
            }
            """.trimIndent(),
        )

        val input = mapOf(
            "orders" to listOf(
                mapOf(
                    "id" to 1,
                    "lines" to listOf(
                        mapOf("sku" to "A"), mapOf("sku" to "B"),
                    ),
                ),
                mapOf(
                    "id" to 2,
                    "lines" to listOf(
                        mapOf("sku" to "C"),
                    ),
                ),
            ),
        )

        val expect = listOf(
            mapOf("id" to 1, "sku" to "A"),
            mapOf("id" to 1, "sku" to "B"),
            mapOf("id" to 2, "sku" to "C"),
        )
        assertEquals(expect, f(input))
    }
}
