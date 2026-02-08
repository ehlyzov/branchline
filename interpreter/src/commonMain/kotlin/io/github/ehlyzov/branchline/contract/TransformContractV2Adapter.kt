package io.github.ehlyzov.branchline.contract

public object TransformContractV2Adapter {
    public fun fromV1(contract: TransformContract): TransformContractV2 {
        val requirementChildren = LinkedHashMap<String, RequirementNodeV2>()
        for ((name, field) in contract.input.fields) {
            requirementChildren[name] = RequirementNodeV2(
                required = field.required,
                shape = field.shape,
                open = true,
                children = linkedMapOf(),
                evidence = emptyList(),
            )
        }
        val requirementExprs = mutableListOf<RequirementExprV2>()
        for (group in contract.input.requiredAnyOf) {
            val alternatives = group.alternatives.map { path ->
                RequirementExprV2.PathNonNull(path)
            }
            requirementExprs += RequirementExprV2.AnyOf(alternatives)
        }
        val opaqueInput = contract.input.dynamicAccess.map { dynamic ->
            OpaqueRegionV2(dynamic.path, dynamic.reason)
        }
        val input = RequirementSchemaV2(
            root = RequirementNodeV2(
                required = true,
                shape = ValueShape.ObjectShape(
                    schema = SchemaGuarantee(
                        fields = linkedMapOf(),
                        mayEmitNull = false,
                        dynamicFields = emptyList(),
                    ),
                    closed = !contract.input.open,
                ),
                open = contract.input.open,
                children = requirementChildren,
                evidence = emptyList(),
            ),
            requirements = requirementExprs,
            opaqueRegions = opaqueInput,
        )
        val guaranteeChildren = LinkedHashMap<String, GuaranteeNodeV2>()
        for ((name, field) in contract.output.fields) {
            guaranteeChildren[name] = GuaranteeNodeV2(
                required = field.required,
                shape = field.shape,
                open = true,
                origin = field.origin,
                children = linkedMapOf(),
                evidence = emptyList(),
            )
        }
        val opaqueOutput = contract.output.dynamicFields.map { dynamic ->
            OpaqueRegionV2(dynamic.path, dynamic.reason)
        }
        val output = GuaranteeSchemaV2(
            root = GuaranteeNodeV2(
                required = true,
                shape = ValueShape.ObjectShape(contract.output, closed = false),
                open = true,
                origin = OriginKind.MERGED,
                children = guaranteeChildren,
                evidence = emptyList(),
            ),
            mayEmitNull = contract.output.mayEmitNull,
            opaqueRegions = opaqueOutput,
        )
        return TransformContractV2(
            input = input,
            output = output,
            source = contract.source,
            metadata = ContractMetadataV2(),
        )
    }

    public fun toV1(contract: TransformContractV2): TransformContract {
        val inputFields = LinkedHashMap<String, FieldConstraint>()
        for ((name, node) in contract.input.root.children) {
            inputFields[name] = FieldConstraint(
                required = node.required,
                shape = node.shape,
                sourceSpans = emptyList(),
            )
        }
        val requiredAnyOf = mutableListOf<RequiredAnyOfGroup>()
        for (expr in contract.input.requirements) {
            val alternatives = flattenAnyOfAlternatives(expr)
            if (alternatives.isNotEmpty()) {
                requiredAnyOf += RequiredAnyOfGroup(alternatives)
            }
        }
        val dynamicAccess = contract.input.opaqueRegions.map { region ->
            DynamicAccess(path = region.path, valueShape = null, reason = region.reason)
        }
        val input = SchemaRequirement(
            fields = inputFields,
            open = contract.input.root.open,
            dynamicAccess = dynamicAccess,
            requiredAnyOf = requiredAnyOf,
        )
        val outputFields = LinkedHashMap<String, FieldShape>()
        for ((name, node) in contract.output.root.children) {
            outputFields[name] = FieldShape(
                required = node.required,
                shape = node.shape,
                origin = node.origin,
            )
        }
        val dynamicFields = contract.output.opaqueRegions.map { region ->
            DynamicField(path = region.path, valueShape = null, reason = region.reason)
        }
        val output = SchemaGuarantee(
            fields = outputFields,
            mayEmitNull = contract.output.mayEmitNull,
            dynamicFields = dynamicFields,
        )
        return TransformContract(
            input = input,
            output = output,
            source = contract.source,
        )
    }

    private fun flattenAnyOfAlternatives(expr: RequirementExprV2): List<AccessPath> = when (expr) {
        is RequirementExprV2.AnyOf -> expr.children.mapNotNull(::pathFromLeaf)
        else -> emptyList()
    }

    private fun pathFromLeaf(expr: RequirementExprV2): AccessPath? = when (expr) {
        is RequirementExprV2.PathPresent -> expr.path
        is RequirementExprV2.PathNonNull -> expr.path
        else -> null
    }
}
