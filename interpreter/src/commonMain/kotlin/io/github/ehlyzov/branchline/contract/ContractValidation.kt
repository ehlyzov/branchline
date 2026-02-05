package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import kotlin.math.min

public enum class ContractValidationMode {
    OFF,
    WARN,
    STRICT,
    ;

    public companion object {
        public fun parse(value: String?): ContractValidationMode = when (value?.trim()?.lowercase()) {
            null, "", "off" -> OFF
            "warn", "warning" -> WARN
            "strict", "error" -> STRICT
            else -> throw IllegalArgumentException("Unknown contract validation mode '$value'")
        }
    }
}

public enum class ContractViolationKind {
    MISSING_FIELD,
    EXTRA_FIELD,
    TYPE_MISMATCH,
    EXPECTED_OBJECT,
    OUTPUT_NULL,
}

public data class ContractViolation(
    val path: String,
    val expected: ValueShape?,
    val actual: ValueShape?,
    val kind: ContractViolationKind,
)

public data class ContractValidationResult(
    val violations: List<ContractViolation>,
) {
    public val isValid: Boolean
        get() = violations.isEmpty()
}

public class ContractViolationException(
    public val violations: List<ContractViolation>,
) : RuntimeException(formatContractViolations(violations))

public object ContractEnforcer {
    private val validator = ContractValidator()

    public fun enforceInput(
        mode: ContractValidationMode,
        requirement: SchemaRequirement,
        value: Any?,
    ): List<ContractViolation> = enforce(mode, validator.validateInput(requirement, value))

    public fun enforceOutput(
        mode: ContractValidationMode,
        guarantee: SchemaGuarantee,
        value: Any?,
    ): List<ContractViolation> = enforce(mode, validator.validateOutput(guarantee, value))

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
    public fun validateInput(requirement: SchemaRequirement, value: Any?): ContractValidationResult {
        val violations = mutableListOf<ContractViolation>()
        validateSchemaRequirement(requirement, value, listOf(PathSegment.Root("input")), violations)
        return ContractValidationResult(sortViolations(violations))
    }

    public fun validateOutput(guarantee: SchemaGuarantee, value: Any?): ContractValidationResult {
        val violations = mutableListOf<ContractViolation>()
        validateOutputValue(guarantee, value, listOf(PathSegment.Root("output")), violations)
        return ContractValidationResult(sortViolations(violations))
    }

    private fun validateOutputValue(
        guarantee: SchemaGuarantee,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        if (value == null) {
            if (!guarantee.mayEmitNull) {
                violations += ContractViolation(
                    path = renderPath(path),
                    expected = ValueShape.ObjectShape(guarantee, closed = false),
                    actual = ValueShape.Null,
                    kind = ContractViolationKind.OUTPUT_NULL,
                )
            }
            return
        }
        when (value) {
            is Map<*, *> -> validateSchemaGuarantee(guarantee, value, path, closed = false, violations = violations)
            is Iterable<*> -> validateOutputCollection(guarantee, value, path, violations)
            is Array<*> -> validateOutputCollection(guarantee, value.asIterable(), path, violations)
            else -> {
                violations += ContractViolation(
                    path = renderPath(path),
                    expected = ValueShape.ObjectShape(guarantee, closed = false),
                    actual = valueShapeOf(value),
                    kind = ContractViolationKind.EXPECTED_OBJECT,
                )
            }
        }
    }

    private fun validateOutputCollection(
        guarantee: SchemaGuarantee,
        value: Iterable<*>,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        var index = 0
        for (item in value) {
            val itemPath = appendIndex(path, index)
            if (item is Map<*, *> || item == null) {
                validateOutputValue(guarantee, item, itemPath, violations)
            } else {
                violations += ContractViolation(
                    path = renderPath(itemPath),
                    expected = ValueShape.ObjectShape(guarantee, closed = false),
                    actual = valueShapeOf(item),
                    kind = ContractViolationKind.EXPECTED_OBJECT,
                )
            }
            index += 1
        }
    }

