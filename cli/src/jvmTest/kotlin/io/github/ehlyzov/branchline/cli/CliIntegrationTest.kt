package io.github.ehlyzov.branchline.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

public class CliIntegrationTest {
    @Test
    fun blRunsFixtureAndEmitsExpectedOutput() {
        val scriptPath = fixturePath("hello.bl")
        val inputPath = fixturePath("input.json")
        val expected = parseJsonInput(fixturePath("expected.json").readText())

        val result = runCli(
            args = listOf(
                scriptPath.toString(),
                "--input",
                inputPath.toString(),
                "--output-format",
                "json-compact",
            ),
        )

        assertEquals(ExitCode.SUCCESS.code, result.exitCode)
        val output = parseJsonInput(result.stdout)
        assertEquals(expected, output)
    }

    @Test
    fun blcCompilesArtifactFromFixture() {
        val scriptPath = fixturePath("hello.bl")
        val outputPath = Files.createTempFile("branchline-cli", ".blc")
        try {
            val result = runCli(
                args = listOf(
                    scriptPath.toString(),
                    "--output",
                    outputPath.toString(),
                    "--output-format",
                    "json-compact",
                ),
                defaultCommand = CliCommand.COMPILE,
            )

            assertEquals(ExitCode.SUCCESS.code, result.exitCode)
            val artifactRaw = Files.readString(outputPath, StandardCharsets.UTF_8)
            val artifact = ArtifactCodec.decode(artifactRaw)
            val expectedScript = scriptPath.readText().trim()
            assertEquals("Hello", artifact.transform)
            assertEquals(expectedScript, artifact.script.trim())
        } finally {
            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun blvmExecutesCompiledArtifact() {
        val scriptPath = fixturePath("hello.bl")
        val inputPath = fixturePath("input.json")
        val expected = parseJsonInput(fixturePath("expected.json").readText())
        val outputPath = Files.createTempFile("branchline-cli", ".blc")
        try {
            val compileResult = runCli(
                args = listOf(
                    scriptPath.toString(),
                    "--output",
                    outputPath.toString(),
                ),
                defaultCommand = CliCommand.COMPILE,
            )
            assertEquals(ExitCode.SUCCESS.code, compileResult.exitCode)

            val execResult = runCli(
                args = listOf(
                    outputPath.toString(),
                    "--input",
                    inputPath.toString(),
                    "--output-format",
                    "json-compact",
                ),
                defaultCommand = CliCommand.EXECUTE,
            )

            assertEquals(ExitCode.SUCCESS.code, execResult.exitCode)
            val output = parseJsonInput(execResult.stdout)
            assertEquals(expected, output)
        } finally {
            Files.deleteIfExists(outputPath)
        }
    }

    @Test
    fun errorFormatJsonIncludesExitCode() {
        val result = runCli(
            args = listOf(
                "--error-format",
                "json",
            ),
        )

        assertEquals(ExitCode.USAGE.code, result.exitCode)
        val errorJson = Json.parseToJsonElement(result.stderr).jsonObject
        val error = errorJson["error"]?.jsonObject
        requireNotNull(error)
        assertEquals("usage", error["kind"]?.jsonPrimitive?.content)
        assertEquals(
            ExitCode.USAGE.code.toString(),
            error["exitCode"]?.jsonPrimitive?.content,
        )
    }

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

    private fun fixturePath(name: String): Path {
        val url = requireNotNull(javaClass.getResource("/fixtures/cli/$name"))
        return Path.of(url.toURI())
    }

    private data class CliRunResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
