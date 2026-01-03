package io.github.ehlyzov.branchline.playground

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import playground.PlaygroundFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

public class PlaygroundFacadeTest {
    private val fullProgram = """
        TRANSFORM Playground { OUTPUT {
                greeting: "Hello, " + input.name
            }
        }
    """.trimIndent()

    private val bodyOnlyProgram = """
        LET greeting = "Hello, " + input.name;
        OUTPUT { greeting: greeting }
    """.trimIndent()

    @Test
    public fun runWithValidJsonProducesExpectedOutput() {
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
        val actual = Json.parseToJsonElement(result.outputJson)
        assertEquals(expected, actual)
    }

    @Test
    public fun runWithMalformedJsonReportsFailure() {
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
        assertTrue(result.errorMessage.isNotBlank())
    }

    @Test
    public fun runWithNonObjectJsonReportsHelpfulError() {
        val result = PlaygroundFacade.run(bodyOnlyProgram, "\"Ada\"")

        assertFalse(result.success)
        assertNull(result.outputJson)
        assertEquals("Input JSON must be an object at the top level.", result.errorMessage)
    }

    @Test
    public fun fullProgramStillSupported() {
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
        val json = Json.parseToJsonElement(result.outputJson)
        assertEquals(
            Json.parseToJsonElement("""{"greeting":"Hello, Grace"}"""),
            json
        )
    }

    @Test
    public fun tracingReportsCheckpoints() {
        val program = """
            LET status = input.status;
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
    public fun contractsIncludeInputAndOutputShapesWhenEnabled() {
        val result = PlaygroundFacade.run(
            bodyOnlyProgram,
            """
            {
              "name": "Ada"
            }
            """.trimIndent(),
            includeContracts = true
        )

        assertTrue(result.success)
        assertEquals("inferred", result.contractSource)
        assertNotNull(result.inputContractJson)
        assertNotNull(result.outputContractJson)

        val inputContract = Json.parseToJsonElement(result.inputContractJson).jsonObject
        val outputContract = Json.parseToJsonElement(result.outputContractJson).jsonObject
        val inputFields = inputContract["fields"]?.jsonObject
        val outputFields = outputContract["fields"]?.jsonObject
        assertTrue(inputFields?.containsKey("name") == true)
        assertTrue(outputFields?.containsKey("greeting") == true)
    }

    @Test
    public fun assertFailureReportsHelpfulError() {
        val program = """
            ASSERT(input.ready, "Not ready for deploy");
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
