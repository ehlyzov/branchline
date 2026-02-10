package io.github.ehlyzov.branchline.contract

public object TransformContractV3Adapter {
    public fun fromV2(contract: TransformContractV2): TransformContractV3 {
        val inputObligations = contract.input.requirements.map { expr ->
            ContractObligationV3(
                expr = requirementExprToV3(expr),
                confidence = 1.0,
                ruleId = "v2-requirement",
                heuristic = false,
            )
        }
        return TransformContractV3(
            input = RequirementSchemaV3(
                root = requirementNodeToV3(contract.input.root),
                obligations = inputObligations,
                opaqueRegions = contract.input.opaqueRegions,
                evidence = emptyList(),
            ),
            output = GuaranteeSchemaV3(
                root = guaranteeNodeToV3(contract.output.root),
                obligations = emptyList(),
                mayEmitNull = contract.output.mayEmitNull,
                opaqueRegions = contract.output.opaqueRegions,
                evidence = emptyList(),
            ),
            source = contract.source,
            metadata = ContractMetadataV3(
                runtimeFit = contract.metadata.runtimeFit,
                typeEval = contract.metadata.typeEval,
                inference = contract.metadata.inference,
            ),
        )
    }

    public fun toV2(contract: TransformContractV3): TransformContractV2 {
        val requirements = contract.input.obligations.mapNotNull { obligation ->
            obligationToRequirementExpr(obligation.expr)
        }
        return TransformContractV2(
            input = RequirementSchemaV2(
                root = requirementNodeToV2(contract.input.root),
                requirements = requirements,
                opaqueRegions = contract.input.opaqueRegions,
            ),
            output = GuaranteeSchemaV2(
                root = guaranteeNodeToV2(contract.output.root),
                mayEmitNull = contract.output.mayEmitNull,
                opaqueRegions = contract.output.opaqueRegions,
            ),
            source = contract.source,
            metadata = ContractMetadataV2(
                runtimeFit = contract.metadata.runtimeFit,
                typeEval = contract.metadata.typeEval,
                inference = contract.metadata.inference,
            ),
        )
    }

    private fun requirementExprToV3(expr: RequirementExprV2): ConstraintExprV3 = when (expr) {
        is RequirementExprV2.AllOf -> ConstraintExprV3.AllOf(expr.children.map(::requirementExprToV3))
        is RequirementExprV2.AnyOf -> ConstraintExprV3.OneOf(expr.children.map(::requirementExprToV3))
        is RequirementExprV2.PathPresent -> ConstraintExprV3.PathPresent(expr.path)
        is RequirementExprV2.PathNonNull -> ConstraintExprV3.PathNonNull(expr.path)
    }

    private fun obligationToRequirementExpr(expr: ConstraintExprV3): RequirementExprV2? = when (expr) {
        is ConstraintExprV3.PathPresent -> RequirementExprV2.PathPresent(expr.path)
        is ConstraintExprV3.PathNonNull -> RequirementExprV2.PathNonNull(expr.path)
        is ConstraintExprV3.OneOf -> RequirementExprV2.AnyOf(expr.children.mapNotNull(::obligationToRequirementExpr))
        is ConstraintExprV3.AllOf -> RequirementExprV2.AllOf(expr.children.mapNotNull(::obligationToRequirementExpr))
        is ConstraintExprV3.ForAll -> null
        is ConstraintExprV3.Exists -> null
        is ConstraintExprV3.ValueDomain -> null
    }

    private fun requirementNodeToV3(node: RequirementNodeV2): NodeV3 {
        val fromShape = nodeFromShape(node.shape, node.required, null)
        val mergedChildren = LinkedHashMap(fromShape.children)
        node.children.forEach { (name, child) ->
            val next = requirementNodeToV3(child)
            val previous = mergedChildren[name]
            mergedChildren[name] = if (previous == null) next else mergeNode(
                previous,
                next,
                normalizeAnyWithChildren = false,
            )
        }
        return fromShape.copy(
            open = node.open,
            children = mergedChildren,
        )
    }

    private fun guaranteeNodeToV3(node: GuaranteeNodeV2): NodeV3 {
        val fromShape = nodeFromShape(node.shape, node.required, node.origin)
        val mergedChildren = LinkedHashMap(fromShape.children)
        node.children.forEach { (name, child) ->
            val next = guaranteeNodeToV3(child)
            val previous = mergedChildren[name]
            mergedChildren[name] = if (previous == null) next else mergeNode(
                previous,
                next,
                normalizeAnyWithChildren = true,
            )
        }
        return normalizeAnyNodeWithChildren(fromShape.copy(
            open = node.open,
            children = mergedChildren,
            origin = node.origin,
        ))
    }

