package io.branchline.cli

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

actual fun readTextFile(path: String): String =
    Files.readString(Path.of(path), StandardCharsets.UTF_8)

actual fun writeTextFile(path: String, contents: String) {
    val target = Path.of(path)
    val parent = target.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
    Files.writeString(target, contents, StandardCharsets.UTF_8)
}

actual fun readStdin(): String {
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    return buildString {
        var line: String? = reader.readLine()
        var first = true
        while (line != null) {
            if (!first) append('\n') else first = false
            append(line)
            line = reader.readLine()
        }
    }
}

actual fun printError(message: String) {
    System.err.println(message)
}

actual fun parseXmlInput(text: String): Map<String, Any?> {
    if (text.isBlank()) return emptyMap()
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isIgnoringComments = true
        isCoalescing = true
    }
    val builder = factory.newDocumentBuilder()
    val document = builder.parse(InputSource(StringReader(text)))
    val root = document.documentElement ?: return emptyMap()
    return mapOf(root.tagName to elementToValue(root))
}

private fun elementToValue(element: Element): Any? {
    val attributes = element.attributes
    val childNodes = element.childNodes
    val values = LinkedHashMap<String, Any?>()

    for (i in 0 until attributes.length) {
        val attr = attributes.item(i)
        values["@${attr.nodeName}"] = attr.nodeValue
    }

    var hasElementChildren = false
    for (i in 0 until childNodes.length) {
        val node = childNodes.item(i)
        if (node is Element) {
            hasElementChildren = true
            val value = elementToValue(node)
            val existing = values[node.tagName]
            values[node.tagName] = when (existing) {
                null -> value
                is MutableList<*> -> (existing as MutableList<Any?>).apply { add(value) }
                else -> mutableListOf(existing, value)
            }
        }
    }

    val textContent = element.textContent?.trim().orEmpty()
    val hasText = textContent.isNotEmpty()

    return when {
        !hasElementChildren && values.isEmpty() && hasText -> textContent
        values.isEmpty() -> emptyMap<String, Any?>()
        hasText -> values + ("#text" to textContent)
        else -> values
    }
}
