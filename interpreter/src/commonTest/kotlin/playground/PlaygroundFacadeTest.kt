package playground

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaygroundFacadeTest {
    private val fullProgram = """
        SOURCE msg;

        TRANSFORM Playground { stream } {
            OUTPUT {
                greeting: "Hello, " + msg.name
            }
        }
    """.trimIndent()

    private val bodyOnlyProgram = """
        LET greeting = "Hello, " + msg.name;
        OUTPUT { greeting: greeting }
    """.trimIndent()

    @Test
    fun runWithValidJsonProducesExpectedOutput() {
        val result = PlaygroundFacade.run(
            bodyOnlyProgram,
            """
            {
              "name": "Ada"
            }
            """.trimIndent()
        )

        assertTrue(result.success)
        assertNull(result.errorMessage)
        assertNull(result.line)
        assertNull(result.column)
        assertNotNull(result.outputJson)

        val expected = Json.parseToJsonElement(
            """
            {
              "greeting": "Hello, Ada"
            }
            """.trimIndent()
        )
        val actual = Json.parseToJsonElement(result.outputJson!!)
        assertEquals(expected, actual)
    }

    @Test
    fun runWithMalformedJsonReportsFailure() {
        val result = PlaygroundFacade.run(
            bodyOnlyProgram,
            """
            {
              "name": "Ada",
            }
            """.trimIndent()
        )

        assertFalse(result.success)
        assertNull(result.outputJson)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.isNotBlank())
    }

    @Test
    fun runWithNonObjectJsonReportsHelpfulError() {
        val result = PlaygroundFacade.run(bodyOnlyProgram, "\"Ada\"")

        assertFalse(result.success)
        assertNull(result.outputJson)
        assertEquals("Input JSON must be an object at the top level.", result.errorMessage)
    }

    @Test
    fun fullProgramStillSupported() {
        val result = PlaygroundFacade.run(
            fullProgram,
            """
            {
              "name": "Grace"
            }
            """.trimIndent()
        )

        assertTrue(result.success)
        assertNotNull(result.outputJson)
        val json = Json.parseToJsonElement(result.outputJson!!)
        assertEquals(
            Json.parseToJsonElement("""{"greeting":"Hello, Grace"}"""),
            json
        )
    }

    @Test
    fun tracingReportsCheckpoints() {
        val program = """
            LET status = msg.status;
            CHECKPOINT("after status");
            OUTPUT { status: status }
        """.trimIndent()

        val result = PlaygroundFacade.run(
            program,
            """
            {
              "status": "ok"
            }
            """.trimIndent(),
            enableTracing = true
        )

        assertTrue(result.success)
        val explainHuman = result.explainHuman
        assertNotNull(explainHuman)
        assertTrue(explainHuman.contains("Checkpoints:"))
        assertTrue(explainHuman.contains("after status"))
        val checkpointLines = explainHuman.lineSequence().map { it.trim() }
        assertTrue(checkpointLines.any { it.startsWith("- @") })
    }

    @Test
    fun assertFailureReportsHelpfulError() {
        val program = """
            ASSERT(msg.ready, "Not ready for deploy");
            OUTPUT { status: "ok" }
        """.trimIndent()

        val result = PlaygroundFacade.run(
            program,
            """
            {
              "ready": false
            }
            """.trimIndent()
        )

        assertFalse(result.success)
        assertNull(result.outputJson)
        assertEquals("Not ready for deploy", result.errorMessage)
    }
}
