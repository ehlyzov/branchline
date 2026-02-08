package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Token
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public object ContractJsonRenderer {
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json

    public fun renderInput(
        contract: TransformContract,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = renderSchemaRequirement(contract.input, includeSpans, pretty)

    public fun renderOutput(
        contract: TransformContract,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = renderSchemaGuarantee(contract.output, includeSpans, pretty)

    public fun inputElement(contract: TransformContract, includeSpans: Boolean): JsonElement =
        encodeRequirement(contract.input, includeSpans)

    public fun outputElement(contract: TransformContract, includeSpans: Boolean): JsonElement =
        encodeGuarantee(contract.output, includeSpans)

    public fun renderSchemaRequirement(
        requirement: SchemaRequirement,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeRequirement(requirement, includeSpans), pretty)

    public fun renderSchemaGuarantee(
        guarantee: SchemaGuarantee,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeGuarantee(guarantee, includeSpans), pretty)

    private fun encodeElement(element: JsonElement, pretty: Boolean): String {
        val json = if (pretty) prettyJson else compactJson
        return json.encodeToString(JsonElement.serializer(), element)
    }

    private fun encodeRequirement(requirement: SchemaRequirement, includeSpans: Boolean): JsonElement {
        val fields = LinkedHashMap<String, JsonElement>()
        requirement.fields.forEach { (name, constraint) ->
            fields[name] = encodeFieldConstraint(constraint, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "fields" to JsonObject(fields),
            "open" to JsonPrimitive(requirement.open),
            "dynamicAccess" to encodeDynamicAccess(requirement.dynamicAccess),
            "requiredAnyOf" to encodeRequiredAnyOf(requirement.requiredAnyOf),
        )
        return JsonObject(payload)
    }

    private fun encodeGuarantee(guarantee: SchemaGuarantee, includeSpans: Boolean): JsonElement {
        val fields = LinkedHashMap<String, JsonElement>()
        guarantee.fields.forEach { (name, shape) ->
            fields[name] = encodeFieldShape(shape, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "fields" to JsonObject(fields),
            "mayEmitNull" to JsonPrimitive(guarantee.mayEmitNull),
            "dynamicFields" to encodeDynamicFields(guarantee.dynamicFields),
        )
        return JsonObject(payload)
    }

    private fun encodeFieldConstraint(constraint: FieldConstraint, includeSpans: Boolean): JsonElement {
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(constraint.required),
            "shape" to encodeValueShape(constraint.shape, includeSpans),
        )
        if (includeSpans && constraint.sourceSpans.isNotEmpty()) {
            payload["sourceSpans"] = JsonArray(constraint.sourceSpans.map(::encodeToken))
        }
        return JsonObject(payload)
    }

    private fun encodeFieldShape(shape: FieldShape, includeSpans: Boolean): JsonElement {
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(shape.required),
            "shape" to encodeValueShape(shape.shape, includeSpans),
            "origin" to JsonPrimitive(shape.origin.name),
        )
        return JsonObject(payload)
    }

    private fun encodeValueShape(shape: ValueShape, includeSpans: Boolean): JsonElement = when (shape) {
        ValueShape.Unknown -> JsonObject(mapOf("type" to JsonPrimitive("any")))
        ValueShape.Null -> JsonObject(mapOf("type" to JsonPrimitive("null")))
        ValueShape.BooleanShape -> JsonObject(mapOf("type" to JsonPrimitive("boolean")))
        ValueShape.NumberShape -> JsonObject(mapOf("type" to JsonPrimitive("number")))
        ValueShape.Bytes -> JsonObject(mapOf("type" to JsonPrimitive("bytes")))
        ValueShape.TextShape -> JsonObject(mapOf("type" to JsonPrimitive("text")))
        is ValueShape.ArrayShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("array"),
                "element" to encodeValueShape(shape.element, includeSpans),
            )
        )
        is ValueShape.SetShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("set"),
                "element" to encodeValueShape(shape.element, includeSpans),
            )
        )
        is ValueShape.ObjectShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "closed" to JsonPrimitive(shape.closed),
                "schema" to encodeGuarantee(shape.schema, includeSpans),
            )
        )
        is ValueShape.Union -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("union"),
                "options" to JsonArray(shape.options.map { option -> encodeValueShape(option, includeSpans) }),
            )
        )
    }

    private fun encodeDynamicAccess(items: List<DynamicAccess>): JsonElement =
        JsonArray(items.map { item -> encodeDynamicItem(item.path, item.valueShape, item.reason) })

    private fun encodeRequiredAnyOf(groups: List<RequiredAnyOfGroup>): JsonElement =
        JsonArray(
            groups.map { group ->
                JsonArray(group.alternatives.map { path -> encodeAccessPath(path) })
            }
        )

    private fun encodeDynamicFields(items: List<DynamicField>): JsonElement =
        JsonArray(items.map { item -> encodeDynamicItem(item.path, item.valueShape, item.reason) })

    private fun encodeDynamicItem(path: AccessPath, shape: ValueShape?, reason: DynamicReason): JsonElement {
        val payload = linkedMapOf<String, JsonElement>(
            "path" to encodeAccessPath(path),
            "reason" to JsonPrimitive(reason.name),
        )
        if (shape != null) {
            payload["valueShape"] = encodeValueShape(shape, includeSpans = false)
        }
        return JsonObject(payload)
    }

    private fun encodeAccessPath(path: AccessPath): JsonElement =
        JsonArray(path.segments.map { segment -> encodeAccessSegment(segment) })

    private fun encodeAccessSegment(segment: AccessSegment): JsonElement = when (segment) {
        is AccessSegment.Field -> JsonPrimitive(segment.name)
        is AccessSegment.Index -> JsonPrimitive(segment.index)
        AccessSegment.Dynamic -> JsonPrimitive("*")
    }

    private fun encodeToken(token: Token): JsonElement = JsonObject(
        mapOf(
            "type" to JsonPrimitive(token.type.name),
            "lexeme" to JsonPrimitive(token.lexeme),
            "line" to JsonPrimitive(token.line),
            "column" to JsonPrimitive(token.column),
        )
    )
}
