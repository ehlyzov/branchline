package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.SharedDecl
import io.github.ehlyzov.branchline.SharedKind
import io.github.ehlyzov.branchline.std.DEFAULT_SHARED_KEY
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.SharedStoreSync

enum class SharedInputKind { FILE, DIR, GLOB }

enum class SharedInputFormat { JSON, XML, TEXT;
    companion object {
        fun parse(value: String): SharedInputFormat = when (value.lowercase()) {
            "json" -> JSON
            "xml" -> XML
            "text" -> TEXT
            else -> throw CliException("Unknown shared format '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

enum class SharedKeyMode { RELATIVE, BASENAME, CUSTOM;
    companion object {
        fun parse(value: String): SharedKeyMode = when (value.lowercase()) {
            "relative" -> RELATIVE
            "basename" -> BASENAME
            "custom" -> CUSTOM
            else -> throw CliException("Unknown shared key mode '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

data class SharedInputSpec(
    val kind: SharedInputKind,
    val resource: String,
    val path: String,
    val customKey: String? = null,
)

data class SharedOptions(
    val inputs: List<SharedInputSpec> = emptyList(),
    val format: SharedInputFormat = SharedInputFormat.TEXT,
    val keyMode: SharedKeyMode = SharedKeyMode.RELATIVE,
)

data class SharedEntry(
    val resource: String,
    val key: String,
    val path: String,
)

data class SharedSeedResult(
    val entries: List<SharedEntry>,
) {
    fun entriesFor(resource: String): List<SharedEntry> = entries.filter { it.resource == resource }
}

fun seedSharedStore(
    options: SharedOptions,
    sharedDecls: List<SharedDecl>,
    store: SharedStore,
): SharedSeedResult {
    if (options.inputs.isEmpty()) return SharedSeedResult(emptyList())
    val sync = store as? SharedStoreSync ?: throw CliException(
        "Shared store does not support synchronous writes",
        kind = CliErrorKind.RUNTIME,
    )
    val declsByName = sharedDecls.associateBy { it.name }
    val entries = mutableListOf<SharedEntry>()
    for (spec in options.inputs) {
        val decl = declsByName[spec.resource] ?: throw CliException(
            "Shared resource '${spec.resource}' must be declared in the script",
            kind = CliErrorKind.USAGE,
        )
        val kind = when (decl.kind) {
            SharedKind.SINGLE -> SharedResourceKind.SINGLE
            SharedKind.MANY -> SharedResourceKind.MANY
        }
        val files = resolveSharedPaths(spec)
        if (files.isEmpty()) {
            throw CliException("No files found for shared ${spec.resource} (${spec.path})", kind = CliErrorKind.INPUT)
        }
        for (path in files) {
            val key = deriveSharedKey(spec, path, options.keyMode)
            val value = parseSharedValue(path, options.format)
            when (kind) {
                SharedResourceKind.SINGLE -> {
                    val ok = sync.setOnceSync(spec.resource, key, value)
                    if (!ok) {
                        throw CliException(
                            "Shared resource '${spec.resource}' already contains key '$key'",
                            kind = CliErrorKind.RUNTIME,
                        )
                    }
                }
                SharedResourceKind.MANY -> sync.putSync(spec.resource, key, value)
            }
            entries += SharedEntry(spec.resource, key, path)
        }
    }
    return SharedSeedResult(entries)
}

private fun resolveSharedPaths(spec: SharedInputSpec): List<String> = when (spec.kind) {
    SharedInputKind.FILE -> listOf(spec.path)
    SharedInputKind.DIR -> listFilesRecursive(spec.path).sorted()
    SharedInputKind.GLOB -> expandGlob(spec.path)
}

private fun parseSharedValue(path: String, format: SharedInputFormat): Any? {
    val text = readTextFileOrThrow(path)
    return when (format) {
        SharedInputFormat.TEXT -> text
        SharedInputFormat.JSON -> parseJsonValue(text)
        SharedInputFormat.XML -> parseXmlInput(text)
    }
}

private fun deriveSharedKey(spec: SharedInputSpec, path: String, mode: SharedKeyMode): String {
    val key = when (mode) {
        SharedKeyMode.RELATIVE -> normalizeKey(
            relativePath(resolveKeyBase(spec), path)
        )
        SharedKeyMode.BASENAME -> fileName(path)
        SharedKeyMode.CUSTOM -> {
            val custom = spec.customKey ?: throw CliException(
                "Shared key mode custom requires '<resource>=<key>=<path>'",
                kind = CliErrorKind.USAGE,
            )
            when (spec.kind) {
                SharedInputKind.FILE -> custom
                SharedInputKind.DIR, SharedInputKind.GLOB -> {
                    val suffix = normalizeKey(relativePath(resolveKeyBase(spec), path))
                    if (suffix.isBlank()) custom else "$custom/$suffix"
                }
            }
        }
    }
    return if (key.isBlank()) DEFAULT_SHARED_KEY else key
}

private fun resolveKeyBase(spec: SharedInputSpec): String = when (spec.kind) {
    SharedInputKind.FILE -> getWorkingDirectory()
    SharedInputKind.DIR -> spec.path
    SharedInputKind.GLOB -> globRoot(spec.path)
}

private fun normalizeKey(value: String): String = value.replace('\\', '/')

private fun expandGlob(pattern: String): List<String> {
    val normalizedPattern = normalizeKey(pattern)
    if (!normalizedPattern.contains('*') && !normalizedPattern.contains('?') && !normalizedPattern.contains('[')) {
        return if (isFile(pattern)) listOf(pattern) else emptyList()
    }
    val root = globRoot(pattern)
    if (!isDirectory(root)) return emptyList()
    val regex = globToRegex(normalizedPattern)
    val candidates = listFilesRecursive(root)
    return candidates.map { it to normalizeKey(it) }
        .filter { (_, normalized) -> regex.matches(normalized) }
        .map { (path, _) -> path }
        .sorted()
}

private fun globRoot(pattern: String): String {
    val normalized = normalizeKey(pattern)
    val wildcardIndex = normalized.indexOfFirst { it == '*' || it == '?' || it == '[' }
    if (wildcardIndex == -1) {
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash == -1) "." else normalized.substring(0, lastSlash)
    }
    val prefix = normalized.substring(0, wildcardIndex)
    val trimmed = prefix.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    return if (lastSlash == -1) "." else trimmed.substring(0, lastSlash)
}

private fun globToRegex(glob: String): Regex {
    val out = StringBuilder()
    var i = 0
    while (i < glob.length) {
        val ch = glob[i]
        when (ch) {
            '*' -> {
                val isDouble = i + 1 < glob.length && glob[i + 1] == '*'
                if (isDouble) {
                    out.append(".*")
                    i += 2
                } else {
                    out.append("[^/]*")
                    i += 1
                }
            }
            '?' -> {
                out.append("[^/]")
                i += 1
            }
            '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> {
                out.append("\\").append(ch)
                i += 1
            }
            else -> {
                out.append(ch)
                i += 1
            }
        }
    }
    return Regex("^$out$")
}
