package io.github.ehlyzov.branchline.runtime.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

actual fun writeText(path: String, text: String) {
    val p = Path.of(path)
    val parent = p.parent
    if (parent != null) Files.createDirectories(parent)
    Files.writeString(p, text)
}

actual fun readText(path: String): String {
    val p = Path.of(path)
    return p.readText(charset = Charsets.UTF_8)
}