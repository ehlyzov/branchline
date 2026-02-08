package io.github.ehlyzov.branchline.contract

import kotlin.math.min

public enum class ContractViolationKindV2 {
    MISSING_REQUIRED_PATH,
    MISSING_CONDITIONAL_GROUP,
    UNEXPECTED_FIELD,
    SHAPE_MISMATCH,
    NULLABILITY_MISMATCH,
    OPAQUE_REGION_WARNING,
}

public data class ContractViolationV2(
    val path: String,
    val kind: ContractViolationKindV2,
    val expected: ValueShape? = null,
    val actual: ValueShape? = null,
    val ruleId: String? = null,
    val hints: List<String> = emptyList(),
)

public data class ContractValidationResultV2(
    val violations: List<ContractViolationV2>,
) {
    public val isValid: Boolean
        get() = violations.isEmpty()
}

public class ContractViolationExceptionV2(
    public val violations: List<ContractViolationV2>,
) : RuntimeException(formatContractViolationsV2(violations))

public object ContractEnforcerV2 {
    private val validator = ContractValidatorV2()

    public fun enforceInput(
        mode: ContractValidationMode,
        requirement: RequirementSchemaV2,
        value: Any?,
    ): List<ContractViolationV2> = enforce(mode, validator.validateInput(requirement, value))

    public fun enforceOutput(
        mode: ContractValidationMode,
        guarantee: GuaranteeSchemaV2,
        value: Any?,
    ): List<ContractViolationV2> = enforce(mode, validator.validateOutput(guarantee, value))

    private fun enforce(mode: ContractValidationMode, result: ContractValidationResultV2): List<ContractViolationV2> {
        return when (mode) {
            ContractValidationMode.OFF -> emptyList()
            ContractValidationMode.WARN -> result.violations
            ContractValidationMode.STRICT -> {
                if (result.violations.isNotEmpty()) {
                    throw ContractViolationExceptionV2(result.violations)
                }
                emptyList()
            }
        }
    }
}

public class ContractValidatorV2 {
    public fun validateInput(requirement: RequirementSchemaV2, value: Any?): ContractValidationResultV2 {
        val violations = mutableListOf<ContractViolationV2>()
        validateRequirementNode(
            node = requirement.root,
            value = value,
            path = listOf(PathSegmentV2.Root("input")),
            violations = violations,
        )
        validateRequirementExpressions(requirement.requirements, value, listOf(PathSegmentV2.Root("input")), violations)
        for (opaque in requirement.opaqueRegions) {
            violations += ContractViolationV2(
                path = renderAccessPath("input", opaque.path),
                kind = ContractViolationKindV2.OPAQUE_REGION_WARNING,
                hints = listOf("Dynamic path is opaque; strict deep validation is intentionally skipped."),
            )
        }
        return ContractValidationResultV2(sortViolations(violations))
    }

