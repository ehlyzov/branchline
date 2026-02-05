package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.json.formatCanonicalJson
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformJsonCanonicalOutputTest {
    @Test
    fun canonicalObjectOrdering() {
        val value = mapOf(
            "b" to 1,
            "aa" to 4,
            "a" to 2,
            "A" to 3,
        )
        val output = formatCanonicalJson(value)
        assertEquals("{\"A\":3,\"a\":2,\"aa\":4,\"b\":1}", output)
    }

    @Test
    fun canonicalNumberFormatting() {
        assertEquals("0", formatCanonicalJson(-0.0))
        assertEquals("1", formatCanonicalJson(1.0))
        assertEquals("10000000", formatCanonicalJson(1e7))
        assertEquals("1e21", formatCanonicalJson(1e21))
        assertEquals("1e-7", formatCanonicalJson(1e-7))
        assertEquals("0.000001", formatCanonicalJson(1e-6))
        assertEquals("1.2345", formatCanonicalJson(1.234500))
    }

    @Test
    fun canonicalStringEscaping() {
        val value = mapOf("line" to "a\nb")
        val output = formatCanonicalJson(value)
        assertEquals("{\"line\":\"a\\u000Ab\"}", output)
    }
}
