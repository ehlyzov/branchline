package io.github.ehlyzov.branchline.cli

public expect fun readTextFile(path: String): String

public expect fun writeTextFile(path: String, contents: String)

public expect fun appendTextFile(path: String, contents: String)

public expect fun readStdin(): String

public expect fun printError(message: String)

public expect fun printTrace(message: String)

public expect fun parseXmlInput(text: String): Map<String, Any?>

public expect fun getEnv(name: String): String?

public expect fun getWorkingDirectory(): String

public expect fun isFile(path: String): Boolean

public expect fun isDirectory(path: String): Boolean

public expect fun listFilesRecursive(path: String): List<String>

public expect fun relativePath(base: String, path: String): String

public expect fun fileName(path: String): String

public expect fun <T, R> parallelMap(limit: Int, items: List<T>, block: (T) -> R): List<R>
