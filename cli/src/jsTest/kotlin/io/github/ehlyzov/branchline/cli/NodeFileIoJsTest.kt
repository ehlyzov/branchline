@file:Suppress("UnsafeCastFromDynamic")

package io.github.ehlyzov.branchline.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeFileIoJsTest {
    private val fs: dynamic = js("require('fs')")
    private val os: dynamic = js("require('os')")
    private val paths: dynamic = js("require('path')")

    @Test
    fun nativeFileErrorsBecomeStructuredIoFailuresForRealCliCommands() = inDirectory { dir ->
        val source = "$dir/main.bl"
        fs.writeFileSync(source, "TRANSFORM Main { OUTPUT { answer: 42 } }", "utf8")
        val blocked = "$dir/blocked"
        fs.writeFileSync(blocked, "ordinary file", "utf8")
        val commands = listOf(
            listOf("run", "$dir/absent.bl"),
            listOf("compile", "$dir/absent.bl"),
            listOf("exec", "$dir/absent.blc"),
            listOf("run", source, "--input", "$dir/absent.json"),
            listOf("compile", source, "--output", "$blocked/result.blc"),
        )
        for (command in commands) {
            val errors = ArrayList<String>()
            val originalError: dynamic = console.asDynamic().error
            try {
                console.asDynamic().error = { message: dynamic -> errors.add(message.toString()) }
                assertEquals(74, BranchlineCli.run(command + listOf("--error-format", "json"), PlatformKind.JS), command.toString())
            } finally {
                console.asDynamic().error = originalError
            }
            assertEquals(1, errors.size, command.toString())
            val error = Json.parseToJsonElement(errors.single()).jsonObject.getValue("error").jsonObject
            assertEquals("io", error.getValue("kind").jsonPrimitive.content)
            assertEquals(74, error.getValue("exitCode").jsonPrimitive.int)
        }
    }

    @Test
    fun readWritePreserveSuccessAndNormalizeNativeFailures() = inDirectory { dir ->
        val file = "$dir/nested/value.txt"
        writeTextFile(file, "first")
        appendTextFile(file, " second")
        assertEquals("first second", readTextFile(file))
        assertFailsWith<RuntimeException> { readTextFile("$dir/absent") }
        assertFailsWith<RuntimeException> { writeTextFile("$file/child", "blocked") }
    }

    @Test
    fun unexpectedNativeAndKotlinErrorsAreNotReclassified() {
        val native: dynamic = js("new TypeError('sentinel')")
        var caught = false
        try {
            withNodeFileErrors<Unit> { throw native }
        } catch (ex: dynamic) {
            caught = true
            assertTrue(ex === native)
        }
        assertTrue(caught)
        val kotlinError = IllegalArgumentException("sentinel")
        assertTrue(assertFailsWith<IllegalArgumentException> { withNodeFileErrors<Unit> { throw kotlinError } } === kotlinError)
    }

    private fun inDirectory(block: (String) -> Unit) {
        val dir = fs.mkdtempSync(paths.join(os.tmpdir(), "branchline node io ")) as String
        try {
            block(dir)
        } finally {
            fs.rmSync(dir, js("({recursive:true,force:true})"))
        }
    }
}
