package io.github.ehlyzov.branchline.xml

import io.github.ehlyzov.branchline.runtime.base64Encode
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet

public fun formatXmlOutput(
    value: Any?,
    pretty: Boolean = true,
    strictNamespaces: Boolean = true,
): String {
    val (rootName, rootValue) = rootElementEntry(value)
    val out = StringBuilder()
    appendXmlElement(
        out = out,
        name = rootName,
        value = rootValue,
        depth = 0,
        pretty = pretty,
        strictNamespaces = strictNamespaces,
        inheritedNamespaces = emptyMap(),
    )
    return out.toString()
}

private fun rootElementEntry(value: Any?): Pair<String, Any?> {
    val root = value as? Map<*, *> ?: throw IllegalArgumentException(
        "XML output expects an object with exactly one root element",
    )
    if (root.size != 1) {
        throw IllegalArgumentException("XML output expects exactly one root element")
    }
    val entry = root.entries.first()
    val name = entry.key?.toString()?.trim().orEmpty()
    if (name.isEmpty()) {
        throw IllegalArgumentException("XML root element name must be a non-empty string")
    }
    return name to entry.value
}

private fun appendXmlElement(
    out: StringBuilder,
    name: String,
    value: Any?,
    depth: Int,
    pretty: Boolean,
    strictNamespaces: Boolean,
    inheritedNamespaces: Map<String, String>,
) {
    val content = parseXmlElementContent(value)
    val allNamespaces = mergedNamespaces(inheritedNamespaces, content.namespaces)
    validateQualifiedName(name, allNamespaces, strictNamespaces, "element")
    validateAttributePrefixes(content.attributes, allNamespaces, strictNamespaces)
    val orderedChildren = orderedChildNames(content.children.keys, content.explicitOrder)
    val textSegments = resolvedTextSegments(
        pureText = content.pureText,
        mixedText = content.mixedText,
        hasChildren = orderedChildren.isNotEmpty(),
    )
    val attributes = orderedAttributes(content.namespaces, content.attributes)
    val multilineChildren = pretty && orderedChildren.isNotEmpty() && textSegments.isEmpty()
    if (pretty) appendIndent(out, depth)
    out.append('<').append(name)
    for ((attrName, attrValue) in attributes) {
        out.append(' ')
            .append(attrName)
            .append("=\"")
            .append(escapeXmlAttribute(attrValue))
            .append('"')
    }
    if (orderedChildren.isEmpty() && textSegments.isEmpty()) {
        out.append("/>")
        if (pretty) out.append('\n')
        return
    }
    out.append('>')
    if (multilineChildren) out.append('\n')
    val childPretty = pretty && textSegments.isEmpty()
    for (childName in orderedChildren) {
        val childValue = content.children[childName]
        appendXmlChildValue(
            out = out,
            childName = childName,
            value = childValue,
            depth = depth + 1,
            pretty = childPretty,
            strictNamespaces = strictNamespaces,
            inheritedNamespaces = allNamespaces,
        )
    }
    for (text in textSegments) {
        out.append(escapeXmlText(text))
    }
    if (multilineChildren) appendIndent(out, depth)
    out.append("</").append(name).append('>')
    if (pretty) out.append('\n')
}

private fun appendXmlChildValue(
    out: StringBuilder,
    childName: String,
    value: Any?,
    depth: Int,
    pretty: Boolean,
    strictNamespaces: Boolean,
    inheritedNamespaces: Map<String, String>,
) {
    if (value is List<*>) {
        for (item in value) {
            appendXmlElement(
                out = out,
                name = childName,
                value = item,
                depth = depth,
                pretty = pretty,
                strictNamespaces = strictNamespaces,
                inheritedNamespaces = inheritedNamespaces,
            )
        }
        return
    }
    appendXmlElement(
        out = out,
        name = childName,
        value = value,
        depth = depth,
        pretty = pretty,
        strictNamespaces = strictNamespaces,
        inheritedNamespaces = inheritedNamespaces,
    )
}