    public fun validateOutput(guarantee: GuaranteeSchemaV2, value: Any?): ContractValidationResultV2 {
        val violations = mutableListOf<ContractViolationV2>()
        if (value == null && !guarantee.mayEmitNull) {
            violations += ContractViolationV2(
                path = "output",
                kind = ContractViolationKindV2.NULLABILITY_MISMATCH,
                expected = guarantee.root.shape,
                actual = ValueShape.Null,
                ruleId = "output-may-emit-null",
            )
            return ContractValidationResultV2(sortViolations(violations))
        }
        if (value != null) {
            validateGuaranteeNode(guarantee.root, value, listOf(PathSegmentV2.Root("output")), violations)
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

    private fun validateRequirementNode(
        node: RequirementNodeV2,
        value: Any?,
        path: List<PathSegmentV2>,
        violations: MutableList<ContractViolationV2>,
    ) {
        if (node.required && value == null) {
            violations += violationForNode(
                path = renderPath(path),
                kind = ContractViolationKindV2.MISSING_REQUIRED_PATH,
                node = node,
                actual = null,
            )
            return
        }
        if (value == null) return
        validateShape(node.shape, value, path, violations)
        val map = value as? Map<*, *> ?: return
        val fields = map.entries.associate { entry -> entry.key?.toString() to entry.value }
        for ((name, child) in node.children) {
            val childValue = fields[name]
            validateRequirementNode(child, childValue, appendField(path, name), violations)
        }
        if (!requirementNodeIsOpen(node)) {
            for (key in fields.keys) {
                if (key == null) continue
                if (!node.children.containsKey(key)) {
                    violations += ContractViolationV2(
                        path = renderPath(appendField(path, key)),
                        kind = ContractViolationKindV2.UNEXPECTED_FIELD,
                        actual = valueShapeOf(fields[key]),
                    )
                }
            }
        }
    }

    private fun validateGuaranteeNode(
        node: GuaranteeNodeV2,
        value: Any?,
        path: List<PathSegmentV2>,
        violations: MutableList<ContractViolationV2>,
    ) {
        if (node.required && value == null) {
            violations += violationForNode(
                path = renderPath(path),
                kind = ContractViolationKindV2.MISSING_REQUIRED_PATH,
                node = node,
                actual = null,
            )
            return
        }
        if (value == null) return
        validateShape(node.shape, value, path, violations)
        val map = value as? Map<*, *> ?: return
        val fields = map.entries.associate { entry -> entry.key?.toString() to entry.value }
        for ((name, child) in node.children) {
            validateGuaranteeNode(child, fields[name], appendField(path, name), violations)
        }
        if (!guaranteeNodeIsOpen(node)) {
            for (key in fields.keys) {
                if (key == null) continue
                if (!node.children.containsKey(key)) {
                    violations += ContractViolationV2(
                        path = renderPath(appendField(path, key)),
                        kind = ContractViolationKindV2.UNEXPECTED_FIELD,
                        actual = valueShapeOf(fields[key]),
                    )
                }
            }
        }
    }

    private fun validateRequirementExpressions(
        expressions: List<RequirementExprV2>,
        value: Any?,
        path: List<PathSegmentV2>,
        violations: MutableList<ContractViolationV2>,
    ) {
        val inputMap = value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        for (expr in expressions) {
            val ok = evaluateRequirementExpr(expr, inputMap)
            if (!ok) {
                violations += ContractViolationV2(
                    path = renderRequirementExpr(path, expr),
                    kind = ContractViolationKindV2.MISSING_CONDITIONAL_GROUP,
                    ruleId = "requirement-expression",
                )
            }
        }
    }

    private fun evaluateRequirementExpr(expr: RequirementExprV2, root: Map<*, *>): Boolean = when (expr) {
        is RequirementExprV2.AllOf -> expr.children.all { child -> evaluateRequirementExpr(child, root) }
        is RequirementExprV2.AnyOf -> expr.children.any { child -> evaluateRequirementExpr(child, root) }
        is RequirementExprV2.PathPresent -> resolvePathValue(root, expr.path.segments, requireNonNull = false).present
        is RequirementExprV2.PathNonNull -> resolvePathValue(root, expr.path.segments, requireNonNull = true).present
    }

    private fun validateShape(
        expected: ValueShape,
        value: Any?,
        path: List<PathSegmentV2>,
        violations: MutableList<ContractViolationV2>,
    ) {
        when (expected) {
            ValueShape.Unknown -> return
            ValueShape.Null -> if (value != null) {
                violations += violationForShape(path, expected, valueShapeOf(value))
            }
            ValueShape.BooleanShape -> if (value !is Boolean) {
                violations += violationForShape(path, expected, valueShapeOf(value))
            }
            ValueShape.NumberShape -> if (!isNumericValue(value)) {
                violations += violationForShape(path, expected, valueShapeOf(value))
            }
            ValueShape.Bytes -> if (value !is ByteArray) {
                violations += violationForShape(path, expected, valueShapeOf(value))
            }
            ValueShape.TextShape -> if (value !is String) {
                violations += violationForShape(path, expected, valueShapeOf(value))
            }
            is ValueShape.ArrayShape -> {
                val list = when (value) {
                    is List<*> -> value
                    is Array<*> -> value.toList()
                    else -> null
                }
                if (list == null) {
                    violations += violationForShape(path, expected, valueShapeOf(value))
                    return
                }
                if (expected.element == ValueShape.Unknown) return
                list.forEachIndexed { index, item ->
                    validateShape(expected.element, item, appendIndex(path, index), violations)
                }
            }
            is ValueShape.SetShape -> {
                val set = value as? Set<*>
                if (set == null) {
                    violations += violationForShape(path, expected, valueShapeOf(value))
                    return
                }
                if (expected.element == ValueShape.Unknown) return
                var idx = 0
                for (item in set) {
                    validateShape(expected.element, item, appendIndex(path, idx), violations)
                    idx += 1
                }
            }
            is ValueShape.ObjectShape -> {
                if (value !is Map<*, *>) {
                    violations += violationForShape(path, expected, valueShapeOf(value))
                }
            }
            is ValueShape.Union -> {
                val accepted = expected.options.any { option -> matchesShape(option, value) }
                if (!accepted) {
                    violations += violationForShape(path, expected, valueShapeOf(value))
                }
            }
        }
    }

    private fun violationForNode(
        path: String,
        kind: ContractViolationKindV2,
        node: Any,
        actual: ValueShape?,
    ): ContractViolationV2 {
        return ContractViolationV2(
            path = path,
            kind = kind,
            expected = shapeOf(node),
            actual = actual,
        )
    }

    private fun violationForShape(
        path: List<PathSegmentV2>,
        expected: ValueShape,
        actual: ValueShape?,
    ): ContractViolationV2 {
        return ContractViolationV2(
            path = renderPath(path),
            kind = ContractViolationKindV2.SHAPE_MISMATCH,
            expected = expected,
            actual = actual,
        )
    }

    private fun shapeOf(node: Any): ValueShape? = when (node) {
        is RequirementNodeV2 -> node.shape
        is GuaranteeNodeV2 -> node.shape
        else -> null
    }

    private fun requirementNodeIsOpen(node: RequirementNodeV2): Boolean = when (val shape = node.shape) {
        is ValueShape.ObjectShape -> !shape.closed
        else -> node.open
    }

    private fun guaranteeNodeIsOpen(node: GuaranteeNodeV2): Boolean = when (val shape = node.shape) {
        is ValueShape.ObjectShape -> !shape.closed
        else -> node.open
    }

    private fun matchesShape(expected: ValueShape, value: Any?): Boolean = when (expected) {
        ValueShape.Unknown -> true
        ValueShape.Null -> value == null
        ValueShape.BooleanShape -> value is Boolean
        ValueShape.NumberShape -> isNumericValue(value)
        ValueShape.Bytes -> value is ByteArray
        ValueShape.TextShape -> value is String
        is ValueShape.ArrayShape -> value is List<*> || value is Array<*>
        is ValueShape.SetShape -> value is Set<*>
        is ValueShape.ObjectShape -> value is Map<*, *>
        is ValueShape.Union -> expected.options.any { option -> matchesShape(option, value) }
    }

    private fun isNumericValue(value: Any?): Boolean = value is Number

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

    private fun renderRequirementExpr(path: List<PathSegmentV2>, expr: RequirementExprV2): String {
        val root = renderPath(path)
        return "$root requires ${renderExpr(expr)}"
    }

    private fun renderExpr(expr: RequirementExprV2): String = when (expr) {
        is RequirementExprV2.AllOf -> expr.children.joinToString(" and ") { child -> renderExpr(child) }
        is RequirementExprV2.AnyOf -> expr.children.joinToString(" or ") { child -> renderExpr(child) }
        is RequirementExprV2.PathPresent -> renderAccessPath("input", expr.path)
        is RequirementExprV2.PathNonNull -> "${renderAccessPath("input", expr.path)} != null"
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
        if (!present) return ResolvedPathValue(false)
        if (!requireNonNull) return ResolvedPathValue(true)
        return ResolvedPathValue(current != null)
    }

    private fun appendField(path: List<PathSegmentV2>, name: String): List<PathSegmentV2> =
        path + PathSegmentV2.Field(name)

    private fun appendIndex(path: List<PathSegmentV2>, index: Int): List<PathSegmentV2> =
        path + PathSegmentV2.Index(index)

    private fun renderPath(path: List<PathSegmentV2>): String {
        val builder = StringBuilder()
        path.forEachIndexed { index, segment ->
            when (segment) {
                is PathSegmentV2.Root -> builder.append(segment.name)
                is PathSegmentV2.Field -> {
                    if (index > 0) builder.append('.')
                    builder.append(segment.name)
                }
                is PathSegmentV2.Index -> builder.append('[').append(segment.index).append(']')
            }
        }
        return builder.toString()
    }

    private fun sortViolations(items: List<ContractViolationV2>): List<ContractViolationV2> =
        items.sortedWith(compareBy({ it.path }, { it.kind.name }, { it.ruleId ?: "" }))

    private data class ResolvedPathValue(val present: Boolean)
}

private sealed interface PathSegmentV2 {
    data class Root(val name: String) : PathSegmentV2
    data class Field(val name: String) : PathSegmentV2
    data class Index(val index: Int) : PathSegmentV2
}

public fun formatContractViolationV2(violation: ContractViolationV2): String {
    val expected = violation.expected?.let(::renderValueShapeLabel)
    val actual = violation.actual?.let(::renderValueShapeLabel)
    val rule = violation.ruleId?.let { " [rule=$it]" } ?: ""
    val suffix = rule
    return when (violation.kind) {
        ContractViolationKindV2.MISSING_REQUIRED_PATH -> "Missing required path at ${violation.path}$suffix"
        ContractViolationKindV2.MISSING_CONDITIONAL_GROUP -> "Missing conditional requirement at ${violation.path}$suffix"
        ContractViolationKindV2.UNEXPECTED_FIELD -> "Unexpected field at ${violation.path}$suffix"
        ContractViolationKindV2.SHAPE_MISMATCH -> "Expected ${expected ?: "unknown"} at ${violation.path}, got ${actual ?: "unknown"}$suffix"
        ContractViolationKindV2.NULLABILITY_MISMATCH -> "Nullability mismatch at ${violation.path}$suffix"
        ContractViolationKindV2.OPAQUE_REGION_WARNING -> "Opaque region at ${violation.path}: ${violation.hints.joinToString("; ")}$suffix"
    }
}

public fun formatContractViolationsV2(violations: List<ContractViolationV2>): String {
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
