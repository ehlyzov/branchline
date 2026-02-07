package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.isJsonSafeInteger
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt

public const val WARN_XML_COMMENTS_DROPPED: String =
    "XML comments are dropped during parsing."
public const val WARN_XML_PROCESSING_INSTRUCTIONS_DROPPED: String =
    "XML processing instructions are dropped during parsing."
public const val WARN_XML_MIXED_CONTENT_ORDER_INPUT: String =
    "XML mixed content ordering is not preserved in map form."
public const val WARN_JSON_BYTES_AS_BASE64: String =
    "JSON output encodes byte arrays as base64 strings."
public const val WARN_JSON_CANONICAL_KEY_REORDER: String =
    "json-canonical output sorts object keys lexicographically."
public const val WARN_JSON_EXTENDED_PRECISION: String =
    "json-numbers extended emits large numeric values as JSON numbers; downstream parsers may lose precision."
public const val WARN_XML_MIXED_CONTENT_ORDER_OUTPUT: String =
    "XML output cannot preserve exact mixed-content interleaving from map form."

private val xmlProcessingInstructionPattern: Regex =
    Regex("""<\?(?!xml\b)[\s\S]*?\?>""", RegexOption.IGNORE_CASE)

private val xmlIndexedTextKeyPattern: Regex = Regex("""^\$\d+$""")

public fun collectInputConversionWarnings(
    rawText: String,
    format: InputFormat,
    parsed: Map<String, Any?>,
): List<String> {
    if (format != InputFormat.XML) return emptyList()
    val warnings = LinkedHashSet<String>()
    if (rawText.contains("<!--")) {
        warnings += WARN_XML_COMMENTS_DROPPED
    }
    if (xmlProcessingInstructionPattern.containsMatchIn(rawText)) {
        warnings += WARN_XML_PROCESSING_INSTRUCTIONS_DROPPED
    }
    if (containsXmlInputMixedContentLoss(parsed)) {
        warnings += WARN_XML_MIXED_CONTENT_ORDER_INPUT
    }
    return warnings.toList()
}

public fun collectOutputConversionWarnings(
    value: Any?,
    format: OutputFormat,
    jsonNumberMode: JsonNumberMode,
): List<String> {
    val warnings = LinkedHashSet<String>()
    when (format) {
        OutputFormat.JSON, OutputFormat.JSON_COMPACT, OutputFormat.JSON_CANONICAL -> {
            if (containsByteArray(value)) {
                warnings += WARN_JSON_BYTES_AS_BASE64
            }
            if (jsonNumberMode == JsonNumberMode.EXTENDED && containsExtendedPrecisionRisk(value)) {
                warnings += WARN_JSON_EXTENDED_PRECISION
            }
            if (format == OutputFormat.JSON_CANONICAL && containsCanonicalKeyReorderRisk(value)) {
                warnings += WARN_JSON_CANONICAL_KEY_REORDER
            }
        }

        OutputFormat.XML, OutputFormat.XML_COMPACT -> {
            if (containsXmlOutputMixedContentLoss(value)) {
                warnings += WARN_XML_MIXED_CONTENT_ORDER_OUTPUT
            }
        }
    }
    return warnings.toList()
}

private fun containsXmlInputMixedContentLoss(value: Any?): Boolean {
    val map = value as? Map<*, *>
    if (map != null) {
        if (isXmlInputMixedContentMap(map)) return true
        for (entry in map.values) {
            if (containsXmlInputMixedContentLoss(entry)) return true
        }
    }
    val collection = value as? Collection<*>
    if (collection != null) {
        for (entry in collection) {
            if (containsXmlInputMixedContentLoss(entry)) return true
        }
    }
    val array = value as? Array<*>
    if (array != null) {
        for (entry in array) {
            if (containsXmlInputMixedContentLoss(entry)) return true
        }
    }
    return false
}

