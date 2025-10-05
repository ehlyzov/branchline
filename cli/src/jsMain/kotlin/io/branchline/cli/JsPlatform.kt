@file:Suppress("UnsafeCastFromDynamic")

package io.branchline.cli

import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.js.json

private val fs: dynamic = js("require('fs')")
private val pathModule: dynamic = js("require('path')")
private fun newXmlParser(): dynamic = js("(function(){var parserModule=require('fast-xml-parser');return new parserModule.XMLParser({ignoreAttributes:false,attributeNamePrefix:'@',textNodeName:'#text',trimValues:true,parseTagValue:false,parseAttributeValue:false});})()")

actual fun readTextFile(path: String): String =
    fs.readFileSync(path, "utf8") as String

actual fun writeTextFile(path: String, contents: String) {
    val dir = pathModule.dirname(path)
    if (dir != null && dir as String != "") {
        fs.mkdirSync(dir, json("recursive" to true))
    }
    fs.writeFileSync(path, contents, "utf8")
}

actual fun readStdin(): String {
    return fs.readFileSync(0, "utf8") as String
}

actual fun printError(message: String) {
    console.error(message)
}

actual fun parseXmlInput(text: String): Map<String, Any?> {
    if (text.trim().isEmpty()) return emptyMap()
    val parsed = newXmlParser().parse(text)
    val converted = dynamicToKotlin(parsed)
    if (converted is Map<*, *>) {
        @Suppress("UncheckedCast")
        return converted as Map<String, Any?>
    }
    return emptyMap()
}

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
