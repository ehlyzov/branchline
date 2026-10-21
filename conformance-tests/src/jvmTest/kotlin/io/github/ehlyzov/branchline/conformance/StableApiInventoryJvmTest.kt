package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.cli.BranchlineCli
import io.github.ehlyzov.branchline.cli.CliCommand
import io.github.ehlyzov.branchline.cli.PlatformKind
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StableApiInventoryJvmTest {
    private val root: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("docs/guides/api-support.md")) }

    @Test
    fun exactDocumentedSourceSignaturesAndEnumsDoNotDrift() {
        val catalog = Files.readString(root.resolve("docs/guides/api-support.md"))
        val declarations = Regex(
            "<!-- api-signature ([^|]+) \\| ([^|]+) \\| (\\w+) -->\\s*```kotlin\\n(.*?)\\n```",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(catalog).toList()
        val expectedNames = setOf(
            "BranchlineInspectRequest", "BranchlineFeatureUsageSummary", "BranchlineSubsetCompatibility",
            "BranchlineInspectTransform", "BranchlineInspectResult", "BranchlineFacade",
            "BranchlineDiagnosticSeverity", "BranchlineDiagnosticCategory", "BranchlineSourceSpan",
            "BranchlineDiagnosticPayload", "BranchlineDiagnostic", "PlaygroundFacade", "PlaygroundResult",
            "PlaygroundInspectResult", "PlatformKind", "CliCommand", "BranchlineCli", "ExitCode",
        )
        assertEquals(27, declarations.size, "An intentional allowlist change must update its evidence")
        assertEquals(expectedNames, declarations.filter { it.groupValues[2] != "fun" }
            .map { it.groupValues[3] }.toSet())
        assertEquals(
            listOf("inspect", "contractsJson", "inspectJson", "inspectJsonPayload", "run", "inspect",
                "runWithContracts", "isBodyOnlyProgram", "run"),
            declarations.filter { it.groupValues[2] == "fun" }.map { it.groupValues[3] },
        )
        declarations.forEach { match ->
            val (path, kind, name, expected) = match.destructured
            val source = Files.readString(root.resolve(path.trim()))
            assertEquals(normalize(expected), normalize(signature(source, kind, name)), "$path#$name")
        }
        assertTrue(catalog.contains("Default exclusion rule"))
        assertTrue(catalog.contains("BranchlineProgram"))
        assertTrue(catalog.contains("binary ABI"))
    }

    @Test
    fun allCliCommandsAndAliasesHaveExplicitClassification() {
        assertEquals(listOf("RUN", "COMPILE", "EXECUTE", "INSPECT", "SCHEMA", "CONTRACT_DIFF"),
            CliCommand.entries.map { it.name })
        val cliSource = Files.readString(root.resolve(
            "cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/BranchlineCli.kt",
        ))
        val dispatch = cliSource.substringAfter("private fun determineCommand(")
            .substringBefore("private fun parseRunArgs(")
        val expectedAliases = linkedMapOf(
            "run" to "RUN", "compile" to "COMPILE", "c" to "COMPILE",
            "exec" to "EXECUTE", "execute" to "EXECUTE", "inspect" to "INSPECT",
            "schema" to "SCHEMA", "contract-diff" to "CONTRACT_DIFF",
        )
        val actualAliases = linkedMapOf<String, String>()
        Regex("((?:\"[^\"]+\"(?:, )?)+) -> CliCommand\\.(\\w+)")
            .findAll(dispatch).forEach { branch ->
                Regex("\"([^\"]+)\"").findAll(branch.groupValues[1]).forEach {
                    actualAliases[it.groupValues[1]] = branch.groupValues[2]
                }
            }
        assertEquals(expectedAliases, actualAliases)
        val catalog = Files.readString(root.resolve("docs/guides/api-support.md"))
        val rows = catalog.lineSequence().filter { it.startsWith("| `") }.toList()
        assertEquals(6, rows.size)
        assertEquals(4, rows.count { it.contains("| Stable candidate |") })
        assertEquals(2, rows.count { it.contains("| Experimental, explicitly excluded |") })
        expectedAliases.keys.forEach { assertTrue(catalog.contains("`$it`"), it) }
    }

    @Test
    fun cliConsumerCompilesSavesLoadsAndExecutesWithIndependentExpectedResult() {
        val directory = Files.createTempDirectory("branchline-api-consumer-")
        try {
            val source = directory.resolve("hello.bl")
            val input = directory.resolve("input.json")
            val artifact = directory.resolve("hello.blc")
            Files.writeString(source, """
                TRANSFORM Main {
                    OUTPUT { greeting: "Hello, " + input.name, total: input.count + 1 }
                }
            """.trimIndent())
            Files.writeString(input, """{"name":"Ada","count":2}""")
            val expected = Json.parseToJsonElement("""{"greeting":"Hello, Ada","total":3}""")
            val runArgs = listOf(source.toString(), "--input", input.toString(), "--output-format", "json-compact")
            for (prefix in listOf(emptyList(), listOf("run"))) {
                val result = cli(prefix + runArgs)
                assertEquals(0, result.code, result.stderr)
                assertEquals(expected, Json.parseToJsonElement(result.stdout))
            }
            val compiled = cli(listOf("compile", source.toString(), "--output", artifact.toString()))
            assertEquals(0, compiled.code, compiled.stderr)
            assertTrue(Files.isRegularFile(artifact))
            for (alias in listOf("exec", "execute")) {
                val executed = cli(listOf(alias, artifact.toString(), "--input", input.toString(),
                    "--output-format", "json-compact", "--contracts", "strict"))
                assertEquals(0, executed.code, executed.stderr)
                assertEquals(expected, Json.parseToJsonElement(executed.stdout))
            }
            val compileAlias = cli(listOf("c", source.toString(), "--output-format", "json-compact"))
            assertEquals(0, compileAlias.code, compileAlias.stderr)
            assertNotNull(Json.parseToJsonElement(compileAlias.stdout).jsonObject["bytecode"])
            val inspected = cli(listOf("inspect", source.toString(), "--contracts", "--contracts-json", "--normalized"))
            assertEquals(0, inspected.code, inspected.stderr)
            val payload = Json.parseToJsonElement(inspected.stdout).jsonObject
            assertEquals("true", payload["success"].toString())
            assertEquals(1, payload.getValue("transforms").jsonArray.size)
            assertNotNull(payload["normalizedSource"])
            assertEquals(64, cli(listOf("run", source.toString(), "--contracts", "invalid-mode")).code)
            assertEquals(64, cli(listOf("run", source.toString(), "--output-format", "invalid-format")).code)
            Files.writeString(artifact, """{"version":999}""")
            val rejected = cli(listOf("exec", artifact.toString()))
            assertEquals(65, rejected.code)
            assertTrue(rejected.stdout.isBlank(), "An incompatible artifact must not produce execution output")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun cliErrorCategoriesProduceDocumentedStatuses() {
        val directory = Files.createTempDirectory("branchline-api-errors-")
        try {
            assertEquals(74, cli(listOf("run", directory.resolve("absent.bl").toString())).code)
            val source = directory.resolve("bad.bl")
            Files.writeString(source, "TRANSFORM Main { OUTPUT { value: } }")
            assertEquals(70, cli(listOf("inspect", source.toString(), "--contracts", "--contracts-json")).code)
            Files.writeString(source, "TRANSFORM Main { OUTPUT { value: ASSERT(false) } }")
            val runtimeFailure = cli(listOf("run", source.toString()))
            assertEquals(70, runtimeFailure.code, runtimeFailure.stderr)
            assertTrue(runtimeFailure.stdout.isBlank())
            // Excluded commands retain documented current special statuses.
            assertEquals(1, cli(listOf("contract-diff", directory.resolve("old.bl").toString(),
                directory.resolve("new.bl").toString())).code)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    /** Deliberately bounded to this allowlist's simple constructor/function headers. */
    private fun signature(source: String, kind: String, name: String): String {
        val declaration = Regex("(?m)^[ \\t]*(?:public\\s+)?${Regex.escape(kind)}\\s+${Regex.escape(name)}\\b")
            .find(source) ?: error("Missing $kind $name")
        val tail = source.substring(declaration.range.first)
        if (kind == "enum class") return tail.substringBefore('}') + "}"
        val opening = if (kind == "data class") tail.indexOf('(') else -1
        var depth = 0
        for (index in (if (opening >= 0) opening else 0) until tail.length) {
            when (tail[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (kind == "data class" && depth == 0) return tail.substring(0, index + 1)
                }
                '{', '=' -> if (depth == 0) return tail.substring(0, index)
            }
        }
        error("Unterminated $kind $name")
    }

    private data class CliResult(val code: Int, val stdout: String, val stderr: String)

    private fun cli(args: List<String>): CliResult {
        val priorOut = System.out
        val priorErr = System.err
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        return try {
            PrintStream(stdout, true, StandardCharsets.UTF_8).use { out ->
                PrintStream(stderr, true, StandardCharsets.UTF_8).use { err ->
                    System.setOut(out)
                    System.setErr(err)
                    val code = BranchlineCli.run(args, PlatformKind.JVM)
                    CliResult(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8))
                }
            }
        } finally {
            System.setOut(priorOut)
            System.setErr(priorErr)
        }
    }
}
