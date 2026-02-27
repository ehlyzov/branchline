package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.formatCanonicalJson
import io.github.ehlyzov.branchline.runtime.base64Decode

public object ContractCoercion {
    public fun coerceInputBytes(
        requirement: AnalysisRequirementSchema,
        value: Map<String, Any?>,
    ): Map<String, Any?> {
        val coerced = coerceObjectFieldsFromRequirementNode(value, requirement.root)
        @Suppress("UNCHECKED_CAST")
        return coerced as Map<String, Any?>
    }

    public fun coerceInputBytes(
        requirement: SchemaRequirement,
        value: Map<String, Any?>,
    ): Map<String, Any?> {
        val coerced = coerceObjectFieldsFromConstraints(value, requirement.fields)
        @Suppress("UNCHECKED_CAST")
        return coerced as Map<String, Any?>
    }

    public fun coerceInputBytes(
        requirement: RequirementSchema,
        value: Map<String, Any?>,
    ): Map<String, Any?> {
        val coerced = coerceValueForNode(value, requirement.root)
        @Suppress("UNCHECKED_CAST")
        return (coerced as? Map<Any?, Any?> ?: value) as Map<String, Any?>
    }

    private fun coerceObjectFieldsFromConstraints(
        value: Map<*, *>,
        fields: Map<String, FieldConstraint>,
    ): Map<Any?, Any?> = coerceObjectFields(value) { key -> fields[key]?.shape }

    private fun coerceObjectFieldsFromRequirementNode(
        value: Map<*, *>,
        node: AnalysisRequirementNode,
    ): Map<Any?, Any?> {
        var changed = false
        val out = LinkedHashMap<Any?, Any?>(value.size)
        for ((rawKey, rawValue) in value) {
            val keyName = rawKey as? String
            val child = if (keyName == null) null else node.children[keyName]
            val coerced = if (child == null) rawValue else coerceValueForRequirementNode(rawValue, child)
            if (!changed && coerced !== rawValue) {
                changed = true
            }
            out[rawKey] = coerced
        }
        if (changed) return out
        @Suppress("UNCHECKED_CAST")
        return value as Map<Any?, Any?>
    }

    private fun coerceObjectFieldsFromShapes(
        value: Map<*, *>,
        fields: Map<String, FieldShape>,
    ): Map<Any?, Any?> = coerceObjectFields(value) { key -> fields[key]?.shape }

    private fun coerceObjectFields(
        value: Map<*, *>,
        shapeForKey: (String) -> ValueShape?,
    ): Map<Any?, Any?> {
        var changed = false
        val out = LinkedHashMap<Any?, Any?>(value.size)
        for ((rawKey, rawValue) in value) {
            val keyName = rawKey as? String
            val shape = if (keyName == null) null else shapeForKey(keyName)
            val coerced = if (shape == null) rawValue else coerceValue(rawValue, shape)
            if (!changed && coerced !== rawValue) {
                changed = true
            }
            out[rawKey] = coerced
        }
        if (changed) return out
        @Suppress("UNCHECKED_CAST")
        return value as Map<Any?, Any?>
    }

    private fun coerceValue(value: Any?, shape: ValueShape): Any? = when (shape) {
        ValueShape.Never -> value
        ValueShape.Bytes -> coerceBytes(value)
        is ValueShape.ArrayShape -> coerceArray(value, shape.element)
        is ValueShape.SetShape -> coerceSet(value, shape.element)
        is ValueShape.ObjectShape -> coerceObject(value, shape)
        is ValueShape.Union -> coerceUnion(value, shape)
        else -> value
    }

    private fun coerceValueForRequirementNode(
        value: Any?,
        node: AnalysisRequirementNode,
    ): Any? {
        val coerced = coerceValue(value, node.shape)
        if (node.children.isEmpty()) return coerced
        val obj = coerced as? Map<*, *> ?: return coerced
        return coerceObjectFieldsFromRequirementNode(obj, node)
    }

    private fun coerceValueForNode(value: Any?, node: Node): Any? = when (node.kind) {
        NodeKind.BYTES -> coerceBytes(value)
        NodeKind.ARRAY -> {
            val elementShape = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown
            coerceArray(value, elementShape)
        }
        NodeKind.SET -> {
            val elementShape = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown
            coerceSet(value, elementShape)
        }
        NodeKind.OBJECT -> {
            val obj = value as? Map<*, *> ?: return value
            var changed = false
            val out = LinkedHashMap<Any?, Any?>(obj.size)
            for ((rawKey, rawValue) in obj) {
                val keyName = rawKey as? String
                val child = if (keyName == null) null else node.children[keyName]
                val coerced = if (child == null) rawValue else coerceValueForNode(rawValue, child)
                if (!changed && coerced !== rawValue) {
                    changed = true
                }
                out[rawKey] = coerced
            }
            if (changed) out else value
        }
        NodeKind.UNION -> {
            val options = ValueShape.Union(node.options.map(::shapeFromNode))
            coerceUnion(value, options)
        }
        else -> value
    }

    private fun coerceBytes(value: Any?): Any? {
        if (value !is String) return value
        return try {
            base64Decode(value)
        } catch (_: IllegalArgumentException) {
            value
        }
    }

    private fun coerceArray(value: Any?, element: ValueShape): Any? {
        val iterable = when (value) {
            is Iterable<*> -> value
            is Array<*> -> value.asIterable()
            else -> return value
        }
        var changed = false
        val out = ArrayList<Any?>(iterable.count())
        for (item in iterable) {
            val coerced = coerceValue(item, element)
            if (!changed && coerced !== item) {
                changed = true
            }
            out.add(coerced)
        }
        return if (changed) out else value
    }

    private fun coerceSet(value: Any?, element: ValueShape): Any? {
        val iterable = when (value) {
            is Set<*> -> value
            is Iterable<*> -> value
            is Array<*> -> value.asIterable()
            else -> return value
        }
        val out = LinkedHashSet<Any?>()
        val seen = LinkedHashSet<String>()
        var changed = value !is Set<*>
        for (item in iterable) {
            val coerced = coerceValue(item, element)
            val key = formatCanonicalJson(coerced, JsonNumberMode.SAFE)
            if (!seen.add(key)) {
                changed = true
                continue
            }
            if (!changed && coerced !== item) {
                changed = true
            }
            out.add(coerced)
        }
        if (!changed && value is Set<*> && out.size == value.size) return value
        return out
    }

    private fun coerceObject(value: Any?, shape: ValueShape.ObjectShape): Any? {
        val obj = value as? Map<*, *> ?: return value
        val fields = shape.schema.fields
        return coerceObjectFieldsFromShapes(obj, fields)
    }

    private fun coerceUnion(value: Any?, shape: ValueShape.Union): Any? {
        val hasText = shape.options.any { it == ValueShape.TextShape }
        if (!hasText) {
            val decoded = coerceBytes(value)
            if (decoded !== value) return decoded
        }
        for (option in shape.options) {
            val coerced = coerceValue(value, option)
            if (coerced !== value) return coerced
        }
        return value
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
}
