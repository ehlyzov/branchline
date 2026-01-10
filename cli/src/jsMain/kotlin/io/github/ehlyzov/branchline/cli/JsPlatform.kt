@file:Suppress("UnsafeCastFromDynamic")

package io.github.ehlyzov.branchline.cli

import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.js.json

private val fs: dynamic = js("require('fs')")
private val pathModule: dynamic = js("require('path')")
private fun newXmlParser(): dynamic = js("(function(){var parserModule=require('fast-xml-parser');return new parserModule.XMLParser({ignoreAttributes:false,attributeNamePrefix:'@',textNodeName:'#text',trimValues:true,parseTagValue:false,parseAttributeValue:false});})()")

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
    val converted = dynamicToKotlin(parsed)
    if (converted is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return converted as Map<String, Any?>
    }
    return emptyMap()
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

private fun dynamicToKotlin(value: dynamic): Any? {
    if (value == null || jsTypeOf(value) == "undefined") return null
    if (js("Array.isArray")(value) as Boolean) {
        val size = (value.length as Int?) ?: 0
        val list = ArrayList<Any?>(size)
        var index = 0
        while (index < size) {
            list.add(dynamicToKotlin(value[index]))
            index += 1
        }
        return list
    }
    return when (jsTypeOf(value)) {
        "string" -> value as String
        "number", "boolean" -> value.toString()
        "object" -> {
            val result = LinkedHashMap<String, Any?>()
            val keys = js("Object.keys")(value) as Array<String>
            for (key in keys) {
                result[key] = dynamicToKotlin(value[key])
            }
            result
        }
        else -> value.toString()
    }
}
