package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.contract.ContractCoercion
import io.github.ehlyzov.branchline.contract.FieldConstraint
import io.github.ehlyzov.branchline.contract.SchemaRequirement
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.formatCanonicalJson
import io.github.ehlyzov.branchline.json.formatJsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConformJsonSetSerializationTest {

    @Test
    fun json_output_sorts_sets_by_type_rank() {
        val value = setOf<Any?>(true, "b", 2, null, false, "a")
        val output = formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.SAFE)
        assertEquals("""[null,false,true,2,"a","b"]""", output)
    }

    @Test
    fun canonical_output_sorts_sets_numerically() {
        val value = setOf(3, 1, 2)
        val output = formatCanonicalJson(value, JsonNumberMode.SAFE)
        assertEquals("""[1,2,3]""", output)
    }

    @Test
    fun json_output_sorts_sets_by_canonical_objects() {
        val value = setOf(
            mapOf("b" to 1),
            mapOf("a" to 1),
        )
        val output = formatJsonValue(value, pretty = false, numberMode = JsonNumberMode.SAFE)
        assertEquals("""[{"a":1},{"b":1}]""", output)
    }

    @Test
    fun contract_coercion_converts_lists_to_sets() {
        val requirement = SchemaRequirement(
            fields = linkedMapOf(
                "tags" to FieldConstraint(
                    required = true,
                    shape = ValueShape.SetShape(ValueShape.TextShape),
                    sourceSpans = emptyList(),
                ),
            ),
            open = true,
            dynamicAccess = emptyList(),
            requiredAnyOf = emptyList(),
        )
        val input = mapOf("tags" to listOf("b", "a", "b"))
        val output = ContractCoercion.coerceInputBytes(requirement, input)
        val tags = output["tags"] as Set<*>
        assertEquals(2, tags.size)
        assertTrue(tags.contains("a"))
        assertTrue(tags.contains("b"))
    }
}
