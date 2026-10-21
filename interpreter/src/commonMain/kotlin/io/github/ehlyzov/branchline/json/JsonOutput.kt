package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString
import io.github.ehlyzov.branchline.runtime.base64Encode
import io.github.ehlyzov.branchline.runtime.isPlatformIntegerNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

public class JsonOutputException(message: String) : IllegalArgumentException(message)

private val prettyJson = Json { prettyPrint = true }
private val compactJson = Json

public fun formatJsonValue(
    value: Any?,
    pretty: Boolean = true,
    numberMode: JsonNumberMode = JsonNumberMode.SAFE,
): String {
    val element = toJsonElement(value, numberMode)
    val serializer = if (pretty) prettyJson else compactJson
    return serializer.encodeToString(JsonElement.serializer(), element)
}

@Suppress("CyclomaticComplexMethod")
public fun toJsonElement(
    value: Any?,
    numberMode: JsonNumberMode = JsonNumberMode.SAFE,
): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is ByteArray -> JsonPrimitive(base64Encode(value))
    is BLBigInt -> jsonPrimitiveForBigInt(value, numberMode)
    is BLBigDec -> jsonPrimitiveForBigDec(value, numberMode)
    is IBig -> jsonPrimitiveForBigInt(value.v, numberMode)
    is Dec -> jsonPrimitiveForBigDec(value.v, numberMode)
    is I32 -> jsonPrimitiveForLong(value.v.toLong(), numberMode)
    is I64 -> jsonPrimitiveForLong(value.v, numberMode)
    is Long -> jsonPrimitiveForLong(value, numberMode)
    // JS Byte/Short/Int/Float/Double type checks all match primitive numbers.
    // Classify the value before converting so fractions never pass through Long.
    is Number -> {
        val number = value.toDouble()
        if (isPlatformIntegerNumber(value) && number in -9_007_199_254_740_991.0..9_007_199_254_740_991.0) {
            jsonPrimitiveForLong(value.toLong(), numberMode)
        } else {
            jsonPrimitiveForDouble(number)
        }
    }
    is Set<*> -> JsonArray(sortSetItems(value, numberMode).map { toJsonElement(it, numberMode) })
    is Map<*, *> -> {
        val content = LinkedHashMap<String, JsonElement>(value.size)
        for ((k, v) in value) {
            content[k?.toString() ?: "null"] = toJsonElement(v, numberMode)
        }
        JsonObject(content)
    }
    is Iterable<*> -> JsonArray(value.map { toJsonElement(it, numberMode) })
    is Array<*> -> JsonArray(value.map { toJsonElement(it, numberMode) })
    is Sequence<*> -> JsonArray(value.map { toJsonElement(it, numberMode) }.toList())
    else -> JsonPrimitive(value.toString())
}

private fun jsonPrimitiveForBigInt(value: BLBigInt, numberMode: JsonNumberMode): JsonPrimitive = when (numberMode) {
    JsonNumberMode.STRICT -> throw JsonOutputException("BigInt output requires json-numbers safe or extended")
    JsonNumberMode.SAFE -> JsonPrimitive(value.toString())
    JsonNumberMode.EXTENDED -> JsonUnquotedLiteral(value.toString())
}

private fun jsonPrimitiveForBigDec(value: BLBigDec, numberMode: JsonNumberMode): JsonPrimitive = when (numberMode) {
    JsonNumberMode.STRICT -> throw JsonOutputException("BigDecimal output requires json-numbers safe or extended")
    JsonNumberMode.SAFE -> JsonPrimitive(value.toPlainString())
    JsonNumberMode.EXTENDED -> JsonUnquotedLiteral(value.toPlainString())
}

private fun jsonPrimitiveForLong(value: Long, numberMode: JsonNumberMode): JsonPrimitive = when (numberMode) {
    JsonNumberMode.STRICT -> {
        if (!isJsonSafeInteger(value)) {
            throw JsonOutputException("Integer output outside safe JSON range")
        }
        JsonPrimitive(value)
    }
    JsonNumberMode.SAFE -> if (isJsonSafeInteger(value)) {
        JsonPrimitive(value)
    } else {
        JsonPrimitive(value.toString())
    }
    JsonNumberMode.EXTENDED -> JsonUnquotedLiteral(value.toString())
}

private fun jsonPrimitiveForDouble(value: Double): JsonPrimitive {
    if (!value.isFinite()) {
        throw JsonOutputException("JSON output cannot encode NaN or Infinity")
    }
    return JsonPrimitive(value)
}
