package io.github.ehlyzov.branchline.contract

import io.github.ehlyzov.branchline.ArrayTypeRef
import io.github.ehlyzov.branchline.BinaryExpr
import io.github.ehlyzov.branchline.CaseExpr
import io.github.ehlyzov.branchline.CaseWhen
import io.github.ehlyzov.branchline.CodeBlock
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.EnumTypeRef
import io.github.ehlyzov.branchline.ExprStmt
import io.github.ehlyzov.branchline.ForEachStmt
import io.github.ehlyzov.branchline.IfElseExpr
import io.github.ehlyzov.branchline.IfStmt
import io.github.ehlyzov.branchline.LetStmt
import io.github.ehlyzov.branchline.LiteralProperty
import io.github.ehlyzov.branchline.NamedTypeRef
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.OutputStmt
import io.github.ehlyzov.branchline.PrimitiveType
import io.github.ehlyzov.branchline.PrimitiveTypeRef
import io.github.ehlyzov.branchline.RecordTypeRef
import io.github.ehlyzov.branchline.SetStmt
import io.github.ehlyzov.branchline.SetVarStmt
import io.github.ehlyzov.branchline.SetTypeRef
import io.github.ehlyzov.branchline.StringExpr
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TransformSignature
import io.github.ehlyzov.branchline.TypeRef
import io.github.ehlyzov.branchline.UnionTypeRef
import io.github.ehlyzov.branchline.sema.BinaryTypeEvalRule
import io.github.ehlyzov.branchline.sema.DefaultBinaryTypeEvalRules
import io.github.ehlyzov.branchline.sema.TypeResolver
import io.github.ehlyzov.branchline.sema.TransformContractSynthesizer

