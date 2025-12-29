package v2.contract

import v2.ArrayTypeRef
import v2.EnumTypeRef
import v2.NamedTypeRef
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.RecordTypeRef
import v2.TransformDecl
import v2.TransformSignature
import v2.TypeRef
import v2.UnionTypeRef
import v2.sema.TypeResolver
import v2.sema.TransformShapeSynthesizer

public class TransformContractBuilder(
    private val typeResolver: TypeResolver,
    hostFns: Set<String> = emptySet(),
) {
    private val synthesizer = TransformShapeSynthesizer(hostFns)

    public fun build(transform: TransformDecl): TransformContract {
        return buildExplicitContract(transform) ?: synthesizer.synthesize(transform)
    }

    public fun buildInferredContract(transform: TransformDecl): TransformContract =
        synthesizer.synthesize(transform)

    public fun buildExplicitContract(transform: TransformDecl): TransformContract? {
        val signature = transform.signature ?: return null
        return buildExplicitContract(signature)
    }

    public fun buildExplicitContract(signature: TransformSignature): TransformContract {
        val inputRequirement = signature.input?.let { inputFromTypeRef(it) }
            ?: SchemaRequirement(
                fields = linkedMapOf(),
                open = true,
                dynamicAccess = emptyList(),
            )
        val outputGuarantee = signature.output?.let { outputFromTypeRef(it) }
            ?: SchemaGuarantee(
                fields = linkedMapOf(),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            )
        return TransformContract(
            input = inputRequirement,
            output = outputGuarantee,
            source = ContractSource.EXPLICIT,
        )
    }

    private fun inputFromTypeRef(typeRef: TypeRef): SchemaRequirement {
        val resolved = typeResolver.resolve(typeRef)
        if (resolved !is RecordTypeRef) {
            return SchemaRequirement(
                fields = linkedMapOf(),
                open = true,
                dynamicAccess = emptyList(),
            )
        }
        val fields = LinkedHashMap<String, FieldConstraint>()
        resolved.fields.forEach { field ->
            fields[field.name] = FieldConstraint(
                required = !field.optional,
                shape = shapeFromTypeRef(field.type),
                sourceSpans = listOf(field.token),
            )
        }
        return SchemaRequirement(
            fields = fields,
            open = false,
            dynamicAccess = emptyList(),
        )
    }

    private fun outputFromTypeRef(typeRef: TypeRef): SchemaGuarantee {
        val resolved = typeResolver.resolve(typeRef)
        if (resolved !is RecordTypeRef) {
            if (resolved is UnionTypeRef) {
                return outputFromUnion(resolved)
            }
            val shape = shapeFromTypeRef(resolved)
            return SchemaGuarantee(
                fields = linkedMapOf(),
                mayEmitNull = shapeMayBeNull(shape),
                dynamicFields = emptyList(),
            )
        }
        return SchemaGuarantee(
            fields = outputFieldsFromRecord(resolved),
            mayEmitNull = false,
            dynamicFields = emptyList(),
        )
    }

    private fun outputFromUnion(typeRef: UnionTypeRef): SchemaGuarantee {
        val resolvedMembers = typeRef.members.map { member -> typeResolver.resolve(member) }
        val nonNullMembers = resolvedMembers.filterNot { member ->
            member is PrimitiveTypeRef && member.kind == PrimitiveType.NULL
        }
        val mayEmitNull = resolvedMembers.any { member -> shapeMayBeNull(shapeFromTypeRef(member)) }
        val record = nonNullMembers.singleOrNull() as? RecordTypeRef
        if (record != null) {
            return SchemaGuarantee(
                fields = outputFieldsFromRecord(record),
                mayEmitNull = mayEmitNull,
                dynamicFields = emptyList(),
            )
        }
        return SchemaGuarantee(
            fields = linkedMapOf(),
            mayEmitNull = mayEmitNull,
            dynamicFields = emptyList(),
        )
    }

    private fun outputFieldsFromRecord(typeRef: RecordTypeRef): LinkedHashMap<String, FieldShape> {
        val fields = LinkedHashMap<String, FieldShape>()
        typeRef.fields.forEach { field ->
            fields[field.name] = FieldShape(
                required = !field.optional,
                shape = shapeFromTypeRef(field.type),
                origin = OriginKind.OUTPUT,
            )
        }
        return fields
    }

    private fun shapeFromTypeRef(typeRef: TypeRef): ValueShape = when (val resolved = typeResolver.resolve(typeRef)) {
        is PrimitiveTypeRef -> when (resolved.kind) {
            PrimitiveType.TEXT -> ValueShape.TextShape
            PrimitiveType.NUMBER -> ValueShape.NumberShape
            PrimitiveType.BOOLEAN -> ValueShape.BooleanShape
            PrimitiveType.NULL -> ValueShape.Null
            PrimitiveType.ANY -> ValueShape.Unknown
            PrimitiveType.ANY_NULLABLE -> ValueShape.Union(listOf(ValueShape.Unknown, ValueShape.Null))
        }
        is EnumTypeRef -> ValueShape.TextShape
        is ArrayTypeRef -> ValueShape.ArrayShape(shapeFromTypeRef(resolved.elementType))
        is RecordTypeRef -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = outputFieldsFromRecord(resolved),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = true,
        )
        is UnionTypeRef -> ValueShape.Union(resolved.members.map { member -> shapeFromTypeRef(member) })
        is NamedTypeRef -> ValueShape.Unknown
    }

    private fun shapeMayBeNull(shape: ValueShape): Boolean = when (shape) {
        ValueShape.Null -> true
        is ValueShape.Union -> shape.options.any { option -> shapeMayBeNull(option) }
        else -> false
    }
}
