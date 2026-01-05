package io.github.ehlyzov.branchline.cli

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.stream.Collectors
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

public actual fun readTextFile(path: String): String =
    Files.readString(Path.of(path), StandardCharsets.UTF_8)

public actual fun writeTextFile(path: String, contents: String) {
    val target = Path.of(path)
    val parent = target.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
    Files.writeString(target, contents, StandardCharsets.UTF_8)
}

public actual fun appendTextFile(path: String, contents: String) {
    val target = Path.of(path)
    val parent = target.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
    Files.writeString(
        target,
        contents,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
    )
}

public actual fun readStdin(): String {
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

public actual fun printError(message: String) {
    System.err.println(message)
}

public actual fun printTrace(message: String) {
    System.err.println(message)
}

public actual fun parseXmlInput(text: String): Map<String, Any?> {
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
                is List<*> -> existing.toMutableList().apply { add(value) }
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

public actual fun getEnv(name: String): String? = System.getenv(name)

public actual fun getWorkingDirectory(): String = System.getProperty("user.dir")

public actual fun isFile(path: String): Boolean = Files.isRegularFile(Path.of(path))

public actual fun isDirectory(path: String): Boolean = Files.isDirectory(Path.of(path))

public actual fun listFilesRecursive(path: String): List<String> {
    val root = Path.of(path)
    if (!Files.exists(root)) return emptyList()
    return Files.walk(root).use { stream ->
        stream.filter { Files.isRegularFile(it) }
            .map { it.toString() }
            .collect(Collectors.toList())
    }
}

public actual fun relativePath(base: String, path: String): String {
    val basePath = Path.of(base).toAbsolutePath().normalize()
    val target = Path.of(path).toAbsolutePath().normalize()
    return basePath.relativize(target).toString()
}

public actual fun fileName(path: String): String = Path.of(path).fileName.toString()

public actual fun <T, R> parallelMap(limit: Int, items: List<T>, block: (T) -> R): List<R> {
    if (limit <= 1 || items.size <= 1) return items.map(block)
    val pool = Executors.newFixedThreadPool(limit)
    try {
        val futures = items.map { item ->
            pool.submit(Callable { block(item) })
        }
        return futures.map { it.get() }
    } finally {
        pool.shutdown()
    }
}
