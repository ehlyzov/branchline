package v2.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("UnsafeCastFromDynamic")
class PlatformIONodeGuardedTest {
    private fun isNode(): Boolean = js(
        "typeof process !== 'undefined' && process.versions && process.versions.node ? true : false"
    ) as Boolean

    @Test
    fun write_and_read_file_node_only() {
        if (!isNode()) return // skip in browser
        val path = "build/tmp/js-io-test.txt"
        val content = "hello-js"
        writeText(path, content)
        val read = readText(path)
        assertEquals(content, read)
    }
}
