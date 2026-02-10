package io.github.ehlyzov.branchline.contract

import kotlin.math.min

public class ContractViolationExceptionV3(
    public val violations: List<ContractViolationV2>,
) : RuntimeException(formatContractViolationsV3(violations))

public object ContractEnforcerV3 {
    private val validator = ContractValidatorV3()

    public fun enforceInput(
        mode: ContractValidationMode,
        requirement: RequirementSchemaV3,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): List<ContractViolationV2> = enforce(
        mode = mode,
        result = validator.validateInput(
            requirement = requirement,
            value = value,
            confidenceThreshold = confidenceThreshold,
            includeHeuristic = includeHeuristic,
        ),
    )

    public fun enforceOutput(
        mode: ContractValidationMode,
        guarantee: GuaranteeSchemaV3,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): List<ContractViolationV2> = enforce(
        mode = mode,
        result = validator.validateOutput(
            guarantee = guarantee,
            value = value,
            confidenceThreshold = confidenceThreshold,
            includeHeuristic = includeHeuristic,
        ),
    )

    private fun enforce(mode: ContractValidationMode, result: ContractValidationResultV2): List<ContractViolationV2> {
        return when (mode) {
            ContractValidationMode.OFF -> emptyList()
            ContractValidationMode.WARN -> result.violations
            ContractValidationMode.STRICT -> {
                if (result.violations.isNotEmpty()) {
                    throw ContractViolationExceptionV3(result.violations)
                }
                emptyList()
            }
        }
    }
}

public class ContractValidatorV3 {
    public fun validateInput(
        requirement: RequirementSchemaV3,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): ContractValidationResultV2 {
        val violations = mutableListOf<ContractViolationV2>()
        validateNode(
            node = requirement.root,
            value = value,
            path = listOf(PathSegmentV3.Root("input")),
            violations = violations,
        )
        validateObligations(
            obligations = requirement.obligations,
            root = value,
            rootName = "input",
            violations = violations,
            confidenceThreshold = confidenceThreshold,
            includeHeuristic = includeHeuristic,
        )
        for (opaque in requirement.opaqueRegions) {
            violations += ContractViolationV2(
                path = renderAccessPath("input", opaque.path),
                kind = ContractViolationKindV2.OPAQUE_REGION_WARNING,
                hints = listOf("Dynamic path is opaque; strict deep validation is intentionally skipped."),
            )
        }
        return ContractValidationResultV2(sortViolations(violations))
    }

    public fun validateOutput(
        guarantee: GuaranteeSchemaV3,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): ContractValidationResultV2 {
        val violations = mutableListOf<ContractViolationV2>()
        if (value == null && !guarantee.mayEmitNull) {
            violations += ContractViolationV2(
                path = "output",
                kind = ContractViolationKindV2.NULLABILITY_MISMATCH,
                expected = shapeFromNode(guarantee.root),
                actual = ValueShape.Null,
                ruleId = "output-may-emit-null",
            )
            return ContractValidationResultV2(sortViolations(violations))
        }
        if (value != null) {
            validateNode(
                node = guarantee.root,
                value = value,
                path = listOf(PathSegmentV3.Root("output")),
                violations = violations,
            )
            validateObligations(
                obligations = guarantee.obligations,
                root = value,
                rootName = "output",
                violations = violations,
                confidenceThreshold = confidenceThreshold,
                includeHeuristic = includeHeuristic,
            )
        }
        for (opaque in guarantee.opaqueRegions) {
            violations += ContractViolationV2(
                path = renderAccessPath("output", opaque.path),
                kind = ContractViolationKindV2.OPAQUE_REGION_WARNING,
                hints = listOf("Dynamic output path is opaque; strict deep validation is intentionally skipped."),
            )
        }
        return ContractValidationResultV2(sortViolations(violations))
    }

