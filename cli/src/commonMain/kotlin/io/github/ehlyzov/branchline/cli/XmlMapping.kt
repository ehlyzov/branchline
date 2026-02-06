package io.github.ehlyzov.branchline.cli

internal data class XmlElementNode(
    val name: String,
    val attributes: LinkedHashMap<String, String>,
    val namespaces: LinkedHashMap<String, String>,
    val children: List<XmlNodeChild>,
)

internal sealed interface XmlNodeChild {
    data class Element(val value: XmlElementNode) : XmlNodeChild

    data class Text(val value: String) : XmlNodeChild
}

internal fun mapXmlInput(root: XmlElementNode): Map<String, Any?> {
    return linkedMapOf(root.name to mapXmlElement(root))
}

private fun mapXmlElement(node: XmlElementNode): Any? {
    val result = LinkedHashMap<String, Any?>()
    if (node.namespaces.isNotEmpty()) {
        result["@xmlns"] = LinkedHashMap(node.namespaces)
    }
    for ((name, value) in node.attributes) {
        result["@$name"] = value
    }
    val textSegments = ArrayList<String>()
    var hasElementChildren = false
    for (child in node.children) {
        when (child) {
            is XmlNodeChild.Element -> {
                hasElementChildren = true
                appendElementValue(result, child.value.name, mapXmlElement(child.value))
            }

            is XmlNodeChild.Text -> {
                val normalized = child.value.trim()
                if (normalized.isNotEmpty()) {
                    textSegments += normalized
                }
            }
        }
    }
    if (hasElementChildren) {
        appendMixedText(result, textSegments)
    } else if (textSegments.isNotEmpty()) {
        appendPureText(result, textSegments)
    }
    return if (result.isEmpty()) {
        ""
    } else {
        result
    }
}

private fun appendElementValue(
    result: LinkedHashMap<String, Any?>,
    name: String,
    value: Any?,
) {
    val existing = result[name]
    if (existing == null) {
        result[name] = value
        return
    }
    if (existing is MutableList<*>) {
        @Suppress("UNCHECKED_CAST")
        (existing as MutableList<Any?>).add(value)
        return
    }
    result[name] = mutableListOf(existing, value)
}

private fun appendMixedText(
    result: LinkedHashMap<String, Any?>,
    segments: List<String>,
) {
    var index = 1
    for (segment in segments) {
        result["${'$'}$index"] = segment
        index += 1
    }
}

private fun appendPureText(
    result: LinkedHashMap<String, Any?>,
    segments: List<String>,
) {
    val value = if (segments.size == 1) {
        segments[0]
    } else {
        segments.joinToString("")
    }
    result["$"] = value
    result["#text"] = value
}
