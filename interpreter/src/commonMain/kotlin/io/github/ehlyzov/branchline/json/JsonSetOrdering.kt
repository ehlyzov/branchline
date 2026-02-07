package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.runtime.base64Encode
import io.github.ehlyzov.branchline.runtime.isNumericValue
import io.github.ehlyzov.branchline.runtime.numericCompare
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val RANK_NULL = 0
private const val RANK_BOOLEAN = 1
private const val RANK_NUMBER = 2
private const val RANK_STRING = 3
private const val RANK_ARRAY = 4
private const val RANK_OBJECT = 5

private data class SetSortKey(
    val rank: Int,
    val boolValue: Boolean? = null,
    val numberValue: Any? = null,
    val textValue: String? = null,
    val canonical: String? = null,
)

private data class SetSortEntry(
    val value: Any?,
    val key: SetSortKey,
)

internal fun sortSetItems(value: Set<*>, numberMode: JsonNumberMode): List<Any?> {
    val entries = ArrayList<SetSortEntry>(value.size)
    for (item in value) {
        entries += SetSortEntry(item, buildSetSortKey(item, numberMode))
    }
    entries.sortWith { a, b -> compareSetSortKeys(a.key, b.key, numberMode) }
    return entries.map { it.value }
}

private fun buildSetSortKey(value: Any?, numberMode: JsonNumberMode): SetSortKey = when (value) {
    null -> SetSortKey(rank = RANK_NULL)
    is JsonNull -> SetSortKey(rank = RANK_NULL)
    is Boolean -> SetSortKey(rank = RANK_BOOLEAN, boolValue = value)
    is JsonPrimitive -> when {
        value.isString -> SetSortKey(rank = RANK_STRING, textValue = value.content)
        value.booleanOrNull != null -> SetSortKey(rank = RANK_BOOLEAN, boolValue = value.booleanOrNull == true)
        value.longOrNull != null -> buildNumberKey(value.longOrNull!!, numberMode)
        value.doubleOrNull != null -> buildNumberKey(value.doubleOrNull!!, numberMode)
        else -> SetSortKey(rank = RANK_STRING, textValue = value.content)
    }
    is ByteArray -> SetSortKey(rank = RANK_STRING, textValue = base64Encode(value))
    is String -> SetSortKey(rank = RANK_STRING, textValue = value)
    else -> {
        when {
            isNumericValue(value) -> buildNumberKey(value, numberMode)
            value is Map<*, *> || value is JsonObject -> buildObjectKey(value, numberMode)
            value is Set<*> -> buildArrayKey(value, numberMode)
            value is Iterable<*> -> buildArrayKey(value, numberMode)
            value is Array<*> -> buildArrayKey(value, numberMode)
            value is Sequence<*> -> buildArrayKey(value, numberMode)
            value is JsonArray -> buildArrayKey(value, numberMode)
            value is JsonElement -> buildObjectKey(value, numberMode)
            else -> SetSortKey(rank = RANK_STRING, textValue = value.toString())
        }
    }
}

private fun buildNumberKey(value: Any?, numberMode: JsonNumberMode): SetSortKey {
    val canonical = formatCanonicalJson(value, numberMode)
    return SetSortKey(rank = RANK_NUMBER, numberValue = value, canonical = canonical)
}

private fun buildArrayKey(value: Any?, numberMode: JsonNumberMode): SetSortKey {
    val canonical = formatCanonicalJson(value, numberMode)
    return SetSortKey(rank = RANK_ARRAY, canonical = canonical)
}

private fun buildObjectKey(value: Any?, numberMode: JsonNumberMode): SetSortKey {
    val canonical = formatCanonicalJson(value, numberMode)
    return SetSortKey(rank = RANK_OBJECT, canonical = canonical)
}

private fun compareSetSortKeys(a: SetSortKey, b: SetSortKey, numberMode: JsonNumberMode): Int {
    if (a.rank != b.rank) return a.rank.compareTo(b.rank)
    return when (a.rank) {
        RANK_NULL -> 0
        RANK_BOOLEAN -> compareBoolean(a.boolValue == true, b.boolValue == true)
        RANK_NUMBER -> compareNumbers(a, b, numberMode)
        RANK_STRING -> compareStrings(a.textValue, b.textValue)
        RANK_ARRAY, RANK_OBJECT -> compareStrings(a.canonical, b.canonical)
        else -> compareStrings(a.textValue, b.textValue)
    }
}

private fun compareBoolean(a: Boolean, b: Boolean): Int = when {
    a == b -> 0
    !a && b -> -1
    else -> 1
}

private fun compareNumbers(a: SetSortKey, b: SetSortKey, numberMode: JsonNumberMode): Int {
    val left = a.numberValue
    val right = b.numberValue
    if (left != null && right != null) {
        try {
            val cmp = numericCompare(left, right)
            if (cmp != 0) return cmp
        } catch (_: IllegalArgumentException) {
            // fall through to canonical comparison
        }
    }
    val leftCanonical = a.canonical ?: formatCanonicalJson(left, numberMode)
    val rightCanonical = b.canonical ?: formatCanonicalJson(right, numberMode)
    return compareStrings(leftCanonical, rightCanonical)
}

private fun compareStrings(a: String?, b: String?): Int {
    if (a == null && b == null) return 0
    if (a == null) return -1
    if (b == null) return 1
    return a.compareTo(b)
}