    private fun validateNode(
        node: NodeV3,
        value: Any?,
        path: List<PathSegmentV3>,
        violations: MutableList<ContractViolationV2>,
    ) {
        if (node.required && value == null) {
            violations += ContractViolationV2(
                path = renderPath(path),
                kind = ContractViolationKindV2.MISSING_REQUIRED_PATH,
                expected = shapeFromNode(node),
                actual = ValueShape.Null,
            )
            return
        }
        if (value == null) return
        if (!matchesNodeKind(node, value)) {
            violations += ContractViolationV2(
                path = renderPath(path),
                kind = ContractViolationKindV2.SHAPE_MISMATCH,
                expected = shapeFromNode(node),
                actual = valueShapeOf(value),
            )
            return
        }
        validateDomains(node.domains, value, path, violations)
        when (node.kind) {
            NodeKindV3.OBJECT -> {
                val map = value as? Map<*, *> ?: return
                val fieldMap = map.entries.associate { entry -> entry.key?.toString() to entry.value }
                for ((name, child) in node.children) {
                    validateNode(child, fieldMap[name], appendField(path, name), violations)
                }
                if (!node.open) {
                    for (key in fieldMap.keys) {
                        if (key == null) continue
                        if (!node.children.containsKey(key)) {
                            violations += ContractViolationV2(
                                path = renderPath(appendField(path, key)),
                                kind = ContractViolationKindV2.UNEXPECTED_FIELD,
                                actual = valueShapeOf(fieldMap[key]),
                            )
                        }
                    }
                }
            }
            NodeKindV3.ARRAY -> {
                val items = when (value) {
                    is List<*> -> value
                    is Array<*> -> value.toList()
                    else -> emptyList()
                }
                val elementNode = node.element ?: return
                items.forEachIndexed { index, item ->
                    validateNode(elementNode, item, appendIndex(path, index), violations)
                }
            }
            NodeKindV3.SET -> {
                val set = value as? Set<*> ?: return
                val elementNode = node.element ?: return
                var index = 0
                for (item in set) {
                    validateNode(elementNode, item, appendIndex(path, index), violations)
                    index += 1
                }
            }
            NodeKindV3.UNION,
            NodeKindV3.ANY,
            NodeKindV3.NEVER,
            NodeKindV3.NULL,
            NodeKindV3.BOOLEAN,
            NodeKindV3.NUMBER,
            NodeKindV3.BYTES,
            NodeKindV3.TEXT,
            -> Unit
        }
    }

    private fun validateDomains(
        domains: List<ValueDomainV3>,
        value: Any?,
        path: List<PathSegmentV3>,
        violations: MutableList<ContractViolationV2>,
    ) {
        for (domain in domains) {
            if (domainMatches(domain, value)) continue
            violations += ContractViolationV2(
                path = renderPath(path),
                kind = ContractViolationKindV2.SHAPE_MISMATCH,
                expected = valueShapeForDomain(domain),
                actual = valueShapeOf(value),
                ruleId = "value-domain",
            )
        }
    }

    private fun validateObligations(
        obligations: List<ContractObligationV3>,
        root: Any?,
        rootName: String,
        violations: MutableList<ContractViolationV2>,
        confidenceThreshold: Double,
        includeHeuristic: Boolean,
    ) {
        val rootMap = root as? Map<*, *> ?: emptyMap<Any?, Any?>()
        for (obligation in obligations) {
            if (obligation.confidence < confidenceThreshold) continue
            if (!includeHeuristic && obligation.heuristic) continue
            val ok = evaluateExpr(obligation.expr, rootMap)
            if (ok) continue
            violations += ContractViolationV2(
                path = renderConstraintPath(rootName, obligation.expr),
                kind = ContractViolationKindV2.MISSING_CONDITIONAL_GROUP,
                ruleId = obligation.ruleId,
            )
        }
    }

    private fun evaluateExpr(expr: ConstraintExprV3, root: Map<*, *>): Boolean = when (expr) {
        is ConstraintExprV3.PathPresent -> resolvePathValue(root, expr.path.segments, requireNonNull = false).present
        is ConstraintExprV3.PathNonNull -> resolvePathValue(root, expr.path.segments, requireNonNull = true).present
        is ConstraintExprV3.OneOf -> expr.children.any { child -> evaluateExpr(child, root) }
        is ConstraintExprV3.AllOf -> expr.children.all { child -> evaluateExpr(child, root) }
        is ConstraintExprV3.ValueDomain -> {
            val resolved = resolvePathValue(root, expr.path.segments, requireNonNull = false)
            resolved.present && domainMatches(expr.domain, resolved.value)
        }
        is ConstraintExprV3.Exists -> {
            val resolved = resolvePathValue(root, expr.path.segments, requireNonNull = false)
            val value = resolved.value
            val count = when (value) {
                is List<*> -> value.size
                is Array<*> -> value.size
                is Set<*> -> value.size
                null -> 0
                else -> 0
            }
            resolved.present && count >= expr.minCount
        }
        is ConstraintExprV3.ForAll -> {
            val resolved = resolvePathValue(root, expr.path.segments, requireNonNull = false)
            if (!resolved.present) return false
            val items = when (val value = resolved.value) {
                is List<*> -> value
                is Array<*> -> value.toList()
                is Set<*> -> value.toList()
                else -> return false
            }
            items.all { item ->
                val map = item as? Map<*, *> ?: return@all false
                val byName = map.entries.associate { entry -> entry.key?.toString() to entry.value }
                val requiredOk = expr.requiredFields.all { field -> byName.containsKey(field) && byName[field] != null }
                val domainsOk = expr.fieldDomains.all { (field, domain) ->
                    val value = byName[field]
                    value != null && domainMatches(domain, value)
                }
                val anyOfOk = expr.requireAnyOf.all { alternatives ->
                    alternatives.any { name ->
                        byName.containsKey(name) && byName[name] != null
                    }
                }
                requiredOk && domainsOk && anyOfOk
            }
        }
    }

