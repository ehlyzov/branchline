package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.std.StdLib
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BytecodeNumericRoundtripTest {
    @Test
    fun explicitBigNumberKindsSurviveNestedValueAndBytecodeRoundtrip() {
        val original = mapOf("numbers" to listOf(blBigIntParse("9223372036854775807"), blBigDecParse("1.25")))
        val bytecode = Bytecode.fromInstructions(listOf(Instruction.PUSH(original)))
        val reloaded = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(bytecode))
        val result = assertIs<Map<*, *>>(strictExecutor(reloaded).run(mutableMapOf()))
        val values = assertIs<List<*>>(result["numbers"])
        assertEquals("9223372036854775807", assertIs<BLBigInt>(values[0]).toString())
        assertEquals(1.25, assertIs<BLBigDec>(values[1]).toDouble())
    }

    @Test
    fun malformedNumericTagsAndPayloadsFailWithMigrationGuidance() {
        for (value in listOf(
            BytecodeIO.SerializedValue("bi"),
            BytecodeIO.SerializedValue("bd"),
            BytecodeIO.SerializedValue("i64"),
            BytecodeIO.SerializedValue("f64"),
            BytecodeIO.SerializedValue("bi", s = "1.0"),
            BytecodeIO.SerializedValue("bi", s = "1e3"),
            BytecodeIO.SerializedValue("bi", s = " 1"),
            BytecodeIO.SerializedValue("bd", s = "1e"),
            BytecodeIO.SerializedValue("bd", s = "1e+"),
            BytecodeIO.SerializedValue("bd", s = "NaN"),
            BytecodeIO.SerializedValue("bd", s = "Infinity"),
            BytecodeIO.SerializedValue("bd", s = "1.25", b = true),
            BytecodeIO.SerializedValue("bi", s = "1", arr = emptyList()),
            BytecodeIO.SerializedValue("i64", s = "9223372036854775808"),
            BytecodeIO.SerializedValue("f64", s = "not a number"),
            BytecodeIO.SerializedValue("future-number", s = "1"),
        )) {
            val failure = assertFailsWith<IllegalArgumentException>(value.toString()) {
                BytecodeIO.deserializeValue(BytecodeIO.SerializedValue("obj", obj = mapOf("nested" to value)))
            }
            assertTrue(failure.message.orEmpty().contains("blc"), failure.message)
        }
    }

    @Test
    fun negativeZeroPrimitiveRetainsSign() {
        val serialized = BytecodeIO.serializeValue(-0.0)
        assertEquals("f64", serialized.t)
        assertEquals(Double.NEGATIVE_INFINITY, 1.0 / assertIs<Double>(BytecodeIO.deserializeValue(serialized)))
    }

    @Test
    fun fractionalPrimitiveConstantsUseFloatingTagsForBothSigns() {
        for (value in listOf(1.5, -1.5, 0.125, -0.125)) {
            val serialized = BytecodeIO.serializeValue(value)
            assertEquals("f64", serialized.t, "constant $value")
            assertEquals(value, BytecodeIO.deserializeValue(serialized), "constant $value")
        }
    }

    @Test
    fun longConstantsRetainIntegerTagsAndAllDigits() {
        for (value in listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            val serialized = BytecodeIO.serializeValue(value)
            assertEquals("i64", serialized.t, "constant $value")
            assertEquals(value.toString(), serialized.s)
            assertEquals(value, BytecodeIO.deserializeValue(serialized), "constant $value")
        }
    }

    @Test
    fun largeFiniteFloatingConstantsDoNotBecomeInvalidIntegerText() {
        for (value in listOf(1e20, -1e20, 1e100)) {
            val bytecode = Bytecode.fromInstructions(listOf(Instruction.PUSH(value)))
            assertEquals("f64", BytecodeIO.serializeBytecode(bytecode).instructions.single().value?.t)
            val reloaded = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(bytecode))
            val executor = strictExecutor(reloaded)
            assertEquals(value, executor.run(mutableMapOf()), "constant $value")
            assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), executor.lastExecution)
        }
    }

    @Test
    fun decimalFractionsCompileEncodeReloadAndExecuteWithoutFallback() {
        // Binary-exact fractions give independent numeric oracles on JVM and JS;
        // this test concerns artifact transport, not wider decimal precision.
        val cases = mapOf(
            "DEC(1.5)" to 1.5,
            "DEC(-1.5)" to -1.5,
            "DEC(1.5) + DEC(0.25)" to 1.75,
            "DEC(-1.5) - DEC(0.25)" to -1.75,
        )
        for ((expression, expected) in cases) {
            val source = "TRANSFORM DecimalRoundtrip { OUTPUT { value: $expression } }"
            val program = Parser(Lexer(source).lex(), source).parse()
            val transform = program.decls.filterIsInstance<TransformDecl>().single()
            val ir = ToIR(emptyMap(), StdLib.fns).compile(transform.body.statements)
            val compiled = Compiler(hostFns = StdLib.fns).compile(ir)
            val reloaded = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(compiled))
            val executor = strictExecutor(reloaded)
            val output = assertIs<Map<*, *>>(executor.run(mutableMapOf()))
            val actual = assertIs<BLBigDec>(output["value"])
            assertEquals(expected, actual.toDouble(), expression)
            assertEquals(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true), executor.lastExecution)
        }
    }

    private fun strictExecutor(bytecode: Bytecode): VMExec = VMExec(
        ir = emptyList(),
        hostFns = StdLib.fns,
        precompiled = bytecode,
        fallbackPolicy = VMFallbackPolicy.NONE,
    )
}
