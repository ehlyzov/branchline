package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.cli.parseXmlInput
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformXmlMappingTest {

    @Test
    fun xml_mapping_handles_attributes_and_repeated_siblings() {
        val xml = """<row id="7"><name>A</name><name>B</name></row>"""
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        assertEquals("7", row["@id"])
        val names = row["name"] as List<*>
        val first = names[0] as Map<*, *>
        val second = names[1] as Map<*, *>
        assertEquals("A", first["$"])
        assertEquals("B", second["$"])
    }

    @Test
    fun xml_mapping_handles_mixed_text_segments() {
        val xml = """<row>pre<item k="1">A</item>post</row>"""
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        val item = row["item"] as Map<*, *>
        assertEquals("pre", row["$1"])
        assertEquals("post", row["$2"])
        assertEquals("1", item["@k"])
        assertEquals("A", item["$"])
    }

    @Test
    fun xml_mapping_returns_empty_string_for_empty_elements() {
        val xml = """<row><empty/></row>"""
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        assertEquals("", row["empty"])
    }

    @Test
    fun xml_mapping_uses_dollar_key_for_pure_text() {
        val xml = """<row>  text  </row>"""
        val parsed = parseXmlInput(xml)
        val row = parsed["row"] as Map<*, *>
        assertEquals("text", row["$"])
    }
}
