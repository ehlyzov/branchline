package io.github.ehlyzov.branchline.contract

public enum class ContractViolationKind {
    MISSING_REQUIRED_PATH,
    MISSING_CONDITIONAL_GROUP,
    UNEXPECTED_FIELD,
    SHAPE_MISMATCH,
    NULLABILITY_MISMATCH,
    OPAQUE_REGION_WARNING,
}

public data class ContractViolation(
    val path: String,
    val kind: ContractViolationKind,
    val expected: ValueShape? = null,
    val actual: ValueShape? = null,
    val ruleId: String? = null,
    val hints: List<String> = emptyList(),
)

public data class ContractValidationResult(
    val violations: List<ContractViolation>,
) {
    public val isValid: Boolean
        get() = violations.isEmpty()
}

public fun formatContractViolation(violation: ContractViolation): String {
    val shapeInfo = when {
        violation.expected == null && violation.actual == null -> ""
        violation.expected != null && violation.actual != null ->
            " (expected=${renderShape(violation.expected)}, actual=${renderShape(violation.actual)})"
        violation.expected != null -> " (expected=${renderShape(violation.expected)})"
        else -> " (actual=${renderShape(violation.actual!!)})"
    }
    val ruleInfo = violation.ruleId?.let { " [rule=$it]" } ?: ""
    val hintsInfo = if (violation.hints.isEmpty()) "" else " hints=${violation.hints.joinToString("; ")}"
    return "${violation.kind.name} at ${violation.path}$shapeInfo$ruleInfo$hintsInfo"
}

private fun renderShape(shape: ValueShape): String = when (shape) {
    ValueShape.Never -> "never"
    ValueShape.Unknown -> "any"
    ValueShape.Null -> "null"
    ValueShape.BooleanShape -> "boolean"
    ValueShape.NumberShape -> "number"
    ValueShape.Bytes -> "bytes"
    ValueShape.TextShape -> "text"
    is ValueShape.ArrayShape -> "array<${renderShape(shape.element)}>"
    is ValueShape.SetShape -> "set<${renderShape(shape.element)}>"
    is ValueShape.ObjectShape -> "object"
    is ValueShape.Union -> shape.options.joinToString(" | ") { option -> renderShape(option) }
}
