package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.vm.BytecodeIO
import io.github.ehlyzov.branchline.vm.Instruction
import io.github.ehlyzov.branchline.vm.Bytecode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompiledArtifactCompatibilityTest {
    @Test
    fun previousWriterFixtureExecutesWithExactExpectedResult() {
        val artifact = ArtifactCodec.decode(LegacyArtifactFixture.JSON)
        assertEquals(1, artifact.version)
        assertTrue(!Json.parseToJsonElement(LegacyArtifactFixture.JSON).jsonObject.containsKey("version"))
        val runtime = BranchlineProgram(artifact.script)
        val transform = runtime.selectTransform(artifact.transform)
        val exec = runtime.prepareVmExec(transform, BytecodeIO.deserializeBytecode(artifact.bytecode))
        assertEquals(
            mapOf("greeting" to "Hello, Kotlin"),
            runtime.executeVmWithContracts(transform, mapOf("name" to "Kotlin"), exec, ContractValidationMode.OFF).output,
        )
    }

    @Test
    fun currentWriterAlwaysEmitsVersionAndOldReaderRejectionMarker() {
        val artifact = currentArtifact()
        for (format in listOf(OutputFormat.JSON, OutputFormat.JSON_COMPACT, OutputFormat.JSON_CANONICAL)) {
            val raw = ArtifactCodec.encode(artifact, format)
            val payload = Json.parseToJsonElement(raw).jsonObject
            assertEquals("2", payload.getValue("version").jsonPrimitive.content)
            assertEquals(ArtifactCodec.FORMAT, payload.getValue("format").jsonPrimitive.content)
            assertEquals(artifact, ArtifactCodec.decode(raw))
            val failure = assertFailsWith<SerializationException> {
                Json.decodeFromString(LegacyCompiledArtifact.serializer(), raw)
            }
            assertTrue(failure.message.orEmpty().contains("format"))
        }
    }

    @Test
    fun oldReaderDidNotEnforceVersionSoVersionAloneIsNotACompatibilityGate() {
        val raw = LegacyArtifactFixture.JSON.trim().replaceFirst("{", "{\"version\":2,")
        assertEquals(2, Json.decodeFromString(LegacyCompiledArtifact.serializer(), raw).version)
    }

    @Test
    fun privateInstructionsRequireCurrentFormat() {
        val artifact = currentArtifact().copy(bytecode = BytecodeIO.serializeBytecode(
            Bytecode.fromInstructions(listOf(Instruction.PRIVATE_SET_STATIC(io.github.ehlyzov.branchline.ObjKey.Name("x")), Instruction.PRIVATE_SET_DYNAMIC, Instruction.PRIVATE_ARRAY_APPEND)),
        ))
        assertEquals(artifact, ArtifactCodec.decode(ArtifactCodec.encode(artifact)))
        val legacy = ArtifactCodec.encode(artifact).replace("\"version\": 2", "\"version\": 1")
        val failure = assertFailsWith<CliException> { ArtifactCodec.decode(legacy) }
        assertTrue(failure.message.orEmpty().contains("PRIVATE_*"))
        assertTrue(failure.message.orEmpty().contains("blc"))
        val oldReaderFailure = assertFailsWith<SerializationException> {
            Json.decodeFromString(LegacyCompiledArtifact.serializer(), ArtifactCodec.encode(artifact))
        }
        assertTrue(oldReaderFailure.message.orEmpty().contains("format"))
    }

    @Test
    fun unsupportedVersionsAndFormatsFailBeforeEmbeddedScriptParsing() {
        for (raw in listOf(
            """{"version":0,"script":"not Branchline"}""",
            """{"version":3,"script":"not Branchline"}""",
            """{"version":-1,"script":"not Branchline"}""",
            """{"version":"2","script":"not Branchline"}""",
            """{"version":2,"script":"not Branchline"}""",
            """{"version":2,"format":"other-vm","script":"not Branchline"}""",
            """{"version":1,"format":"other-vm","script":"not Branchline"}""",
        )) {
            val failure = assertFailsWith<CliException>(raw) { ArtifactCodec.decode(raw) }
            assertEquals(CliErrorKind.INPUT, failure.kind)
            assertTrue(failure.message.orEmpty().contains("blc"), failure.message)
        }
    }

    @Test
    fun writerDoesNotOfferAnUnsafeLegacyDowngrade() {
        assertFailsWith<CliException> { ArtifactCodec.encode(currentArtifact().copy(version = 1)) }
    }

    @Test
    fun unknownInstructionsAndValueTypesGiveMigrationGuidance() {
        val instructionFailure = assertFailsWith<IllegalArgumentException> {
            BytecodeIO.deserializeBytecode(BytecodeIO.SerializedBytecode(listOf(BytecodeIO.SerializedInstruction("FUTURE_OP"))))
        }
        assertTrue(instructionFailure.message.orEmpty().contains("blc"))
        val valueFailure = assertFailsWith<IllegalArgumentException> {
            BytecodeIO.deserializeValue(BytecodeIO.SerializedValue("future-number", s = "42"))
        }
        assertTrue(valueFailure.message.orEmpty().contains("blc"))
    }

    private fun currentArtifact(): CompiledArtifact {
        val runtime = BranchlineProgram("TRANSFORM Current { OUTPUT { ok: true } }")
        val transform = runtime.selectTransform(null)
        return CompiledArtifact(
            transform = transform.name,
            script = "TRANSFORM Current { OUTPUT { ok: true } }",
            bytecode = BytecodeIO.serializeBytecode(runtime.compileBytecode(transform)),
        )
    }
}

// Frozen envelope from ce58599, including its strict Json decoder and version default.
// It intentionally has no format property: older readers reject the v2 marker.
@Serializable
private data class LegacyCompiledArtifact(
    val version: Int = 1,
    val transform: String?,
    val script: String,
    val bytecode: BytecodeIO.SerializedBytecode,
    val contract: TransformContract? = null,
)
