package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.cli.collectInputConversionWarnings
import io.github.ehlyzov.branchline.cli.collectOutputConversionWarnings
import io.github.ehlyzov.branchline.cli.InputFormat
import io.github.ehlyzov.branchline.cli.OutputFormat
import io.github.ehlyzov.branchline.cli.WARN_JSON_BYTES_AS_BASE64
import io.github.ehlyzov.branchline.cli.WARN_JSON_CANONICAL_KEY_REORDER
import io.github.ehlyzov.branchline.cli.WARN_JSON_EXTENDED_PRECISION
import io.github.ehlyzov.branchline.cli.WARN_XML_COMMENTS_DROPPED
import io.github.ehlyzov.branchline.cli.WARN_XML_MIXED_CONTENT_ORDER_INPUT
import io.github.ehlyzov.branchline.cli.WARN_XML_MIXED_CONTENT_ORDER_OUTPUT
import io.github.ehlyzov.branchline.cli.WARN_XML_PROCESSING_INSTRUCTIONS_DROPPED
import io.github.ehlyzov.branchline.cli.parseXmlInput
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import kotlin.test.Test
import kotlin.test.assertTrue

class ConformConversionLossAuditTest {

    @Test
    fun xml_input_emits_mixed_content_and_dropped_node_warnings() {
        val xml = """<row><!--note--><?audit test?>pre<item>A</item>post</row>"""
        val parsed = parseXmlInput(xml)
        val warnings = collectInputConversionWarnings(
            rawText = xml,
            format = InputFormat.XML,
            parsed = parsed,
        )
        assertTrue(warnings.contains(WARN_XML_COMMENTS_DROPPED))
        assertTrue(warnings.contains(WARN_XML_PROCESSING_INSTRUCTIONS_DROPPED))
        assertTrue(warnings.contains(WARN_XML_MIXED_CONTENT_ORDER_INPUT))
    }

    @Test
    fun json_output_emits_base64_and_extended_precision_warnings() {
        val value = linkedMapOf<String, Any?>(
            "blob" to byteArrayOf(1, 2, 3),
            "big" to blBigIntParse("9007199254740993"),
        )
        val warnings = collectOutputConversionWarnings(
            value = value,
            format = OutputFormat.JSON_COMPACT,
            jsonNumberMode = JsonNumberMode.EXTENDED,
        )
        assertTrue(warnings.contains(WARN_JSON_BYTES_AS_BASE64))
        assertTrue(warnings.contains(WARN_JSON_EXTENDED_PRECISION))
    }

    @Test
    fun json_canonical_emits_key_reorder_warning_when_object_order_changes() {
        val value = linkedMapOf<String, Any?>(
            "b" to 1,
            "a" to 2,
        )
        val warnings = collectOutputConversionWarnings(
            value = value,
            format = OutputFormat.JSON_CANONICAL,
            jsonNumberMode = JsonNumberMode.SAFE,
        )
        assertTrue(warnings.contains(WARN_JSON_CANONICAL_KEY_REORDER))
    }

    @Test
    fun xml_output_emits_mixed_content_order_warning() {
        val value = mapOf(
            "row" to linkedMapOf<String, Any?>(
                "$1" to "pre",
                "item" to mapOf("$" to "A"),
                "$2" to "post",
            ),
        )
        val warnings = collectOutputConversionWarnings(
            value = value,
            format = OutputFormat.XML_COMPACT,
            jsonNumberMode = JsonNumberMode.SAFE,
        )
        assertTrue(warnings.contains(WARN_XML_MIXED_CONTENT_ORDER_OUTPUT))
    }
}
