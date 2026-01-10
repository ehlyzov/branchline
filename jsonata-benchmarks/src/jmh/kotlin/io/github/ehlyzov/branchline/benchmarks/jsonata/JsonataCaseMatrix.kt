package io.github.ehlyzov.branchline.benchmarks.jsonata

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

private const val CASE_MATRIX_RESOURCE: String = "/jsonata-case-matrix.yaml"
private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())

public data class CaseMatrixEntry(
    val id: String,
    val jsonataExpression: String?,
    val inputJson: String?,
    val branchlineProgram: String?,
    val kotlinEvalId: String?,
    val expectedFailures: Map<String, String>,
)

public object JsonataCaseMatrix {
    public fun load(): List<CaseMatrixEntry> {
        val root = readCaseMatrixRoot()
        val casesNode = root.get("cases") ?: error("$CASE_MATRIX_RESOURCE must define cases.")
        require(casesNode.isArray) { "$CASE_MATRIX_RESOURCE cases must be a list." }
        val entries = ArrayList<CaseMatrixEntry>(casesNode.size())
        for (caseNode in casesNode) {
            entries.add(parseCaseEntry(caseNode))
        }
        return entries
    }
}

private fun readCaseMatrixRoot(): JsonNode {
    val resource = requireNotNull(JsonataCaseMatrix::class.java.getResource(CASE_MATRIX_RESOURCE)) {
        "Missing $CASE_MATRIX_RESOURCE on the classpath."
    }
    return yamlMapper.readTree(resource)
}

private fun parseCaseEntry(node: JsonNode): CaseMatrixEntry {
    val id = readRequiredText(node, "id")
    val jsonataExpression = readOptionalText(node, "jsonataExpression")
    val inputJson = readOptionalText(node, "inputJson")
    val branchlineProgram = readOptionalText(node, "branchlineProgram")
    val kotlinEvalId = readOptionalText(node, "kotlinEvalId")
    val expectedFailures = readExpectedFailures(node)
    return CaseMatrixEntry(
        id = id,
        jsonataExpression = jsonataExpression,
        inputJson = inputJson,
        branchlineProgram = branchlineProgram,
        kotlinEvalId = kotlinEvalId,
        expectedFailures = expectedFailures,
    )
}

private fun readExpectedFailures(node: JsonNode): Map<String, String> {
    val failuresNode = node.get("expectedFailures") ?: return emptyMap()
    require(failuresNode.isObject) { "expectedFailures must be a map." }
    val result = LinkedHashMap<String, String>()
    val fields = failuresNode.fields()
    while (fields.hasNext()) {
        val entry = fields.next()
        val reason = entry.value.asText()
        if (reason.isNotBlank()) {
            result[entry.key] = reason
        }
    }
    return result
}

private fun readRequiredText(node: JsonNode, field: String): String {
    val child = node.get(field) ?: error("Missing $field in case matrix entry.")
    val value = child.asText()
    require(value.isNotBlank()) { "$field must not be blank." }
    return value
}

private fun readOptionalText(node: JsonNode, field: String): String? {
    val child = node.get(field) ?: return null
    val value = child.asText()
    return value.takeIf { it.isNotBlank() }
}
