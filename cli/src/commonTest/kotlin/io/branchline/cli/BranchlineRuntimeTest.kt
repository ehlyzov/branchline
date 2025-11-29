package io.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import v2.runtime.bignum.BLBigDec
import v2.runtime.bignum.toPlainString
import v2.vm.BytecodeIO

class BranchlineRuntimeTest {
    private val script = """
        TRANSFORM Hello { OUTPUT { greeting: "Hello, " + input.name };
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
        val env = mutableMapOf<String, Any?>().apply {
            this[v2.DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in v2.COMPAT_INPUT_ALIASES) this[alias] = input
        }
        val result = vmExec.run(env, stringifyKeys = true) as Map<*, *>
        assertEquals("Hello, Kotlin", result["greeting"])
    }

    @Test
    fun addsStringNumbersWithoutTypeErrors() {
        val scriptWithNumberMath = """
            TRANSFORM Totals { LET totals = { tests: 0 };
                SET totals.tests = totals.tests + NUMBER(input.count ?? 0);
                OUTPUT totals;
            }
        """.trimIndent()

        val runtime = BranchlineProgram(scriptWithNumberMath)
        val transform = runtime.selectTransform(null)
        val result = runtime.execute(transform, mapOf("count" to "2")) as Map<*, *>

        val testsValue = result["tests"]
        val numericString = when (testsValue) {
            is Number -> testsValue.toString()
            is BLBigDec -> testsValue.toPlainString()
            else -> testsValue?.toString()
        }

        assertEquals("2", numericString)
    }
}
