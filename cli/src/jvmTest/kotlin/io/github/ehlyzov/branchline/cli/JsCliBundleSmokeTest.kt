package io.github.ehlyzov.branchline.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsCliBundleSmokeTest {
    @Test
    fun runPackagedCliWithJsonInput() {
        val node = locateNodeBinary() ?: run {
            println("Skipping JS CLI smoke test: node executable not found")
            return
        }

        val cliBin = locateCliBinary()
        require(Files.exists(cliBin)) { "JS CLI bundle was not prepared at $cliBin" }

        val script = createTempFile(prefix = "branchline-cli", suffix = ".bl")
        val input = createTempFile(prefix = "branchline-cli", suffix = ".json")

        try {
            script.writeText(
                """
                TRANSFORM Hello {
                    OUTPUT { message: "Hello, " + input.name };
                }
                """.trimIndent(),
            )
            input.writeText("""{"name":"JS"}""")

            val process = ProcessBuilder(node, cliBin.toString(), script.toString(), "--input", input.toString())
                .directory(projectRoot().toFile())
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            assertEquals(0, exitCode, "CLI exited with $exitCode: $output")
            val pattern = Regex("\\\"message\\\"\\s*:\\s*\\\"Hello, JS\\\"")
            assertTrue(pattern.containsMatchIn(output), "Unexpected CLI output: $output")
        } finally {
            script.deleteIfExists()
            input.deleteIfExists()
        }
    }

    private fun locateCliBinary(): Path {
        val modulePath = projectRoot().resolve("cli")
        val candidate = modulePath.resolve(Paths.get("build", "cliJsPackage", "bin", "bl.cjs"))
        if (Files.exists(candidate)) {
            return candidate
        }
        return projectRoot().resolve(Paths.get("build", "cliJsPackage", "bin", "bl.cjs"))
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get("").toAbsolutePath()
        return if (cwd.endsWith("cli")) cwd.parent else cwd
    }

    private fun locateNodeBinary(): String? {
        val override = System.getenv("NODE_BINARY")?.takeIf { it.isNotBlank() }
        val candidates = buildList {
            if (override != null) add(override)
            add("node")
        }

        for (candidate in candidates) {
            if (isExecutable(candidate)) {
                return candidate
            }
        }

        return null
    }

    private fun isExecutable(name: String): Boolean {
        return try {
            val process = ProcessBuilder(name, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (ignored: Exception) {
            false
        }
    }
}
