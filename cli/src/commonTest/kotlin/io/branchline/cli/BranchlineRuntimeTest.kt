package io.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.vm.BytecodeIO

class BranchlineRuntimeTest {
    private val script = """
        SOURCE row;
        TRANSFORM Hello { stream } {
            OUTPUT { greeting: "Hello, " + row.name };
        }
    """.trimIndent()

    @Test
    fun runProducesGreeting() {
        val runtime = BranchlineProgram(script)
        val transform = runtime.selectTransform(null)
        val result = runtime.execute(transform, mapOf("name" to "World")) as Map<*, *>
        assertEquals("Hello, World", result["greeting"])
    }

    @Test
    fun compileArtifactRoundTrip() {
        val runtime = BranchlineProgram(script)
        val transform = runtime.selectTransform(null)
        val bytecode = runtime.compileBytecode(transform)
        val artifact = CompiledArtifact(
            version = 1,
            transform = transform.name,
            script = script,
            bytecode = BytecodeIO.serializeBytecode(bytecode),
        )
        val encoded = ArtifactCodec.encode(artifact)
        val decoded = ArtifactCodec.decode(encoded)
        val runtime2 = BranchlineProgram(decoded.script)
        val transform2 = runtime2.selectTransform(decoded.transform)
        val vmExec = runtime2.prepareVmExec(transform2, BytecodeIO.deserializeBytecode(decoded.bytecode))
        val input = mapOf("name" to "Kotlin")
        val env = mutableMapOf<String, Any?>("row" to input).apply { putAll(input) }
        val result = vmExec.run(env, stringifyKeys = true) as Map<*, *>
        assertEquals("Hello, Kotlin", result["greeting"])
    }
}
