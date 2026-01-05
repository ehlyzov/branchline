package io.github.ehlyzov.branchline.cli

sealed interface OutputPathSeg {
    data class Field(val name: String) : OutputPathSeg
    data class Index(val index: Int) : OutputPathSeg
}

fun parseOutputPath(path: String): List<OutputPathSeg> {
    if (path.isBlank()) return emptyList()
    val segs = mutableListOf<OutputPathSeg>()
    val buffer = StringBuilder()
    var idx = 0
    while (idx < path.length) {
        when (val ch = path[idx]) {
            '.' -> {
                flushOutputField(buffer, segs)
                idx += 1
            }
            '[' -> {
                flushOutputField(buffer, segs)
                val end = path.indexOf(']', idx + 1)
                if (end == -1) throw CliException("Output path '$path' has unterminated '['", kind = CliErrorKind.USAGE)
                val raw = path.substring(idx + 1, end).trim()
                if (raw.isBlank()) {
                    throw CliException("Output path '$path' has empty index", kind = CliErrorKind.USAGE)
                }
                val content = if (
                    (raw.startsWith("'") && raw.endsWith("'")) ||
                    (raw.startsWith("\"") && raw.endsWith("\""))
                ) {
                    raw.substring(1, raw.length - 1)
                } else {
                    raw
                }
                val seg = content.toIntOrNull()?.let { OutputPathSeg.Index(it) }
                    ?: OutputPathSeg.Field(content)
                segs += seg
                idx = end + 1
            }
            else -> {
                buffer.append(ch)
                idx += 1
            }
        }
    }
    flushOutputField(buffer, segs)
    return segs
}

private fun flushOutputField(buffer: StringBuilder, segs: MutableList<OutputPathSeg>) {
    if (buffer.isEmpty()) return
    segs += OutputPathSeg.Field(buffer.toString())
    buffer.setLength(0)
}

fun selectOutputByPath(value: Any?, path: String): Any? {
    val segs = parseOutputPath(path)
    var current = value
    for (seg in segs) {
        current = when (seg) {
            is OutputPathSeg.Field -> {
                val map = current as? Map<*, *> ?: return null
                val key = map.keys.firstOrNull { it?.toString() == seg.name }
                if (key == null) return null
                map[key]
            }
            is OutputPathSeg.Index -> {
                when (current) {
                    is List<*> -> current.getOrNull(seg.index)
                    is Map<*, *> -> current[seg.index]
                    else -> return null
                }
            }
        }
    }
    return current
}

fun formatOutputValue(value: Any?, pretty: Boolean, raw: Boolean): String {
    if (!raw) return formatJson(value, pretty = pretty)
    return when (value) {
        null -> ""
        is String -> value
        is Boolean -> value.toString()
        is Number -> value.toString()
        else -> formatJson(value, pretty = pretty)
    }
}
