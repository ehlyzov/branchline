package io.github.ehlyzov.branchline.contract

public object TransformContractAdapter {
    public fun fromAnalysis(contract: AnalysisContract): TransformContract {
        val inputObligations = contract.input.requirements.map { expr ->
            ContractObligation(
                expr = requirementExprFromAnalysis(expr),
                confidence = 1.0,
                ruleId = "analysis-requirement",
                heuristic = false,
            )
        }
        return TransformContract(
            input = RequirementSchema(
                root = requirementNodeFromAnalysis(contract.input.root),
                obligations = inputObligations,
                opaqueRegions = contract.input.opaqueRegions,
                evidence = emptyList(),
            ),
            output = GuaranteeSchema(
                root = guaranteeNodeFromAnalysis(contract.output.root),
                obligations = emptyList(),
                mayEmitNull = contract.output.mayEmitNull,
                opaqueRegions = contract.output.opaqueRegions,
                evidence = emptyList(),
            ),
            source = contract.source,
            metadata = ContractMetadata(
                runtimeFit = contract.metadata.runtimeFit,
                typeEval = contract.metadata.typeEval,
                inference = contract.metadata.inference,
            ),
        )
    }

    public fun toAnalysis(contract: TransformContract): AnalysisContract {
        val requirements = contract.input.obligations.mapNotNull { obligation ->
            obligationToRequirementExpr(obligation.expr)
        }
        return AnalysisContract(
            input = AnalysisRequirementSchema(
                root = requirementNodeToAnalysis(contract.input.root),
                requirements = requirements,
                opaqueRegions = contract.input.opaqueRegions,
            ),
            output = AnalysisGuaranteeSchema(
                root = guaranteeNodeToAnalysis(contract.output.root),
                mayEmitNull = contract.output.mayEmitNull,
                opaqueRegions = contract.output.opaqueRegions,
            ),
            source = contract.source,
            metadata = AnalysisContractMetadata(
                runtimeFit = contract.metadata.runtimeFit,
                typeEval = contract.metadata.typeEval,
                inference = contract.metadata.inference,
            ),
        )
    }

    private fun requirementExprFromAnalysis(expr: AnalysisRequirementExpr): ConstraintExpr = when (expr) {
        is AnalysisRequirementExpr.AllOf -> ConstraintExpr.AllOf(expr.children.map(::requirementExprFromAnalysis))
        is AnalysisRequirementExpr.AnyOf -> ConstraintExpr.OneOf(expr.children.map(::requirementExprFromAnalysis))
        is AnalysisRequirementExpr.PathPresent -> ConstraintExpr.PathPresent(expr.path)
        is AnalysisRequirementExpr.PathNonNull -> ConstraintExpr.PathNonNull(expr.path)
    }

    private fun obligationToRequirementExpr(expr: ConstraintExpr): AnalysisRequirementExpr? = when (expr) {
        is ConstraintExpr.PathPresent -> AnalysisRequirementExpr.PathPresent(expr.path)
        is ConstraintExpr.PathNonNull -> AnalysisRequirementExpr.PathNonNull(expr.path)
        is ConstraintExpr.OneOf -> AnalysisRequirementExpr.AnyOf(expr.children.mapNotNull(::obligationToRequirementExpr))
        is ConstraintExpr.AllOf -> AnalysisRequirementExpr.AllOf(expr.children.mapNotNull(::obligationToRequirementExpr))
        is ConstraintExpr.ForAll -> null
        is ConstraintExpr.Exists -> null
        is ConstraintExpr.DomainConstraint -> null
    }