private fun parseXmlElementContent(value: Any?): XmlElementContent {
    if (value == null) {
        return XmlElementContent(
            namespaces = LinkedHashMap(),
            attributes = LinkedHashMap(),
            explicitOrder = emptyList(),
            children = LinkedHashMap(),
            pureText = null,
            mixedText = emptyList(),
        )
    }
    if (value !is Map<*, *>) {
        return XmlElementContent(
            namespaces = LinkedHashMap(),
            attributes = LinkedHashMap(),
            explicitOrder = emptyList(),
            children = LinkedHashMap(),
            pureText = xmlScalar(value, "text"),
            mixedText = emptyList(),
        )
    }
    val namespaces = LinkedHashMap<String, String>()
    val attributes = LinkedHashMap<String, String>()
    val children = LinkedHashMap<String, Any?>()
    var explicitOrder = emptyList<String>()
    var pureText: String? = null
    val mixedText = ArrayList<IndexedTextSegment>()
    for ((rawKey, rawValue) in value) {
        val key = mapKeyString(rawKey)
        when {
            key == "@xmlns" -> namespaces.putAll(parsedNamespaceMap(rawValue))
            key == "@order" -> explicitOrder = parsedOrder(rawValue)
            key == "$" -> pureText = xmlScalar(rawValue, "text")
            key == "#text" && pureText == null -> pureText = xmlScalar(rawValue, "text")
            isMixedTextKey(key) -> mixedText += IndexedTextSegment(
                index = key.substring(1).toInt(),
                value = xmlScalar(rawValue, "text"),
            )
            key.startsWith("@") -> appendAttributeOrNamespace(key, rawValue, attributes, namespaces)
            else -> children[key] = rawValue
        }
    }
    return XmlElementContent(
        namespaces = namespaces,
        attributes = attributes,
        explicitOrder = explicitOrder,
        children = children,
        pureText = pureText,
        mixedText = mixedText.sortedBy { it.index },
    )
}

private fun appendAttributeOrNamespace(
    key: String,
    rawValue: Any?,
    attributes: LinkedHashMap<String, String>,
    namespaces: LinkedHashMap<String, String>,
) {
    val attrName = key.substring(1)
    if (attrName.isEmpty()) {
        throw IllegalArgumentException("XML attribute key '$key' is invalid")
    }
    if (attrName == "xmlns") {
        namespaces["$"] = xmlScalar(rawValue, "namespace declaration")
        return
    }
    if (attrName.startsWith("xmlns:")) {
        val nsKey = normalizedNamespaceKey(attrName.substringAfter("xmlns:"))
        namespaces[nsKey] = xmlScalar(rawValue, "namespace declaration")
        return
    }
    attributes[attrName] = xmlScalar(rawValue, "attribute '$attrName'")
}

private fun parsedNamespaceMap(value: Any?): LinkedHashMap<String, String> {
    if (value == null) return LinkedHashMap()
    val map = value as? Map<*, *> ?: throw IllegalArgumentException("@xmlns must be an object")
    val namespaces = LinkedHashMap<String, String>()
    for ((rawKey, rawValue) in map) {
        val nsKey = normalizedNamespaceKey(mapKeyString(rawKey))
        namespaces[nsKey] = xmlScalar(rawValue, "namespace declaration")
    }
    return namespaces
}

private fun parsedOrder(value: Any?): List<String> {
    if (value == null) return emptyList()
    val list = value as? List<*> ?: throw IllegalArgumentException("@order must be an array of element names")
    val result = ArrayList<String>()
    val seen = LinkedHashSet<String>()
    for (entry in list) {
        val name = entry?.toString()?.trim().orEmpty()
        if (name.isEmpty()) continue
        if (seen.add(name)) result += name
    }
    return result
}

private fun orderedChildNames(children: Set<String>, explicitOrder: List<String>): List<String> {
    val ordered = ArrayList<String>()
    val seen = LinkedHashSet<String>()
    for (name in explicitOrder) {
        if (children.contains(name) && seen.add(name)) ordered += name
    }
    val remaining = children.filterNot { seen.contains(it) }.sorted()
    ordered.addAll(remaining)
    return ordered
}

