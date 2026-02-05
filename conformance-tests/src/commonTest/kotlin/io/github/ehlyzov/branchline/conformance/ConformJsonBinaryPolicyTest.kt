package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.contract.ContractCoercion
import io.github.ehlyzov.branchline.contract.FieldConstraint
import io.github.ehlyzov.branchline.contract.SchemaRequirement
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.formatCanonicalJson
import io.github.ehlyzov.branchline.json.formatJsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConformJsonBinaryPolicyTest {

    @Test
    fun json_output_encodes_bytes_as_base64() {
        val value = mapOf("data" to byteArrayOf(1, 2, 3, 4))
        val output = formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.SAFE)
        assertEquals("""{"data":"AQIDBA=="}""", output)
    }

    @Test
    fun canonical_output_encodes_bytes_as_base64() {
        val value = mapOf("data" to byteArrayOf(1))
        val output = formatCanonicalJson(value, JsonNumberMode.SAFE)
        assertEquals("""{"data":"AQ=="}""", output)
    }

    @Test
    fun base64_helpers_round_trip_bytes() {
        val program = """
            TRANSFORM T {
                LET bytes = BASE64_DECODE("AQID");
                OUTPUT { bytes: bytes, base64: BASE64_ENCODE(bytes) }
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val result = runner(emptyMap()) as Map<*, *>
        val bytes = result["bytes"] as ByteArray
        assertTrue(bytes.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals("AQID", result["base64"])
    }

    @Test
    fun contract_coercion_decodes_base64_strings() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "payload" to FieldConstraint(
                    required = true,
                    shape = ValueShape.Bytes,
                    sourceSpans = emptyList(),
                ),
            ),
            open = true,
            dynamicAccess = emptyList(),
        )
        val input = mapOf("payload" to "AQID")
        val output = ContractCoercion.coerceInputBytes(requirement, input)
        val payload = output["payload"] as ByteArray
        assertTrue(payload.contentEquals(byteArrayOf(1, 2, 3)))
    }
}
