package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.ArrayTypeRef
import io.github.ehlyzov.branchline.EnumTypeRef
import io.github.ehlyzov.branchline.NamedTypeRef
import io.github.ehlyzov.branchline.PrimitiveType
import io.github.ehlyzov.branchline.PrimitiveTypeRef
import io.github.ehlyzov.branchline.RecordTypeRef
import io.github.ehlyzov.branchline.SetTypeRef
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TransformSignature
import io.github.ehlyzov.branchline.TypeRef
import io.github.ehlyzov.branchline.UnionTypeRef
import io.github.ehlyzov.branchline.sema.BinaryTypeEvalRule
import io.github.ehlyzov.branchline.sema.DefaultBinaryTypeEvalRules
import io.github.ehlyzov.branchline.sema.TypeResolver
import io.github.ehlyzov.branchline.sema.TransformShapeSynthesizer
import io.github.ehlyzov.branchline.sema.TransformContractV2Synthesizer

public class TransformContractBuilder(
    private val typeResolver: TypeResolver,
    hostFns: Set<String> = emptySet(),
    binaryTypeEvalRules: List<BinaryTypeEvalRule> = DefaultBinaryTypeEvalRules.rules,
    private val contractFitter: ContractFitterV2 = NoOpContractFitterV2,
) {
    private val synthesizer = TransformShapeSynthesizer(hostFns)
    private val synthesizerV2 = TransformContractV2Synthesizer(hostFns, binaryTypeEvalRules)

    public fun build(transform: TransformDecl): TransformContract {
        return buildExplicitContract(transform) ?: synthesizer.synthesize(transform)
    }

    public fun buildV2(
        transform: TransformDecl,
        runtimeExamples: List<RuntimeContractExampleV2> = emptyList(),
    ): TransformContractV2 {
        val staticContract = buildStaticContractV2(transform)
        if (runtimeExamples.isEmpty()) return staticContract
        return contractFitter.fit(staticContract, runtimeExamples).contract
    }

    private fun buildStaticContractV2(transform: TransformDecl): TransformContractV2 {
        val signature = transform.signature ?: return synthesizerV2.synthesize(transform)
        val outputType = signature.output ?: return TransformContractV2Adapter.fromV1(buildExplicitContract(signature))
        if (!isWildcardOutputType(outputType)) {
            return TransformContractV2Adapter.fromV1(buildExplicitContract(signature))
        }
        val declaredInput = signature.input?.let(::buildDeclaredRequirementSchemaV2)
        val seededInference = synthesizerV2.synthesize(
            transform = transform,
            inputSeedShape = declaredInput?.root?.shape,
        )
        val mergedInput = declaredInput?.let { declared ->
            mergeDeclaredAndInferredInput(declared, seededInference.input)
        } ?: seededInference.input
        return seededInference.copy(
            input = mergedInput,
            metadata = seededInference.metadata.copy(
                inference = seededInference.metadata.inference.copy(
                    inputTypeSeeded = declaredInput != null,
                ),
            ),
        )
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
                requiredAnyOf = emptyList(),
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
                requiredAnyOf = emptyList(),
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
            requiredAnyOf = emptyList(),
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

    private fun isWildcardOutputType(typeRef: TypeRef): Boolean {
        return typeContainsWildcardAny(typeResolver.resolve(typeRef))
    }

    private fun typeContainsWildcardAny(typeRef: TypeRef): Boolean = when (typeRef) {
        is PrimitiveTypeRef -> typeRef.kind == PrimitiveType.ANY || typeRef.kind == PrimitiveType.ANY_NULLABLE
        is UnionTypeRef -> typeRef.members.any { member -> typeContainsWildcardAny(typeResolver.resolve(member)) }
        is NamedTypeRef -> typeContainsWildcardAny(typeResolver.resolve(typeRef))
        else -> false
    }

    private fun buildDeclaredRequirementSchemaV2(typeRef: TypeRef): RequirementSchemaV2 = RequirementSchemaV2(
        root = requirementNodeFromShape(
            shape = shapeFromTypeRef(typeRef),
            required = true,
        ),
        requirements = emptyList(),
        opaqueRegions = emptyList(),
    )

    private fun requirementNodeFromShape(shape: ValueShape, required: Boolean): RequirementNodeV2 {
        if (shape is ValueShape.ObjectShape) {
            val children = LinkedHashMap<String, RequirementNodeV2>()
            for ((name, field) in shape.schema.fields) {
                children[name] = requirementNodeFromShape(
                    shape = field.shape,
                    required = field.required,
                )
            }
            return RequirementNodeV2(
                required = required,
                shape = shape,
                open = !shape.closed,
                children = children,
                evidence = emptyList(),
            )
        }
        return RequirementNodeV2(
            required = required,
            shape = shape,
            open = true,
            children = linkedMapOf(),
            evidence = emptyList(),
        )
    }

    private fun mergeDeclaredAndInferredInput(
        declared: RequirementSchemaV2,
        inferred: RequirementSchemaV2,
    ): RequirementSchemaV2 {
        val mergedOpaque = (declared.opaqueRegions + inferred.opaqueRegions)
            .associateBy { region -> opaqueRegionKey(region.path, region.reason) }
            .values
            .toList()
        return RequirementSchemaV2(
            root = mergeRequirementNodes(declared.root, inferred.root),
            requirements = (declared.requirements + inferred.requirements).distinct(),
            opaqueRegions = mergedOpaque,
        )
    }

    private fun mergeRequirementNodes(
        declared: RequirementNodeV2,
        inferred: RequirementNodeV2,
    ): RequirementNodeV2 {
        val childNames = declared.children.keys + inferred.children.keys
        val mergedChildren = LinkedHashMap<String, RequirementNodeV2>()
        for (name in childNames) {
            val declaredChild = declared.children[name]
            val inferredChild = inferred.children[name]
            mergedChildren[name] = when {
                declaredChild == null -> inferredChild!!
                inferredChild == null -> declaredChild
                else -> mergeRequirementNodes(declaredChild, inferredChild)
            }
        }
        return RequirementNodeV2(
            required = declared.required || inferred.required,
            shape = mergeDeclaredPreferredShape(declared.shape, inferred.shape),
            open = declared.open && inferred.open,
            children = mergedChildren,
            evidence = emptyList(),
        )
    }

    private fun mergeDeclaredPreferredShape(declared: ValueShape, inferred: ValueShape): ValueShape {
        if (declared == ValueShape.Unknown) {
            return inferred
        }
        if (declared is ValueShape.Union && declared.options.any { option -> option == ValueShape.Unknown }) {
            return narrowNullableAnyUnion(declared, inferred)
        }
        return declared
    }

    private fun narrowNullableAnyUnion(declared: ValueShape.Union, inferred: ValueShape): ValueShape {
        val keepsNull = declared.options.any { option -> option == ValueShape.Null }
        val filtered = declared.options.filterNot { option ->
            option == ValueShape.Unknown || option == ValueShape.Null
        }
        val mergedOptions = LinkedHashSet<ValueShape>()
        mergedOptions += inferred
        if (keepsNull && !shapeMayBeNull(inferred)) {
            mergedOptions += ValueShape.Null
        }
        mergedOptions += filtered
        return when (mergedOptions.size) {
            0 -> ValueShape.Unknown
            1 -> mergedOptions.first()
            else -> ValueShape.Union(mergedOptions.toList())
        }
    }

    private fun opaqueRegionKey(path: AccessPath, reason: DynamicReason): String {
        val pathKey = path.segments.joinToString(".") { segment ->
            when (segment) {
                is AccessSegment.Field -> segment.name
                is AccessSegment.Index -> segment.index
                AccessSegment.Dynamic -> "*"
            }
        }
        return "$pathKey|${reason.name}"
    }

    private fun shapeFromTypeRef(typeRef: TypeRef): ValueShape = when (val resolved = typeResolver.resolve(typeRef)) {
        is PrimitiveTypeRef -> when (resolved.kind) {
            PrimitiveType.TEXT -> ValueShape.TextShape
            PrimitiveType.BYTES -> ValueShape.Bytes
            PrimitiveType.NUMBER -> ValueShape.NumberShape
            PrimitiveType.BOOLEAN -> ValueShape.BooleanShape
            PrimitiveType.NULL -> ValueShape.Null
            PrimitiveType.ANY -> ValueShape.Unknown
            PrimitiveType.ANY_NULLABLE -> ValueShape.Union(listOf(ValueShape.Unknown, ValueShape.Null))
        }
        is EnumTypeRef -> ValueShape.TextShape
        is ArrayTypeRef -> ValueShape.ArrayShape(shapeFromTypeRef(resolved.elementType))
        is SetTypeRef -> ValueShape.SetShape(shapeFromTypeRef(resolved.elementType))
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
