package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.JsonParseOptions
import io.github.ehlyzov.branchline.json.JsonKeyMode
import io.github.ehlyzov.branchline.json.formatJsonValue as formatJsonValueInternal
import io.github.ehlyzov.branchline.json.parseJsonObjectInput as parseJsonObjectInputInternal
import io.github.ehlyzov.branchline.json.parseJsonValue as parseJsonValueInternal
import io.github.ehlyzov.branchline.json.toJsonElement as toJsonElementInternal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.collections.iterator

fun parseJsonInput(
    text: String,
    numberMode: JsonNumberMode = JsonNumberMode.SAFE,
    keyMode: JsonKeyMode = JsonKeyMode.STRING,
): Map<String, Any?> {
    return parseJsonObjectInputInternal(text, JsonParseOptions(numberMode = numberMode, keyMode = keyMode))
}

fun parseJsonValue(
    text: String,
    numberMode: JsonNumberMode = JsonNumberMode.SAFE,
    keyMode: JsonKeyMode = JsonKeyMode.STRING,
): Any? {
    return parseJsonValueInternal(text, JsonParseOptions(numberMode = numberMode, keyMode = keyMode))
}

fun formatJson(value: Any?, pretty: Boolean = true, numberMode: JsonNumberMode = JsonNumberMode.SAFE): String {
    return formatJsonValueInternal(value, pretty = pretty, numberMode = numberMode)
}

@Suppress("CyclomaticComplexMethod")
fun fromJsonElement(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.booleanOrNull
        element.longOrNull != null -> element.longOrNull
        element.doubleOrNull != null -> element.doubleOrNull
        else -> element.content
    }
    is JsonArray -> element.map { fromJsonElement(it) }
    is JsonObject -> LinkedHashMap<String, Any?>(element.size).apply {
        for ((k, v) in element) put(k, fromJsonElement(v))
    }
}

@Suppress("CyclomaticComplexMethod")
fun toJsonElement(value: Any?, numberMode: JsonNumberMode = JsonNumberMode.SAFE): JsonElement {
    return toJsonElementInternal(value, numberMode)
}
