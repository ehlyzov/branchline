package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.Token

public class RuntimeErrorWithContext(
    public val token: Token,
    public val snippet: String?,
    cause: Throwable,
) : RuntimeException(cause.message ?: cause.toString(), cause)

internal fun renderRuntimeSnippet(source: String, token: Token, contextLines: Int = 1): String {
    if (source.isBlank()) return ""
    val lines = source.lines()
    if (lines.isEmpty()) return ""
    val lineIdx = (token.line - 1).coerceIn(0, lines.lastIndex)
    val start = (lineIdx - contextLines).coerceAtLeast(0)
    val end = (lineIdx + contextLines).coerceAtMost(lines.lastIndex)
    val lineNoWidth = (end + 1).toString().length
    val prefixLen = lineNoWidth + 3 // "NN | "
    val caretPos = (token.column - 1).coerceAtLeast(0)

    val builder = StringBuilder()
    for (i in start..end) {
        val lineNo = (i + 1).toString().padStart(lineNoWidth)
        builder.append(lineNo).append(" | ").append(lines[i]).appendLine()
        if (i == lineIdx) {
            builder.append(" ".repeat(prefixLen + caretPos)).append("^").appendLine()
        }
    }
    return builder.toString().trimEnd()
}
