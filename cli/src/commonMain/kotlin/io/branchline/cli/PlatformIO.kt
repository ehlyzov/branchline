package io.branchline.cli

expect fun readTextFile(path: String): String

expect fun writeTextFile(path: String, contents: String)

expect fun readStdin(): String

expect fun printError(message: String)

expect fun parseXmlInput(text: String): Map<String, Any?>
