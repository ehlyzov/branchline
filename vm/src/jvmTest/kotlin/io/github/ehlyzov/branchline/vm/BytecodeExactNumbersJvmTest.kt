package io.github.ehlyzov.branchline.vm

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BytecodeExactNumbersJvmTest {
    @Test
    fun integerKindAndArbitraryPrecisionArePreserved() {
        for (text in listOf("0", "1", "-1", "9223372036854775808", "-1234567890123456789012345678901234567890")) {
            val expected = BigInteger(text)
            val encoded = BytecodeIO.serializeValue(expected)
            assertEquals("bi", encoded.t)
            assertEquals(expected, assertIs<BigInteger>(BytecodeIO.deserializeValue(encoded)))
        }
    }

    @Test
    fun decimalValueAndScaleArePreservedIncludingExponentAndTrailingZeros() {
        for (text in listOf("1.2300", "0.0000", "0E+12", "1E+12", "-1.2345678901234567890123456789012345678900E-90")) {
            val expected = BigDecimal(text)
            val encoded = BytecodeIO.serializeValue(expected)
            assertEquals("bd", encoded.t)
            val actual = assertIs<BigDecimal>(BytecodeIO.deserializeValue(encoded))
            assertEquals(expected.unscaledValue(), actual.unscaledValue(), text)
            assertEquals(expected.scale(), actual.scale(), text)
            assertEquals(expected, actual, text)
        }
    }

    @Test
    fun nestedExactNumbersSurviveBytecodeAndSnapshotState() {
        val integer = BigInteger("12345678901234567890123456789012345678901234567890")
        val decimal = BigDecimal("1.23000000000000000000000000000000000000100E+70")
        val nested = mapOf("values" to listOf(integer, mapOf("decimal" to decimal)))
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(integer), Instruction.STORE_LOCAL(0),
            Instruction.PUSH(decimal), Instruction.SUSPEND, Instruction.POP,
            Instruction.LOAD_VAR("numbers"), Instruction.RETURN_VALUE,
        ))
        val reloaded = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(bytecode))
        val suspended = assertIs<Map<*, *>>(VM().execute(reloaded, mutableMapOf("numbers" to nested)))
        val snapshot = assertIs<String>(suspended["snapshot"])
        assertTrue(snapshot.contains("\"bi\""))
        assertTrue(snapshot.contains("\"bd\""))
        val restored = VM.restoreFromSnapshot(snapshot)
        // Taking another snapshot exercises restored stack/local/environment values
        // before resume, in addition to the final nested result.
        val state = kotlinx.serialization.json.Json.decodeFromString(VM.Snapshot.serializer(), restored.snapshotJson())
        assertEquals(integer, BytecodeIO.deserializeValue(state.locals[0]))
        assertEquals(decimal, BytecodeIO.deserializeValue(state.stack[0]))
        assertTrue(restored.resume())
        assertEquals(nested, restored.resultOrNull())
    }
}
