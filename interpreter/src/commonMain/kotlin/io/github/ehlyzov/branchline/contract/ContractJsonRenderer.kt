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

    public fun inputElement(contract: TransformContractV2, includeSpans: Boolean): JsonElement =
        encodeRequirementV2(contract.input, includeSpans)

    public fun outputElement(contract: TransformContractV2, includeSpans: Boolean): JsonElement =
        encodeGuaranteeV2(contract.output, includeSpans)

    public fun renderSchemaRequirementV2(
        requirement: RequirementSchemaV2,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeRequirementV2(requirement, includeSpans), pretty)

    public fun renderSchemaGuaranteeV2(
        guarantee: GuaranteeSchemaV2,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeGuaranteeV2(guarantee, includeSpans), pretty)

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

    private fun encodeRequirementV2(requirement: RequirementSchemaV2, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf(
            "version" to JsonPrimitive("v2"),
            "root" to encodeRequirementNodeV2(requirement.root, includeSpans),
            "requirements" to encodeRequirementExprListV2(requirement.requirements),
            "opaqueRegions" to encodeOpaqueRegions(requirement.opaqueRegions),
        ),
    )

    private fun encodeGuaranteeV2(guarantee: GuaranteeSchemaV2, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf(
            "version" to JsonPrimitive("v2"),
            "root" to encodeGuaranteeNodeV2(guarantee.root, includeSpans),
            "mayEmitNull" to JsonPrimitive(guarantee.mayEmitNull),
            "opaqueRegions" to encodeOpaqueRegions(guarantee.opaqueRegions),
        ),
    )

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

    private fun encodeRequirementNodeV2(node: RequirementNodeV2, includeSpans: Boolean): JsonElement {
        val children = LinkedHashMap<String, JsonElement>()
        node.children.forEach { (name, child) ->
            children[name] = encodeRequirementNodeV2(child, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(node.required),
            "shape" to encodeValueShape(node.shape, includeSpans),
            "open" to JsonPrimitive(node.open),
            "children" to JsonObject(children),
        )
        if (node.evidence.isNotEmpty()) {
            payload["evidence"] = encodeEvidence(node.evidence, includeSpans)
        }
        return JsonObject(payload)
    }

    private fun encodeGuaranteeNodeV2(node: GuaranteeNodeV2, includeSpans: Boolean): JsonElement {
        val children = LinkedHashMap<String, JsonElement>()
        node.children.forEach { (name, child) ->
            children[name] = encodeGuaranteeNodeV2(child, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(node.required),
            "shape" to encodeValueShape(node.shape, includeSpans),
            "open" to JsonPrimitive(node.open),
            "origin" to JsonPrimitive(node.origin.name),
            "children" to JsonObject(children),
        )
        if (node.evidence.isNotEmpty()) {
            payload["evidence"] = encodeEvidence(node.evidence, includeSpans)
        }
        return JsonObject(payload)
    }

    private fun encodeRequirementExprListV2(expressions: List<RequirementExprV2>): JsonElement =
        JsonArray(expressions.map(::encodeRequirementExprV2))

    private fun encodeRequirementExprV2(expr: RequirementExprV2): JsonElement = when (expr) {
        is RequirementExprV2.AllOf -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("allOf"),
                "children" to JsonArray(expr.children.map(::encodeRequirementExprV2)),
            ),
        )
        is RequirementExprV2.AnyOf -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("anyOf"),
                "children" to JsonArray(expr.children.map(::encodeRequirementExprV2)),
            ),
        )
        is RequirementExprV2.PathPresent -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("pathPresent"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
        is RequirementExprV2.PathNonNull -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("pathNonNull"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
    }

    private fun encodeOpaqueRegions(items: List<OpaqueRegionV2>): JsonElement =
        JsonArray(items.map { region ->
            JsonObject(
                linkedMapOf(
                    "path" to encodeAccessPath(region.path),
                    "reason" to JsonPrimitive(region.reason.name),
                ),
            )
        })

    private fun encodeEvidence(items: List<InferenceEvidenceV2>, includeSpans: Boolean): JsonElement =
        JsonArray(items.map { evidence ->
            val payload = linkedMapOf<String, JsonElement>(
                "ruleId" to JsonPrimitive(evidence.ruleId),
                "confidence" to JsonPrimitive(evidence.confidence),
            )
            if (evidence.notes != null) {
                payload["notes"] = JsonPrimitive(evidence.notes)
            }
            if (includeSpans && evidence.sourceSpans.isNotEmpty()) {
                payload["sourceSpans"] = JsonArray(evidence.sourceSpans.map(::encodeToken))
            }
            JsonObject(payload)
        })

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