private fun isXmlInputMixedContentMap(map: Map<*, *>): Boolean {
    var hasIndexedText = false
    var hasElementChildren = false
    for (key in map.keys) {
        val name = key?.toString() ?: "null"
        if (xmlIndexedTextKeyPattern.matches(name)) {
            hasIndexedText = true
            continue
        }
        if (isXmlChildKey(name)) {
            hasElementChildren = true
        }
    }
    return hasIndexedText && hasElementChildren
}

private fun containsByteArray(value: Any?): Boolean {
    if (value is ByteArray) return true
    val map = value as? Map<*, *>
    if (map != null) {
        for (entry in map.values) {
            if (containsByteArray(entry)) return true
        }
    }
    val collection = value as? Collection<*>
    if (collection != null) {
        for (entry in collection) {
            if (containsByteArray(entry)) return true
        }
    }
    val array = value as? Array<*>
    if (array != null) {
        for (entry in array) {
            if (containsByteArray(entry)) return true
        }
    }
    return false
}

private fun containsExtendedPrecisionRisk(value: Any?): Boolean {
    if (value is BLBigInt || value is BLBigDec || value is IBig || value is Dec) return true
    if (value is Long && !isJsonSafeInteger(value)) return true
    if (value is I64 && !isJsonSafeInteger(value.v)) return true
    val map = value as? Map<*, *>
    if (map != null) {
        for (entry in map.values) {
            if (containsExtendedPrecisionRisk(entry)) return true
        }
    }
    val collection = value as? Collection<*>
    if (collection != null) {
        for (entry in collection) {
            if (containsExtendedPrecisionRisk(entry)) return true
        }
    }
    val array = value as? Array<*>
    if (array != null) {
        for (entry in array) {
            if (containsExtendedPrecisionRisk(entry)) return true
        }
    }
    return false
}

private fun containsCanonicalKeyReorderRisk(value: Any?): Boolean {
    val map = value as? Map<*, *>
    if (map != null) {
        if (map.size > 1) {
            val keys = map.keys.map { it?.toString() ?: "null" }
            if (keys != keys.sorted()) return true
        }
        for (entry in map.values) {
            if (containsCanonicalKeyReorderRisk(entry)) return true
        }
    }
    val collection = value as? Collection<*>
    if (collection != null) {
        for (entry in collection) {
            if (containsCanonicalKeyReorderRisk(entry)) return true
        }
    }
    val array = value as? Array<*>
    if (array != null) {
        for (entry in array) {
            if (containsCanonicalKeyReorderRisk(entry)) return true
        }
    }
    return false
}

private fun containsXmlOutputMixedContentLoss(value: Any?): Boolean {
    val map = value as? Map<*, *>
    if (map != null) {
        if (isXmlOutputMixedContentMap(map)) return true
        for (entry in map.values) {
            if (containsXmlOutputMixedContentLoss(entry)) return true
        }
    }
    val collection = value as? Collection<*>
    if (collection != null) {
        for (entry in collection) {
            if (containsXmlOutputMixedContentLoss(entry)) return true
        }
    }
    val array = value as? Array<*>
    if (array != null) {
        for (entry in array) {
            if (containsXmlOutputMixedContentLoss(entry)) return true
        }
    }
    return false
}

private fun isXmlOutputMixedContentMap(map: Map<*, *>): Boolean {
    var hasText = false
    var hasElementChildren = false
    for (key in map.keys) {
        val name = key?.toString() ?: "null"
        if (isXmlTextKey(name)) {
            hasText = true
            continue
        }
        if (isXmlChildKey(name)) {
            hasElementChildren = true
        }
    }
    return hasText && hasElementChildren
}

private fun isXmlTextKey(name: String): Boolean =
    name == "$" || name == "#text" || xmlIndexedTextKeyPattern.matches(name)

private fun isXmlChildKey(name: String): Boolean =
    !name.startsWith("@") && !isXmlTextKey(name)
