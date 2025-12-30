package v2.sema

import v2.AbortStmt
import v2.AccessExpr
import v2.AccessSeg
import v2.AppendToStmt
import v2.AppendToVarStmt
import v2.ArrayCompExpr
import v2.ArrayExpr
import v2.BoolExpr
import v2.CallExpr
import v2.CaseExpr
import v2.CodeBlock
import v2.ComputedProperty
import v2.Expr
import v2.ExprStmt
import v2.ForEachStmt
import v2.I32
import v2.I64
import v2.IBig
import v2.IdentifierExpr
import v2.IfElseExpr
import v2.IfStmt
import v2.InvokeExpr
import v2.LetStmt
import v2.LiteralProperty
import v2.ModifyStmt
import v2.NullLiteral
import v2.NumberLiteral
import v2.ObjectExpr
import v2.ObjKey
import v2.OutputStmt
import v2.ReturnStmt
import v2.SetStmt
import v2.SetVarStmt
import v2.StringExpr
import v2.Token
import v2.TransformDecl
import v2.TryCatchExpr
import v2.TryCatchStmt
import v2.UnaryExpr
import v2.DEFAULT_INPUT_ALIAS
import v2.COMPAT_INPUT_ALIASES
import v2.contract.AccessPath
import v2.contract.AccessSegment
import v2.contract.ContractSource
import v2.contract.DynamicAccess
import v2.contract.DynamicField
import v2.contract.DynamicReason
import v2.contract.FieldConstraint
import v2.contract.FieldShape
import v2.contract.OriginKind
import v2.contract.SchemaGuarantee
import v2.contract.SchemaRequirement
import v2.contract.TransformContract
import v2.contract.ValueShape

