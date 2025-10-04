package io.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class XmlInputJsTest {
    @Test
    fun parseSimpleXml() {
        val xml = """
            <row>
              <name>XML-JS</name>
            </row>
        """.trimIndent()
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        assertEquals("XML-JS", row["name"])
    }
}

