package io.github.ehlyzov.branchline.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonOutputNumericTest {
    @Test
    fun fractionalAndLargeFloatingNumbersSurviveEveryOrdinaryJsonMode() {
        for (value in listOf(1.5, -1.5, 0.125, -0.125, 1e20, -1e20, 1e100)) {
            for (mode in JsonNumberMode.entries) {
                assertEquals(value, toJsonElement(value, mode).jsonPrimitive.double, "$value in $mode")
                for (pretty in listOf(true, false)) {
                    val rendered = formatJsonValue(mapOf("number" to value), pretty = pretty, numberMode = mode)
                    val actual = Json.parseToJsonElement(rendered).jsonObject.getValue("number").jsonPrimitive.double
                    assertEquals(value, actual, "$value in $mode, pretty=$pretty")
                }
            }
        }
    }

    @Test
    fun actualLongKeepsItsSafeAndStrictBoundaryRules() {
        assertEquals("\"9223372036854775807\"", formatJsonValue(Long.MAX_VALUE, pretty = false))
        assertEquals("9223372036854775807", formatJsonValue(Long.MAX_VALUE, pretty = false, numberMode = JsonNumberMode.EXTENDED))
        assertFailsWith<JsonOutputException> { toJsonElement(Long.MAX_VALUE, JsonNumberMode.STRICT) }
        assertEquals("42", formatJsonValue(42, pretty = false))
    }

    @Test
    fun nonFinitePrimitivesAreRejectedInsteadOfBeingConvertedToIntegers() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (mode in JsonNumberMode.entries) {
                assertFailsWith<JsonOutputException> { toJsonElement(value, mode) }
            }
        }
    }
}