private fun resolvedTextSegments(
    pureText: String?,
    mixedText: List<IndexedTextSegment>,
    hasChildren: Boolean,
): List<String> {
    if (!hasChildren) {
        if (pureText != null) return listOf(pureText)
        if (mixedText.isEmpty()) return emptyList()
        return listOf(mixedText.joinToString(separator = "") { it.value })
    }
    val segments = ArrayList<String>()
    if (pureText != null) segments += pureText
    for (segment in mixedText) segments += segment.value
    return segments
}

private fun orderedAttributes(
    namespaces: Map<String, String>,
    attributes: Map<String, String>,
): List<Pair<String, String>> {
    val combined = LinkedHashMap<String, String>()
    for ((key, value) in namespaces) {
        val attrName = if (key == "$") "xmlns" else "xmlns:$key"
        combined[attrName] = value
    }
    for ((name, value) in attributes) {
        if (combined.containsKey(name)) {
            throw IllegalArgumentException("Duplicate XML attribute '$name'")
        }
        combined[name] = value
    }
    return combined.entries
        .sortedBy { it.key }
        .map { it.key to it.value }
}

private fun mergedNamespaces(
    inherited: Map<String, String>,
    local: Map<String, String>,
): LinkedHashMap<String, String> {
    val merged = LinkedHashMap<String, String>()
    for ((key, value) in inherited) merged[key] = value
    for ((key, value) in local) merged[key] = value
    return merged
}

private fun validateAttributePrefixes(
    attributes: Map<String, String>,
    namespaces: Map<String, String>,
    strictNamespaces: Boolean,
) {
    for (name in attributes.keys) {
        validateQualifiedName(name, namespaces, strictNamespaces, "attribute")
    }
}

private fun validateQualifiedName(
    name: String,
    namespaces: Map<String, String>,
    strictNamespaces: Boolean,
    kind: String,
) {
    if (!strictNamespaces) return
    val prefix = namespacePrefix(name) ?: return
    if (prefix == "xml" || prefix == "xmlns") return
    if (!namespaces.containsKey(prefix)) {
        throw IllegalArgumentException("Undeclared XML namespace prefix '$prefix' on $kind '$name'")
    }
}

private fun namespacePrefix(name: String): String? {
    val idx = name.indexOf(':')
    if (idx <= 0 || idx >= name.length - 1) return null
    return name.substring(0, idx)
}

private fun mapKeyString(key: Any?): String {
    val stringKey = key?.toString()?.trim().orEmpty()
    if (stringKey.isEmpty()) {
        throw IllegalArgumentException("XML output object keys must be non-empty strings")
    }
    return stringKey
}

private fun normalizedNamespaceKey(raw: String): String {
    val key = raw.trim()
    if (key.isEmpty() || key == "$") return "$"
    if (key.contains(':')) {
        throw IllegalArgumentException("Invalid namespace key '$raw' in @xmlns")
    }
    return key
}

private fun xmlScalar(value: Any?, field: String): String {
    if (value == null) return ""
    return when (value) {
        is String -> value
        is Boolean -> value.toString()
        is Number -> value.toString()
        is Char -> value.toString()
        is ByteArray -> base64Encode(value)
        is Map<*, *>, is List<*> -> throw IllegalArgumentException("XML $field must be a scalar value")
        else -> value.toString()
    }
}

private fun isMixedTextKey(key: String): Boolean {
    if (key.length < 2 || key[0] != '$') return false
    val suffix = key.substring(1)
    if (suffix.isEmpty()) return false
    if (!suffix.all { it.isDigit() }) return false
    return suffix.toInt() >= 1
}

private fun appendIndent(out: StringBuilder, depth: Int) {
    repeat(depth) { out.append("  ") }
}

private fun escapeXmlText(value: String): String {
    val out = StringBuilder(value.length)
    for (ch in value) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

private fun escapeXmlAttribute(value: String): String {
    val out = StringBuilder(value.length)
    for (ch in value) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

private data class XmlElementContent(
    val namespaces: LinkedHashMap<String, String>,
    val attributes: LinkedHashMap<String, String>,
    val explicitOrder: List<String>,
    val children: LinkedHashMap<String, Any?>,
    val pureText: String?,
    val mixedText: List<IndexedTextSegment>,
)

private data class IndexedTextSegment(
    val index: Int,
    val value: String,
)
