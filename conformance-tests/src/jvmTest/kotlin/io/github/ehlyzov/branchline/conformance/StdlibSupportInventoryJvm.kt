package io.github.ehlyzov.branchline.conformance

import java.nio.file.Files
import java.nio.file.Path

internal actual fun readStdlibSupportInventory(): String {
    val guide = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("docs/language/stdlib-support.md") }
        .firstOrNull { Files.isRegularFile(it) }
        ?: error("Cannot locate docs/language/stdlib-support.md from ${Path.of("").toAbsolutePath()}")
    return Files.readString(guide)
}
