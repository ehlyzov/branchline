package io.github.ehlyzov.branchline

import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileProgramAndRun

class HostFunctionsTest {

    @OptIn(ExperimentalTime::class)
    private fun hostFns(): Map<String, (List<Any?>) -> Any?> = mapOf(
        "uuid" to { args ->
            require(args.isEmpty())
            UUID.randomUUID().toString()
        },
        "now" to { args ->
            require(args.isEmpty())
            Clock.System.now().toString()
        },
        "md5" to { args ->
            val bytes = args.single().toString().toByteArray()
            MessageDigest.getInstance("MD5")
                .digest(bytes)
                .joinToString(separator = "") { "%02x".format(it) }
        },
        "concat" to { args -> args.joinToString(separator = "") { it.toString() } },
    )

    private fun exec(code: String, row: Map<String, Any?> = emptyMap(), engine: ExecutionEngine): Any? =
        compileProgramAndRun(code.trimIndent(), row, extraFns = hostFns(), engine = engine)

    @EngineTest
    fun uuid_is_unique_per_call(engine: ExecutionEngine) {
        val src = """
            TRANSFORM {
                LET id = uuid();
                OUTPUT { id:id };
            }
        """.trimIndent()

        val out1 = exec(src, engine = engine) as Map<*, *>
        val out2 = exec(src, engine = engine) as Map<*, *>
        assertNotEquals(out1["id"], out2["id"], "uuid() should yield different strings")
    }

    @EngineTest
    fun concat_joins_all_arguments(engine: ExecutionEngine) {
        val src = """
            TRANSFORM {
                OUTPUT { v: concat("a", "b", "c") };
            }
        """.trimIndent()

        val out = exec(src, engine = engine) as Map<*, *>
        assertEquals("abc", out["v"])
    }

    @EngineTest
    fun md5_returns_expected_hash(engine: ExecutionEngine) {
        val src = """
            TRANSFORM {
                OUTPUT { h: md5("hello") };
            }
        """.trimIndent()

        val out = exec(src, engine = engine) as Map<*, *>
        assertEquals("5d41402abc4b2a76b9719d911017c592", out["h"])
    }

    @EngineTest
    fun host_func_can_use_input_row_values(engine: ExecutionEngine) {
        val src = """
            TRANSFORM {
                OUTPUT { v: concat(input.a, "-", input.b) };
            }
        """.trimIndent()

        val out = exec(src, mapOf("a" to "X", "b" to "Y"), engine) as Map<*, *>
        assertEquals("X-Y", out["v"])
    }
}
