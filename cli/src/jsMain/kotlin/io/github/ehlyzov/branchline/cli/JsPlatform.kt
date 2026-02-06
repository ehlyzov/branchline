@file:Suppress("UnsafeCastFromDynamic")

package io.github.ehlyzov.branchline.cli

import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.js.json

private val fs: dynamic = js("require('fs')")
private val pathModule: dynamic = js("require('path')")
private fun newXmlParser(): dynamic = js("(function(){var parserModule=require('fast-xml-parser');return new parserModule.XMLParser({preserveOrder:true,ignoreAttributes:false,attributeNamePrefix:'@',textNodeName:'#text',trimValues:true,parseTagValue:false,parseAttributeValue:false,ignoreDeclaration:true});})()")

public actual fun readTextFile(path: String): String =
    fs.readFileSync(path, "utf8") as String

public actual fun writeTextFile(path: String, contents: String) {
    val dir = pathModule.dirname(path)
    if (dir != null && dir as String != "") {
        fs.mkdirSync(dir, json("recursive" to true))
    }
    fs.writeFileSync(path, contents, "utf8")
}

public actual fun appendTextFile(path: String, contents: String) {
    fs.appendFileSync(path, contents, "utf8")
}

public actual fun readStdin(): String {
    return fs.readFileSync(0, "utf8") as String
}

public actual fun printError(message: String) {
    console.error(message)
}

public actual fun printTrace(message: String) {
    console.error(message)
}

public actual fun parseXmlInput(text: String): Map<String, Any?> {
    if (text.trim().isEmpty()) return emptyMap()
    val parsed = newXmlParser().parse(text)
    val root = parseOrderedXmlRoot(parsed) ?: return emptyMap()
    return mapXmlInput(root)
}

public actual fun getEnv(name: String): String? {
    val process: dynamic = js("process")
    val value = process.env[name]
    return if (jsTypeOf(value) == "undefined") null else value as String
}

public actual fun getWorkingDirectory(): String {
    val process: dynamic = js("process")
    return process.cwd() as String
}

public actual fun isFile(path: String): Boolean = try {
    fs.statSync(path).isFile() as Boolean
} catch (ex: dynamic) {
    false
}

public actual fun isDirectory(path: String): Boolean = try {
    fs.statSync(path).isDirectory() as Boolean
} catch (ex: dynamic) {
    false
}

public actual fun listFilesRecursive(path: String): List<String> {
    if (!isDirectory(path)) return emptyList()
    val results = ArrayList<String>()
    fun walk(dir: String) {
        val entries = fs.readdirSync(dir, json("withFileTypes" to true)) as Array<dynamic>
        for (entry in entries) {
            val name = entry.name as String
            val fullPath = pathModule.join(dir, name) as String
            when {
                entry.isDirectory() as Boolean -> walk(fullPath)
                entry.isFile() as Boolean -> results.add(fullPath)
            }
        }
    }
    walk(path)
    return results
}

public actual fun relativePath(base: String, path: String): String =
    pathModule.relative(base, path) as String

public actual fun fileName(path: String): String = pathModule.basename(path) as String

public actual fun <T, R> parallelMap(limit: Int, items: List<T>, block: (T) -> R): List<R> =
    items.map(block)

private fun parseOrderedXmlRoot(value: dynamic): XmlElementNode? {
    val entries = asDynamicArray(value) ?: return null
    for (entry in entries) {
        val element = parseOrderedXmlEntry(entry)
        if (element != null) {
            return element
        }
    }
    return null
}

private fun parseOrderedXmlEntry(entry: dynamic): XmlElementNode? {
    if (entry == null || jsTypeOf(entry) == "undefined") return null
    val keys = dynamicKeys(entry)
    var elementName: String? = null
    for (key in keys) {
        if (key == ":@" || key == "#text") continue
        elementName = key
        break
    }
    if (elementName == null) return null
    val parsedAttributes = parseOrderedXmlAttributes(entry[":@"])
    val children = parseOrderedXmlChildren(entry[elementName])
    return XmlElementNode(
        name = elementName,
        attributes = parsedAttributes.attributes,
        namespaces = parsedAttributes.namespaces,
        children = children,
    )
}

private fun parseOrderedXmlChildren(value: dynamic): List<XmlNodeChild> {
    val entries = asDynamicArray(value) ?: return emptyList()
    val children = ArrayList<XmlNodeChild>(entries.size)
    for (entry in entries) {
        if (entry == null || jsTypeOf(entry) == "undefined") continue
        val text = dynamicString(entry["#text"])
        if (text != null) {
            children += XmlNodeChild.Text(text)
            continue
        }
        val element = parseOrderedXmlEntry(entry)
        if (element != null) {
            children += XmlNodeChild.Element(element)
        }
    }
    return children
}

private fun parseOrderedXmlAttributes(value: dynamic): ParsedXmlAttributes {
    val attributes = LinkedHashMap<String, String>()
    val namespaces = LinkedHashMap<String, String>()
    if (value == null || jsTypeOf(value) == "undefined") {
        return ParsedXmlAttributes(
            attributes = attributes,
            namespaces = namespaces,
        )
    }
    val keys = dynamicKeys(value)
    for (key in keys) {
        val attrName = if (key.startsWith("@")) key.substring(1) else key
        val attrValue = dynamicString(value[key]).orEmpty()
        when {
            attrName == "xmlns" -> namespaces["$"] = attrValue
            attrName.startsWith("xmlns:") -> namespaces[attrName.substringAfter("xmlns:")] = attrValue
            else -> attributes[attrName] = attrValue
        }
    }
    return ParsedXmlAttributes(
        attributes = attributes,
        namespaces = namespaces,
    )
}

private data class ParsedXmlAttributes(
    val attributes: LinkedHashMap<String, String>,
    val namespaces: LinkedHashMap<String, String>,
)

private fun asDynamicArray(value: dynamic): Array<dynamic>? {
    if (value == null || jsTypeOf(value) == "undefined") return null
    return if (js("Array.isArray")(value) as Boolean) {
        value as Array<dynamic>
    } else {
        null
    }
}

private fun dynamicKeys(value: dynamic): Array<String> {
    if (value == null || jsTypeOf(value) == "undefined") return emptyArray()
    return js("Object.keys")(value) as Array<String>
}

private fun dynamicString(value: dynamic): String? {
    if (value == null || jsTypeOf(value) == "undefined") return null
    return value.toString()
}
