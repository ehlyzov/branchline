package io.github.ehlyzov.branchline.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.collections.iterator

private val prettyJson = Json { prettyPrint = true }
private val compactJson = Json

fun parseJsonInput(text: String): Map<String, Any?> {
    if (text.isBlank()) return emptyMap()
    val element = compactJson.parseToJsonElement(text)
    val parsed = fromJsonElement(element)
    require(parsed is Map<*, *>) { "Input JSON must be an object" }
    val result = LinkedHashMap<String, Any?>(parsed.size)
    for ((key, value) in parsed) {
        require(key is String) { "Input JSON keys must be strings" }
        result[key] = value
    }
    return result
}

fun formatJson(value: Any?, pretty: Boolean = true): String {
    val element = toJsonElement(value)
    val serializer = if (pretty) prettyJson else compactJson
    return serializer.encodeToString(element)
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
fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toDouble())
    is Map<*, *> -> {
        val content = LinkedHashMap<String, JsonElement>(value.size)
        for ((k, v) in value) {
            content[k.toString()] = toJsonElement(v)
        }
        JsonObject(content)
    }
    is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
    is Array<*> -> JsonArray(value.map { toJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}
