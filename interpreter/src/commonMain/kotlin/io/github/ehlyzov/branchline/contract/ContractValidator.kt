package io.github.ehlyzov.branchline.contract

import kotlin.math.min

public class ContractViolationException(
    public val violations: List<ContractViolation>,
) : RuntimeException(formatContractViolations(violations))

public object ContractEnforcer {
    private val validator = ContractValidator()

    public fun enforceInput(
        mode: ContractValidationMode,
        requirement: RequirementSchema,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): List<ContractViolation> = enforce(
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
        guarantee: GuaranteeSchema,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): List<ContractViolation> = enforce(
        mode = mode,
        result = validator.validateOutput(
            guarantee = guarantee,
            value = value,
            confidenceThreshold = confidenceThreshold,
            includeHeuristic = includeHeuristic,
        ),
    )

    private fun enforce(mode: ContractValidationMode, result: ContractValidationResult): List<ContractViolation> {
        return when (mode) {
            ContractValidationMode.OFF -> emptyList()
            ContractValidationMode.WARN -> result.violations
            ContractValidationMode.STRICT -> {
                if (result.violations.isNotEmpty()) {
                    throw ContractViolationException(result.violations)
                }
                emptyList()
            }
        }
    }
}

public class ContractValidator {
    public fun validateInput(
        requirement: RequirementSchema,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): ContractValidationResult {
        val violations = mutableListOf<ContractViolation>()
        validateNode(
            node = requirement.root,
            value = value,
            path = listOf(PathSegment.Root("input")),
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
            violations += ContractViolation(
                path = renderAccessPath("input", opaque.path),
                kind = ContractViolationKind.OPAQUE_REGION_WARNING,
                hints = listOf("Dynamic path is opaque; strict deep validation is intentionally skipped."),
            )
        }
        return ContractValidationResult(sortViolations(violations))
    }

