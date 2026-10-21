package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.JsonParseOptions
import io.github.ehlyzov.branchline.json.formatJsonValue
import io.github.ehlyzov.branchline.json.parseJsonValue
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.vm.VMExec
import io.github.ehlyzov.branchline.vm.VMExecutionPath
import io.github.ehlyzov.branchline.vm.VMFallbackPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Runs each independent oracle through the interpreter and ordinary VM, without fallback. */
internal object NumericContractHarness {
    fun evaluate(expression: String, vm: Boolean): Any? {
        return outcome(expression, vm).getOrThrow()
    }

    fun assertFailure(expression: String, vm: Boolean) {
        // outcome verifies the VM path outside the captured numeric failure.
        assertTrue(outcome(expression, vm).isFailure, "vm=$vm: $expression")
    }

    private fun outcome(expression: String, vm: Boolean): Result<Any?> {
        val source = "TRANSFORM NumericContract { OUTPUT { value: $expression } }"
        if (!vm) return runCatching { (buildRunnerFromProgramMP(source)(emptyMap()) as Map<*, *>)["value"] }
        val program = Parser(Lexer(source).lex()).parse()
        val transform = program.decls.filterIsInstance<TransformDecl>().single()
        val executor = VMExec(
            ToIR(emptyMap(), StdLib.fns).compile(transform.body.statements),
            hostFns = StdLib.fns,
            fallbackPolicy = VMFallbackPolicy.NONE,
        )
        val result = runCatching { (executor.run(mutableMapOf()) as Map<*, *>)["value"] }
        assertEquals(VMExecutionPath.VM_BYTECODE, executor.lastExecution?.path, expression)
        return result
    }

    fun eachEngine(check: (Boolean) -> Unit) {
        for (vm in listOf(false, true)) check(vm)
    }
}

class ConformNumericContractTest {
    @Test
    fun boundedIntegerProfileHasIndependentBoundaryAndSignedOracles() {
        val cases = mapOf(
            "9007199254740990 + 1" to "9007199254740991",
            "-9007199254740990 - 1" to "-9007199254740991",
            "9007199254740991 - 9007199254740990" to "1",
            "1000001 * 1000001" to "1000002000001",
            "2147483647 + 1" to "2147483648",
            "0 - 2147483647 - 2" to "-2147483649",
            "65536 * 65536" to "4294967296",
            "2147483648 + 2147483648" to "4294967296",
            "-9007199254740991 // 2" to "-4503599627370495",
            "-9007199254740991 % 2" to "-1",
            "9007199254740991 % -2" to "1",
            "-(-9007199254740991)" to "9007199254740991",
            "INT(\"9007199254740991\")" to "9007199254740991",
            "INT(9007199254740991)" to "9007199254740991",
            "INT(-9007199254740991)" to "-9007199254740991",
            "INT(1.0)" to "1",
        )
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in cases) {
                assertEquals(expected, NumericContractHarness.evaluate(expression, vm).toString(), "vm=$vm: $expression")
            }
            assertEquals(true, NumericContractHarness.evaluate("9007199254740990 < 9007199254740991", vm))
            assertEquals(false, NumericContractHarness.evaluate("9007199254740990 == 9007199254740991", vm))
        }
    }

    @Test
    fun integerPromotionBeyondCommonProfileStillPreservesThisSupportedValue() {
        NumericContractHarness.eachEngine { vm ->
            assertEquals("9007199254740992", NumericContractHarness.evaluate("9007199254740991 + 1", vm).toString())
            assertEquals("9007199254740993", NumericContractHarness.evaluate("DEC(\"9007199254740993\")", vm).toString())
            assertEquals("4611686018427387904", NumericContractHarness.evaluate("2147483648 * 2147483648", vm).toString())
        }
    }

    @Test
    fun ordinaryDivisionAndRoundingHaveIndependentOracles() {
        NumericContractHarness.eachEngine { vm ->
            assertEquals(0.5, NumericContractHarness.evaluate("1 / 2", vm))
            assertEquals(0.30000000000000004, NumericContractHarness.evaluate("0.1 + 0.2", vm))
            assertEquals("2", NumericContractHarness.evaluate("ROUND(2.5)", vm).toString())
            assertEquals("4", NumericContractHarness.evaluate("ROUND(3.5)", vm).toString())
        }
    }

    @Test
    fun decimalNumberOptInPreservesFractionalValueAndSerializedDigits() {
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in mapOf("DEC(1.5)" to 1.5, "DEC(0.5)" to 0.5)) {
                val actual = NumericContractHarness.evaluate(expression, vm) as BLBigDec
                assertEquals(expected, actual.toDouble(), "vm=$vm: $expression")
                assertEquals("\"$expected\"", formatJsonValue(actual, pretty = false), "vm=$vm: $expression")
            }
        }
    }

    @Test
    fun supportedErrorsAreNotSilentlyCoerced() {
        NumericContractHarness.eachEngine { vm ->
            for (expression in listOf(
                "1 / 0", "1 // 0", "1 % 0", "1.5 // 1", "DEC(\"1.0\") // 1",
                "DEC(\"1.5\") + 0.5", "DEC(\"1.5\") == 0.5", "INT(1.5)", "INT(-1.5)",
                "INT(NUMBER(\"1e309\"))", "INT(NUMBER(\"-1e309\"))",
                "INT(\"1.5\")", "DEC(\"bad\")", "NUMBER(\"9223372036854775808\")",
            )) NumericContractHarness.assertFailure(expression, vm)
        }
    }

    @Test
    fun zeroDivisorsInUnselectedBranchesDoNotFailCompilation() {
        NumericContractHarness.eachEngine { vm ->
            for (expression in listOf(
                "IF false THEN 1 // 0 ELSE 7", "IF false THEN 1 % 0 ELSE 7",
                "IF false THEN 1.5 // 1 ELSE 7",
            )) {
                assertEquals("7", NumericContractHarness.evaluate(expression, vm).toString(), "vm=$vm: $expression")
            }
        }
    }

    @Test
    fun jsonModesPreserveTextButDoNotRestoreNumericTypesFromStrings() {
        assertEquals("9007199254740991", formatJsonValue(parseJsonValue("9007199254740991"), pretty = false))
        val large = parseJsonValue("9007199254740993")
        assertEquals("\"9007199254740993\"", formatJsonValue(large, pretty = false))
        assertEquals("9007199254740993", parseJsonValue(formatJsonValue(large, pretty = false)))
        assertEquals("9007199254740993", formatJsonValue(large, pretty = false, numberMode = JsonNumberMode.EXTENDED))
        assertFails { parseJsonValue("9007199254740992", JsonParseOptions(JsonNumberMode.STRICT)) }
        NumericContractHarness.eachEngine { vm ->
            val explicitSmall = NumericContractHarness.evaluate("DEC(1)", vm)
            assertEquals("\"1\"", formatJsonValue(explicitSmall, pretty = false))
            assertFails { formatJsonValue(explicitSmall, pretty = false, numberMode = JsonNumberMode.STRICT) }
        }
    }
}
