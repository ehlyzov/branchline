package io.github.ehlyzov.branchline.cli

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CliVersionTest {
    @Test
    fun generatedVersionMatchesGradleAndPackagedNodeCli() {
        val expected = System.getProperty("branchline.expectedVersion")
            ?: error("Run with Gradle so the canonical version is supplied")
        assertEquals("v$expected", CliVersion.CURRENT)
        val cwd = Paths.get("").toAbsolutePath()
        val module = if (cwd.endsWith("cli")) cwd else cwd.resolve("cli")
        val bundle = module.resolve("build/cliJsPackage")
        val pkg = Json.parseToJsonElement(Files.readString(bundle.resolve("package.json"))).jsonObject
        assertEquals(expected, pkg.getValue("version").jsonPrimitive.content)
        val node = System.getenv("NODE_BINARY")?.takeIf { it.isNotBlank() } ?: "node"
        val process = ProcessBuilder(node, bundle.resolve("bin/bl.cjs").toString(), "--version")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        assertEquals(0, process.waitFor(), output)
        assertEquals("Branchline CLI v$expected", output)
    }
}