public class TransformShapeSynthesizer(
    private val hostFns: Set<String> = emptySet(),
) {

    private val scopes = ArrayDeque<MutableSet<String>>()

    public fun synthesize(transform: TransformDecl): TransformContract {
        val initialScope = transform.params.toMutableSet()
        val state = analyzeBlock(transform.body as CodeBlock, initialScope)
        val input = SchemaRequirement(
            fields = state.inputFields,
            open = true,
            dynamicAccess = state.dynamicInput,
        )
        val output = SchemaGuarantee(
            fields = state.outputFields,
            mayEmitNull = state.outputMayEmitNull,
            dynamicFields = state.dynamicOutput,
        )
        return TransformContract(
            input = input,
            output = output,
            source = ContractSource.INFERRED,
        )
    }

    private fun analyzeBlock(block: CodeBlock, initialScope: Collection<String>): ShapeState {
        scopes.addLast(initialScope.toMutableSet())
        var acc = ShapeState.empty()
        for (stmt in block.statements) {
            acc = mergeSequential(acc, analyzeStmt(stmt))
        }
        scopes.removeLast()
        return acc
    }

    private fun analyzeStmt(stmt: v2.Stmt): ShapeState = when (stmt) {
        is LetStmt -> {
            val exprState = analyzeExpr(stmt.expr)
            scopes.last().add(stmt.name)
            exprState
        }

        is SetStmt -> {
            val targetState = analyzeAccessTarget(stmt.target)
            val valueState = analyzeExpr(stmt.value)
            mergeSequential(targetState, valueState)
        }

        is SetVarStmt -> analyzeExpr(stmt.value)

        is AppendToStmt -> {
            val targetState = analyzeAccessTarget(stmt.target)
            val valueState = analyzeExpr(stmt.value)
            val initState = stmt.init?.let { analyzeExpr(it) } ?: ShapeState.empty()
            mergeSequential(targetState, mergeSequential(valueState, initState))
        }

        is AppendToVarStmt -> {
            val valueState = analyzeExpr(stmt.value)
            val initState = stmt.init?.let { analyzeExpr(it) } ?: ShapeState.empty()
            mergeSequential(valueState, initState)
        }

        is ModifyStmt -> {
            val targetState = analyzeAccessTarget(stmt.target)
            var updateState = ShapeState.empty()
            for (prop in stmt.updates) {
                updateState = mergeSequential(updateState, analyzeProperty(prop))
            }
            mergeSequential(targetState, updateState)
        }

        is OutputStmt -> analyzeOutputExpr(stmt.template)

        is IfStmt -> {
            val conditionState = analyzeExpr(stmt.condition)
            val thenState = analyzeBlock(stmt.thenBlock, scopes.last())
            val elseState = stmt.elseBlock?.let { analyzeBlock(it, scopes.last()) } ?: ShapeState.empty()
            val branchState = mergeBranches(thenState, elseState)
            mergeSequential(conditionState, branchState)
        }

        is ForEachStmt -> {
            val iterableState = analyzeExpr(stmt.iterable)
            val loopScope = scopes.last().toMutableSet().apply { add(stmt.varName) }
            val bodyState = analyzeBlock(stmt.body, loopScope)
            val whereState = stmt.where?.let { analyzeExpr(it) } ?: ShapeState.empty()
            val loopState = mergeSequential(whereState, bodyState)
            val optionalLoop = mergeBranches(loopState, ShapeState.empty())
            mergeSequential(iterableState, optionalLoop)
        }

        is TryCatchStmt -> {
            val tryState = analyzeExpr(stmt.tryExpr)
            val fallbackExprState = stmt.fallbackExpr?.let { analyzeExpr(it) } ?: ShapeState.empty()
            val fallbackAbortState = stmt.fallbackAbort?.let { analyzeAbort(it) } ?: ShapeState.empty()
            val fallbackState = mergeSequential(fallbackExprState, fallbackAbortState)
            val optionalFallback = mergeBranches(fallbackState, ShapeState.empty())
            mergeSequential(tryState, optionalFallback)
        }

        is ExprStmt -> analyzeExpr(stmt.expr)
        is ReturnStmt -> stmt.value?.let { analyzeExpr(it) } ?: ShapeState.empty()
        is AbortStmt -> analyzeAbort(stmt)

        else -> ShapeState.empty()
    }

    private fun analyzeAbort(stmt: AbortStmt): ShapeState = stmt.value?.let { analyzeExpr(it) } ?: ShapeState.empty()

    private fun analyzeProperty(prop: v2.Property): ShapeState = when (prop) {
        is LiteralProperty -> analyzeExpr(prop.value)
        is ComputedProperty -> mergeSequential(analyzeExpr(prop.keyExpr), analyzeExpr(prop.value))
    }

    private fun analyzeAccessTarget(target: AccessExpr): ShapeState {
        var acc = ShapeState.empty()
        for (seg in target.segs) {
            if (seg is AccessSeg.Dynamic) {
                acc = mergeSequential(acc, analyzeExpr(seg.keyExpr))
            }
        }
        return acc
    }

    private fun analyzeOutputExpr(expr: Expr): ShapeState {
        var acc = ShapeState.empty()
        val shape = shapeFromExpr(expr)
        val outputMayEmitNull = shapeMayBeNull(shape)
        acc = acc.copy(outputMayEmitNull = outputMayEmitNull)
        when (expr) {
            is ObjectExpr -> {
                val outputData = outputDataFromObject(expr)
                for (prop in expr.fields) {
                    acc = mergeSequential(acc, analyzeOutputProperty(prop))
                }
                val mergedFields = mergeOutputFields(acc.outputFields, outputData.fields)
                val mergedDynamic = acc.dynamicOutput + outputData.dynamicFields
                acc = acc.copy(outputFields = mergedFields, dynamicOutput = mergedDynamic)
            }
            else -> {
                acc = mergeSequential(acc, analyzeExpr(expr))
            }
        }
        return acc
    }

    private fun analyzeOutputProperty(prop: v2.Property): ShapeState = when (prop) {
        is LiteralProperty -> analyzeExpr(prop.value)
        is ComputedProperty -> mergeSequential(analyzeExpr(prop.keyExpr), analyzeExpr(prop.value))
    }

    private fun outputDataFromObject(expr: ObjectExpr): OutputData {
        val fields = LinkedHashMap<String, FieldShape>()
        val dynamicFields = mutableListOf<DynamicField>()
        for (prop in expr.fields) {
            when (prop) {
                is LiteralProperty -> {
                    val key = prop.key
                    if (key is ObjKey.Name) {
                        fields[key.v] = FieldShape(
                            required = true,
                            shape = shapeFromExpr(prop.value),
                            origin = OriginKind.OUTPUT,
                        )
                    } else {
                        dynamicFields.add(
                            DynamicField(
                                path = AccessPath(listOf(staticSegment(key))),
                                valueShape = shapeFromExpr(prop.value),
                                reason = DynamicReason.INDEX,
                            ),
                        )
                    }
                }

                is ComputedProperty -> {
                    dynamicFields.add(
                        DynamicField(
                            path = AccessPath(listOf(AccessSegment.Dynamic)),
                            valueShape = shapeFromExpr(prop.value),
                            reason = DynamicReason.COMPUTED,
                        ),
                    )
                }
            }
        }
        return OutputData(fields, dynamicFields)
    }

    private fun analyzeExpr(expr: Expr): ShapeState = when (expr) {
        is IdentifierExpr -> analyzeIdentifier(expr)
        is AccessExpr -> analyzeAccess(expr)
        is StringExpr -> ShapeState.empty()
        is NumberLiteral -> ShapeState.empty()
        is BoolExpr -> ShapeState.empty()
        is NullLiteral -> ShapeState.empty()
        is ObjectExpr -> analyzeObjectExpr(expr)
        is ArrayExpr -> analyzeArrayExpr(expr)
        is ArrayCompExpr -> analyzeArrayCompExpr(expr)
        is CallExpr -> analyzeCallExpr(expr)
        is InvokeExpr -> analyzeInvokeExpr(expr)
        is UnaryExpr -> analyzeExpr(expr.expr)
        is IfElseExpr -> analyzeIfElseExpr(expr)
        is CaseExpr -> analyzeCaseExpr(expr)
        is TryCatchExpr -> analyzeTryCatchExpr(expr)
        is v2.BinaryExpr -> mergeSequential(analyzeExpr(expr.left), analyzeExpr(expr.right))
        is v2.SharedStateAwaitExpr -> ShapeState.empty()
        is v2.LambdaExpr -> analyzeLambdaExpr(expr)
    }

    private fun analyzeIdentifier(expr: IdentifierExpr): ShapeState {
        val name = expr.name
        if (isLocal(name) || isInputAlias(name) || hostFns.contains(name)) {
            return ShapeState.empty()
        }
        val field = FieldConstraint(
            required = true,
            shape = ValueShape.Unknown,
            sourceSpans = listOf(expr.token),
        )
        val fields = LinkedHashMap<String, FieldConstraint>()
        fields[name] = field
        return ShapeState(
            inputFields = fields,
            outputFields = LinkedHashMap(),
            dynamicInput = emptyList(),
            dynamicOutput = emptyList(),
            outputMayEmitNull = false,
        )
    }

    private fun analyzeAccess(expr: AccessExpr): ShapeState {
        var acc = ShapeState.empty()
        if (expr.base !is IdentifierExpr) {
            acc = mergeSequential(acc, analyzeExpr(expr.base))
        }
        for (seg in expr.segs) {
            if (seg is AccessSeg.Dynamic) {
                acc = mergeSequential(acc, analyzeExpr(seg.keyExpr))
            }
        }

        val baseIdent = expr.base as? IdentifierExpr ?: return acc
        if (isInputAlias(baseIdent.name)) {
            val pathState = inputAccessFromSegments(expr.segs, baseIdent.token)
            acc = mergeSequential(acc, pathState)
            return acc
        }
        if (!isLocal(baseIdent.name) && !hostFns.contains(baseIdent.name)) {
            val fieldState = inputAccessForField(baseIdent.name, baseIdent.token)
            val dynamicState = dynamicInputFromSegments(listOf(AccessSegment.Field(baseIdent.name)), expr.segs)
            acc = mergeSequential(acc, mergeSequential(fieldState, dynamicState))
        }
        return acc
    }

    private fun analyzeObjectExpr(expr: ObjectExpr): ShapeState {
        var acc = ShapeState.empty()
        for (prop in expr.fields) {
            acc = mergeSequential(acc, analyzeProperty(prop))
        }
        return acc
    }

    private fun analyzeArrayExpr(expr: ArrayExpr): ShapeState {
        var acc = ShapeState.empty()
        for (item in expr.elements) {
            acc = mergeSequential(acc, analyzeExpr(item))
        }
        return acc
    }

    private fun analyzeArrayCompExpr(expr: ArrayCompExpr): ShapeState {
        val iterableState = analyzeExpr(expr.iterable)
        val loopScope = scopes.last().toMutableSet().apply { add(expr.varName) }
        val whereState = expr.where?.let { analyzeExpr(it) } ?: ShapeState.empty()
        val bodyState = analyzeExpr(expr.mapExpr)
        val loopState = mergeSequential(whereState, bodyState)
        val optionalLoop = mergeBranches(loopState, ShapeState.empty())
        return mergeSequential(iterableState, optionalLoop)
    }

    private fun analyzeCallExpr(expr: CallExpr): ShapeState {
        var acc = ShapeState.empty()
        for (arg in expr.args) {
            acc = mergeSequential(acc, analyzeExpr(arg))
        }
        return acc
    }

    private fun analyzeInvokeExpr(expr: InvokeExpr): ShapeState {
        var acc = analyzeExpr(expr.target)
        for (arg in expr.args) {
            acc = mergeSequential(acc, analyzeExpr(arg))
        }
        return acc
    }

    private fun analyzeLambdaExpr(expr: v2.LambdaExpr): ShapeState {
        val lambdaScope = scopes.last().toMutableSet().apply { addAll(expr.params) }
        return when (val body = expr.body) {
            is v2.ExprBody -> analyzeExprWithScope(body.expr, lambdaScope)
            is v2.BlockBody -> analyzeBlock(body.block, lambdaScope)
        }
    }

    private fun analyzeIfElseExpr(expr: IfElseExpr): ShapeState {
        val conditionState = analyzeExpr(expr.condition)
        val thenState = analyzeExpr(expr.thenBranch)
        val elseState = analyzeExpr(expr.elseBranch)
        val branchState = mergeBranches(thenState, elseState)
        return mergeSequential(conditionState, branchState)
    }

    private fun analyzeCaseExpr(expr: CaseExpr): ShapeState {
        var branchState = ShapeState.empty()
        for (whenBranch in expr.whens) {
            val condState = analyzeExpr(whenBranch.condition)
            val resultState = analyzeExpr(whenBranch.result)
            branchState = mergeBranches(branchState, mergeSequential(condState, resultState))
        }
        val elseState = analyzeExpr(expr.elseBranch)
        branchState = mergeBranches(branchState, elseState)
        return branchState
    }

    private fun analyzeTryCatchExpr(expr: TryCatchExpr): ShapeState {
        val tryState = analyzeExpr(expr.tryExpr)
        val fallbackScope = scopes.last().toMutableSet().apply { add(expr.exceptionName) }
        val fallbackState = analyzeExprWithScope(expr.fallbackExpr, fallbackScope)
        val optionalFallback = mergeBranches(fallbackState, ShapeState.empty())
        return mergeSequential(tryState, optionalFallback)
    }

    private fun analyzeExprWithScope(expr: Expr, scope: MutableSet<String>): ShapeState {
        scopes.addLast(scope)
        val state = analyzeExpr(expr)
        scopes.removeLast()
        return state
    }

    private fun inputAccessFromSegments(segs: List<AccessSeg>, token: Token): ShapeState {
        if (segs.isEmpty()) {
            return ShapeState.empty()
        }
        val dynamicState = if (segs.any { it is AccessSeg.Dynamic }) {
            dynamicInputFromSegments(emptyList(), segs)
        } else {
            ShapeState.empty()
        }
        val first = segs.first()
        return when (first) {
            is AccessSeg.Static -> {
                val key = first.key
                when (key) {
                    is ObjKey.Name -> mergeSequential(inputAccessForField(key.v, token), dynamicState)
                    else -> dynamicState
                }
            }
            is AccessSeg.Dynamic -> dynamicState
        }
    }

    private fun inputAccessForField(name: String, token: Token): ShapeState {
        val fields = LinkedHashMap<String, FieldConstraint>()
        fields[name] = FieldConstraint(
            required = true,
            shape = ValueShape.Unknown,
            sourceSpans = listOf(token),
        )
        return ShapeState(
            inputFields = fields,
            outputFields = LinkedHashMap(),
            dynamicInput = emptyList(),
            dynamicOutput = emptyList(),
            outputMayEmitNull = false,
        )
    }

    private fun dynamicInputFromSegments(prefix: List<AccessSegment>, segs: List<AccessSeg>): ShapeState {
        val segments = prefix + segs.map { seg ->
            when (seg) {
                is AccessSeg.Static -> staticSegment(seg.key)
                is AccessSeg.Dynamic -> AccessSegment.Dynamic
            }
        }
        val access = DynamicAccess(
            path = AccessPath(segments),
            valueShape = null,
            reason = DynamicReason.KEY,
        )
        return ShapeState(
            inputFields = LinkedHashMap(),
            outputFields = LinkedHashMap(),
            dynamicInput = listOf(access),
            dynamicOutput = emptyList(),
            outputMayEmitNull = false,
        )
    }

    private fun staticSegment(key: ObjKey): AccessSegment = when (key) {
        is ObjKey.Name -> AccessSegment.Field(key.v)
        is I32 -> AccessSegment.Index(key.v.toString())
        is I64 -> AccessSegment.Index(key.v.toString())
        is IBig -> AccessSegment.Index(key.v.toString())
    }

    private fun shapeFromExpr(expr: Expr): ValueShape = when (expr) {
        is NullLiteral -> ValueShape.Null
        is StringExpr -> ValueShape.TextShape
        is NumberLiteral -> ValueShape.NumberShape
        is BoolExpr -> ValueShape.BooleanShape
        is ObjectExpr -> ValueShape.ObjectShape(
            schema = SchemaGuarantee(
                fields = outputDataFromObject(expr).fields,
                mayEmitNull = false,
                dynamicFields = emptyList(),
            ),
            closed = false,
        )
        is ArrayExpr -> {
            val elementShape = if (expr.elements.isEmpty()) {
                ValueShape.Unknown
            } else {
                expr.elements.map { shapeFromExpr(it) }.reduce(::mergeValueShape)
            }
            ValueShape.ArrayShape(elementShape)
        }
        is ArrayCompExpr -> ValueShape.ArrayShape(shapeFromExpr(expr.mapExpr))
        is IfElseExpr -> mergeValueShape(shapeFromExpr(expr.thenBranch), shapeFromExpr(expr.elseBranch))
        is TryCatchExpr -> mergeValueShape(shapeFromExpr(expr.tryExpr), shapeFromExpr(expr.fallbackExpr))
        is CaseExpr -> {
            var acc = shapeFromExpr(expr.elseBranch)
            for (branch in expr.whens) {
                acc = mergeValueShape(acc, shapeFromExpr(branch.result))
            }
            acc
        }
        else -> ValueShape.Unknown
    }

    private fun shapeMayBeNull(shape: ValueShape): Boolean = when (shape) {
        ValueShape.Null -> true
        is ValueShape.Union -> shape.options.any { shapeMayBeNull(it) }
        else -> false
    }

    private fun mergeSequential(left: ShapeState, right: ShapeState): ShapeState {
        val input = mergeInputFields(left.inputFields, right.inputFields, requiredAll = false)
        val output = mergeOutputFields(left.outputFields, right.outputFields)
        val dynamicInput = left.dynamicInput + right.dynamicInput
        val dynamicOutput = left.dynamicOutput + right.dynamicOutput
        return ShapeState(
            inputFields = input,
            outputFields = output,
            dynamicInput = dynamicInput,
            dynamicOutput = dynamicOutput,
            outputMayEmitNull = left.outputMayEmitNull || right.outputMayEmitNull,
        )
    }

    private fun mergeBranches(left: ShapeState, right: ShapeState): ShapeState {
        val input = mergeInputFields(left.inputFields, right.inputFields, requiredAll = true)
        val output = mergeOutputFieldsBranch(left.outputFields, right.outputFields)
        val dynamicInput = left.dynamicInput + right.dynamicInput
        val dynamicOutput = left.dynamicOutput + right.dynamicOutput
        return ShapeState(
            inputFields = input,
            outputFields = output,
            dynamicInput = dynamicInput,
            dynamicOutput = dynamicOutput,
            outputMayEmitNull = left.outputMayEmitNull || right.outputMayEmitNull,
        )
    }

    private fun mergeInputFields(
        left: LinkedHashMap<String, FieldConstraint>,
        right: LinkedHashMap<String, FieldConstraint>,
        requiredAll: Boolean,
    ): LinkedHashMap<String, FieldConstraint> {
        val merged = LinkedHashMap<String, FieldConstraint>()
        val keys = left.keys + right.keys
        for (key in keys) {
            val l = left[key]
            val r = right[key]
            val required = if (requiredAll) {
                l != null && r != null && l.required && r.required
            } else {
                (l?.required == true) || (r?.required == true)
            }
            val shape = when {
                l == null -> r?.shape ?: ValueShape.Unknown
                r == null -> l.shape
                else -> mergeValueShape(l.shape, r.shape)
            }
            val spans = (l?.sourceSpans.orEmpty() + r?.sourceSpans.orEmpty()).distinct()
            merged[key] = FieldConstraint(
                required = required,
                shape = shape,
                sourceSpans = spans,
            )
        }
        return merged
    }

    private fun mergeOutputFields(
        left: LinkedHashMap<String, FieldShape>,
        right: LinkedHashMap<String, FieldShape>,
    ): LinkedHashMap<String, FieldShape> {
        val merged = LinkedHashMap<String, FieldShape>()
        val keys = left.keys + right.keys
        for (key in keys) {
            val l = left[key]
            val r = right[key]
            val required = (l?.required == true) || (r?.required == true)
            val shape = when {
                l == null -> r?.shape ?: ValueShape.Unknown
                r == null -> l.shape
                else -> mergeValueShape(l.shape, r.shape)
            }
            val origin = mergeOrigin(l?.origin, r?.origin)
            merged[key] = FieldShape(
                required = required,
                shape = shape,
                origin = origin,
            )
        }
        return merged
    }

    private fun mergeOutputFieldsBranch(
        left: LinkedHashMap<String, FieldShape>,
        right: LinkedHashMap<String, FieldShape>,
    ): LinkedHashMap<String, FieldShape> {
        val merged = LinkedHashMap<String, FieldShape>()
        val keys = left.keys + right.keys
        for (key in keys) {
            val l = left[key]
            val r = right[key]
            val required = l != null && r != null && l.required && r.required
            val shape = when {
                l == null -> r?.shape ?: ValueShape.Unknown
                r == null -> l.shape
                else -> mergeValueShape(l.shape, r.shape)
            }
            val origin = mergeOrigin(l?.origin, r?.origin)
            merged[key] = FieldShape(
                required = required,
                shape = shape,
                origin = origin,
            )
        }
        return merged
    }

    private fun mergeOrigin(left: OriginKind?, right: OriginKind?): OriginKind {
        return when {
            left == null -> right ?: OriginKind.MERGED
            right == null -> left
            left == right -> left
            else -> OriginKind.MERGED
        }
    }

    private fun mergeValueShape(left: ValueShape, right: ValueShape): ValueShape {
        if (left == ValueShape.Unknown) {
            return right
        }
        if (right == ValueShape.Unknown) {
            return left
        }
        if (left == right) {
            return left
        }
        val leftOptions = if (left is ValueShape.Union) left.options else listOf(left)
        val rightOptions = if (right is ValueShape.Union) right.options else listOf(right)
        val options = LinkedHashSet<ValueShape>()
        options.addAll(leftOptions)
        options.addAll(rightOptions)
        return ValueShape.Union(options.toList())
    }

    private fun isLocal(name: String): Boolean = scopes.any { name in it }

    private fun isInputAlias(name: String): Boolean = name == DEFAULT_INPUT_ALIAS || name in COMPAT_INPUT_ALIASES

    private data class ShapeState(
        val inputFields: LinkedHashMap<String, FieldConstraint>,
        val outputFields: LinkedHashMap<String, FieldShape>,
        val dynamicInput: List<DynamicAccess>,
        val dynamicOutput: List<DynamicField>,
        val outputMayEmitNull: Boolean,
    ) {
        companion object {
            fun empty(): ShapeState = ShapeState(
                inputFields = LinkedHashMap(),
                outputFields = LinkedHashMap(),
                dynamicInput = emptyList(),
                dynamicOutput = emptyList(),
                outputMayEmitNull = false,
            )
        }
    }

    private data class OutputData(
        val fields: LinkedHashMap<String, FieldShape>,
        val dynamicFields: List<DynamicField>,
    )
}
