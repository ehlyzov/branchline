package io.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class XmlInputTest {
    @Test
    fun parseSimpleXml() {
        val xml = """
            <row>
              <name>XML</name>
              <count>3</count>
            </row>
        """.trimIndent()
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        assertEquals("XML", row["name"])
        assertEquals("3", row["count"])
    }
}