    public fun validateOutput(
        guarantee: GuaranteeSchema,
        value: Any?,
        confidenceThreshold: Double = 0.7,
        includeHeuristic: Boolean = false,
    ): ContractValidationResult {
        val violations = mutableListOf<ContractViolation>()
        if (value == null && !guarantee.mayEmitNull) {
            violations += ContractViolation(
                path = "output",
                kind = ContractViolationKind.NULLABILITY_MISMATCH,
                expected = shapeFromNode(guarantee.root),
                actual = ValueShape.Null,
                ruleId = "output-may-emit-null",
            )
            return ContractValidationResult(sortViolations(violations))
        }
        if (value != null) {
            validateNode(
                node = guarantee.root,
                value = value,
                path = listOf(PathSegment.Root("output")),
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
            violations += ContractViolation(
                path = renderAccessPath("output", opaque.path),
                kind = ContractViolationKind.OPAQUE_REGION_WARNING,
                hints = listOf("Dynamic output path is opaque; strict deep validation is intentionally skipped."),
            )
        }
        return ContractValidationResult(sortViolations(violations))
    }

    private fun validateNode(
        node: Node,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        if (node.required && value == null) {
            violations += ContractViolation(
                path = renderPath(path),
                kind = ContractViolationKind.MISSING_REQUIRED_PATH,
                expected = shapeFromNode(node),
                actual = ValueShape.Null,
            )
            return
        }
        if (value == null) return
        if (!matchesNodeKind(node, value)) {
            violations += ContractViolation(
                path = renderPath(path),
                kind = ContractViolationKind.SHAPE_MISMATCH,
                expected = shapeFromNode(node),
                actual = valueShapeOf(value),
            )
            return
        }
        validateDomains(node.domains, value, path, violations)
        when (node.kind) {
            NodeKind.OBJECT -> {
                val map = value as? Map<*, *> ?: return
                val fieldMap = map.entries.associate { entry -> entry.key?.toString() to entry.value }
                for ((name, child) in node.children) {
                    validateNode(child, fieldMap[name], appendField(path, name), violations)
                }
                if (!node.open) {
                    for (key in fieldMap.keys) {
                        if (key == null) continue
                        if (!node.children.containsKey(key)) {
                            violations += ContractViolation(
                                path = renderPath(appendField(path, key)),
                                kind = ContractViolationKind.UNEXPECTED_FIELD,
                                actual = valueShapeOf(fieldMap[key]),
                            )
                        }
                    }
                }
            }
            NodeKind.ARRAY -> {
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
            NodeKind.SET -> {
                val set = value as? Set<*> ?: return
                val elementNode = node.element ?: return
                var index = 0
                for (item in set) {
                    validateNode(elementNode, item, appendIndex(path, index), violations)
                    index += 1
                }
            }
            NodeKind.UNION,
            NodeKind.ANY,
            NodeKind.NEVER,
            NodeKind.NULL,
            NodeKind.BOOLEAN,
            NodeKind.NUMBER,
            NodeKind.BYTES,
            NodeKind.TEXT,
            -> Unit
        }
    }

    private fun validateDomains(
        domains: List<ValueDomain>,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        for (domain in domains) {
            if (domainMatches(domain, value)) continue
            violations += ContractViolation(
                path = renderPath(path),
                kind = ContractViolationKind.SHAPE_MISMATCH,
                expected = valueShapeForDomain(domain),
                actual = valueShapeOf(value),
                ruleId = "value-domain",
            )
        }
    }

    private fun validateObligations(
        obligations: List<ContractObligation>,
        root: Any?,
        rootName: String,
        violations: MutableList<ContractViolation>,
        confidenceThreshold: Double,
        includeHeuristic: Boolean,
    ) {
        val rootMap = root as? Map<*, *> ?: emptyMap<Any?, Any?>()
        for (obligation in obligations) {
            if (obligation.confidence < confidenceThreshold) continue
            if (!includeHeuristic && obligation.heuristic) continue
            val ok = evaluateExpr(obligation.expr, rootMap)
            if (ok) continue
            violations += ContractViolation(
                path = renderConstraintPath(rootName, obligation.expr),
                kind = ContractViolationKind.MISSING_CONDITIONAL_GROUP,
                ruleId = obligation.ruleId,
            )
        }
    }

    private fun evaluateExpr(expr: ConstraintExpr, root: Map<*, *>): Boolean = when (expr) {
        is ConstraintExpr.PathPresent -> resolvePathValue(root, expr.path.segments, requireNonNull = false).present
        is ConstraintExpr.PathNonNull -> resolvePathValue(root, expr.path.segments, requireNonNull = true).present
        is ConstraintExpr.OneOf -> expr.children.any { child -> evaluateExpr(child, root) }
        is ConstraintExpr.AllOf -> expr.children.all { child -> evaluateExpr(child, root) }
        is ConstraintExpr.DomainConstraint -> {
            val resolved = resolvePathValue(root, expr.path.segments, requireNonNull = false)
            resolved.present && domainMatches(expr.domain, resolved.value)
        }
        is ConstraintExpr.Exists -> {
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
        is ConstraintExpr.ForAll -> {
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

    private fun matchesNodeKind(node: Node, value: Any?): Boolean = when (node.kind) {
        NodeKind.ANY -> true
        NodeKind.NEVER -> false
        NodeKind.NULL -> value == null
        NodeKind.BOOLEAN -> value is Boolean
        NodeKind.NUMBER -> value is Number
        NodeKind.BYTES -> value is ByteArray
        NodeKind.TEXT -> value is String
        NodeKind.OBJECT -> value is Map<*, *>
        NodeKind.ARRAY -> value is List<*> || value is Array<*>
        NodeKind.SET -> value is Set<*>
        NodeKind.UNION -> node.options.any { option -> matchesNodeKind(option, value) }
    }

    private fun domainMatches(domain: ValueDomain, value: Any?): Boolean = when (domain) {
        is ValueDomain.EnumText -> value is String && value in domain.values
        is ValueDomain.NumberRange -> {
            val number = (value as? Number)?.toDouble() ?: return false
            val minOk = domain.min?.let { number >= it } ?: true
            val maxOk = domain.max?.let { number <= it } ?: true
            val intOk = if (!domain.integerOnly) true else number % 1.0 == 0.0
            minOk && maxOk && intOk
        }
        is ValueDomain.Regex -> {
            val text = value as? String ?: return false
            Regex(domain.pattern).matches(text)
        }
    }

    private fun valueShapeForDomain(domain: ValueDomain): ValueShape = when (domain) {
        is ValueDomain.EnumText -> ValueShape.TextShape
        is ValueDomain.NumberRange -> ValueShape.NumberShape
        is ValueDomain.Regex -> ValueShape.TextShape
    }

    private fun shapeFromNode(node: Node): ValueShape = when (node.kind) {
        NodeKind.NEVER -> ValueShape.Never
        NodeKind.ANY -> ValueShape.Unknown
        NodeKind.NULL -> ValueShape.Null
        NodeKind.BOOLEAN -> ValueShape.BooleanShape
        NodeKind.NUMBER -> ValueShape.NumberShape
        NodeKind.BYTES -> ValueShape.Bytes
        NodeKind.TEXT -> ValueShape.TextShape
        NodeKind.ARRAY -> ValueShape.ArrayShape(node.element?.let(::shapeFromNode) ?: ValueShape.Unknown)
        NodeKind.SET -> ValueShape.SetShape(node.element?.let(::shapeFromNode) ?: ValueShape.Unknown)
        NodeKind.UNION -> ValueShape.Union(node.options.map(::shapeFromNode))
        NodeKind.OBJECT -> {
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

    private fun renderConstraintPath(rootName: String, expr: ConstraintExpr): String = when (expr) {
        is ConstraintExpr.PathPresent -> renderAccessPath(rootName, expr.path)
        is ConstraintExpr.PathNonNull -> "${renderAccessPath(rootName, expr.path)} != null"
        is ConstraintExpr.OneOf -> expr.children.joinToString(" or ") { child -> renderConstraintPath(rootName, child) }
        is ConstraintExpr.AllOf -> expr.children.joinToString(" and ") { child -> renderConstraintPath(rootName, child) }
        is ConstraintExpr.ForAll -> "${renderAccessPath(rootName, expr.path)}[*]"
        is ConstraintExpr.Exists -> "${renderAccessPath(rootName, expr.path)} count >= ${expr.minCount}"
        is ConstraintExpr.DomainConstraint -> renderAccessPath(rootName, expr.path)
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
    ): ResolvedPathValue {
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
        if (!present) return ResolvedPathValue(false, null)
        if (!requireNonNull) return ResolvedPathValue(true, current)
        return ResolvedPathValue(current != null, current)
    }

    private fun appendField(path: List<PathSegment>, name: String): List<PathSegment> =
        path + PathSegment.Field(name)

    private fun appendIndex(path: List<PathSegment>, index: Int): List<PathSegment> =
        path + PathSegment.Index(index)

    private fun renderPath(path: List<PathSegment>): String {
        val builder = StringBuilder()
        path.forEachIndexed { index, segment ->
            when (segment) {
                is PathSegment.Root -> builder.append(segment.name)
                is PathSegment.Field -> {
                    if (index > 0) builder.append('.')
                    builder.append(segment.name)
                }
                is PathSegment.Index -> builder.append('[').append(segment.index).append(']')
            }
        }
        return builder.toString()
    }

    private fun sortViolations(items: List<ContractViolation>): List<ContractViolation> =
        items.sortedWith(compareBy({ it.path }, { it.kind.name }, { it.ruleId ?: "" }))
}

private sealed interface PathSegment {
    data class Root(val name: String) : PathSegment
    data class Field(val name: String) : PathSegment
    data class Index(val index: Int) : PathSegment
}

private data class ResolvedPathValue(
    val present: Boolean,
    val value: Any?,
)

public fun formatContractViolations(violations: List<ContractViolation>): String {
    if (violations.isEmpty()) return "Contract validation failed."
    val maxViolations = min(violations.size, 25)
    val lines = mutableListOf<String>()
    lines += "Contract validation failed (${violations.size} mismatch${if (violations.size == 1) "" else "es"}):"
    for (i in 0 until maxViolations) {
        lines += "  - ${formatContractViolation(violations[i])}"
    }
    if (violations.size > maxViolations) {
        lines += "  - ... (${violations.size - maxViolations} more)"
    }
    return lines.joinToString("\n")
}
