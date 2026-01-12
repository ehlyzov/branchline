package io.github.ehlyzov.branchline.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public class CliAdvancedFlagsTest {
    @Test
    public fun outputPathSupportsRawSelection() {
        val script = """
            TRANSFORM Main {
                OUTPUT { answer: { value: 42 }, note: "ok" };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--output-path",
                "answer.value",
                "--output-raw",
            ),
        )

        assertSuccess(result)
        assertEquals("42", result.stdout.trim())
    }

    @Test
    public fun writeOutputWritesFiles() {
        val script = """
            TRANSFORM Main {
                OUTPUT {
                    files: {
                        "note.txt": "hello",
                        "more.txt": "world"
                    }
                };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val outputDir = Files.createTempDirectory("branchline-output")

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--write-output",
                "--write-output-dir",
                outputDir.toString(),
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val firstPath = outputDir.resolve("note.txt")
        val secondPath = outputDir.resolve("more.txt")
        assertTrue(Files.exists(firstPath))
        assertTrue(Files.exists(secondPath))
        assertEquals("hello", firstPath.readText().trim())
        assertEquals("world", secondPath.readText().trim())
    }

    @Test
    public fun writeOutputFileMappingWritesToPath() {
        val script = """
            TRANSFORM Main {
                OUTPUT {
                    files: [
                        { name: "report.txt", contents: "ok" }
                    ]
                };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val outputPath = Files.createTempFile("branchline", ".txt")
        Files.deleteIfExists(outputPath)

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--write-output",
                "--write-output-file",
                "report.txt=${outputPath}",
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        assertTrue(Files.exists(outputPath))
        assertEquals("ok", outputPath.readText().trim())
    }

    @Test
    public fun outputFileWritesToDirectory() {
        val script = """
            TRANSFORM Main {
                OUTPUT { answer: 42 };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val outputDir = Files.createTempDirectory("branchline-output")

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--output-file",
                outputDir.toString(),
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val outputPath = outputDir.resolve("output.json")
        assertTrue(Files.exists(outputPath))
        assertEquals("{\"answer\":42}", Files.readString(outputPath, StandardCharsets.UTF_8).trim())
    }

    @Test
    public fun outputLinesWritesJsonl() {
        val script = """
            TRANSFORM Main {
                OUTPUT [{ value: 1 }, { value: 2 }, { value: 3 }];
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val outputPath = Files.createTempFile("branchline-output", ".jsonl")

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--output-lines",
                outputPath.toString(),
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val lines = Files.readAllLines(outputPath, StandardCharsets.UTF_8)
        assertEquals(listOf("{\"value\":1}", "{\"value\":2}", "{\"value\":3}"), lines)
    }

    @Test
    public fun sharedFileMappingSeedsStore() {
        val script = """
            SHARED payloads MANY;
            TRANSFORM Main {
                LET keys = SORT(KEYS(payloads));
                LET first = AWAIT_SHARED("payloads", keys[0]);
                OUTPUT { keys: keys, first: first };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val payloadPath = writeTempFile("branchline", ".json", "{ \"value\": 7 }")

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--shared-file",
                "payloads=alpha=${payloadPath}",
                "--shared-format",
                "json",
                "--shared-key",
                "custom",
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val output = parseJsonInput(result.stdout)
        @Suppress("UNCHECKED_CAST")
        val keys = output["keys"] as List<Any?>
        assertEquals(listOf("alpha"), keys)
        val first = output["first"] as Map<*, *>
        assertEquals(7L, first["value"])
    }

    @Test
    public fun fanoutRunsSummaryTransform() {
        val script = """
            SHARED items MANY;

            TRANSFORM Item {
                LET value = AWAIT_SHARED("items", input.key);
                OUTPUT { value: value };
            }

            TRANSFORM Summary {
                LET total = 0;
                LET count = 0;
                FOR report IN input.reports {
                    SET total = total + (report.value ?? 0);
                    SET count = count + 1;
                }
                OUTPUT { total: total, count: count };
            }
        """.trimIndent()
        val scriptPath = writeTempFile("branchline", ".bl", script)
        val dir = Files.createTempDirectory("branchline-shared")
        writeTempFile(dir, "one.json", "2")
        writeTempFile(dir, "two.json", "3")

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--transform",
                "Item",
                "--summary-transform",
                "Summary",
                "--shared-dir",
                "items=${dir}",
                "--shared-format",
                "json",
                "--shared-key",
                "basename",
                "--jobs",
                "2",
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val output = parseJsonInput(result.stdout)
        assertNumberEquals(5.0, output["total"])
        assertNumberEquals(2.0, output["count"])
    }

    @Test
    public fun junitSummaryHonorsReportStatus() {
        val scriptPath = resolveJunitSummaryScript()
        val baseDir = Files.createDirectories(Path.of("build", "tmp", "cli-junit"))
        val reportsDir = Files.createTempDirectory(baseDir, "branchline-junit-")
        val emptyReport = reportsDir.resolve("empty.xml")
        val okReport = reportsDir.resolve("ok.xml")

        Files.writeString(
            emptyReport,
            """<?xml version="1.0" encoding="UTF-8"?><testsuite name="empty" tests="0" failures="0" errors="0" skipped="0"></testsuite>""",
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            okReport,
            """<?xml version="1.0" encoding="UTF-8"?><testsuite name="ok" tests="1" failures="0" errors="0" skipped="0"></testsuite>""",
            StandardCharsets.UTF_8,
        )

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--transform",
                "FileSummary",
                "--summary-transform",
                "Summary",
                "--shared-glob",
                "reports=${reportsDir}/*.xml",
                "--shared-format",
                "xml",
                "--shared-key",
                "relative",
                "--jobs",
                "2",
                "--output-format",
                "json-compact",
            ),
        )

        assertSuccess(result)
        val output = parseJsonInput(result.stdout)
        assertEquals("error", output["status"])
    }
}

private data class CliRunResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private fun runCli(args: List<String>, defaultCommand: CliCommand? = null): CliRunResult {
    val stdoutStream = ByteArrayOutputStream()
    val stderrStream = ByteArrayOutputStream()
    val originalOut = System.out
    val originalErr = System.err
    try {
        System.setOut(PrintStream(stdoutStream, true, StandardCharsets.UTF_8))
        System.setErr(PrintStream(stderrStream, true, StandardCharsets.UTF_8))
        val exitCode = BranchlineCli.run(args, PlatformKind.JVM, defaultCommand)
        return CliRunResult(
            exitCode = exitCode,
            stdout = stdoutStream.toString(StandardCharsets.UTF_8),
            stderr = stderrStream.toString(StandardCharsets.UTF_8),
        )
    } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
}

private fun writeTempFile(prefix: String, suffix: String, contents: String): Path {
    val path = Files.createTempFile(prefix, suffix)
    Files.writeString(path, contents, StandardCharsets.UTF_8)
    return path
}

private fun writeTempFile(dir: Path, name: String, contents: String): Path {
    val path = dir.resolve(name)
    Files.writeString(path, contents, StandardCharsets.UTF_8)
    return path
}

private fun resolveJunitSummaryScript(): Path {
    var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    var attempts = 0
    while (attempts < 8) {
        val candidate = current.resolve(".github").resolve("scripts").resolve("junit-summary.bl")
        if (Files.exists(candidate)) return candidate
        val parent = current.parent ?: break
        current = parent
        attempts += 1
    }
    error("Unable to locate .github/scripts/junit-summary.bl from ${System.getProperty("user.dir")}")
}

private fun assertNumberEquals(expected: Double, actual: Any?) {
    val value = actual as? Number
    requireNotNull(value) { "Expected numeric value" }
    assertEquals(expected, value.toDouble())
}

private fun assertSuccess(result: CliRunResult) {
    if (result.exitCode == ExitCode.SUCCESS.code) return
    val message = buildString {
        append("CLI failed with exit code ")
        append(result.exitCode)
        append("\nstdout:\n")
        append(result.stdout)
        append("\nstderr:\n")
        append(result.stderr)
    }
    throw AssertionError(message)
}
