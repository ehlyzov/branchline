package io.github.ehlyzov.branchline.conformance

import java.nio.file.Files
import java.nio.file.Path

internal actual fun readGettingStartedGuide(): String {
    val guide = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("docs/guides/getting-started.md") }
        .firstOrNull { Files.isRegularFile(it) }
        ?: error("Cannot locate docs/guides/getting-started.md from ${Path.of("").toAbsolutePath()}")
    return Files.readString(guide)
}
