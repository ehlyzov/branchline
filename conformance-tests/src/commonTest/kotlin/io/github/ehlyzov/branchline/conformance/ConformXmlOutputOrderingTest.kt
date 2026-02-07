package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.cli.formatXmlOutput
import io.github.ehlyzov.branchline.cli.formatOutputValue
import io.github.ehlyzov.branchline.cli.OutputFormat
import io.github.ehlyzov.branchline.json.JsonNumberMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConformXmlOutputOrderingTest {

    @Test
    fun xml_output_orders_attributes_and_children_deterministically() {
        val value = mapOf(
            "root" to linkedMapOf<String, Any?>(
                "@z" to "2",
                "@a" to "1",
                "beta" to listOf(
                    mapOf("$" to "b1"),
                    mapOf("$" to "b2"),
                ),
                "alpha" to mapOf("$" to "a"),
            ),
        )
        val xml = formatXmlOutput(value, pretty = false)
        assertEquals(
            "<root a=\"1\" z=\"2\"><alpha>a</alpha><beta>b1</beta><beta>b2</beta></root>",
            xml,
        )
    }

    @Test
    fun xml_output_honors_explicit_order_then_lexicographic_fallback() {
        val value = mapOf(
            "root" to linkedMapOf<String, Any?>(
                "@order" to listOf("beta", "alpha"),
                "gamma" to mapOf("$" to "g"),
                "alpha" to mapOf("$" to "a"),
                "delta" to mapOf("$" to "d"),
                "beta" to listOf(
                    mapOf("$" to "b1"),
                    mapOf("$" to "b2"),
                ),
            ),
        )
        val xml = formatXmlOutput(value, pretty = false)
        assertEquals(
            "<root><beta>b1</beta><beta>b2</beta><alpha>a</alpha><delta>d</delta><gamma>g</gamma></root>",
            xml,
        )
    }

    @Test
    fun xml_output_emits_namespaces_and_validates_prefixed_names() {
        val value = mapOf(
            "root" to linkedMapOf<String, Any?>(
                "@xmlns" to linkedMapOf(
                    "$" to "urn:default",
                    "x" to "urn:x",
                ),
                "@x:id" to "7",
                "x:item" to mapOf("$" to "value"),
            ),
        )
        val xml = formatXmlOutput(value, pretty = false)
        assertEquals(
            "<root x:id=\"7\" xmlns=\"urn:default\" xmlns:x=\"urn:x\"><x:item>value</x:item></root>",
            xml,
        )
    }

    @Test
    fun xml_output_rejects_undeclared_namespace_prefixes_in_strict_mode() {
        val value = mapOf(
            "root" to mapOf(
                "x:item" to mapOf("$" to "value"),
            ),
        )
        val ex = assertFailsWith<IllegalArgumentException> {
            formatXmlOutput(value, pretty = false)
        }
        assertTrue(ex.message?.contains("Undeclared XML namespace prefix 'x'") == true)
    }

    @Test
    fun xml_compact_output_format_uses_xml_serializer() {
        val value = mapOf(
            "root" to mapOf(
                "item" to mapOf("$" to "ok"),
            ),
        )
        val xml = formatOutputValue(
            value = value,
            format = OutputFormat.XML_COMPACT,
            raw = false,
            jsonNumberMode = JsonNumberMode.SAFE,
        )
        assertEquals("<root><item>ok</item></root>", xml)
    }
}
