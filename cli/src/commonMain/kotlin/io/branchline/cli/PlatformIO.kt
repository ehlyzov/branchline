package io.branchline.cli

public expect fun readTextFile(path: String): String

public expect fun writeTextFile(path: String, contents: String)

public expect fun readStdin(): String

public expect fun printError(message: String)

public expect fun printTrace(message: String)

public expect fun parseXmlInput(text: String): Map<String, Any?>
