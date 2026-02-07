package io.github.ehlyzov.branchline.cli

import kotlin.collections.get
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
        val name = row["name"] as Map<*, *>
        val count = row["count"] as Map<*, *>
        assertEquals("XML", name["$"])
        assertEquals("3", count["$"])
    }
}