    private fun requirementNodeToV2(node: NodeV3): RequirementNodeV2 {
        val children = LinkedHashMap<String, RequirementNodeV2>()
        node.children.forEach { (name, child) ->
            children[name] = requirementNodeToV2(child)
        }
        return RequirementNodeV2(
            required = node.required,
            shape = shapeFromNode(node),
            open = node.open,
            children = children,
            evidence = emptyList(),
        )
    }

    private fun guaranteeNodeToV2(node: NodeV3): GuaranteeNodeV2 {
        val children = LinkedHashMap<String, GuaranteeNodeV2>()
        node.children.forEach { (name, child) ->
            children[name] = guaranteeNodeToV2(child)
        }
        return GuaranteeNodeV2(
            required = node.required,
            shape = shapeFromNode(node),
            open = node.open,
            origin = node.origin ?: OriginKind.MERGED,
            children = children,
            evidence = emptyList(),
        )
    }

    private fun mergeNode(
        left: NodeV3,
        right: NodeV3,
        normalizeAnyWithChildren: Boolean,
    ): NodeV3 {
        val childNames = left.children.keys + right.children.keys
        val children = LinkedHashMap<String, NodeV3>()
        for (name in childNames) {
            val l = left.children[name]
            val r = right.children[name]
            children[name] = when {
                l == null -> r!!
                r == null -> l
                else -> mergeNode(l, r, normalizeAnyWithChildren)
            }
        }
        val options = (left.options + right.options).distinct()
        val merged = left.copy(
            required = left.required || right.required,
            open = left.open && right.open,
            children = children,
            domains = (left.domains + right.domains).distinct(),
            options = options,
            origin = left.origin ?: right.origin,
        )
        return if (normalizeAnyWithChildren) normalizeAnyNodeWithChildren(merged) else merged
    }

    private fun normalizeAnyNodeWithChildren(node: NodeV3): NodeV3 {
        if (node.kind != NodeKindV3.ANY || node.children.isEmpty()) {
            return node
        }
        return node.copy(
            kind = NodeKindV3.OBJECT,
            open = true,
        )
    }

    private fun nodeFromShape(shape: ValueShape, required: Boolean, origin: OriginKind?): NodeV3 = when (shape) {
        ValueShape.Never -> NodeV3(required = required, kind = NodeKindV3.NEVER, origin = origin)
        ValueShape.Unknown -> NodeV3(required = required, kind = NodeKindV3.ANY, origin = origin)
        ValueShape.Null -> NodeV3(required = required, kind = NodeKindV3.NULL, origin = origin)
        ValueShape.BooleanShape -> NodeV3(required = required, kind = NodeKindV3.BOOLEAN, origin = origin)
        ValueShape.NumberShape -> NodeV3(required = required, kind = NodeKindV3.NUMBER, origin = origin)
        ValueShape.Bytes -> NodeV3(required = required, kind = NodeKindV3.BYTES, origin = origin)
        ValueShape.TextShape -> NodeV3(required = required, kind = NodeKindV3.TEXT, origin = origin)
        is ValueShape.ArrayShape -> NodeV3(
            required = required,
            kind = NodeKindV3.ARRAY,
            element = nodeFromShape(shape.element, required = true, origin = origin),
            origin = origin,
        )
        is ValueShape.SetShape -> NodeV3(
            required = required,
            kind = NodeKindV3.SET,
            element = nodeFromShape(shape.element, required = true, origin = origin),
            origin = origin,
        )
        is ValueShape.Union -> NodeV3(
            required = required,
            kind = NodeKindV3.UNION,
            options = shape.options.map { option -> nodeFromShape(option, required = true, origin = origin) },
            origin = origin,
        )
        is ValueShape.ObjectShape -> {
            val children = LinkedHashMap<String, NodeV3>()
            for ((name, field) in shape.schema.fields) {
                children[name] = nodeFromShape(field.shape, field.required, field.origin)
            }
            NodeV3(
                required = required,
                kind = NodeKindV3.OBJECT,
                open = !shape.closed,
                children = children,
                origin = origin,
            )
        }
    }

    private fun shapeFromNode(node: NodeV3): ValueShape = when (node.kind) {
        NodeKindV3.NEVER -> ValueShape.Never
        NodeKindV3.ANY -> ValueShape.Unknown
        NodeKindV3.NULL -> ValueShape.Null
        NodeKindV3.BOOLEAN -> ValueShape.BooleanShape
        NodeKindV3.NUMBER -> ValueShape.NumberShape
        NodeKindV3.BYTES -> ValueShape.Bytes
        NodeKindV3.TEXT -> ValueShape.TextShape
        NodeKindV3.ARRAY -> ValueShape.ArrayShape(
            element = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown,
        )
        NodeKindV3.SET -> ValueShape.SetShape(
            element = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown,
        )
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
}
