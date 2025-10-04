@file:Suppress("UnsafeCastFromDynamic")

package io.branchline.cli

import kotlin.js.Json
import kotlin.js.json

private val fs: dynamic = js("require('fs')")
private val pathModule: dynamic = js("require('path')")

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
    throw CliException("XML input is not supported on the JS CLI yet")
}