    private fun requirementNodeFromAnalysis(node: AnalysisRequirementNode): Node {
        val fromShape = nodeFromShape(node.shape, node.required, null)
        val mergedChildren = LinkedHashMap(fromShape.children)
        node.children.forEach { (name, child) ->
            val next = requirementNodeFromAnalysis(child)
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

    private fun guaranteeNodeFromAnalysis(node: AnalysisGuaranteeNode): Node {
        val fromShape = nodeFromShape(node.shape, node.required, node.origin)
        val mergedChildren = LinkedHashMap(fromShape.children)
        node.children.forEach { (name, child) ->
            val next = guaranteeNodeFromAnalysis(child)
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

    private fun requirementNodeToAnalysis(node: Node): AnalysisRequirementNode {
        val children = LinkedHashMap<String, AnalysisRequirementNode>()
        node.children.forEach { (name, child) ->
            children[name] = requirementNodeToAnalysis(child)
        }
        return AnalysisRequirementNode(
            required = node.required,
            shape = shapeFromNode(node),
            open = node.open,
            children = children,
            evidence = emptyList(),
        )
    }

    private fun guaranteeNodeToAnalysis(node: Node): AnalysisGuaranteeNode {
        val children = LinkedHashMap<String, AnalysisGuaranteeNode>()
        node.children.forEach { (name, child) ->
            children[name] = guaranteeNodeToAnalysis(child)
        }
        return AnalysisGuaranteeNode(
            required = node.required,
            shape = shapeFromNode(node),
            open = node.open,
            origin = node.origin ?: OriginKind.MERGED,
            children = children,
            evidence = emptyList(),
        )
    }

    private fun mergeNode(
        left: Node,
        right: Node,
        normalizeAnyWithChildren: Boolean,
    ): Node {
        val childNames = left.children.keys + right.children.keys
        val children = LinkedHashMap<String, Node>()
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

    private fun normalizeAnyNodeWithChildren(node: Node): Node {
        if (node.kind != NodeKind.ANY || node.children.isEmpty()) {
            return node
        }
        return node.copy(
            kind = NodeKind.OBJECT,
            open = true,
        )
    }

    private fun nodeFromShape(shape: ValueShape, required: Boolean, origin: OriginKind?): Node = when (shape) {
        ValueShape.Never -> Node(required = required, kind = NodeKind.NEVER, origin = origin)
        ValueShape.Unknown -> Node(required = required, kind = NodeKind.ANY, origin = origin)
        ValueShape.Null -> Node(required = required, kind = NodeKind.NULL, origin = origin)
        ValueShape.BooleanShape -> Node(required = required, kind = NodeKind.BOOLEAN, origin = origin)
        ValueShape.NumberShape -> Node(required = required, kind = NodeKind.NUMBER, origin = origin)
        ValueShape.Bytes -> Node(required = required, kind = NodeKind.BYTES, origin = origin)
        ValueShape.TextShape -> Node(required = required, kind = NodeKind.TEXT, origin = origin)
        is ValueShape.ArrayShape -> Node(
            required = required,
            kind = NodeKind.ARRAY,
            element = nodeFromShape(shape.element, required = true, origin = origin),
            origin = origin,
        )
        is ValueShape.SetShape -> Node(
            required = required,
            kind = NodeKind.SET,
            element = nodeFromShape(shape.element, required = true, origin = origin),
            origin = origin,
        )
        is ValueShape.Union -> Node(
            required = required,
            kind = NodeKind.UNION,
            options = shape.options.map { option -> nodeFromShape(option, required = true, origin = origin) },
            origin = origin,
        )
        is ValueShape.ObjectShape -> {
            val children = LinkedHashMap<String, Node>()
            for ((name, field) in shape.schema.fields) {
                children[name] = nodeFromShape(field.shape, field.required, field.origin)
            }
            Node(
                required = required,
                kind = NodeKind.OBJECT,
                open = !shape.closed,
                children = children,
                origin = origin,
            )
        }
    }

    private fun shapeFromNode(node: Node): ValueShape = when (node.kind) {
        NodeKind.NEVER -> ValueShape.Never
        NodeKind.ANY -> ValueShape.Unknown
        NodeKind.NULL -> ValueShape.Null
        NodeKind.BOOLEAN -> ValueShape.BooleanShape
        NodeKind.NUMBER -> ValueShape.NumberShape
        NodeKind.BYTES -> ValueShape.Bytes
        NodeKind.TEXT -> ValueShape.TextShape
        NodeKind.ARRAY -> ValueShape.ArrayShape(
            element = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown,
        )
        NodeKind.SET -> ValueShape.SetShape(
            element = node.element?.let(::shapeFromNode) ?: ValueShape.Unknown,
        )
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
