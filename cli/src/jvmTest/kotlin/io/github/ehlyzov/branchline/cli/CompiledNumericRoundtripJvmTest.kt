package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.json.formatJsonValue
import io.github.ehlyzov.branchline.vm.BytecodeIO
import io.github.ehlyzov.branchline.vm.VMExecutionPath
import io.github.ehlyzov.branchline.vm.VMExecutionReport
import io.github.ehlyzov.branchline.vm.VMFallbackPolicy
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CompiledNumericRoundtripJvmTest {
    @Test
    fun embeddingCompilerDecimalConstantSurvivesSavedArtifactWithExactScale() {
        // Source numeric literals are F64; an embedder can also compile a typed
        // decimal AST. This exercises the bd constant itself, not a DEC call that
        // would reconstruct the value after loading the artifact.
        val expected = java.math.BigDecimal("123456789012345678901234567890.123400")
        val source = "TRANSFORM DecimalConstant { OUTPUT { value: DEC(\"$expected\") } }"
        val token = io.github.ehlyzov.branchline.Lexer("0").lex().first()
        val ir = listOf(io.github.ehlyzov.branchline.ir.IROutput(listOf(
            io.github.ehlyzov.branchline.LiteralProperty(
                io.github.ehlyzov.branchline.ObjKey.Name("value"),
                io.github.ehlyzov.branchline.NumberLiteral(io.github.ehlyzov.branchline.Dec(expected), token),
            ),
        )))
        val bytecode = io.github.ehlyzov.branchline.vm.Compiler().compile(ir)
        val artifact = CompiledArtifact(transform = "DecimalConstant", script = source,
            bytecode = BytecodeIO.serializeBytecode(bytecode))
        assertEquals(expected.toString(), artifact.bytecode.instructions.mapNotNull { it.value }.single { it.t == "bd" }.s)
        val directory = Files.createTempDirectory("branchline-decimal-artifact")
        val artifactFile = directory.resolve("decimal.blc")
        val input = directory.resolve("input.json")
        try {
            Files.writeString(artifactFile, ArtifactCodec.encode(artifact), StandardCharsets.UTF_8)
            Files.writeString(input, "{}", StandardCharsets.UTF_8)
            val loaded = ArtifactCodec.decode(Files.readString(artifactFile))
            val runtime = BranchlineProgram(loaded.script)
            val transform = runtime.selectTransform(loaded.transform)
            val executor = runtime.prepareVmExec(transform, BytecodeIO.deserializeBytecode(loaded.bytecode), VMFallbackPolicy.NONE)
            val output = kotlin.test.assertIs<Map<*, *>>(executor.run(runtime.buildEnv(transform, emptyMap()), stringifyKeys = true))
            val actual = kotlin.test.assertIs<java.math.BigDecimal>(output["value"])
            assertEquals(expected.unscaledValue(), actual.unscaledValue())
            assertEquals(expected.scale(), actual.scale())
            assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), executor.lastExecution)
            val executed = runCli(listOf(artifactFile.toString(), "--input", input.toString(),
                "--output-format", "json-compact"), CliCommand.EXECUTE)
            assertEquals(ExitCode.SUCCESS.code, executed.exitCode, executed.stderr)
            assertEquals(Json.parseToJsonElement("""{"value":"123456789012345678901234567890.123400"}"""),
                Json.parseToJsonElement(executed.stdout))
        } finally {
            Files.deleteIfExists(artifactFile)
            Files.deleteIfExists(input)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun arbitraryPrecisionIntegerLiteralSurvivesSavedCompiledArtifact() {
        assertArtifactRoundtrip(
            "12345678901234567890123456789012345678901234567890",
            "12345678901234567890123456789012345678901234567890",
        )
    }

    @Test
    fun longLiteralAdditionRetainsPromotedValueAfterArtifactReload() {
        assertArtifactRoundtrip(
            "9223372036854775807 + 2147483648",
            "9223372039002259455",
        )
    }

    @Test
    fun longLiteralMultiplicationRetainsPromotedValueAfterArtifactReload() {
        assertArtifactRoundtrip(
            "9223372036854775807 * 2147483648",
            "19807040628566084396238503936",
        )
    }

    private fun assertArtifactRoundtrip(expression: String, expectedInteger: String) {
        val directory = Files.createTempDirectory("branchline-numeric-artifact")
        val script = directory.resolve("numeric.bl")
        val input = directory.resolve("input.json")
        val artifactFile = directory.resolve("numeric.blc")
        // The oracle is independently specified integer text. Safe JSON must emit
        // it as a string; parsing through Double would hide a precision regression.
        val expectedJson = Json.parseToJsonElement("""{"value":"$expectedInteger"}""")
        try {
            val source = "TRANSFORM NumericRoundtrip { OUTPUT { value: $expression } }"
            Files.writeString(script, source, StandardCharsets.UTF_8)
            Files.writeString(input, "{}", StandardCharsets.UTF_8)
            val compiled = runCli(
                listOf(script.toString(), "--output", artifactFile.toString()),
                CliCommand.COMPILE,
            )
            assertEquals(ExitCode.SUCCESS.code, compiled.exitCode, "Compile: ${compiled.stderr}")

            // Reload bytes produced by the real compiler, then prove that the
            // saved bytecode succeeds without recompilation/interpreter fallback.
            val artifact = ArtifactCodec.decode(Files.readString(artifactFile, StandardCharsets.UTF_8))
            assertEquals(source, artifact.script)
            assertEquals(3, artifact.version)
            if (expression == expectedInteger) {
                assertEquals("bi", artifact.bytecode.instructions.mapNotNull { it.value }.single { it.t == "bi" }.t)
            }
            val bytecode = BytecodeIO.deserializeBytecode(artifact.bytecode)
            val runtime = BranchlineProgram(artifact.script)
            val transform = runtime.selectTransform(artifact.transform)
            val executor = runtime.prepareVmExec(transform, bytecode, VMFallbackPolicy.NONE)
            val output = executor.run(runtime.buildEnv(transform, emptyMap()), stringifyKeys = true)
            val actualInteger = kotlin.test.assertIs<java.math.BigInteger>(kotlin.test.assertIs<Map<*, *>>(output)["value"])
            assertEquals(java.math.BigInteger(expectedInteger), actualInteger)
            assertEquals(expectedJson, Json.parseToJsonElement(formatJsonValue(output, pretty = false)))
            assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), executor.lastExecution)

            val executed = runCli(
                listOf(artifactFile.toString(), "--input", input.toString(), "--output-format", "json-compact"),
                CliCommand.EXECUTE,
            )
            assertEquals(ExitCode.SUCCESS.code, executed.exitCode, "Execute: ${executed.stderr}")
            assertEquals(expectedJson, Json.parseToJsonElement(executed.stdout))
        } finally {
            Files.deleteIfExists(artifactFile)
            Files.deleteIfExists(input)
            Files.deleteIfExists(script)
            Files.deleteIfExists(directory)
        }
    }

    private data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

    // Match the existing CLI integration harness: call the public CLI entrypoint
    // while capturing process output, and always restore the original streams.
    private fun runCli(args: List<String>, command: CliCommand): CliResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        try {
            System.setOut(PrintStream(stdout, true, StandardCharsets.UTF_8))
            System.setErr(PrintStream(stderr, true, StandardCharsets.UTF_8))
            val code = BranchlineCli.run(args, PlatformKind.JVM, command)
            return CliResult(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }
}
