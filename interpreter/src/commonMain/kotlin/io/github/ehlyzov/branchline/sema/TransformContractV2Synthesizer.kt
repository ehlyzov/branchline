package io.github.ehlyzov.branchline.sema

import io.github.ehlyzov.branchline.AbortStmt
import io.github.ehlyzov.branchline.AccessExpr
import io.github.ehlyzov.branchline.AccessSeg
import io.github.ehlyzov.branchline.AppendToStmt
import io.github.ehlyzov.branchline.AppendToVarStmt
import io.github.ehlyzov.branchline.ArrayCompExpr
import io.github.ehlyzov.branchline.ArrayExpr
import io.github.ehlyzov.branchline.BinaryExpr
import io.github.ehlyzov.branchline.BoolExpr
import io.github.ehlyzov.branchline.CallExpr
import io.github.ehlyzov.branchline.CaseExpr
import io.github.ehlyzov.branchline.CodeBlock
import io.github.ehlyzov.branchline.ComputedProperty
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.ExprStmt
import io.github.ehlyzov.branchline.ForEachStmt
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.IdentifierExpr
import io.github.ehlyzov.branchline.IfElseExpr
import io.github.ehlyzov.branchline.IfStmt
import io.github.ehlyzov.branchline.InvokeExpr
import io.github.ehlyzov.branchline.LetStmt
import io.github.ehlyzov.branchline.LiteralProperty
import io.github.ehlyzov.branchline.ModifyStmt
import io.github.ehlyzov.branchline.NullLiteral
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.ObjectExpr
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.OutputStmt
import io.github.ehlyzov.branchline.ReturnStmt
import io.github.ehlyzov.branchline.SetStmt
import io.github.ehlyzov.branchline.SetVarStmt
import io.github.ehlyzov.branchline.StringExpr
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TryCatchExpr
import io.github.ehlyzov.branchline.TryCatchStmt
import io.github.ehlyzov.branchline.UnaryExpr
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.ContractSource
import io.github.ehlyzov.branchline.contract.DynamicReason
import io.github.ehlyzov.branchline.contract.FieldShape
import io.github.ehlyzov.branchline.contract.GuaranteeNodeV2
import io.github.ehlyzov.branchline.contract.GuaranteeSchemaV2
import io.github.ehlyzov.branchline.contract.InferenceEvidenceV2
import io.github.ehlyzov.branchline.contract.OpaqueRegionV2
import io.github.ehlyzov.branchline.contract.OriginKind
import io.github.ehlyzov.branchline.contract.RequirementExprV2
import io.github.ehlyzov.branchline.contract.RequirementNodeV2
import io.github.ehlyzov.branchline.contract.RequirementSchemaV2
import io.github.ehlyzov.branchline.contract.SchemaGuarantee
import io.github.ehlyzov.branchline.contract.TransformContractV2
import io.github.ehlyzov.branchline.contract.ValueShape