public class TransformContractBuilder(
    private val typeResolver: TypeResolver,
    hostFns: Set<String> = emptySet(),
    binaryTypeEvalRules: List<BinaryTypeEvalRule> = DefaultBinaryTypeEvalRules.rules,
    private val contractFitter: ContractFitter = NoOpContractFitter,
) {
    private val synthesizer = TransformContractSynthesizer(hostFns, binaryTypeEvalRules)

    public fun build(
        transform: TransformDecl,
        options: BuildOptions = BuildOptions(),
    ): TransformContract {
        val explicitContract = transform.signature?.let { signature ->
            val outputType = signature.output
            if (outputType != null && !isWildcardOutputType(outputType)) {
                buildExplicitContract(signature)
            } else {
                null
            }
        }
        val inferredOrHybridBase = if (explicitContract == null) {
            val analysisContract = buildAnalysisContract(transform)
            val fittedContract = contractFitter.fit(
                staticContract = analysisContract,
                examples = emptyList(),
            ).contract
            TransformContractAdapter.fromAnalysis(fittedContract)
        } else {
            explicitContract
        }
        val enriched = if (inferredOrHybridBase.source == ContractSource.INFERRED) {
            enrichContract(inferredOrHybridBase, transform)
        } else {
            inferredOrHybridBase
        }
        val filtered = applyBuildOptions(enriched, options)
        val satisfiability = ContractSatisfiability.check(filtered)
        if (satisfiability.satisfiable) {
            return filtered.copy(
                metadata = filtered.metadata.copy(
                    satisfiability = SatisfiabilityMetadata(
                        checked = true,
                        satisfiable = true,
                        diagnostics = emptyList(),
                    ),
                ),
            )
        }
        val degraded = filtered.copy(
            input = filtered.input.copy(obligations = emptyList()),
            output = filtered.output.copy(obligations = emptyList()),
            metadata = filtered.metadata.copy(
                satisfiability = SatisfiabilityMetadata(
                    checked = true,
                    satisfiable = false,
                    diagnostics = satisfiability.diagnostics,
                ),
            ),
        )
        return degraded
    }

    private fun applyBuildOptions(contract: TransformContract, options: BuildOptions): TransformContract {
        return contract.copy(
            input = contract.input.copy(
                obligations = filterObligations(
                    obligations = contract.input.obligations,
                    confidenceThreshold = options.confidenceThreshold,
                    includeHeuristic = options.includeHeuristicObligations,
                ),
            ),
            output = contract.output.copy(
                obligations = filterObligations(
                    obligations = contract.output.obligations,
                    confidenceThreshold = options.confidenceThreshold,
                    includeHeuristic = options.includeHeuristicObligations,
                ),
            ),
        )
    }

    private fun filterObligations(
        obligations: List<ContractObligation>,
        confidenceThreshold: Double,
        includeHeuristic: Boolean,
    ): List<ContractObligation> = obligations.filter { obligation ->
        obligation.confidence >= confidenceThreshold && (includeHeuristic || !obligation.heuristic)
    }

    private fun buildAnalysisContract(transform: TransformDecl): AnalysisContract {
        val signature = transform.signature
        if (signature == null) {
            return synthesizer.synthesize(transform)
        }
        val outputType = signature.output
        if (outputType == null || !isWildcardOutputType(outputType)) {
            return synthesizer.synthesize(transform)
        }
        val declaredInput = signature.input?.let(::buildDeclaredAnalysisRequirementSchema)
        val seededInference = synthesizer.synthesize(
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
        build(transform)

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
            input = requirementFromSchema(inputRequirement),
            output = guaranteeFromSchema(outputGuarantee),
            source = ContractSource.EXPLICIT,
        ).let { contract ->
            val input = signature.input?.let { inputType ->
                contract.input.copy(root = applyDomainsFromTypeRef(inputType, contract.input.root))
            } ?: contract.input
            val output = signature.output?.let { outputType ->
                contract.output.copy(root = applyDomainsFromTypeRef(outputType, contract.output.root))
            } ?: contract.output
            contract.copy(input = input, output = output)
        }
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

    private fun buildDeclaredAnalysisRequirementSchema(typeRef: TypeRef): AnalysisRequirementSchema = AnalysisRequirementSchema(
        root = requirementNodeFromShape(
            shape = shapeFromTypeRef(typeRef),
            required = true,
        ),
        requirements = emptyList(),
        opaqueRegions = emptyList(),
    )

    private fun requirementNodeFromShape(shape: ValueShape, required: Boolean): AnalysisRequirementNode {
        if (shape is ValueShape.ObjectShape) {
            val children = LinkedHashMap<String, AnalysisRequirementNode>()
            for ((name, field) in shape.schema.fields) {
                children[name] = requirementNodeFromShape(
                    shape = field.shape,
                    required = field.required,
                )
            }
            return AnalysisRequirementNode(
                required = required,
                shape = shape,
                open = !shape.closed,
                children = children,
                evidence = emptyList(),
            )
        }
        return AnalysisRequirementNode(
            required = required,
            shape = shape,
            open = true,
            children = linkedMapOf(),
            evidence = emptyList(),
        )
    }

    private fun mergeDeclaredAndInferredInput(
        declared: AnalysisRequirementSchema,
        inferred: AnalysisRequirementSchema,
    ): AnalysisRequirementSchema {
        val mergedOpaque = (declared.opaqueRegions + inferred.opaqueRegions)
            .associateBy { region -> opaqueRegionKey(region.path, region.reason) }
            .values
            .toList()
        return AnalysisRequirementSchema(
            root = mergeRequirementNodes(declared.root, inferred.root),
            requirements = (declared.requirements + inferred.requirements).distinct(),
            opaqueRegions = mergedOpaque,
        )
    }

    private fun mergeRequirementNodes(
        declared: AnalysisRequirementNode,
        inferred: AnalysisRequirementNode,
    ): AnalysisRequirementNode {
        val childNames = declared.children.keys + inferred.children.keys
        val mergedChildren = LinkedHashMap<String, AnalysisRequirementNode>()
        for (name in childNames) {
            val declaredChild = declared.children[name]
            val inferredChild = inferred.children[name]
            mergedChildren[name] = when {
                declaredChild == null -> inferredChild!!
                inferredChild == null -> declaredChild
                else -> mergeRequirementNodes(declaredChild, inferredChild)
            }
        }
        return AnalysisRequirementNode(
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

    private fun enrichContract(contract: TransformContract, transform: TransformDecl): TransformContract {
        val outputObligations = mutableListOf<ContractObligation>()
        outputObligations += deriveForAllObligations(contract.output.root, AccessPath(emptyList()))
        deriveStatusEnumDomain(transform)?.let { enumDomain ->
            outputObligations += ContractObligation(
                expr = ConstraintExpr.DomainConstraint(
                    path = AccessPath(listOf(AccessSegment.Field("status"))),
                    domain = enumDomain,
                ),
                confidence = 0.95,
                ruleId = "status-enum-domain",
                heuristic = false,
            )
        }
        val outputRoot = applyStatusDomain(contract.output.root, deriveStatusEnumDomain(transform))
        val mergedOutputObligations = (contract.output.obligations + outputObligations).distinct()
        val dedupedOutputObligations = dedupeOutputObligationsAgainstNode(outputRoot, mergedOutputObligations)
        return contract.copy(
            output = contract.output.copy(
                root = outputRoot,
                obligations = dedupedOutputObligations,
            ),
        )
    }

    private fun deriveForAllObligations(node: Node, path: AccessPath): List<ContractObligation> {
        val obligations = mutableListOf<ContractObligation>()
        if (node.kind == NodeKind.ARRAY) {
            val element = node.element
            if (element != null && element.kind == NodeKind.OBJECT && element.children.isNotEmpty()) {
                val required = element.children.entries
                    .filter { (_, child) -> child.required }
                    .map { (name, _) -> name }
                obligations += ContractObligation(
                    expr = ConstraintExpr.ForAll(
                        path = path,
                        requiredFields = required,
                    ),
                    confidence = 0.95,
                    ruleId = "array-element-required-fields",
                    heuristic = false,
                )
            }
        }
        node.children.forEach { (name, child) ->
            obligations += deriveForAllObligations(child, appendPath(path, AccessSegment.Field(name)))
        }
        node.options.forEach { option ->
            obligations += deriveForAllObligations(option, path)
        }
        return obligations
    }

    private fun deriveStatusEnumDomain(transform: TransformDecl): ValueDomain.EnumText? {
        val definitions = collectDefinitions(transform.body as CodeBlock)
        val values = linkedSetOf<String>()
        collectOutputFieldExpressions(transform.body as CodeBlock, "status").forEach { expr ->
            collectStringLiteralsFromExpr(expr, definitions, values, mutableSetOf())
        }
        return if (values.size >= 2) ValueDomain.EnumText(values.toList()) else null
    }

    private fun applyStatusDomain(root: Node, domain: ValueDomain.EnumText?): Node {
        if (domain == null) return root
        val status = root.children["status"] ?: return root
        val updated = status.copy(domains = (status.domains + domain).distinct())
        val children = LinkedHashMap(root.children)
        children["status"] = updated
        return root.copy(children = children)
    }

    private fun collectDefinitions(block: CodeBlock): Map<String, Expr> {
        val defs = linkedMapOf<String, Expr>()
        collectDefinitionsFromStatements(block.statements, defs)
        return defs
    }

    private fun collectDefinitionsFromStatements(
        statements: List<io.github.ehlyzov.branchline.Stmt>,
        defs: MutableMap<String, Expr>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is LetStmt -> defs[stmt.name] = stmt.expr
                is SetVarStmt -> defs[stmt.name] = stmt.value
                is IfStmt -> {
                    collectDefinitionsFromStatements(stmt.thenBlock.statements, defs)
                    stmt.elseBlock?.let { block -> collectDefinitionsFromStatements(block.statements, defs) }
                }
                is ForEachStmt -> collectDefinitionsFromStatements(stmt.body.statements, defs)
                else -> Unit
            }
        }
    }

    private fun collectOutputFieldExpressions(
        block: CodeBlock,
        fieldName: String,
    ): List<Expr> {
        val expressions = mutableListOf<Expr>()
        collectOutputFieldExpressionsFromStatements(block.statements, fieldName, expressions)
        return expressions
    }

    private fun collectOutputFieldExpressionsFromStatements(
        statements: List<io.github.ehlyzov.branchline.Stmt>,
        fieldName: String,
        out: MutableList<Expr>,
    ) {
        for (stmt in statements) {
            when (stmt) {
                is OutputStmt -> {
                    val obj = stmt.template as? io.github.ehlyzov.branchline.ObjectExpr ?: continue
                    for (field in obj.fields) {
                        val literal = field as? LiteralProperty ?: continue
                        val key = literal.key as? ObjKey.Name ?: continue
                        if (key.v == fieldName) {
                            out += literal.value
                        }
                    }
                }
                is IfStmt -> {
                    collectOutputFieldExpressionsFromStatements(stmt.thenBlock.statements, fieldName, out)
                    stmt.elseBlock?.let { block ->
                        collectOutputFieldExpressionsFromStatements(block.statements, fieldName, out)
                    }
                }
                is ForEachStmt -> collectOutputFieldExpressionsFromStatements(stmt.body.statements, fieldName, out)
                else -> Unit
            }
        }
    }

    private fun collectStringLiteralsFromExpr(
        expr: Expr,
        defs: Map<String, Expr>,
        out: MutableSet<String>,
        visitingNames: MutableSet<String>,
    ) {
        when (expr) {
            is StringExpr -> out += expr.value
            is IfElseExpr -> {
                collectStringLiteralsFromExpr(expr.thenBranch, defs, out, visitingNames)
                collectStringLiteralsFromExpr(expr.elseBranch, defs, out, visitingNames)
            }
            is CaseExpr -> {
                expr.whens.forEach { whenExpr: CaseWhen ->
                    collectStringLiteralsFromExpr(whenExpr.result, defs, out, visitingNames)
                }
                collectStringLiteralsFromExpr(expr.elseBranch, defs, out, visitingNames)
            }
            is BinaryExpr -> {
                if (expr.token.type == TokenType.COALESCE) {
                    collectStringLiteralsFromExpr(expr.left, defs, out, visitingNames)
                    collectStringLiteralsFromExpr(expr.right, defs, out, visitingNames)
                }
            }
            is io.github.ehlyzov.branchline.IdentifierExpr -> {
                if (!visitingNames.add(expr.name)) return
                val resolved = defs[expr.name]
                if (resolved != null) {
                    collectStringLiteralsFromExpr(resolved, defs, out, visitingNames)
                }
                visitingNames.remove(expr.name)
            }
            else -> Unit
        }
    }

    private fun appendPath(path: AccessPath, segment: AccessSegment): AccessPath =
        AccessPath(path.segments + segment)

    private fun dedupeOutputObligationsAgainstNode(
        root: Node,
        obligations: List<ContractObligation>,
    ): List<ContractObligation> = obligations.filterNot { obligation ->
        isRepresentedByOutputNode(root, obligation.expr)
    }

    private fun isRepresentedByOutputNode(root: Node, expr: ConstraintExpr): Boolean = when (expr) {
        is ConstraintExpr.DomainConstraint -> {
            val node = resolveNode(root, expr.path)
            node != null && expr.domain in node.domains
        }
        is ConstraintExpr.ForAll -> {
            if (expr.requireAnyOf.isNotEmpty()) return false
            val node = resolveNode(root, expr.path) ?: return false
            if (node.kind != NodeKind.ARRAY && node.kind != NodeKind.SET) return false
            val element = node.element ?: return false
            if (element.kind != NodeKind.OBJECT) return false
            val requiredFieldsCovered = expr.requiredFields.all { name ->
                val child = element.children[name]
                child != null && child.required
            }
            val fieldDomainsCovered = expr.fieldDomains.all { (name, domain) ->
                val child = element.children[name]
                child != null && domain in child.domains
            }
            requiredFieldsCovered && fieldDomainsCovered
        }
        else -> false
    }

    private fun resolveNode(root: Node, path: AccessPath): Node? {
        var current: Node? = root
        for (segment in path.segments) {
            current = when (segment) {
                is AccessSegment.Field -> current?.children?.get(segment.name)
                is AccessSegment.Index -> {
                    when (current?.kind) {
                        NodeKind.OBJECT -> current.children[segment.index]
                        NodeKind.ARRAY, NodeKind.SET -> current.element
                        NodeKind.UNION -> null
                        else -> null
                    }
                }
                AccessSegment.Dynamic -> return null
            }
            if (current == null) return null
        }
        return current
    }

    private fun requirementFromSchema(schema: SchemaRequirement): RequirementSchema = RequirementSchema(
        root = Node(
            required = true,
            kind = NodeKind.OBJECT,
            open = schema.open,
            children = LinkedHashMap<String, Node>().apply {
                schema.fields.forEach { (name, field) ->
                    this[name] = nodeFromShape(
                        shape = field.shape,
                        required = field.required,
                        origin = null,
                    )
                }
            },
        ),
        obligations = schema.requiredAnyOf.map { group ->
            ContractObligation(
                expr = ConstraintExpr.OneOf(
                    group.alternatives.map { path -> ConstraintExpr.PathNonNull(path) },
                ),
                confidence = 1.0,
                ruleId = "explicit-required-any-of",
                heuristic = false,
            )
        },
        opaqueRegions = schema.dynamicAccess.map { access ->
            OpaqueRegion(path = access.path, reason = access.reason)
        },
    )

    private fun guaranteeFromSchema(schema: SchemaGuarantee): GuaranteeSchema = GuaranteeSchema(
        root = Node(
            required = true,
            kind = NodeKind.OBJECT,
            open = false,
            children = LinkedHashMap<String, Node>().apply {
                schema.fields.forEach { (name, field) ->
                    this[name] = nodeFromShape(
                        shape = field.shape,
                        required = field.required,
                        origin = field.origin,
                    )
                }
            },
        ),
        obligations = emptyList(),
        mayEmitNull = schema.mayEmitNull,
        opaqueRegions = schema.dynamicFields.map { field ->
            OpaqueRegion(path = field.path, reason = field.reason)
        },
    )

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
            options = shape.options.map { option ->
                nodeFromShape(option, required = true, origin = origin)
            },
            origin = origin,
        )
        is ValueShape.ObjectShape -> {
            val children = LinkedHashMap<String, Node>()
            shape.schema.fields.forEach { (name, field) ->
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

    private fun applyDomainsFromTypeRef(typeRef: TypeRef, node: Node): Node {
        val resolved = typeResolver.resolve(typeRef)
        return applyDomainsFromResolvedType(resolved, node)
    }

    private fun applyDomainsFromResolvedType(typeRef: TypeRef, node: Node): Node = when (typeRef) {
        is NamedTypeRef -> applyDomainsFromResolvedType(typeResolver.resolve(typeRef), node)
        is EnumTypeRef -> {
            val domain = ValueDomain.EnumText(typeRef.values)
            node.copy(domains = (node.domains + domain).distinct())
        }
        is PrimitiveTypeRef -> node
        is ArrayTypeRef -> {
            val elementNode = node.element ?: return node
            node.copy(
                element = applyDomainsFromResolvedType(
                    typeRef = typeResolver.resolve(typeRef.elementType),
                    node = elementNode,
                ),
            )
        }
        is SetTypeRef -> {
            val elementNode = node.element ?: return node
            node.copy(
                element = applyDomainsFromResolvedType(
                    typeRef = typeResolver.resolve(typeRef.elementType),
                    node = elementNode,
                ),
            )
        }
        is RecordTypeRef -> {
            val children = LinkedHashMap(node.children)
            for (field in typeRef.fields) {
                val child = children[field.name] ?: continue
                children[field.name] = applyDomainsFromTypeRef(field.type, child)
            }
            node.copy(
                open = false,
                children = children,
            )
        }
        is UnionTypeRef -> {
            if (node.kind == NodeKind.UNION && node.options.size == typeRef.members.size) {
                node.copy(
                    options = node.options.mapIndexed { index, option ->
                        applyDomainsFromTypeRef(typeRef.members[index], option)
                    },
                )
            } else {
                val resolvedMembers = typeRef.members.map { member -> typeResolver.resolve(member) }
                val nonNullMembers = resolvedMembers.filterNot { member ->
                    member is PrimitiveTypeRef && member.kind == PrimitiveType.NULL
                }
                if (nonNullMembers.size == 1) {
                    applyDomainsFromResolvedType(nonNullMembers.single(), node)
                } else {
                    node
                }
            }
        }
    }
}

public data class BuildOptions(
    val confidenceThreshold: Double = 0.7,
    val includeHeuristicObligations: Boolean = false,
)