    private fun matchesNodeKind(node: NodeV3, value: Any?): Boolean = when (node.kind) {
        NodeKindV3.ANY -> true
        NodeKindV3.NEVER -> false
        NodeKindV3.NULL -> value == null
        NodeKindV3.BOOLEAN -> value is Boolean
        NodeKindV3.NUMBER -> value is Number
        NodeKindV3.BYTES -> value is ByteArray
        NodeKindV3.TEXT -> value is String
        NodeKindV3.OBJECT -> value is Map<*, *>
        NodeKindV3.ARRAY -> value is List<*> || value is Array<*>
        NodeKindV3.SET -> value is Set<*>
        NodeKindV3.UNION -> node.options.any { option -> matchesNodeKind(option, value) }
    }

    private fun domainMatches(domain: ValueDomainV3, value: Any?): Boolean = when (domain) {
        is ValueDomainV3.EnumText -> value is String && value in domain.values
        is ValueDomainV3.NumberRange -> {
            val number = (value as? Number)?.toDouble() ?: return false
            val minOk = domain.min?.let { number >= it } ?: true
            val maxOk = domain.max?.let { number <= it } ?: true
            val intOk = if (!domain.integerOnly) true else number % 1.0 == 0.0
            minOk && maxOk && intOk
        }
        is ValueDomainV3.Regex -> {
            val text = value as? String ?: return false
            Regex(domain.pattern).matches(text)
        }
    }

    private fun valueShapeForDomain(domain: ValueDomainV3): ValueShape = when (domain) {
        is ValueDomainV3.EnumText -> ValueShape.TextShape
        is ValueDomainV3.NumberRange -> ValueShape.NumberShape
        is ValueDomainV3.Regex -> ValueShape.TextShape
    }

    private fun shapeFromNode(node: NodeV3): ValueShape = when (node.kind) {
        NodeKindV3.NEVER -> ValueShape.Never
        NodeKindV3.ANY -> ValueShape.Unknown
        NodeKindV3.NULL -> ValueShape.Null
        NodeKindV3.BOOLEAN -> ValueShape.BooleanShape
        NodeKindV3.NUMBER -> ValueShape.NumberShape
        NodeKindV3.BYTES -> ValueShape.Bytes
        NodeKindV3.TEXT -> ValueShape.TextShape
        NodeKindV3.ARRAY -> ValueShape.ArrayShape(node.element?.let(::shapeFromNode) ?: ValueShape.Unknown)
        NodeKindV3.SET -> ValueShape.SetShape(node.element?.let(::shapeFromNode) ?: ValueShape.Unknown)
        NodeKindV3.UNION -> ValueShape.Union(node.options.map(::shapeFromNode))
        NodeKindV3.OBJECT -> {
            val fields = LinkedHashMap<String, FieldShape>()
            node.children.forEach { (name, child) ->
                fields[name] = FieldShape(
                    required = child.required,
                    shape = shapeFromNode(child),
                    origin = child.origin ?: OriginKind.MERGED,
                )
            }
            ValueShape.ObjectShape(
                schema = SchemaGuarantee(
                    fields = fields,
                    mayEmitNull = false,
                    dynamicFields = emptyList(),
                ),
                closed = !node.open,
            )
        }
    }

    private fun valueShapeOf(value: Any?): ValueShape = when (value) {
        null -> ValueShape.Null
        is Boolean -> ValueShape.BooleanShape
        is String -> ValueShape.TextShape
        is Number -> ValueShape.NumberShape
        is ByteArray -> ValueShape.Bytes
        is List<*> -> ValueShape.ArrayShape(ValueShape.Unknown)
        is Array<*> -> ValueShape.ArrayShape(ValueShape.Unknown)
        is Set<*> -> ValueShape.SetShape(ValueShape.Unknown)
        is Map<*, *> -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = linkedMapOf(),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
        else -> ValueShape.Unknown
    }