    private fun validateSchemaRequirement(
        requirement: SchemaRequirement,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        val obj = value as? Map<*, *>
        if (obj == null) {
            if (requirement.fields.isNotEmpty() || !requirement.open) {
                violations += ContractViolation(
                    path = renderPath(path),
                    expected = ValueShape.ObjectShape(
                        schema = SchemaGuarantee(
                            fields = LinkedHashMap(),
                            mayEmitNull = false,
                            dynamicFields = emptyList(),
                        ),
                        closed = false,
                    ),
                    actual = valueShapeOf(value),
                    kind = ContractViolationKind.EXPECTED_OBJECT,
                )
            }
            return
        }
        val resolvedFields = obj.entries.associate { entry ->
            entry.key?.toString() to entry.value
        }
        for ((name, constraint) in requirement.fields) {
            if (constraint.required && !resolvedFields.containsKey(name)) {
                violations += ContractViolation(
                    path = renderPath(appendField(path, name)),
                    expected = constraint.shape,
                    actual = null,
                    kind = ContractViolationKind.MISSING_FIELD,
                )
            }
        }
        if (!requirement.open) {
            for (fieldName in resolvedFields.keys) {
                if (fieldName == null) continue
                if (!requirement.fields.containsKey(fieldName)) {
                    violations += ContractViolation(
                        path = renderPath(appendField(path, fieldName)),
                        expected = null,
                        actual = valueShapeOf(resolvedFields[fieldName]),
                        kind = ContractViolationKind.EXTRA_FIELD,
                    )
                }
            }
        }
        for ((name, constraint) in requirement.fields) {
            if (!resolvedFields.containsKey(name)) continue
            val fieldPath = appendField(path, name)
            validateValueShape(constraint.shape, resolvedFields[name], fieldPath, violations)
        }
    }

    private fun validateSchemaGuarantee(
        guarantee: SchemaGuarantee,
        value: Map<*, *>,
        path: List<PathSegment>,
        closed: Boolean,
        violations: MutableList<ContractViolation>,
    ) {
        val resolvedFields = value.entries.associate { entry ->
            entry.key?.toString() to entry.value
        }
        for ((name, field) in guarantee.fields) {
            if (field.required && !resolvedFields.containsKey(name)) {
                violations += ContractViolation(
                    path = renderPath(appendField(path, name)),
                    expected = field.shape,
                    actual = null,
                    kind = ContractViolationKind.MISSING_FIELD,
                )
            }
        }
        if (closed) {
            for (fieldName in resolvedFields.keys) {
                if (fieldName == null) continue
                if (!guarantee.fields.containsKey(fieldName)) {
                    violations += ContractViolation(
                        path = renderPath(appendField(path, fieldName)),
                        expected = null,
                        actual = valueShapeOf(resolvedFields[fieldName]),
                        kind = ContractViolationKind.EXTRA_FIELD,
                    )
                }
            }
        }
        for ((name, field) in guarantee.fields) {
            if (!resolvedFields.containsKey(name)) continue
            val fieldPath = appendField(path, name)
            validateValueShape(field.shape, resolvedFields[name], fieldPath, violations)
        }
    }

    private fun validateValueShape(
        expected: ValueShape,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        when (expected) {
            ValueShape.Unknown -> return
            ValueShape.Null -> if (value != null) {
                violations += typeMismatch(path, expected, value)
            }
            ValueShape.BooleanShape -> if (value !is Boolean) {
                violations += typeMismatch(path, expected, value)
            }
            ValueShape.NumberShape -> if (!isNumberValue(value)) {
                violations += typeMismatch(path, expected, value)
            }
            ValueShape.Bytes -> if (value !is ByteArray) {
                violations += typeMismatch(path, expected, value)
            }
            ValueShape.TextShape -> if (value !is String) {
                violations += typeMismatch(path, expected, value)
            }
            is ValueShape.ArrayShape -> validateArray(expected, value, path, violations)
            is ValueShape.ObjectShape -> validateObject(expected, value, path, violations)
            is ValueShape.Union -> validateUnion(expected, value, path, violations)
        }
    }

    private fun validateArray(
        expected: ValueShape.ArrayShape,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        val iterable = when (value) {
            is Iterable<*> -> value
            is Array<*> -> value.asIterable()
            else -> null
        }
        if (iterable == null) {
            violations += typeMismatch(path, expected, value)
            return
        }
        if (expected.element == ValueShape.Unknown) return
        var index = 0
        for (item in iterable) {
            validateValueShape(expected.element, item, appendIndex(path, index), violations)
            index += 1
        }
    }

    private fun validateObject(
        expected: ValueShape.ObjectShape,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        val obj = value as? Map<*, *>
        if (obj == null) {
            violations += typeMismatch(path, expected, value)
            return
        }
        validateSchemaGuarantee(expected.schema, obj, path, expected.closed, violations)
    }

    private fun validateUnion(
        expected: ValueShape.Union,
        value: Any?,
        path: List<PathSegment>,
        violations: MutableList<ContractViolation>,
    ) {
        for (option in expected.options) {
            if (matchesShape(option, value)) {
                return
            }
        }
        violations += typeMismatch(path, expected, value)
    }

