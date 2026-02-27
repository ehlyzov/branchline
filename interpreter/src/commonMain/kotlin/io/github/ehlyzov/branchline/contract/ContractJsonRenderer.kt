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

    public fun inputElement(contract: AnalysisContract, includeSpans: Boolean): JsonElement =
        encodeAnalysisRequirement(contract.input, includeSpans)

    public fun outputElement(contract: AnalysisContract, includeSpans: Boolean): JsonElement =
        encodeAnalysisGuarantee(contract.output, includeSpans)

    public fun inputElement(contract: TransformContract, includeSpans: Boolean): JsonElement =
        encodeRequirement(contract.input, includeSpans)

    public fun outputElement(contract: TransformContract, includeSpans: Boolean): JsonElement =
        encodeGuarantee(contract.output, includeSpans)

    public fun renderAnalysisRequirement(
        requirement: AnalysisRequirementSchema,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeAnalysisRequirement(requirement, includeSpans), pretty)

    public fun renderAnalysisGuarantee(
        guarantee: AnalysisGuaranteeSchema,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeAnalysisGuarantee(guarantee, includeSpans), pretty)

    public fun renderSchemaRequirement(
        requirement: RequirementSchema,
        includeSpans: Boolean,
        pretty: Boolean = true,
    ): String = encodeElement(encodeRequirement(requirement, includeSpans), pretty)

    public fun renderSchemaGuarantee(
        guarantee: GuaranteeSchema,
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

    private fun encodeAnalysisRequirement(requirement: AnalysisRequirementSchema, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf(
            "root" to encodeAnalysisRequirementNode(requirement.root, includeSpans),
            "requirements" to encodeAnalysisRequirementExprList(requirement.requirements),
            "opaqueRegions" to encodeOpaqueRegions(requirement.opaqueRegions),
        ),
    )

    private fun encodeAnalysisGuarantee(guarantee: AnalysisGuaranteeSchema, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf(
            "root" to encodeAnalysisGuaranteeNode(guarantee.root, includeSpans),
            "mayEmitNull" to JsonPrimitive(guarantee.mayEmitNull),
            "opaqueRegions" to encodeOpaqueRegions(guarantee.opaqueRegions),
        ),
    )

    private fun encodeRequirement(requirement: RequirementSchema, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf<String, JsonElement>(
            "root" to encodeNode(requirement.root, includeSpans),
            "obligations" to encodeObligations(requirement.obligations, includeSpans),
            "opaqueRegions" to encodeOpaqueRegions(requirement.opaqueRegions),
        ).apply {
            if (includeSpans && requirement.evidence.isNotEmpty()) {
                this["evidence"] = encodeEvidence(requirement.evidence, includeSpans = true)
            }
        },
    )

    private fun encodeGuarantee(guarantee: GuaranteeSchema, includeSpans: Boolean): JsonElement = JsonObject(
        linkedMapOf<String, JsonElement>(
            "root" to encodeNode(guarantee.root, includeSpans),
            "obligations" to encodeObligations(guarantee.obligations, includeSpans),
            "mayEmitNull" to JsonPrimitive(guarantee.mayEmitNull),
            "opaqueRegions" to encodeOpaqueRegions(guarantee.opaqueRegions),
        ).apply {
            if (includeSpans && guarantee.evidence.isNotEmpty()) {
                this["evidence"] = encodeEvidence(guarantee.evidence, includeSpans = true)
            }
        },
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
        ValueShape.Never -> JsonObject(mapOf("type" to JsonPrimitive("never")))
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

    private fun encodeAnalysisRequirementNode(node: AnalysisRequirementNode, includeSpans: Boolean): JsonElement {
        val children = LinkedHashMap<String, JsonElement>()
        node.children.forEach { (name, child) ->
            children[name] = encodeAnalysisRequirementNode(child, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(node.required),
            "shape" to encodeAnalysisValueShape(node.shape),
            "children" to JsonObject(children),
        )
        return JsonObject(payload)
    }

    private fun encodeAnalysisGuaranteeNode(node: AnalysisGuaranteeNode, includeSpans: Boolean): JsonElement {
        val children = LinkedHashMap<String, JsonElement>()
        node.children.forEach { (name, child) ->
            children[name] = encodeAnalysisGuaranteeNode(child, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(node.required),
            "shape" to encodeAnalysisValueShape(node.shape),
            "children" to JsonObject(children),
        )
        if (includeSpans) {
            payload["origin"] = JsonPrimitive(node.origin.name)
        }
        return JsonObject(payload)
    }

    private fun encodeAnalysisValueShape(shape: ValueShape): JsonElement = when (shape) {
        ValueShape.Never -> JsonObject(mapOf("type" to JsonPrimitive("never")))
        ValueShape.Unknown -> JsonObject(mapOf("type" to JsonPrimitive("any")))
        ValueShape.Null -> JsonObject(mapOf("type" to JsonPrimitive("null")))
        ValueShape.BooleanShape -> JsonObject(mapOf("type" to JsonPrimitive("boolean")))
        ValueShape.NumberShape -> JsonObject(mapOf("type" to JsonPrimitive("number")))
        ValueShape.Bytes -> JsonObject(mapOf("type" to JsonPrimitive("bytes")))
        ValueShape.TextShape -> JsonObject(mapOf("type" to JsonPrimitive("text")))
        is ValueShape.ArrayShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("array"),
                "element" to encodeAnalysisValueShape(shape.element),
            )
        )
        is ValueShape.SetShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("set"),
                "element" to encodeAnalysisValueShape(shape.element),
            )
        )
        is ValueShape.ObjectShape -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "closed" to JsonPrimitive(shape.closed),
            )
        )
        is ValueShape.Union -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("union"),
                "options" to JsonArray(shape.options.map(::encodeAnalysisValueShape)),
            )
        )
    }

    private fun encodeAnalysisRequirementExprList(expressions: List<AnalysisRequirementExpr>): JsonElement =
        JsonArray(expressions.map(::encodeAnalysisRequirementExpr))

    private fun encodeNode(node: Node, includeSpans: Boolean): JsonElement {
        val children = LinkedHashMap<String, JsonElement>()
        node.children.forEach { (name, child) ->
            children[name] = encodeNode(child, includeSpans)
        }
        val payload = linkedMapOf<String, JsonElement>(
            "required" to JsonPrimitive(node.required),
            "kind" to JsonPrimitive(node.kind.name.lowercase()),
            "open" to JsonPrimitive(node.open),
            "children" to JsonObject(children),
            "domains" to JsonArray(node.domains.map(::encodeDomain)),
        )
        node.element?.let { element ->
            payload["element"] = encodeNode(element, includeSpans)
        }
        if (node.options.isNotEmpty()) {
            payload["options"] = JsonArray(node.options.map { option -> encodeNode(option, includeSpans) })
        }
        if (includeSpans && node.origin != null) {
            payload["origin"] = JsonPrimitive(node.origin.name)
        }
        return JsonObject(payload)
    }

    private fun encodeObligations(
        obligations: List<ContractObligation>,
        includeDebugMetadata: Boolean,
    ): JsonElement =
        JsonArray(obligations.map { obligation -> encodeObligation(obligation, includeDebugMetadata) })

    private fun encodeObligation(
        obligation: ContractObligation,
        includeDebugMetadata: Boolean,
    ): JsonElement = JsonObject(
        linkedMapOf<String, JsonElement>(
            "expr" to encodeConstraintExpr(obligation.expr),
        ).apply {
            if (includeDebugMetadata) {
                this["confidence"] = JsonPrimitive(obligation.confidence)
                this["ruleId"] = JsonPrimitive(obligation.ruleId)
                this["heuristic"] = JsonPrimitive(obligation.heuristic)
            }
        },
    )

    private fun encodeConstraintExpr(expr: ConstraintExpr): JsonElement = when (expr) {
        is ConstraintExpr.PathPresent -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("pathPresent"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
        is ConstraintExpr.PathNonNull -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("pathNonNull"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
        is ConstraintExpr.OneOf -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("oneOf"),
                "children" to JsonArray(expr.children.map(::encodeConstraintExpr)),
            ),
        )
        is ConstraintExpr.AllOf -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("allOf"),
                "children" to JsonArray(expr.children.map(::encodeConstraintExpr)),
            ),
        )
        is ConstraintExpr.ForAll -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("forAll"),
                "path" to encodeAccessPath(expr.path),
                "requiredFields" to JsonArray(expr.requiredFields.map(::JsonPrimitive)),
                "fieldDomains" to JsonObject(
                    LinkedHashMap<String, JsonElement>().apply {
                        expr.fieldDomains.forEach { (name, domain) ->
                            this[name] = encodeDomain(domain)
                        }
                    },
                ),
                "requireAnyOf" to JsonArray(
                    expr.requireAnyOf.map { group -> JsonArray(group.map(::JsonPrimitive)) },
                ),
            ),
        )
        is ConstraintExpr.Exists -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("exists"),
                "path" to encodeAccessPath(expr.path),
                "minCount" to JsonPrimitive(expr.minCount),
            ),
        )
        is ConstraintExpr.DomainConstraint -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("valueDomain"),
                "path" to encodeAccessPath(expr.path),
                "domain" to encodeDomain(expr.domain),
            ),
        )
    }

    private fun encodeDomain(domain: ValueDomain): JsonElement = when (domain) {
        is ValueDomain.EnumText -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("enumText"),
                "values" to JsonArray(domain.values.map(::JsonPrimitive)),
            ),
        )
        is ValueDomain.NumberRange -> {
            val payload = linkedMapOf<String, JsonElement>(
                "kind" to JsonPrimitive("numberRange"),
                "integerOnly" to JsonPrimitive(domain.integerOnly),
            )
            domain.min?.let { min -> payload["min"] = JsonPrimitive(min) }
            domain.max?.let { max -> payload["max"] = JsonPrimitive(max) }
            JsonObject(payload)
        }
        is ValueDomain.Regex -> JsonObject(
            linkedMapOf(
                "kind" to JsonPrimitive("regex"),
                "pattern" to JsonPrimitive(domain.pattern),
            ),
        )
    }

    private fun encodeEvidence(evidence: List<InferenceEvidence>, includeSpans: Boolean): JsonElement =
        JsonArray(evidence.map { item -> encodeEvidenceItem(item, includeSpans) })

    private fun encodeEvidenceItem(evidence: InferenceEvidence, includeSpans: Boolean): JsonElement {
        val payload = linkedMapOf<String, JsonElement>(
            "ruleId" to JsonPrimitive(evidence.ruleId),
            "confidence" to JsonPrimitive(evidence.confidence),
        )
        evidence.notes?.let { notes -> payload["notes"] = JsonPrimitive(notes) }
        if (includeSpans && evidence.sourceSpans.isNotEmpty()) {
            payload["sourceSpans"] = JsonArray(evidence.sourceSpans.map(::encodeToken))
        }
        return JsonObject(payload)
    }

    private fun encodeAnalysisRequirementExpr(expr: AnalysisRequirementExpr): JsonElement = when (expr) {
        is AnalysisRequirementExpr.AllOf -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("allOf"),
                "children" to JsonArray(expr.children.map(::encodeAnalysisRequirementExpr)),
            ),
        )
        is AnalysisRequirementExpr.AnyOf -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("anyOf"),
                "children" to JsonArray(expr.children.map(::encodeAnalysisRequirementExpr)),
            ),
        )
        is AnalysisRequirementExpr.PathPresent -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("pathPresent"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
        is AnalysisRequirementExpr.PathNonNull -> JsonObject(
            mapOf(
                "kind" to JsonPrimitive("pathNonNull"),
                "path" to encodeAccessPath(expr.path),
            ),
        )
    }

    private fun encodeOpaqueRegions(items: List<OpaqueRegion>): JsonElement =
        JsonArray(items.map { region ->
            JsonObject(
                linkedMapOf(
                    "path" to encodeAccessPath(region.path),
                    "reason" to JsonPrimitive(region.reason.name),
                ),
            )
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
