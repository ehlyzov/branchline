package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.json.JsonInputException
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.JsonOutputException
import io.github.ehlyzov.branchline.json.JsonParseOptions
import io.github.ehlyzov.branchline.json.formatCanonicalJson
import io.github.ehlyzov.branchline.json.formatJsonValue
import io.github.ehlyzov.branchline.json.parseJsonObjectInput
import io.github.ehlyzov.branchline.json.parseJsonValue
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConformJsonLargeNumberTest {
    @Test
    fun parse_large_numbers_promote_to_big_types() {
        val options = JsonParseOptions(JsonNumberMode.SAFE)
        val bigInt = parseJsonValue("9007199254740993", options)
        val bigDec = parseJsonValue("0.1234567890123456789", options)
        assertTrue(bigInt is BLBigInt)
        assertTrue(bigDec is BLBigDec)
    }

    @Test
    fun strict_mode_rejects_large_numbers() {
        val options = JsonParseOptions(JsonNumberMode.STRICT)
        assertFailsWith<JsonInputException> {
            parseJsonValue("9007199254740993", options)
        }
        assertFailsWith<JsonInputException> {
            parseJsonValue("0.1234567890123456789", options)
        }
    }

    @Test
    fun safe_output_emits_strings_for_big_numbers() {
        val value = mapOf(
            "big" to blBigIntParse("9007199254740993"),
            "dec" to blBigDecParse("0.1234567890123456789"),
        )
        val output = formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.SAFE)
        val parsed = parseJsonObjectInput(output)
        assertTrue(parsed["big"] is String)
        assertTrue(parsed["dec"] is String)
    }

    @Test
    fun extended_output_emits_numbers_for_big_types() {
        val value = mapOf(
            "big" to blBigIntParse("9007199254740993"),
            "dec" to blBigDecParse("0.1234567890123456789"),
        )
        val output = formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.EXTENDED)
        val parsed = parseJsonObjectInput(output)
        assertTrue(parsed["big"] is BLBigInt)
        assertTrue(parsed["dec"] is BLBigDec)
    }

    @Test
    fun strict_output_rejects_big_numbers() {
        val value = blBigIntParse("9007199254740993")
        assertFailsWith<JsonOutputException> {
            formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.STRICT)
        }
    }

    @Test
    fun canonical_output_respects_number_mode() {
        val value = mapOf(
            "big" to blBigIntParse("9007199254740993"),
            "dec" to blBigDecParse("0.1234567890123456789"),
        )
        val safeOutput = formatCanonicalJson(value, JsonNumberMode.SAFE)
        val safeParsed = parseJsonObjectInput(safeOutput)
        assertTrue(safeParsed["big"] is String)
        assertTrue(safeParsed["dec"] is String)
        val extendedOutput = formatCanonicalJson(value, JsonNumberMode.EXTENDED)
        val extendedParsed = parseJsonObjectInput(extendedOutput)
        assertTrue(extendedParsed["big"] is BLBigInt)
        assertTrue(extendedParsed["dec"] is BLBigDec)
    }
}