public class TransformContractV2Synthesizer(
    private val hostFns: Set<String> = emptySet(),
) {
    private val env: LinkedHashMap<String, AbstractValue> = linkedMapOf()
    private val scopes = ArrayDeque<MutableSet<String>>()
    private val inputPathRecords = LinkedHashMap<String, InputPathRecord>()
    private val requirementExprs = mutableListOf<RequirementExprV2>()
    private val inputOpaque = LinkedHashMap<String, OpaqueRegionV2>()
    private val outputOpaque = LinkedHashMap<String, OpaqueRegionV2>()
    private var outputRoot: GuaranteeNodeV2? = null

    public fun synthesize(transform: TransformDecl): TransformContractV2 {
        reset()
        withScope(transform.params) {
            analyzeBlock(transform.body as CodeBlock)
        }
        return TransformContractV2(
            input = buildInputSchema(),
            output = buildOutputSchema(),
            source = ContractSource.INFERRED,
        )
    }

    private fun reset() {
        env.clear()
        scopes.clear()
        inputPathRecords.clear()
        requirementExprs.clear()
        inputOpaque.clear()
        outputOpaque.clear()
        outputRoot = null
    }

    private fun analyzeBlock(block: CodeBlock) {
        for (stmt in block.statements) {
            analyzeStmt(stmt)
        }
    }

    private fun analyzeStmt(stmt: io.github.ehlyzov.branchline.Stmt) {
        when (stmt) {
            is LetStmt -> {
                val value = evalExpr(stmt.expr)
                env[stmt.name] = value
                declareLocal(stmt.name)
            }

            is SetVarStmt -> {
                val value = evalExpr(stmt.value)
                env[stmt.name] = value
                declareLocal(stmt.name)
            }

            is AppendToVarStmt -> {
                val value = evalExpr(stmt.value)
                stmt.init?.let { evalExpr(it) }
                val previous = env[stmt.name]
                val elementShape = mergeValueShape(previous?.arrayElement() ?: ValueShape.Unknown, value.shape)
                env[stmt.name] = AbstractValue(
                    shape = ValueShape.ArrayShape(elementShape),
                    provenance = emptySet(),
                    evidence = value.evidence,
                )
                declareLocal(stmt.name)
            }

            is SetStmt -> {
                val value = evalExpr(stmt.value)
                evalAccessTarget(stmt.target)
                applySetToLocalTarget(stmt.target, value)
            }

            is AppendToStmt -> {
                evalAccessTarget(stmt.target)
                val value = evalExpr(stmt.value)
                stmt.init?.let { evalExpr(it) }
                applyAppendToLocalTarget(stmt.target, value)
            }

            is ModifyStmt -> {
                evalAccessTarget(stmt.target)
                val updateShapes = mutableListOf<Pair<ObjKey, AbstractValue>>()
                for (prop in stmt.updates) {
                    when (prop) {
                        is LiteralProperty -> {
                            val value = evalExpr(prop.value)
                            updateShapes += prop.key to value
                        }
                        is ComputedProperty -> {
                            evalExpr(prop.keyExpr)
                            val value = evalExpr(prop.value)
                            addOutputOpaque(AccessPath(listOf(AccessSegment.Dynamic)), DynamicReason.COMPUTED)
                            if (value.provenance.isNotEmpty()) {
                                addInputOpaque(AccessPath(listOf(AccessSegment.Dynamic)), DynamicReason.COMPUTED)
                            }
                        }
                    }
                }
                applyModifyToLocalTarget(stmt.target, updateShapes)
            }

            is IfStmt -> analyzeIfStmt(stmt)
            is ForEachStmt -> analyzeForEachStmt(stmt)
            is TryCatchStmt -> analyzeTryCatchStmt(stmt)
            is OutputStmt -> analyzeOutput(stmt)
            is ExprStmt -> {
                evalExpr(stmt.expr)
            }
            is ReturnStmt -> {
                stmt.value?.let { evalExpr(it) }
            }
            is AbortStmt -> {
                stmt.value?.let { evalExpr(it) }
            }
            else -> Unit
        }
    }

    private fun analyzeIfStmt(stmt: IfStmt) {
        evalExpr(stmt.condition)
        val baseline = cloneEnv()
        val refinement = refineFromCondition(stmt.condition)
        val thenSeed = applyRefinements(baseline, refinement.thenRules)
        val elseSeed = applyRefinements(baseline, refinement.elseRules)
        val thenEnv = analyzeBranchWithEnv(thenSeed, stmt.thenBlock)
        val elseEnv = stmt.elseBlock?.let { analyzeBranchWithEnv(elseSeed, it) } ?: elseSeed
        replaceEnv(mergeEnv(thenEnv, elseEnv))
    }

    private fun analyzeForEachStmt(stmt: ForEachStmt) {
        val iterable = evalExpr(stmt.iterable)
        val baseline = cloneEnv()
        val loopSeed = LinkedHashMap(baseline)
        val element = AbstractValue(
            shape = iterable.arrayElement(),
            provenance = emptySet(),
            evidence = iterable.evidence,
        )
        loopSeed[stmt.varName] = element
        val loopEnv = analyzeBranchWithEnv(loopSeed, stmt.body, setOf(stmt.varName), stmt.where)
        replaceEnv(mergeEnv(baseline, loopEnv))
    }

    private fun analyzeTryCatchStmt(stmt: TryCatchStmt) {
        evalExpr(stmt.tryExpr)
        val baseline = cloneEnv()
        val fallbackEnv = analyzeBranchWithEnv(
            baseline,
            block = CodeBlock(emptyList(), stmt.token),
            initialLocals = setOf(stmt.exceptionName),
            fallbackExpr = stmt.fallbackExpr,
            fallbackAbort = stmt.fallbackAbort,
        )
        replaceEnv(mergeEnv(baseline, fallbackEnv))
    }

    private fun analyzeOutput(stmt: OutputStmt) {
        val value = evalExpr(stmt.template)
        val node = guaranteeNodeFromShape(
            shape = value.shape,
            required = true,
            origin = OriginKind.OUTPUT,
            evidence = value.evidence,
        )
        outputRoot = if (outputRoot == null) node else mergeGuaranteeNodes(outputRoot!!, node)
    }

    private fun evalAccessTarget(target: AccessExpr) {
        if (target.base !is IdentifierExpr) {
            evalExpr(target.base)
        }
        for (seg in target.segs) {
            if (seg is AccessSeg.Dynamic) {
                evalExpr(seg.keyExpr)
            }
        }
    }

    private fun evalExpr(expr: Expr): AbstractValue = when (expr) {
        is IdentifierExpr -> evalIdentifier(expr)
        is AccessExpr -> evalAccess(expr)
        is StringExpr -> AbstractValue(ValueShape.TextShape)
        is NumberLiteral -> AbstractValue(ValueShape.NumberShape)
        is BoolExpr -> AbstractValue(ValueShape.BooleanShape)
        is NullLiteral -> AbstractValue(ValueShape.Null)
        is ObjectExpr -> evalObject(expr)
        is ArrayExpr -> evalArray(expr)
        is ArrayCompExpr -> evalArrayComp(expr)
        is CallExpr -> evalCall(expr)
        is InvokeExpr -> evalInvoke(expr)
        is UnaryExpr -> evalExpr(expr.expr)
        is IfElseExpr -> mergeAbstractValues(evalExpr(expr.thenBranch), evalExpr(expr.elseBranch))
        is CaseExpr -> evalCase(expr)
        is TryCatchExpr -> mergeAbstractValues(evalExpr(expr.tryExpr), evalExpr(expr.fallbackExpr))
        is BinaryExpr -> evalBinary(expr)
        is io.github.ehlyzov.branchline.SharedStateAwaitExpr -> AbstractValue(ValueShape.Unknown)
        is io.github.ehlyzov.branchline.LambdaExpr -> AbstractValue(ValueShape.Unknown)
    }

    private fun evalIdentifier(expr: IdentifierExpr): AbstractValue {
        val name = expr.name
        if (isInputAlias(name) || hostFns.contains(name)) {
            return AbstractValue(ValueShape.Unknown)
        }
        env[name]?.let { return it }
        val path = AccessPath(listOf(AccessSegment.Field(name)))
        recordInputPath(path, ValueShape.Unknown, expr.token, "free-identifier")
        return AbstractValue(
            shape = ValueShape.Unknown,
            provenance = setOf(path),
            evidence = listOf(evidence(expr.token, "free-identifier")),
        )
    }

    private fun evalAccess(expr: AccessExpr): AbstractValue {
        if (expr.base !is IdentifierExpr) {
            evalExpr(expr.base)
        }
        val base = expr.base as? IdentifierExpr
        if (base != null && isInputAlias(base.name)) {
            return evalInputAccess(expr, base.token)
        }
        val baseValue = evalExpr(expr.base)
        val dynamic = expr.segs.any { it is AccessSeg.Dynamic }
        for (seg in expr.segs) {
            if (seg is AccessSeg.Dynamic) {
                evalExpr(seg.keyExpr)
            }
        }
        if (base != null) {
            promoteShapeForStaticAccess(base.name, expr.segs)
        }
        if (dynamic) {
            addInputOpaque(
                AccessPath(expr.segs.map {
                    when (it) {
                        is AccessSeg.Static -> staticSegment(it.key)
                        is AccessSeg.Dynamic -> AccessSegment.Dynamic
                    }
                }),
                DynamicReason.KEY,
            )
            return AbstractValue(ValueShape.Unknown, evidence = listOf(evidence(expr.token, "dynamic-access")))
        }
        val descended = descendShape(baseValue.shape, expr.segs)
        val provenance = if (baseValue.provenance.isEmpty()) {
            emptySet()
        } else {
            baseValue.provenance.map { path ->
                AccessPath(path.segments + expr.segs.map { seg -> staticSegment((seg as AccessSeg.Static).key) })
            }.toSet()
        }
        for (path in provenance) {
            recordInputPath(path, descended, expr.token, "derived-provenance")
        }
        return AbstractValue(
            shape = descended,
            provenance = provenance,
            evidence = listOf(evidence(expr.token, "access")),
        )
    }

    private fun evalInputAccess(expr: AccessExpr, token: Token): AbstractValue {
        if (expr.segs.isEmpty()) {
            return AbstractValue(ValueShape.Unknown)
        }
        val staticSegments = mutableListOf<AccessSegment>()
        var hasDynamic = false
        for (seg in expr.segs) {
            when (seg) {
                is AccessSeg.Static -> staticSegments += staticSegment(seg.key)
                is AccessSeg.Dynamic -> {
                    hasDynamic = true
                    evalExpr(seg.keyExpr)
                    staticSegments += AccessSegment.Dynamic
                }
            }
        }
        val shape = ValueShape.Unknown
        if (hasDynamic) {
            addInputOpaque(AccessPath(staticSegments), DynamicReason.KEY)
            return AbstractValue(shape, evidence = listOf(evidence(token, "dynamic-input-access")))
        }
        val path = AccessPath(staticSegments)
        recordInputPath(path, shape, token, "input-access")
        return AbstractValue(
            shape = shape,
            provenance = setOf(path),
            evidence = listOf(evidence(token, "input-access")),
        )
    }

    private fun evalObject(expr: ObjectExpr): AbstractValue {
        val fields = LinkedHashMap<String, FieldShape>()
        var hasDynamic = false
        for (prop in expr.fields) {
            when (prop) {
                is LiteralProperty -> {
                    val value = evalExpr(prop.value)
                    val keyName = (prop.key as? ObjKey.Name)?.v
                    if (keyName == null) {
                        hasDynamic = true
                        addOutputOpaque(AccessPath(listOf(staticSegment(prop.key))), DynamicReason.INDEX)
                    } else {
                        fields[keyName] = FieldShape(
                            required = true,
                            shape = value.shape,
                            origin = OriginKind.OUTPUT,
                        )
                    }
                }

                is ComputedProperty -> {
                    evalExpr(prop.keyExpr)
                    evalExpr(prop.value)
                    hasDynamic = true
                    addOutputOpaque(AccessPath(listOf(AccessSegment.Dynamic)), DynamicReason.COMPUTED)
                }
            }
        }
        val shape = ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = fields,
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
        val rule = if (hasDynamic) "object-with-dynamic-props" else "object-literal"
        return AbstractValue(
            shape = shape,
            evidence = listOf(evidence(expr.token, rule)),
        )
    }

    private fun evalArray(expr: ArrayExpr): AbstractValue {
        val element = if (expr.elements.isEmpty()) {
            ValueShape.Unknown
        } else {
            expr.elements.map { item -> evalExpr(item).shape }.reduce(::mergeValueShape)
        }
        return AbstractValue(
            shape = ValueShape.ArrayShape(element),
            evidence = listOf(evidence(expr.token, "array-literal")),
        )
    }

    private fun evalArrayComp(expr: ArrayCompExpr): AbstractValue {
        val iterable = evalExpr(expr.iterable)
        val elementSeed = AbstractValue(iterable.arrayElement(), evidence = iterable.evidence)
        val mapped = withScopedValue(expr.varName, elementSeed) {
            expr.where?.let { evalExpr(it) }
            evalExpr(expr.mapExpr)
        }
        return AbstractValue(
            shape = ValueShape.ArrayShape(mapped.shape),
            evidence = listOf(evidence(expr.token, "array-comprehension")),
        )
    }

    private fun evalCall(expr: CallExpr): AbstractValue {
        val args = expr.args.map { arg -> evalExpr(arg) }
        val callee = expr.callee.name.uppercase()
        return when (callee) {
            "NUMBER" -> {
                enforceProvenanceShape(args.firstOrNull()?.provenance.orEmpty(), ValueShape.NumberShape, expr.token, "stdlib-number-arg")
                AbstractValue(ValueShape.NumberShape, evidence = listOf(evidence(expr.token, "stdlib-number")))
            }
            "BOOLEAN" -> {
                enforceProvenanceShape(args.firstOrNull()?.provenance.orEmpty(), ValueShape.BooleanShape, expr.token, "stdlib-boolean-arg")
                AbstractValue(ValueShape.BooleanShape, evidence = listOf(evidence(expr.token, "stdlib-boolean")))
            }
            "TEXT", "STRING" -> {
                enforceProvenanceShape(args.firstOrNull()?.provenance.orEmpty(), ValueShape.TextShape, expr.token, "stdlib-text-arg")
                AbstractValue(ValueShape.TextShape, evidence = listOf(evidence(expr.token, "stdlib-text")))
            }
            else -> AbstractValue(
                shape = ValueShape.Unknown,
                provenance = args.flatMap { value -> value.provenance }.toSet(),
                evidence = listOf(evidence(expr.token, "call-unknown")),
            )
        }
    }

    private fun evalInvoke(expr: InvokeExpr): AbstractValue {
        evalExpr(expr.target)
        expr.args.forEach { arg -> evalExpr(arg) }
        return AbstractValue(ValueShape.Unknown, evidence = listOf(evidence(expr.token, "invoke")))
    }

    private fun evalCase(expr: CaseExpr): AbstractValue {
        var value = evalExpr(expr.elseBranch)
        for (branch in expr.whens) {
            evalExpr(branch.condition)
            value = mergeAbstractValues(value, evalExpr(branch.result))
        }
        return value
    }

    private fun evalBinary(expr: BinaryExpr): AbstractValue {
        val left = evalExpr(expr.left)
        val right = evalExpr(expr.right)
        if (expr.token.type == TokenType.COALESCE) {
            val alternatives = collectCoalesceAlternatives(expr)
            if (alternatives.size >= 2) {
                requirementExprs += RequirementExprV2.AnyOf(
                    alternatives.map { path -> RequirementExprV2.PathNonNull(path) },
                )
            }
            return mergeAbstractValues(left, right)
        }
        val numericOps = setOf(
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.STAR,
            TokenType.SLASH,
            TokenType.PERCENT,
        )
        if (expr.token.type in numericOps && (left.shape == ValueShape.NumberShape || right.shape == ValueShape.NumberShape)) {
            enforceProvenanceShape(left.provenance + right.provenance, ValueShape.NumberShape, expr.token, "numeric-binary-input")
            return AbstractValue(
                shape = ValueShape.NumberShape,
                provenance = (left.provenance + right.provenance),
                evidence = listOf(evidence(expr.token, "numeric-binary-op")),
            )
        }
        return mergeAbstractValues(left, right)
    }

    private fun refineFromCondition(expr: Expr): Refinement {
        val nullEq = when (expr) {
            is BinaryExpr -> refineNullComparison(expr)
            else -> null
        }
        if (nullEq != null) {
            return nullEq
        }
        val objectRule = when (expr) {
            is CallExpr -> refineIsObject(expr)
            else -> null
        }
        if (objectRule != null) {
            return objectRule
        }
        return Refinement(emptyList(), emptyList())
    }

    private fun refineNullComparison(expr: BinaryExpr): Refinement? {
        val op = expr.token.type
        if (op != TokenType.EQ && op != TokenType.NEQ) {
            return null
        }
        val leftNull = expr.left is NullLiteral
        val rightNull = expr.right is NullLiteral
        if (leftNull == rightNull) return null
        val targetExpr = if (leftNull) expr.right else expr.left
        val target = refinementTarget(targetExpr) ?: return null
        val isNotNullComparison = op == TokenType.NEQ
        return if (isNotNullComparison) {
            Refinement(
                thenRules = listOf(RefinementRule(target, RefineKind.NON_NULL)),
                elseRules = listOf(RefinementRule(target, RefineKind.NULL_ONLY)),
            )
        } else {
            Refinement(
                thenRules = listOf(RefinementRule(target, RefineKind.NULL_ONLY)),
                elseRules = listOf(RefinementRule(target, RefineKind.NON_NULL)),
            )
        }
    }

    private fun refineIsObject(expr: CallExpr): Refinement? {
        if (expr.callee.name.uppercase() != "IS_OBJECT") return null
        if (expr.args.size != 1) return null
        val target = refinementTarget(expr.args[0]) ?: return null
        return Refinement(
            thenRules = listOf(RefinementRule(target, RefineKind.OBJECT_ONLY)),
            elseRules = listOf(RefinementRule(target, RefineKind.NOT_OBJECT)),
        )
    }

    private fun refinementTarget(expr: Expr): RefinementTarget? = when (expr) {
        is IdentifierExpr -> {
            if (isInputAlias(expr.name) || hostFns.contains(expr.name)) return null
            RefinementTarget(expr.name, emptyList())
        }
        is AccessExpr -> {
            val base = expr.base as? IdentifierExpr ?: return null
            if (isInputAlias(base.name) || hostFns.contains(base.name)) return null
            val path = mutableListOf<AccessSegment>()
            for (seg in expr.segs) {
                val static = seg as? AccessSeg.Static ?: return null
                path += staticSegment(static.key)
            }
            RefinementTarget(base.name, path)
        }
        else -> null
    }

    private fun applyRefinements(
        seed: LinkedHashMap<String, AbstractValue>,
        rules: List<RefinementRule>,
    ): LinkedHashMap<String, AbstractValue> {
        if (rules.isEmpty()) return LinkedHashMap(seed)
        val next = LinkedHashMap(seed)
        for (rule in rules) {
            val current = next[rule.target.base] ?: continue
            val refinedShape = refineShapeAtPath(current.shape, rule.target.segments, rule.kind)
            next[rule.target.base] = current.copy(
                shape = refinedShape,
                evidence = current.evidence + listOf(
                    InferenceEvidenceV2(
                        sourceSpans = emptyList(),
                        ruleId = "path-refinement-${rule.kind.name.lowercase()}",
                        confidence = 0.9,
                    ),
                ),
            )
        }
        return next
    }

    private fun refineShapeAtPath(
        shape: ValueShape,
        path: List<AccessSegment>,
        kind: RefineKind,
    ): ValueShape {
        if (path.isEmpty()) {
            return refineTerminalShape(shape, kind)
        }
        val head = path.first()
        val tail = path.drop(1)
        return when (shape) {
            is ValueShape.ObjectShape -> {
                val key = when (head) {
                    is AccessSegment.Field -> head.name
                    is AccessSegment.Index -> head.index
                    AccessSegment.Dynamic -> return shape
                }
                val fields = LinkedHashMap(shape.schema.fields)
                val existing = fields[key]
                val nextShape = refineShapeAtPath(existing?.shape ?: ValueShape.Unknown, tail, kind)
                fields[key] = FieldShape(
                    required = existing?.required ?: true,
                    shape = nextShape,
                    origin = existing?.origin ?: OriginKind.MERGED,
                )
                ValueShape.ObjectShape(
                    schema = shape.schema.copy(fields = fields),
                    closed = shape.closed,
                )
            }
            is ValueShape.Union -> {
                val refined = shape.options.map { option ->
                    refineShapeAtPath(option, path, kind)
                }
                ValueShape.Union(refined.distinct())
            }
            ValueShape.Unknown -> {
                val child = refineShapeAtPath(ValueShape.Unknown, tail, kind)
                val keyName = when (head) {
                    is AccessSegment.Field -> head.name
                    is AccessSegment.Index -> head.index
                    AccessSegment.Dynamic -> return shape
                }
                val fields = linkedMapOf<String, FieldShape>(
                    keyName to FieldShape(
                        required = true,
                        shape = child,
                        origin = OriginKind.MERGED,
                    ),
                )
                ValueShape.ObjectShape(
                    schema = SchemaGuarantee(
                        fields = fields,
                        mayEmitNull = false,
                        dynamicFields = emptyList(),
                    ),
                    closed = false,
                )
            }
            else -> shape
        }
    }

    private fun refineTerminalShape(shape: ValueShape, kind: RefineKind): ValueShape = when (kind) {
        RefineKind.NON_NULL -> removeNullFromShape(shape)
        RefineKind.NULL_ONLY -> ValueShape.Null
        RefineKind.OBJECT_ONLY -> toObjectOnlyShape(shape)
        RefineKind.NOT_OBJECT -> removeObjectFromShape(shape)
    }

    private fun removeNullFromShape(shape: ValueShape): ValueShape = when (shape) {
        ValueShape.Null -> ValueShape.Unknown
        is ValueShape.Union -> {
            val remaining = shape.options.filterNot { option -> option == ValueShape.Null }
            when (remaining.size) {
                0 -> ValueShape.Unknown
                1 -> remaining.first()
                else -> ValueShape.Union(remaining)
            }
        }
        else -> shape
    }

    private fun toObjectOnlyShape(shape: ValueShape): ValueShape = when (shape) {
        is ValueShape.ObjectShape -> shape
        is ValueShape.Union -> {
            val objectOptions = shape.options.filterIsInstance<ValueShape.ObjectShape>()
            when (objectOptions.size) {
                0 -> ValueShape.ObjectShape(
                    schema = SchemaGuarantee(
                        fields = linkedMapOf(),
                        mayEmitNull = false,
                        dynamicFields = emptyList(),
                    ),
                    closed = false,
                )
                1 -> objectOptions.first()
                else -> ValueShape.Union(objectOptions)
            }
        }
        ValueShape.Unknown -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = linkedMapOf(),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
        else -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = linkedMapOf(),
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
    }

    private fun removeObjectFromShape(shape: ValueShape): ValueShape = when (shape) {
        is ValueShape.ObjectShape -> ValueShape.Unknown
        is ValueShape.Union -> {
            val remaining = shape.options.filterNot { option -> option is ValueShape.ObjectShape }
            when (remaining.size) {
                0 -> ValueShape.Unknown
                1 -> remaining.first()
                else -> ValueShape.Union(remaining)
            }
        }
        else -> shape
    }

    private fun collectCoalesceAlternatives(expr: BinaryExpr): List<AccessPath> {
        val operands = mutableListOf<Expr>()
        collectCoalesceOperands(expr, operands)
        if (operands.size < 2) return emptyList()
        val alternatives = mutableListOf<AccessPath>()
        for (operand in operands) {
            val path = staticInputAccessPath(operand) ?: return emptyList()
            alternatives += path
        }
        return alternatives.distinct()
    }

    private fun collectCoalesceOperands(expr: Expr, out: MutableList<Expr>) {
        if (expr is BinaryExpr && expr.token.type == TokenType.COALESCE) {
            collectCoalesceOperands(expr.left, out)
            collectCoalesceOperands(expr.right, out)
            return
        }
        out += expr
    }

    private fun staticInputAccessPath(expr: Expr): AccessPath? = when (expr) {
        is IdentifierExpr -> if (isInputAlias(expr.name) || hostFns.contains(expr.name) || isLocal(expr.name)) null
        else AccessPath(listOf(AccessSegment.Field(expr.name)))
        is AccessExpr -> staticInputAccessPath(expr)
        else -> null
    }

    private fun staticInputAccessPath(expr: AccessExpr): AccessPath? {
        val base = expr.base as? IdentifierExpr ?: return null
        val staticSegments = mutableListOf<AccessSegment>()
        for (seg in expr.segs) {
            val static = seg as? AccessSeg.Static ?: return null
            staticSegments += staticSegment(static.key)
        }
        return if (isInputAlias(base.name)) {
            AccessPath(staticSegments)
        } else if (!isLocal(base.name) && !hostFns.contains(base.name)) {
            AccessPath(listOf(AccessSegment.Field(base.name)) + staticSegments)
        } else {
            null
        }
    }

    private fun buildInputSchema(): RequirementSchemaV2 {
        val root = RequirementNodeV2(
            required = true,
            shape = ValueShape.ObjectShape(
                schema = SchemaGuarantee(
                    fields = linkedMapOf(),
                    mayEmitNull = false,
                    dynamicFields = emptyList(),
                ),
                closed = false,
            ),
            open = true,
            children = linkedMapOf(),
            evidence = emptyList(),
        )
        for (record in inputPathRecords.values) {
            addRequirementPath(root, record.path, record.shape, record.evidence)
        }
        val optionalTopLevel = topLevelOptionalFromAnyOf(requirementExprs)
        if (optionalTopLevel.isNotEmpty()) {
            for (name in optionalTopLevel) {
                val child = root.children[name] ?: continue
                root.children[name] = child.copy(required = false)
            }
        }
        return RequirementSchemaV2(
            root = root,
            requirements = requirementExprs.distinct(),
            opaqueRegions = inputOpaque.values.toList(),
        )
    }

    private fun buildOutputSchema(): GuaranteeSchemaV2 {
        val root = outputRoot ?: GuaranteeNodeV2(
            required = true,
            shape = ValueShape.ObjectShape(
                schema = SchemaGuarantee(
                    fields = linkedMapOf(),
                    mayEmitNull = false,
                    dynamicFields = emptyList(),
                ),
                closed = false,
            ),
            open = true,
            origin = OriginKind.OUTPUT,
            children = linkedMapOf(),
            evidence = emptyList(),
        )
        return GuaranteeSchemaV2(
            root = root,
            mayEmitNull = shapeMayBeNull(root.shape),
            opaqueRegions = outputOpaque.values.toList(),
        )
    }

    private fun addRequirementPath(
        root: RequirementNodeV2,
        path: AccessPath,
        shape: ValueShape,
        evidence: List<InferenceEvidenceV2>,
    ) {
        if (path.segments.isEmpty()) return
        var cursor = root
        path.segments.forEachIndexed { index, segment ->
            val name = when (segment) {
                is AccessSegment.Field -> segment.name
                is AccessSegment.Index -> segment.index
                AccessSegment.Dynamic -> return@forEachIndexed
            }
            val existing = cursor.children[name]
            val isLeaf = index == path.segments.lastIndex
            val defaultShape = if (isLeaf) shape else ValueShape.ObjectShape(
                schema = SchemaGuarantee(
                    fields = linkedMapOf(),
                    mayEmitNull = false,
                    dynamicFields = emptyList(),
                ),
                closed = false,
            )
            val next = if (existing == null) {
                RequirementNodeV2(
                    required = true,
                    shape = defaultShape,
                    open = true,
                    children = linkedMapOf(),
                    evidence = if (isLeaf) evidence else emptyList(),
                )
            } else {
                existing.copy(
                    shape = mergeValueShape(existing.shape, defaultShape),
                    evidence = if (isLeaf) (existing.evidence + evidence).distinct() else existing.evidence,
                )
            }
            cursor.children[name] = next
            cursor = next
        }
    }

    private fun topLevelOptionalFromAnyOf(expressions: List<RequirementExprV2>): Set<String> {
        val names = linkedSetOf<String>()
        for (expr in expressions) {
            val anyOf = expr as? RequirementExprV2.AnyOf ?: continue
            for (child in anyOf.children) {
                val leaf = child as? RequirementExprV2.PathNonNull ?: continue
                val first = leaf.path.segments.firstOrNull() as? AccessSegment.Field ?: continue
                names += first.name
            }
        }
        return names
    }

    private fun guaranteeNodeFromShape(
        shape: ValueShape,
        required: Boolean,
        origin: OriginKind,
        evidence: List<InferenceEvidenceV2>,
    ): GuaranteeNodeV2 {
        if (shape is ValueShape.ObjectShape) {
            val children = LinkedHashMap<String, GuaranteeNodeV2>()
            for ((name, field) in shape.schema.fields) {
                children[name] = guaranteeNodeFromShape(
                    shape = field.shape,
                    required = field.required,
                    origin = field.origin,
                    evidence = evidence,
                )
            }
            return GuaranteeNodeV2(
                required = required,
                shape = shape,
                open = !shape.closed,
                origin = origin,
                children = children,
                evidence = evidence,
            )
        }
        return GuaranteeNodeV2(
            required = required,
            shape = shape,
            open = true,
            origin = origin,
            children = linkedMapOf(),
            evidence = evidence,
        )
    }

    private fun mergeGuaranteeNodes(left: GuaranteeNodeV2, right: GuaranteeNodeV2): GuaranteeNodeV2 {
        val names = left.children.keys + right.children.keys
        val mergedChildren = LinkedHashMap<String, GuaranteeNodeV2>()
        for (name in names) {
            val l = left.children[name]
            val r = right.children[name]
            mergedChildren[name] = when {
                l == null -> r!!
                r == null -> l
                else -> mergeGuaranteeNodes(l, r)
            }
        }
        return GuaranteeNodeV2(
            required = left.required || right.required,
            shape = mergeValueShape(left.shape, right.shape),
            open = left.open || right.open,
            origin = if (left.origin == right.origin) left.origin else OriginKind.MERGED,
            children = mergedChildren,
            evidence = (left.evidence + right.evidence).distinct(),
        )
    }

    private fun mergeEnv(
        left: LinkedHashMap<String, AbstractValue>,
        right: LinkedHashMap<String, AbstractValue>,
    ): LinkedHashMap<String, AbstractValue> {
        val merged = LinkedHashMap<String, AbstractValue>()
        val names = left.keys + right.keys
        for (name in names) {
            val l = left[name]
            val r = right[name]
            merged[name] = when {
                l == null -> r!!
                r == null -> l
                else -> mergeAbstractValues(l, r)
            }
        }
        return merged
    }

    private fun mergeAbstractValues(left: AbstractValue, right: AbstractValue): AbstractValue = AbstractValue(
        shape = mergeValueShape(left.shape, right.shape),
        provenance = left.provenance + right.provenance,
        evidence = (left.evidence + right.evidence).distinct(),
    )

    private fun mergeValueShape(left: ValueShape, right: ValueShape): ValueShape {
        if (left == ValueShape.Unknown) return right
        if (right == ValueShape.Unknown) return left
        if (left == right) return left
        val leftOptions = if (left is ValueShape.Union) left.options else listOf(left)
        val rightOptions = if (right is ValueShape.Union) right.options else listOf(right)
        val merged = LinkedHashSet<ValueShape>()
        merged.addAll(leftOptions)
        merged.addAll(rightOptions)
        return ValueShape.Union(merged.toList())
    }

    private fun shapeMayBeNull(shape: ValueShape): Boolean = when (shape) {
        ValueShape.Null -> true
        is ValueShape.Union -> shape.options.any(::shapeMayBeNull)
        else -> false
    }

    private fun descendShape(shape: ValueShape, segs: List<AccessSeg>): ValueShape {
        var current = shape
        for (seg in segs) {
            current = when (seg) {
                is AccessSeg.Dynamic -> ValueShape.Unknown
                is AccessSeg.Static -> descendStatic(current, seg.key)
            }
            if (current == ValueShape.Unknown) return current
        }
        return current
    }

    private fun descendStatic(shape: ValueShape, key: ObjKey): ValueShape = when (shape) {
        is ValueShape.ObjectShape -> when (key) {
            is ObjKey.Name -> shape.schema.fields[key.v]?.shape ?: ValueShape.Unknown
            is ObjKey.Index -> shape.schema.fields[renderIndexKey(key)]?.shape ?: ValueShape.Unknown
        }
        is ValueShape.ArrayShape -> shape.element
        is ValueShape.SetShape -> shape.element
        is ValueShape.Union -> shape.options.map { option -> descendStatic(option, key) }.reduce(::mergeValueShape)
        else -> ValueShape.Unknown
    }

    private fun renderIndexKey(key: ObjKey.Index): String = when (key) {
        is I32 -> key.v.toString()
        is I64 -> key.v.toString()
        is IBig -> key.v.toString()
    }

    private fun addInputOpaque(path: AccessPath, reason: DynamicReason) {
        inputOpaque[opaqueKey(path)] = OpaqueRegionV2(path, reason)
    }

    private fun addOutputOpaque(path: AccessPath, reason: DynamicReason) {
        outputOpaque[opaqueKey(path)] = OpaqueRegionV2(path, reason)
    }

    private fun opaqueKey(path: AccessPath): String = path.segments.joinToString(".") { segment ->
        when (segment) {
            is AccessSegment.Field -> segment.name
            is AccessSegment.Index -> segment.index
            AccessSegment.Dynamic -> "*"
        }
    }

    private fun recordInputPath(path: AccessPath, shape: ValueShape, token: Token, ruleId: String) {
        val key = opaqueKey(path)
        val existing = inputPathRecords[key]
        val nextEvidence = evidence(token, ruleId)
        if (existing == null) {
            inputPathRecords[key] = InputPathRecord(path, shape, mutableListOf(nextEvidence))
            return
        }
        existing.shape = mergeValueShape(existing.shape, shape)
        existing.evidence += nextEvidence
    }

    private fun enforceProvenanceShape(
        paths: Collection<AccessPath>,
        shape: ValueShape,
        token: Token,
        ruleId: String,
    ) {
        for (path in paths) {
            recordInputPath(path, shape, token, ruleId)
        }
    }

    private fun promoteShapeForStaticAccess(baseName: String, segs: List<AccessSeg>) {
        if (isInputAlias(baseName) || hostFns.contains(baseName)) return
        if (segs.isEmpty()) return
        val current = env[baseName] ?: return
        val staticSegments = mutableListOf<AccessSegment>()
        for (seg in segs) {
            val static = seg as? AccessSeg.Static ?: return
            staticSegments += staticSegment(static.key)
        }
        val promoted = ensureObjectPath(current.shape, staticSegments)
        env[baseName] = current.copy(shape = promoted)
    }

    private fun applySetToLocalTarget(target: AccessExpr, value: AbstractValue) {
        val resolved = resolveLocalTargetPath(target) ?: return
        val current = env[resolved.base] ?: return
        val nextShape = writeShapeAtPath(current.shape, resolved.segments, value.shape)
        env[resolved.base] = current.copy(
            shape = nextShape,
            evidence = (current.evidence + value.evidence).distinct(),
        )
    }

    private fun applyAppendToLocalTarget(target: AccessExpr, value: AbstractValue) {
        val resolved = resolveLocalTargetPath(target) ?: return
        val current = env[resolved.base] ?: return
        val existing = descendBySegments(current.shape, resolved.segments)
        val nextLeaf = when (existing) {
            is ValueShape.ArrayShape -> ValueShape.ArrayShape(mergeValueShape(existing.element, value.shape))
            else -> ValueShape.ArrayShape(value.shape)
        }
        val nextShape = writeShapeAtPath(current.shape, resolved.segments, nextLeaf)
        env[resolved.base] = current.copy(
            shape = nextShape,
            evidence = (current.evidence + value.evidence).distinct(),
        )
    }

    private fun applyModifyToLocalTarget(
        target: AccessExpr,
        updates: List<Pair<ObjKey, AbstractValue>>,
    ) {
        val resolved = resolveLocalTargetPath(target) ?: return
        val current = env[resolved.base] ?: return
        var nextShape = ensureObjectPath(current.shape, resolved.segments)
        for ((key, value) in updates) {
            val seg = staticSegment(key)
            nextShape = writeShapeAtPath(nextShape, resolved.segments + seg, value.shape)
        }
        env[resolved.base] = current.copy(
            shape = nextShape,
            evidence = (current.evidence + updates.flatMap { (_, value) -> value.evidence }).distinct(),
        )
    }

    private fun resolveLocalTargetPath(target: AccessExpr): ResolvedLocalTarget? {
        val base = target.base as? IdentifierExpr ?: return null
        if (isInputAlias(base.name) || hostFns.contains(base.name)) return null
        val segments = mutableListOf<AccessSegment>()
        for (seg in target.segs) {
            val static = seg as? AccessSeg.Static ?: return null
            segments += staticSegment(static.key)
        }
        return ResolvedLocalTarget(base.name, segments)
    }

    private fun writeShapeAtPath(
        shape: ValueShape,
        path: List<AccessSegment>,
        valueShape: ValueShape,
    ): ValueShape {
        if (path.isEmpty()) return mergeValueShape(shape, valueShape)
        val head = path.first()
        val tail = path.drop(1)
        return when (shape) {
            is ValueShape.ObjectShape -> {
                val key = when (head) {
                    is AccessSegment.Field -> head.name
                    is AccessSegment.Index -> head.index
                    AccessSegment.Dynamic -> return shape
                }
                val fields = LinkedHashMap(shape.schema.fields)
                val existing = fields[key]
                val next = writeShapeAtPath(existing?.shape ?: ValueShape.Unknown, tail, valueShape)
                fields[key] = FieldShape(
                    required = true,
                    shape = next,
                    origin = existing?.origin ?: OriginKind.MERGED,
                )
                ValueShape.ObjectShape(
                    schema = shape.schema.copy(fields = fields),
                    closed = shape.closed,
                )
            }
            is ValueShape.Union -> ValueShape.Union(shape.options.map { option ->
                writeShapeAtPath(option, path, valueShape)
            }.distinct())
            else -> {
                val promoted = ensureObjectPath(shape, path)
                writeShapeAtPath(promoted, path, valueShape)
            }
        }
    }

    private fun descendBySegments(shape: ValueShape, path: List<AccessSegment>): ValueShape {
        var current = shape
        for (segment in path) {
            current = when (current) {
                is ValueShape.ObjectShape -> {
                    val key = when (segment) {
                        is AccessSegment.Field -> segment.name
                        is AccessSegment.Index -> segment.index
                        AccessSegment.Dynamic -> return ValueShape.Unknown
                    }
                    current.schema.fields[key]?.shape ?: ValueShape.Unknown
                }
                is ValueShape.ArrayShape -> current.element
                is ValueShape.SetShape -> current.element
                is ValueShape.Union -> current.options.map { option ->
                    descendBySegments(option, listOf(segment))
                }.reduce(::mergeValueShape)
                else -> ValueShape.Unknown
            }
            if (current == ValueShape.Unknown) break
        }
        return current
    }

    private fun ensureObjectPath(shape: ValueShape, path: List<AccessSegment>): ValueShape {
        if (path.isEmpty()) return shape
        val head = path.first()
        val tail = path.drop(1)
        return when (shape) {
            is ValueShape.ObjectShape -> {
                val key = when (head) {
                    is AccessSegment.Field -> head.name
                    is AccessSegment.Index -> head.index
                    AccessSegment.Dynamic -> return shape
                }
                val fields = LinkedHashMap(shape.schema.fields)
                val existing = fields[key]
                val child = ensureObjectPath(existing?.shape ?: ValueShape.Unknown, tail)
                fields[key] = FieldShape(
                    required = existing?.required ?: true,
                    shape = child,
                    origin = existing?.origin ?: OriginKind.MERGED,
                )
                ValueShape.ObjectShape(
                    schema = shape.schema.copy(fields = fields),
                    closed = shape.closed,
                )
            }
            is ValueShape.Union -> ValueShape.Union(shape.options.map { option ->
                ensureObjectPath(option, path)
            }.distinct())
            else -> {
                val key = when (head) {
                    is AccessSegment.Field -> head.name
                    is AccessSegment.Index -> head.index
                    AccessSegment.Dynamic -> return shape
                }
                val child = ensureObjectPath(ValueShape.Unknown, tail)
                ValueShape.ObjectShape(
                    schema = SchemaGuarantee(
                        fields = linkedMapOf(
                            key to FieldShape(
                                required = true,
                                shape = child,
                                origin = OriginKind.MERGED,
                            ),
                        ),
                        mayEmitNull = false,
                        dynamicFields = emptyList(),
                    ),
                    closed = false,
                )
            }
        }
    }

    private fun evidence(token: Token, ruleId: String, confidence: Double = 0.8): InferenceEvidenceV2 =
        InferenceEvidenceV2(
            sourceSpans = listOf(token),
            ruleId = ruleId,
            confidence = confidence,
            notes = null,
        )

    private fun cloneEnv(): LinkedHashMap<String, AbstractValue> = LinkedHashMap(env)

    private fun replaceEnv(next: LinkedHashMap<String, AbstractValue>) {
        env.clear()
        env.putAll(next)
    }

    private fun analyzeBranchWithEnv(
        seed: LinkedHashMap<String, AbstractValue>,
        block: CodeBlock,
        initialLocals: Set<String> = emptySet(),
        whereExpr: Expr? = null,
        fallbackExpr: Expr? = null,
        fallbackAbort: AbortStmt? = null,
    ): LinkedHashMap<String, AbstractValue> {
        val before = cloneEnv()
        replaceEnv(seed)
        withScope(initialLocals) {
            whereExpr?.let { expr -> evalExpr(expr) }
            analyzeBlock(block)
            fallbackExpr?.let { expr -> evalExpr(expr) }
            fallbackAbort?.value?.let { value -> evalExpr(value) }
        }
        val result = cloneEnv()
        replaceEnv(before)
        return result
    }

    private fun withScope(initialLocals: Collection<String>, action: () -> Unit) {
        val created = mutableSetOf<String>()
        scopes.addLast(created)
        for (name in initialLocals) {
            if (!env.containsKey(name)) {
                env[name] = AbstractValue(ValueShape.Unknown)
                created += name
            }
        }
        try {
            action()
        } finally {
            scopes.removeLast()
            for (name in created) {
                env.remove(name)
            }
        }
    }

    private fun <T> withScopedValue(name: String, value: AbstractValue, action: () -> T): T {
        val previous = env[name]
        env[name] = value
        val created = previous == null
        if (created) {
            declareLocal(name)
        }
        return try {
            action()
        } finally {
            if (created) env.remove(name) else env[name] = previous
        }
    }

    private fun declareLocal(name: String) {
        if (scopes.isEmpty()) return
        if (isLocal(name)) return
        scopes.last().add(name)
    }

    private fun isLocal(name: String): Boolean = scopes.any { scope -> name in scope }

    private fun isInputAlias(name: String): Boolean = name == DEFAULT_INPUT_ALIAS || name in COMPAT_INPUT_ALIASES

    private fun staticSegment(key: ObjKey): AccessSegment = when (key) {
        is ObjKey.Name -> AccessSegment.Field(key.v)
        is I32 -> AccessSegment.Index(key.v.toString())
        is I64 -> AccessSegment.Index(key.v.toString())
        is IBig -> AccessSegment.Index(key.v.toString())
    }

    private data class InputPathRecord(
        val path: AccessPath,
        var shape: ValueShape,
        val evidence: MutableList<InferenceEvidenceV2>,
    )

    private data class ResolvedLocalTarget(
        val base: String,
        val segments: List<AccessSegment>,
    )

    private data class AbstractValue(
        val shape: ValueShape,
        val provenance: Set<AccessPath> = emptySet(),
        val evidence: List<InferenceEvidenceV2> = emptyList(),
    ) {
        fun arrayElement(): ValueShape = when (shape) {
            is ValueShape.ArrayShape -> shape.element
            is ValueShape.SetShape -> shape.element
            is ValueShape.Union -> shape.options.map { option ->
                when (option) {
                    is ValueShape.ArrayShape -> option.element
                    is ValueShape.SetShape -> option.element
                    else -> ValueShape.Unknown
                }
            }.reduce(::mergeUnionShapes)
            else -> ValueShape.Unknown
        }

        private fun mergeUnionShapes(left: ValueShape, right: ValueShape): ValueShape {
            if (left == ValueShape.Unknown) return right
            if (right == ValueShape.Unknown) return left
            if (left == right) return left
            return ValueShape.Union(listOf(left, right).distinct())
        }
    }

    private data class Refinement(
        val thenRules: List<RefinementRule>,
        val elseRules: List<RefinementRule>,
    )

    private data class RefinementRule(
        val target: RefinementTarget,
        val kind: RefineKind,
    )

    private data class RefinementTarget(
        val base: String,
        val segments: List<AccessSegment>,
    )

    private enum class RefineKind {
        NON_NULL,
        NULL_ONLY,
        OBJECT_ONLY,
        NOT_OBJECT,
    }
}
