package v2.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("UnsafeCastFromDynamic")
class PlatformIOJsTest {
    @Test
    fun write_and_read_file() {
        val isNode = js("typeof process !== 'undefined' && process.versions && process.versions.node != null") as Boolean
        if (!isNode) {
            return
        }
        val path = "build/tmp/js-io-test.txt"
        val content = "hello-js"
        writeText(path, content)
        val fs = js("require('fs')")
        val read = fs.readFileSync(path, "utf8") as String
        assertEquals(content, read)
    }
}

