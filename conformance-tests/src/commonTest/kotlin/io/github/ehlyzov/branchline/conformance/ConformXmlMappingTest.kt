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

    @Test
    fun xml_mapping_captures_default_and_prefixed_namespaces() {
        val xml = """<root xmlns="urn:default" xmlns:x="urn:items"><x:item x:id="7">value</x:item></root>"""
        val parsed = parseXmlInput(xml)
        val root = parsed["root"] as Map<*, *>
        val namespaces = root["@xmlns"] as Map<*, *>
        val item = root["x:item"] as Map<*, *>
        assertEquals("urn:default", namespaces["$"])
        assertEquals("urn:items", namespaces["x"])
        assertEquals("7", item["@x:id"])
        assertEquals("value", item["$"])
    }

    @Test
    fun xml_mapping_keeps_child_namespace_declarations_local() {
        val xml = """<root xmlns:x="urn:root"><x:item xmlns:y="urn:child" y:id="9"/></root>"""
        val parsed = parseXmlInput(xml)
        val root = parsed["root"] as Map<*, *>
        val rootNamespaces = root["@xmlns"] as Map<*, *>
        val item = root["x:item"] as Map<*, *>
        val childNamespaces = item["@xmlns"] as Map<*, *>
        assertEquals("urn:root", rootNamespaces["x"])
        assertEquals("urn:child", childNamespaces["y"])
        assertEquals("9", item["@y:id"])
    }
}