    private fun matchesShape(expected: ValueShape, value: Any?): Boolean = when (expected) {
        ValueShape.Unknown -> true
        ValueShape.Null -> value == null
        ValueShape.BooleanShape -> value is Boolean
        ValueShape.NumberShape -> isNumberValue(value)
        ValueShape.Bytes -> value is ByteArray
        ValueShape.TextShape -> value is String
        is ValueShape.ArrayShape -> value is Iterable<*> || value is Array<*>
        is ValueShape.ObjectShape -> value is Map<*, *>
        is ValueShape.Union -> expected.options.any { option -> matchesShape(option, value) }
    }

    private fun typeMismatch(path: List<PathSegment>, expected: ValueShape, value: Any?): ContractViolation =
        ContractViolation(
            path = renderPath(path),
            expected = expected,
            actual = valueShapeOf(value),
            kind = ContractViolationKind.TYPE_MISMATCH,
        )

    private fun isNumberValue(value: Any?): Boolean = when (value) {
        is Number -> true
        is BLBigInt -> true
        is BLBigDec -> true
        is I32 -> true
        is I64 -> true
        is IBig -> true
        is Dec -> true
        else -> false
    }

    private fun valueShapeOf(value: Any?): ValueShape = when (value) {
        null -> ValueShape.Null
        is Boolean -> ValueShape.BooleanShape
        is String -> ValueShape.TextShape
        is Number -> ValueShape.NumberShape
        is BLBigInt -> ValueShape.NumberShape
        is BLBigDec -> ValueShape.NumberShape
        is I32 -> ValueShape.NumberShape
        is I64 -> ValueShape.NumberShape
        is IBig -> ValueShape.NumberShape
        is Dec -> ValueShape.NumberShape
        is ByteArray -> ValueShape.Bytes
        is Map<*, *> -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = LinkedHashMap(),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
        is Iterable<*> -> ValueShape.ArrayShape(ValueShape.Unknown)
        is Array<*> -> ValueShape.ArrayShape(ValueShape.Unknown)
        else -> ValueShape.Unknown
    }

    private fun appendField(path: List<PathSegment>, name: String): List<PathSegment> =
        path + PathSegment.Field(name)

    private fun appendIndex(path: List<PathSegment>, index: Int): List<PathSegment> =
        path + PathSegment.Index(index)

    private fun renderPath(path: List<PathSegment>): String {
        val builder = StringBuilder()
        path.forEachIndexed { idx, segment ->
            when (segment) {
                is PathSegment.Root -> builder.append(segment.name)
                is PathSegment.Field -> {
                    if (idx > 0) builder.append('.')
                    builder.append(segment.name)
                }
                is PathSegment.Index -> builder.append('[').append(segment.index).append(']')
            }
        }
        return builder.toString()
    }

    private fun sortViolations(violations: List<ContractViolation>): List<ContractViolation> =
        violations.sortedWith(compareBy({ it.path }, { it.kind.name }))
}

private sealed interface PathSegment {
    data class Root(val name: String) : PathSegment
    data class Field(val name: String) : PathSegment
    data class Index(val index: Int) : PathSegment
}

public fun formatContractViolation(violation: ContractViolation): String {
    val expected = violation.expected?.let(::renderValueShapeLabel)
    val actual = violation.actual?.let(::renderValueShapeLabel)
    return when (violation.kind) {
        ContractViolationKind.MISSING_FIELD -> "Missing required field at ${violation.path}"
        ContractViolationKind.EXTRA_FIELD -> "Field not declared in contract at ${violation.path}"
        ContractViolationKind.EXPECTED_OBJECT -> "Expected object at ${violation.path}, got ${actual ?: "unknown"}"
        ContractViolationKind.OUTPUT_NULL -> "Output is null but contract does not allow null at ${violation.path}"
        ContractViolationKind.TYPE_MISMATCH -> "Expected ${expected ?: "unknown"} at ${violation.path}, got ${actual ?: "unknown"}"
    }
}

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

public fun renderValueShapeLabel(shape: ValueShape): String = when (shape) {
    ValueShape.Unknown -> "any"
    ValueShape.Null -> "null"
    ValueShape.BooleanShape -> "boolean"
    ValueShape.NumberShape -> "number"
    ValueShape.Bytes -> "bytes"
    ValueShape.TextShape -> "text"
    is ValueShape.ArrayShape -> "array<${renderValueShapeLabel(shape.element)}>"
    is ValueShape.ObjectShape -> "object"
    is ValueShape.Union -> shape.options.joinToString(" | ") { option -> renderValueShapeLabel(option) }
}
