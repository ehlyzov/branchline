package v2.runtime.io

import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("UnsafeCastFromDynamic")
class PlatformIOJsNodeTest {
    @Test
    fun write_and_read_file() {
        val path = "build/tmp/js-io-test.txt"
        val content = "hello-js"
        writeText(path, content)
        val fs = js("require('fs')")
        val read = fs.readFileSync(path, "utf8") as String
        assertEquals(content, read)
    }
}