    private fun renderConstraintPath(rootName: String, expr: ConstraintExprV3): String = when (expr) {
        is ConstraintExprV3.PathPresent -> renderAccessPath(rootName, expr.path)
        is ConstraintExprV3.PathNonNull -> "${renderAccessPath(rootName, expr.path)} != null"
        is ConstraintExprV3.OneOf -> expr.children.joinToString(" or ") { child -> renderConstraintPath(rootName, child) }
        is ConstraintExprV3.AllOf -> expr.children.joinToString(" and ") { child -> renderConstraintPath(rootName, child) }
        is ConstraintExprV3.ForAll -> "${renderAccessPath(rootName, expr.path)}[*]"
        is ConstraintExprV3.Exists -> "${renderAccessPath(rootName, expr.path)} count >= ${expr.minCount}"
        is ConstraintExprV3.ValueDomain -> renderAccessPath(rootName, expr.path)
    }

    private fun renderAccessPath(root: String, path: AccessPath): String {
        val builder = StringBuilder(root)
        for (segment in path.segments) {
            when (segment) {
                is AccessSegment.Field -> builder.append('.').append(segment.name)
                is AccessSegment.Index -> builder.append('[').append(segment.index).append(']')
                AccessSegment.Dynamic -> builder.append("[*]")
            }
        }
        return builder.toString()
    }

    private fun resolvePathValue(
        root: Map<*, *>,
        segments: List<AccessSegment>,
        requireNonNull: Boolean,
    ): ResolvedPathValueV3 {
        var current: Any? = root
        var present = true
        for (segment in segments) {
            when (segment) {
                is AccessSegment.Field -> {
                    val map = current as? Map<*, *>
                    if (map == null) {
                        present = false
                        current = null
                        break
                    }
                    val key = map.keys.firstOrNull { key -> key?.toString() == segment.name }
                    if (key == null) {
                        present = false
                        current = null
                        break
                    }
                    current = map[key]
                }
                is AccessSegment.Index -> {
                    current = when (current) {
                        is List<*> -> current.getOrNull(segment.index.toIntOrNull() ?: -1)
                        is Array<*> -> current.getOrNull(segment.index.toIntOrNull() ?: -1)
                        is Map<*, *> -> {
                            val key = current.keys.firstOrNull { key -> key?.toString() == segment.index }
                            if (key == null) {
                                present = false
                                null
                            } else {
                                current[key]
                            }
                        }
                        else -> {
                            present = false
                            null
                        }
                    }
                    if (!present) break
                }
                AccessSegment.Dynamic -> {
                    present = false
                    current = null
                    break
                }
            }
        }
        if (!present) return ResolvedPathValueV3(false, null)
        if (!requireNonNull) return ResolvedPathValueV3(true, current)
        return ResolvedPathValueV3(current != null, current)
    }

    private fun appendField(path: List<PathSegmentV3>, name: String): List<PathSegmentV3> =
        path + PathSegmentV3.Field(name)

    private fun appendIndex(path: List<PathSegmentV3>, index: Int): List<PathSegmentV3> =
        path + PathSegmentV3.Index(index)

    private fun renderPath(path: List<PathSegmentV3>): String {
        val builder = StringBuilder()
        path.forEachIndexed { index, segment ->
            when (segment) {
                is PathSegmentV3.Root -> builder.append(segment.name)
                is PathSegmentV3.Field -> {
                    if (index > 0) builder.append('.')
                    builder.append(segment.name)
                }
                is PathSegmentV3.Index -> builder.append('[').append(segment.index).append(']')
            }
        }
        return builder.toString()
    }

    private fun sortViolations(items: List<ContractViolationV2>): List<ContractViolationV2> =
        items.sortedWith(compareBy({ it.path }, { it.kind.name }, { it.ruleId ?: "" }))
}

private sealed interface PathSegmentV3 {
    data class Root(val name: String) : PathSegmentV3
    data class Field(val name: String) : PathSegmentV3
    data class Index(val index: Int) : PathSegmentV3
}

private data class ResolvedPathValueV3(
    val present: Boolean,
    val value: Any?,
)

public fun formatContractViolationsV3(violations: List<ContractViolationV2>): String {
    if (violations.isEmpty()) return "Contract validation failed."
    val maxViolations = min(violations.size, 25)
    val lines = mutableListOf<String>()
    lines += "Contract validation failed (${violations.size} mismatch${if (violations.size == 1) "" else "es"}):"
    for (i in 0 until maxViolations) {
        lines += "  - ${formatContractViolationV2(violations[i])}"
    }
    if (violations.size > maxViolations) {
        lines += "  - ... (${violations.size - maxViolations} more)"
    }
    return lines.joinToString("\n")
}
