package v2.vm

import v2.*
import v2.ir.*
import v2.vm.Instruction.*
import v2.vm.PathSegmentMeta
import v2.vm.PathWriteMeta
import v2.vm.PathWriteOp
import v2.vm.TraceMetadata
import kotlin.concurrent.Volatile

/**
 * Compiles Branchline IR nodes into VM bytecode instructions
 *
 * This compiler translates the existing intermediate representation (IR)
 * used by the current interpreter into stack-based VM bytecode for
 * efficient execution.
 */
class Compiler(
    private val funcs: Map<String, FuncDecl> = emptyMap(),
    private val hostFns: Map<String, (List<Any?>) -> Any?> = emptyMap(),
    private val useLocals: Boolean = true,
) {
    /** Quick metrics for instruction counts emitted in fast/slow SET/APPEND paths. */
    object Metrics {
        @Volatile
        var setFastPathInstr: Int = 0

        @Volatile var setGeneralInstr: Int = 0

        @Volatile var appendFastPathInstr: Int = 0

        @Volatile var appendGeneralInstr: Int = 0

        fun reset() {
            setFastPathInstr = 0
            setGeneralInstr = 0
            appendFastPathInstr = 0
            appendGeneralInstr = 0
        }

        fun snapshot(): Map<String, Int> = mapOf(
            "setFastPathInstr" to setFastPathInstr,
            "setGeneralInstr" to setGeneralInstr,
            "appendFastPathInstr" to appendFastPathInstr,
            "appendGeneralInstr" to appendGeneralInstr,
        )
    }

    // Instruction builder
    private val instructions = mutableListOf<Instruction>()
    private val constants = mutableListOf<Any?>()
    private val debugInfo = mutableMapOf<Int, String>()
    private val enterEvents = mutableMapOf<Int, MutableList<IRNode>>()
    private val exitEvents = mutableMapOf<Int, MutableList<IRNode>>()
    private val pathWrites = mutableMapOf<Int, PathWriteMeta>()

    // Jump table for control flow
    private val jumpTable = mutableMapOf<String, Int>()
    private val unresolvedJumps = mutableListOf<UnresolvedJump>()
    private val hostNameToIndex = LinkedHashMap<String, Int>()
    private val localIndexByName = LinkedHashMap<String, Int>()

    private data class UnresolvedJump(
        val instructionIndex: Int,
        val labelName: String,
        val jumpType: String // "JUMP", "JUMP_IF_TRUE", etc.
    )

    /**
     * Compile a list of IR nodes into bytecode
     */
    fun compile(irNodes: List<IRNode>): Bytecode {
        instructions.clear()
        constants.clear()
        debugInfo.clear()
        enterEvents.clear()
        exitEvents.clear()
        pathWrites.clear()
        jumpTable.clear()
        unresolvedJumps.clear()
        localIndexByName.clear()
        hostNameToIndex.clear()

        // Compile all IR nodes
        for (node in irNodes) {
            compileNode(node)
        }

        // Resolve jump addresses
        resolveJumps()

        // Peephole optimize
        val optimized = optimize(instructions)

        val metadata = TraceMetadata(
            enterEvents = remapEventMap(enterEvents, optimized.indexMap),
            exitEvents = remapEventMap(exitEvents, optimized.indexMap),
            pathWrites = remapPathWrites(pathWrites, optimized.indexMap),
        )

        return Bytecode.fromInstructions(
            instructions = optimized.instructions,
            constants = constants.toList(),
            debugInfo = debugInfo.toMap(),
            traceMetadata = metadata,
        )
    }

    /**
     * Compile a single IR node
     */
    private fun compileNode(node: IRNode) {
        val startIndex = instructions.size
        when (node) {
            is IRLet -> compileLet(node)
            is IRSet -> compileSet(node)
            is IRSetVar -> compileSetVar(node)
            is IRAppendTo -> compileAppendTo(node)
            is IRAppendVar -> compileAppendVar(node)
            is IRModify -> compileModify(node)
            is IROutput -> compileOutput(node)
            is IRExprOutput -> compileExprOutput(node)
            is IRIf -> compileIf(node)
            is IRForEach -> compileForEach(node)
            is IRTryCatch -> compileTryCatch(node)
            is IRReturn -> compileReturn(node)
            is IRAbort -> compileAbort(node)
            is IRExprStmt -> compileExprStmt(node)
        }
        if (instructions.size > startIndex) {
            val enterList = enterEvents.getOrPut(startIndex) { mutableListOf() }
            enterList.add(node)
            val endIndex = instructions.lastIndex
            val exitList = exitEvents.getOrPut(endIndex) { mutableListOf() }
            exitList.add(node)
        }
    }

    /**
     * Compile expressions into bytecode
     */
    private fun compileExpr(expr: Expr) {
        when (expr) {
            is NumberLiteral -> {
                emit(PUSH(unwrapNum(expr.value)))
            }
            is StringExpr -> {
                emit(PUSH(expr.value))
            }
            is BoolExpr -> {
                emit(PUSH(expr.value))
            }
            is NullLiteral -> {
                emit(PUSH(null))
            }
            is IdentifierExpr -> {
                if (useLocals) {
                    val idx = localIndexByName[expr.name]
                    if (idx != null) emit(LOAD_LOCAL(idx)) else emit(LOAD_VAR(expr.name))
                } else emit(LOAD_VAR(expr.name))
            }
            is BinaryExpr -> compileBinaryExpr(expr)
            is UnaryExpr -> compileUnaryExpr(expr)
            is CallExpr -> compileCallExpr(expr)
            is AccessExpr -> compileAccessExpr(expr)
            is ArrayExpr -> compileArrayExpr(expr)
            is ArrayCompExpr -> compileArrayCompExpr(expr)
            is ObjectExpr -> compileObjectExpr(expr)
            is LambdaExpr -> compileLambdaExpr(expr)
            is IfElseExpr -> compileIfElseExpr(expr)
            is CaseExpr -> compileExpr(lowerCaseExpr(expr))
            is TryCatchExpr -> compileTryCatchExpr(expr)
            is InvokeExpr -> compileInvokeExpr(expr)
            is SharedStateAwaitExpr -> {
                // Push args so that VM pops into (resource, key) in correct order
                // VM.pop() builds args left-to-right from top of stack
                emit(PUSH(expr.key))
                emit(PUSH(expr.resource))
                emit(CALL("AWAIT_SHARED", 2))
            }
        }
    }

    // === IR Node Compilation ===

    private fun compileLet(node: IRLet) {
        compileExpr(node.expr)
        if (useLocals) {
            val idx = localIndexByName.getOrPut(node.name) { localIndexByName.size }
            // Keep env mirror for compatibility with existing tests/integration
            emit(DUP)
            emit(STORE_VAR(node.name))
            emit(STORE_LOCAL(idx))
        } else {
            emit(STORE_VAR(node.name))
        }
    }

    private fun compileSet(node: IRSet) {
        val before = instructions.size
        // Fast-path: SET root.staticKey = value
        val target = node.target
        val baseIdent = target.base as? IdentifierExpr
        val staticSeg = target.segs.singleOrNull() as? AccessSeg.Static
        if (baseIdent != null && staticSeg != null) {
            val root = baseIdent.name
            val key = staticSeg.key
            // stack: value
            compileExpr(node.value)
            // load container and arrange (container, value)
            emit(LOAD_VAR(root))
            emit(SWAP)
            emit(SET_STATIC(key))
            val storeIndex = emitStoreRoot(root)
            recordPathWrite(
                storeIndex,
                PathWriteMeta(
                    op = PathWriteOp.SET,
                    root = root,
                    segments = listOf(PathSegmentMeta.Static(key)),
                )
            )
            Metrics.setFastPathInstr += (instructions.size - before)
            return
        }
        // General path
        compileExpr(node.value)
        compileAccessExprForSet(node.target)
        Metrics.setGeneralInstr += (instructions.size - before)
    }

    private fun compileSetVar(node: IRSetVar) {
        compileExpr(node.value)
        if (useLocals) {
            val idx = localIndexByName[node.name]
            if (idx != null) {
                // keep env mirror updated
                emit(DUP)
                emit(STORE_VAR(node.name))
                emit(STORE_LOCAL(idx))
            } else emit(STORE_VAR(node.name))
        } else emit(STORE_VAR(node.name))
    }

    private fun compileAppendTo(node: IRAppendTo) {
        val before = instructions.size
        // Fast-path: APPEND TO root.staticKey elem INIT init
        val target = node.target
        val baseIdent = target.base as? IdentifierExpr
        val staticSeg = target.segs.singleOrNull() as? AccessSeg.Static
        if (baseIdent != null && staticSeg != null) {
            val root = baseIdent.name
            val key = staticSeg.key
            // stack: elem
            compileExpr(node.value)
            // push init (or empty [])
            node.init?.let { compileExpr(it) } ?: emit(MAKE_ARRAY(0))
            // load current array: result.items
            emit(LOAD_VAR(root))
            emit(ACCESS_STATIC(key))
            // COALESCE(current, init): need (left=current, right=init) with right on top
            emit(SWAP)
            emit(COALESCE)
            // Now have elem (bottom), array (top). Want (array, elem)
            emit(SWAP)
            emit(APPEND)
            // Write back to result
            emit(LOAD_VAR(root))
            emit(SWAP)
            emit(SET_STATIC(key))
            val storeIndex = emitStoreRoot(root)
            recordPathWrite(
                storeIndex,
                PathWriteMeta(
                    op = PathWriteOp.APPEND,
                    root = root,
                    segments = listOf(PathSegmentMeta.Static(key)),
                )
            )
            Metrics.setFastPathInstr += (instructions.size - before)
            return
        }
        // Generic path
        compileExpr(node.value)
        val initExpr = node.init
        if (initExpr != null) {
            compileExpr(initExpr)
        } else {
            emit(PUSH(emptyList<Any?>()))
        }
        compileAccessExprForAppend(node.target, initProvided = initExpr != null)
        Metrics.appendGeneralInstr += (instructions.size - before)
    }

    private fun compileAppendVar(node: IRAppendVar) {
        val valVar = nextTemp("val")
        val initVar = nextTemp("init")

        compileExpr(node.value)
        emit(STORE_VAR(valVar))

        val initExpr = node.init
        if (initExpr != null) {
            compileExpr(initExpr)
        } else {
            emit(PUSH(emptyList<Any?>()))
        }
        emit(STORE_VAR(initVar))

        emit(LOAD_VAR(node.name))
        emit(LOAD_VAR(initVar))
        emit(COALESCE)
        emit(LOAD_VAR(valVar))
        emit(APPEND)
        val storeIndex = emitStoreRoot(node.name)
        recordPathWrite(
            storeIndex,
            PathWriteMeta(
                op = PathWriteOp.APPEND,
                root = node.name,
                segments = emptyList(),
                displayPath = listOf(node.name),
                directValues = true,
            )
        )
    }

    private fun compileModify(node: IRModify) {
        // Compile property updates (value then key), reversed for stack order
        val updateCount = node.updates.size
        for (property in node.updates.reversed()) {
            when (property) {
                is LiteralProperty -> {
                    compileExpr(property.value)
                    emit(PUSH(unwrapKey(property.key)))
                }
                is ComputedProperty -> {
                    compileExpr(property.value)
                    compileExpr(property.keyExpr)
                }
            }
        }

        val (baseName, segs, objVars) = compileAccessExprForModify(node.target)
        emit(MODIFY(updateCount))
        if (segs.isEmpty()) {
            // Target is a variable; write back directly
            emitStoreRoot(baseName)
        } else {
            // Rebuild to root with updated object now on stack
            rebuildToRoot(baseName, segs, objVars)
        }
    }

    private fun compileOutput(node: IROutput) {
        val fieldCount = node.fields.size
        for (property in node.fields.reversed()) { // Reverse for stack order
            when (property) {
                is LiteralProperty -> {
                    emit(PUSH(unwrapKey(property.key)))
                    compileExpr(property.value)
                }
                is ComputedProperty -> {
                    compileExpr(property.keyExpr)
                    compileExpr(property.value)
                }
            }
        }
        when (fieldCount) {
            0 -> emit(OUTPUT(0))
            1 -> emit(OUTPUT_1)
            2 -> emit(OUTPUT_2)
            else -> emit(OUTPUT(fieldCount))
        }
    }

    private fun compileExprOutput(node: IRExprOutput) {
        compileExpr(node.expr)
        // Convert single expression to output format
        emit(OUTPUT(0)) // Special case for expression output
    }

    private fun compileIf(node: IRIf) {
        // Compile condition
        compileExpr(node.condition)

        // Jump to else block if condition is false
        val elseLabel = generateLabel("else")
        val endLabel = generateLabel("end_if")

        emit(JUMP_IF_FALSE(0)) // Will be resolved later
        addUnresolvedJump(instructions.size - 1, elseLabel, "JUMP_IF_FALSE")

        // Compile then block
        for (stmt in node.thenBody) {
            compileNode(stmt)
        }

        // Jump to end to skip else block
        emit(JUMP(0))
        addUnresolvedJump(instructions.size - 1, endLabel, "JUMP")

        // Else block
        setLabel(elseLabel)
        node.elseBody?.let { elseBody ->
            for (stmt in elseBody) {
                compileNode(stmt)
            }
        }

        setLabel(endLabel)
    }

    private fun compileForEach(node: IRForEach) {
        // Set up loop iteration
        compileExpr(node.iterable)

        val loopStart = generateLabel("loop_start")
        val loopEnd = generateLabel("loop_end")
        val loopContinue = generateLabel("loop_continue")

        emit(INIT_FOREACH(node.varName, 0))
        addUnresolvedJump(instructions.size - 1, loopEnd, "INIT_FOREACH")

        setLabel(loopStart)

        // Compile where condition if present
        node.where?.let { whereExpr ->
            compileExpr(whereExpr)
            emit(JUMP_IF_FALSE(0))
            addUnresolvedJump(instructions.size - 1, loopContinue, "JUMP_IF_FALSE")
        }

        // Compile loop body
        for (stmt in node.body) {
            compileNode(stmt)
        }

        setLabel(loopContinue)

        // Continue loop
        emit(NEXT_FOREACH(0, 0))
        addUnresolvedJump(instructions.size - 1, loopStart, "NEXT_FOREACH_START")
        addUnresolvedJump(instructions.size - 1, loopEnd, "NEXT_FOREACH_END")

        setLabel(loopEnd)
    }

    private fun compileTryCatch(node: IRTryCatch) {
        val catchLabel = generateLabel("catch")
        val endLabel = generateLabel("end_try")

        // Start try block
        emit(TRY_START(0))
        addUnresolvedJump(instructions.size - 1, catchLabel, "TRY_START")

        // Compile try expression
        compileExpr(node.tryExpr)

        emit(TRY_END)
        emit(JUMP(0))
        addUnresolvedJump(instructions.size - 1, endLabel, "JUMP")

        // Catch block
        setLabel(catchLabel)
        emit(CATCH(node.exceptionName, node.retry))

        // Handle fallback
        node.fallbackExpr?.let { fallback ->
            compileExpr(fallback)
        }

        node.fallbackAbort?.let { abortExpr ->
            compileExpr(abortExpr)
            emit(ABORT)
        }

        setLabel(endLabel)
    }

    private fun compileTryCatchExpr(expr: TryCatchExpr) {
        val catchLabel = generateLabel("catch_expr")
        val endLabel = generateLabel("end_try_expr")

        emit(TRY_START(0))
        addUnresolvedJump(instructions.size - 1, catchLabel, "TRY_START")

        compileExpr(expr.tryExpr)

        emit(TRY_END)
        emit(JUMP(0))
        addUnresolvedJump(instructions.size - 1, endLabel, "JUMP")

        setLabel(catchLabel)
        emit(CATCH(expr.exceptionName, expr.retry ?: 0))
        compileExpr(expr.fallbackExpr)

        setLabel(endLabel)
    }

    private fun compileReturn(node: IRReturn) {
        node.value?.let { returnValue ->
            compileExpr(returnValue)
            emit(RETURN_VALUE)
        } ?: run {
            emit(RETURN)
        }
    }

    private fun compileAbort(node: IRAbort) {
        node.value?.let { abortValue ->
            compileExpr(abortValue)
        }
        emit(ABORT)
    }

    private fun compileExprStmt(node: IRExprStmt) {
        // Special-case SUSPEND: it doesn't push a value so no POP after it
        if (node.expr is UnaryExpr && node.expr.token.type == TokenType.SUSPEND) {
            compileExpr(node.expr)
        } else {
            compileExpr(node.expr)
            emit(POP) // Discard the result
        }
    }

    // === Expression Compilation ===

    /**
     * Recursively attempts to evaluate expression to a constant string.
     * Used to pre-build static parts of string concatenations so the VM
     * doesn't waste time concatenating them at runtime.
     */
    private fun constString(expr: Expr): String? = when (expr) {
        is StringExpr -> expr.value
        is BinaryExpr -> if (expr.token.type == TokenType.PLUS || expr.token.type == TokenType.CONCAT) {
            val l = constString(expr.left)
            val r = constString(expr.right)
            if (l != null && r != null) l + r else null
        } else {
            null
        }
        else -> null
    }

    private fun compileBinaryExpr(expr: BinaryExpr) {
        // Constant folding for numeric literals (preserve integer types when possible)
        run {
            val lnum = (expr.left as? NumberLiteral)?.let { unwrapNum(it.value) }
            val rnum = (expr.right as? NumberLiteral)?.let { unwrapNum(it.value) }
            when {
                lnum is Int && rnum is Int -> {
                    val folded: Any? = when (expr.token.type) {
                        TokenType.PLUS -> lnum + rnum
                        TokenType.MINUS -> lnum - rnum
                        TokenType.STAR -> lnum * rnum
                        TokenType.PERCENT -> if (rnum == 0) null else lnum % rnum
                        // avoid folding division due to int/float semantics
                        else -> null
                    }
                    if (folded != null) { emit(PUSH(folded)); return }
                }
                lnum is Long && rnum is Long -> {
                    val folded: Any? = when (expr.token.type) {
                        TokenType.PLUS -> lnum + rnum
                        TokenType.MINUS -> lnum - rnum
                        TokenType.STAR -> lnum * rnum
                        TokenType.PERCENT -> if (rnum == 0L) null else lnum % rnum
                        else -> null
                    }
                    if (folded != null) { emit(PUSH(folded)); return }
                }
                else -> Unit
            }
        }
        // Fold constant string concatenations like "foo" + "bar" at compile time
        if (expr.token.type == TokenType.PLUS || expr.token.type == TokenType.CONCAT) {
            constString(expr)?.let {
                emit(PUSH(it))
                return
            }
        }

        when (expr.token.type) {
            TokenType.AND -> {
                compileExpr(expr.left)
                emit(DUP)
                val shortCircuitLabel = generateLabel("and_short")
                emit(JUMP_IF_FALSE(0))
                addUnresolvedJump(instructions.size - 1, shortCircuitLabel, "JUMP_IF_FALSE")
                emit(POP)
                compileExpr(expr.right)
                setLabel(shortCircuitLabel)
            }
            TokenType.OR -> {
                compileExpr(expr.left)
                emit(DUP)
                val shortCircuitLabel = generateLabel("or_short")
                emit(JUMP_IF_TRUE(0))
                addUnresolvedJump(instructions.size - 1, shortCircuitLabel, "JUMP_IF_TRUE")
                emit(POP)
                compileExpr(expr.right)
                setLabel(shortCircuitLabel)
            }
            else -> {
                // Standard binary operations
                compileExpr(expr.left)
                compileExpr(expr.right)
                when (expr.token.type) {
                    TokenType.PLUS -> emit(ADD)
                    TokenType.MINUS -> emit(SUB)
                    TokenType.STAR -> emit(MUL)
                    TokenType.SLASH -> emit(DIV)
                    TokenType.PERCENT -> emit(MOD)
                    TokenType.EQ -> emit(EQ)
                    TokenType.NEQ -> emit(NEQ)
                    TokenType.LT -> emit(LT)
                    TokenType.LE -> emit(LE)
                    TokenType.GT -> emit(GT)
                    TokenType.GE -> emit(GE)
                    TokenType.CONCAT -> emit(CONCAT)
                    TokenType.COALESCE -> emit(COALESCE)
                    else -> error("Unsupported binary operator: ${expr.token.lexeme}")
                }
            }
        }
    }

    private fun compileUnaryExpr(expr: UnaryExpr) {
        when (expr.token.type) {
            TokenType.MINUS -> {
                compileExpr(expr.expr); emit(NEG)
            }
            TokenType.BANG -> {
                compileExpr(expr.expr); emit(NOT)
            }
            TokenType.AWAIT -> {
                // No-op: compile inner expression directly (interpreter does the same)
                compileExpr(expr.expr)
            }
            TokenType.SUSPEND -> {
                // Suspension point; no stack placeholder
                emit(SUSPEND)
            }
            else -> error("Unsupported unary operator: ${expr.token.lexeme}")
        }
    }

    private fun compileCallExpr(expr: CallExpr) {
        // Compile arguments in reverse order for stack
        for (arg in expr.args.reversed()) {
            compileExpr(arg)
        }
        val name = expr.callee.name
        when {
            funcs.containsKey(name) -> emit(CALL_FN(name, expr.args.size))
            hostFns.containsKey(name) -> {
                val idx = hostNameToIndex.getOrPut(name) { hostNameToIndex.size }
                emit(CALL_HOST(idx, name, expr.args.size))
            }
            else -> emit(CALL(name, expr.args.size))
        }
    }

    private fun compileInvokeExpr(expr: InvokeExpr) {
        // Compile target (should be a lambda)
        compileExpr(expr.target)

        // Compile arguments
        for (arg in expr.args.reversed()) {
            compileExpr(arg)
        }
        emit(CALL_LAMBDA(expr.args.size))
    }

    private fun compileAccessExpr(expr: AccessExpr) {
        // Compile base expression
        compileExpr(expr.base)

        // Apply access segments
        for (seg in expr.segs) {
            when (seg) {
                is AccessSeg.Static -> {
                    emit(ACCESS_STATIC(seg.key))
                }
                is AccessSeg.Dynamic -> {
                    when (val k = seg.keyExpr) {
                        is StringExpr -> {
                            emit(ACCESS_STATIC(ObjKey.Name(k.value)))
                        }
                        is NumberLiteral -> {
                            val num = k.value
                            if (num is ObjKey) {
                                emit(ACCESS_STATIC(num))
                            } else {
                                compileExpr(k)
                                emit(ACCESS_DYNAMIC)
                            }
                        }
                        else -> {
                            compileExpr(k)
                            emit(ACCESS_DYNAMIC)
                        }
                    }
                }
            }
        }
    }

    private fun compileArrayExpr(expr: ArrayExpr) {
        // Compile elements in natural order; runtime will reverse to maintain
        // proper sequence when building the array.
        for (element in expr.elements) {
            compileExpr(element)
        }
        emit(MAKE_ARRAY(expr.elements.size))
    }

    private fun compileArrayCompExpr(expr: ArrayCompExpr) {
        // Accumulator for results
        emit(MAKE_ARRAY(0))

        // Iterable to loop over
        compileExpr(expr.iterable)

        val loopStart = generateLabel("arr_comp_start")
        val loopEnd = generateLabel("arr_comp_end")
        val loopContinue = generateLabel("arr_comp_continue")

        emit(INIT_FOREACH(expr.varName, 0))
        addUnresolvedJump(instructions.size - 1, loopEnd, "INIT_FOREACH")

        setLabel(loopStart)

        expr.where?.let { whereExpr ->
            compileExpr(whereExpr)
            emit(JUMP_IF_FALSE(0))
            addUnresolvedJump(instructions.size - 1, loopContinue, "JUMP_IF_FALSE")
        }

        // Map expression result appended to accumulator
        compileExpr(expr.mapExpr)
        emit(APPEND)

        setLabel(loopContinue)

        emit(NEXT_FOREACH(0, 0))
        addUnresolvedJump(instructions.size - 1, loopStart, "NEXT_FOREACH_START")
        addUnresolvedJump(instructions.size - 1, loopEnd, "NEXT_FOREACH_END")

        setLabel(loopEnd)
    }

    private fun compileObjectExpr(expr: ObjectExpr) {
        val fieldCount = expr.fields.size
        for (property in expr.fields.reversed()) {
            when (property) {
                is LiteralProperty -> {
                    compileExpr(property.value)
                    emit(PUSH(unwrapKey(property.key)))
                }
                is ComputedProperty -> {
                    compileExpr(property.value)
                    compileExpr(property.keyExpr)
                }
            }
        }
        emit(MAKE_OBJECT(fieldCount))
    }

    private fun compileLambdaExpr(expr: LambdaExpr) {
        // Compile lambda body separately to its own bytecode and emit it as a
        // constant LambdaTemplate. The template will be turned into a closure
        // with captured environment at runtime by the VM when the PUSH
        // instruction is executed.

        // Convert the lambda body to IR nodes
        val bodyIR: List<IRNode> = when (val b = expr.body) {
            is ExprBody -> listOf(IRReturn(b.expr))
            is BlockBody -> ToIR(funcs, hostFns).compile(b.block.statements)
        }
                enforceFuncBody(bodyIR)

        // Recursively compile the IR for the lambda body to bytecode
        val lambdaCompiler = Compiler(funcs, hostFns, useLocals = false)
        val bodyBytecode = lambdaCompiler.compile(bodyIR)

        // Emit as a template which will capture the environment at runtime
        emit(PUSH(LambdaTemplate(expr.params, bodyBytecode)))
    }

    private fun compileIfElseExpr(expr: IfElseExpr) {
        compileExpr(expr.condition)

        val elseLabel = generateLabel("if_else")
        val endLabel = generateLabel("if_end")

        emit(JUMP_IF_FALSE(0))
        addUnresolvedJump(instructions.size - 1, elseLabel, "JUMP_IF_FALSE")

        compileExpr(expr.thenBranch)
        emit(JUMP(0))
        addUnresolvedJump(instructions.size - 1, endLabel, "JUMP")

        setLabel(elseLabel)
        compileExpr(expr.elseBranch)

        setLabel(endLabel)
    }

    // === Helper Functions ===
    private var tempCounter: Int = 0


    companion object {
        internal fun enforceFuncBody(ir: List<IRNode>) {
            for (node in ir) {
                when (node) {
                    is IRLet -> Unit
                    is IRReturn -> Unit
                    is IRIf -> {
                        enforceFuncBody(node.thenBody)
                        node.elseBody?.let { enforceFuncBody(it) }
                    }
                    is IRForEach -> enforceFuncBody(node.body)
                    is IRExprStmt -> enforceExpr(node.expr)
                    else -> throw IllegalStateException("Only LET/RETURN/IF/FOR in FUNC body")
                }
            }
        }

        private fun enforceExpr(expr: Expr) {
            when (expr) {
                is UnaryExpr -> {
                    if (expr.token.type != TokenType.SUSPEND) {
                        throw IllegalStateException("Only LET/RETURN/IF/FOR in FUNC body")
                    }
                }
                else -> throw IllegalStateException("Only LET/RETURN/IF/FOR in FUNC body")
            }
        }
    }

    private fun nextTemp(prefix: String): String {
        val n = tempCounter++
        return "__vm_${prefix}_$n"
    }

    private fun isDynamic(seg: AccessSeg): Boolean = seg is AccessSeg.Dynamic

    private fun setWithKeyAtTop(seg: AccessSeg) {
        when (seg) {
            is AccessSeg.Static -> emit(SET_STATIC(seg.key))
            is AccessSeg.Dynamic -> {
                // Stack before: container, value, key (top)
                // Need: container, key, value (value on top for SET_DYNAMIC pop order)
                emit(SWAP)
                emit(SET_DYNAMIC)
            }
        }
    }

    private fun accessWithKeyLoaded(seg: AccessSeg) {
        when (seg) {
            is AccessSeg.Static -> emit(ACCESS_STATIC(seg.key))
            is AccessSeg.Dynamic -> emit(ACCESS_DYNAMIC)
        }
    }

    private fun loadKey(seg: AccessSeg, dynKeyVar: String?) {
        when (seg) {
            is AccessSeg.Static -> Unit
            is AccessSeg.Dynamic -> emit(LOAD_VAR(dynKeyVar!!))
        }
    }

    private fun preparePath(expr: AccessExpr): Pair<String, List<Pair<AccessSeg, String?>>> {
        val base = expr.base
        require(base is IdentifierExpr) {
            "Only identifier base is supported in lvalue paths"
        }
        val baseName = base.name
        val segs = expr.segs
        val withDynNames = ArrayList<Pair<AccessSeg, String?>>(segs.size)
        for ((_, seg) in segs.withIndex()) {
            if (seg is AccessSeg.Dynamic) {
                val kVar = nextTemp("k")
                // Evaluate dynamic key and store in temp
                compileExpr(seg.keyExpr)
                emit(STORE_VAR(kVar))
                withDynNames.add(Pair(seg, kVar))
            } else {
                withDynNames.add(Pair(seg, null))
            }
        }
        return Pair(baseName, withDynNames)
    }

    private fun loadChainObjects(baseName: String, segs: List<Pair<AccessSeg, String?>>): List<String> {
        // Load intermediate objects for each prefix of the path (excluding leaf).
        val objVars = ArrayList<String>(segs.size)
        // obj0 = base
        run {
            emit(LOAD_VAR(baseName))
            val obj0 = nextTemp("obj")
            emit(STORE_VAR(obj0))
            objVars.add(obj0)
        }
        for (i in 0 until segs.size - 1) {
            val (seg, dyn) = segs[i]
            emit(LOAD_VAR(objVars[i]))
            if (seg is AccessSeg.Dynamic) emit(LOAD_VAR(dyn!!))
            accessWithKeyLoaded(seg)
            val objI = nextTemp("obj")
            emit(STORE_VAR(objI))
            objVars.add(objI)
        }
        return objVars
    }

    private fun rebuildToRoot(
        baseName: String,
        segs: List<Pair<AccessSeg, String?>>, // full seg list
        objVars: List<String>, // same length as segs, holds parent object at each depth
    ): Int {
        // Assumes stack top is the updated value for the last segment.
        // Rebuild upwards and store into base variable.
        var currentUpdatedVar: String? = null
        // last parent index in objVars corresponds to parent of leaf
        val lastParentIdx = objVars.lastIndex
        if (lastParentIdx >= 0) {
            // Write into last parent
            val (lastSeg, lastDyn) = segs.last()
            val parentVar = objVars[lastParentIdx]
            // stack: newLeafValue
            emit(LOAD_VAR(parentVar))
            emit(SWAP)
            loadKey(lastSeg, lastDyn)
            setWithKeyAtTop(lastSeg)
            currentUpdatedVar = nextTemp("obj")
            emit(STORE_VAR(currentUpdatedVar))

            for (i in (lastParentIdx - 1) downTo 0) {
                val (seg, dyn) = segs[i]
                val parent = objVars[i]
                val curNonNull = currentUpdatedVar!!
                emit(LOAD_VAR(curNonNull))
                emit(LOAD_VAR(parent))
                emit(SWAP)
                loadKey(seg, dyn)
                setWithKeyAtTop(seg)
                currentUpdatedVar = nextTemp("obj")
                emit(STORE_VAR(currentUpdatedVar))
            }

            // Final write back to base
            emit(LOAD_VAR(currentUpdatedVar))
            return emitStoreRoot(baseName)
        } else {
            // Path has single segment; we directly wrote it? Not here. This branch shouldn't be used.
            return emitStoreRoot(baseName)
        }
    }

    private fun compileAccessExprForSet(expr: AccessExpr) {
        // Precondition: value to set is on stack top
        val (baseName, segs) = preparePath(expr)
        require(segs.isNotEmpty()) { "SET expects at least one access segment" }

        // If only one segment, we can just load base and set directly
        if (segs.size == 1) {
            emit(LOAD_VAR(baseName))
            emit(SWAP)
            val (seg, dyn) = segs[0]
            loadKey(seg, dyn)
            setWithKeyAtTop(seg)
            val storeIndex = emitStoreRoot(baseName)
            recordPathWrite(
                storeIndex,
                PathWriteMeta(
                    op = PathWriteOp.SET,
                    root = baseName,
                    segments = toPathSegments(segs),
                )
            )
            return
        }

        val objVars = loadChainObjects(baseName, segs)
        // Now stack top is the value to set; rebuild upward and record metadata
        val storeIndex = rebuildToRoot(baseName, segs, objVars)
        recordPathWrite(
            storeIndex,
            PathWriteMeta(
                op = PathWriteOp.SET,
                root = baseName,
                segments = toPathSegments(segs),
            )
        )
    }

    private fun compileAccessExprForAppend(target: AccessExpr, initProvided: Boolean) {
        // Stack precondition (from caller): elem (top), init list below
        val valVar = nextTemp("val")
        val initVar = nextTemp("init")
        // Stack on entry: value (bottom), init (top). Store init first, then value.
        emit(STORE_VAR(initVar))
        emit(STORE_VAR(valVar))

        val (baseName, segs) = preparePath(target)
        require(segs.isNotEmpty()) { "APPEND expects at least one access segment" }

        // Walk to parent of leaf
        val objVars = loadChainObjects(baseName, segs)

        // Load current at leaf, coalesce with init
        val (leafSeg, leafDyn) = segs.last()
        val parentVar = objVars.last()
        emit(LOAD_VAR(parentVar))
        loadKey(leafSeg, leafDyn)
        accessWithKeyLoaded(leafSeg)
        emit(LOAD_VAR(initVar))
        // COALESCE expects (left=current, right=init) with right on top
        emit(COALESCE)

        // Load element and append
        emit(LOAD_VAR(valVar))
        // Stack: array, elem (elem on top)
        emit(APPEND)

        // Rebuild to root with updated array on stack
        val storeIndex = rebuildToRoot(baseName, segs, objVars)
        recordPathWrite(
            storeIndex,
            PathWriteMeta(
                op = PathWriteOp.APPEND,
                root = baseName,
                segments = toPathSegments(segs),
            )
        )
    }

    private fun compileAccessExprForModify(expr: AccessExpr): Triple<String, List<Pair<AccessSeg, String?>>, List<String>> {
        val (baseName, segs) = preparePath(expr)
        if (segs.isEmpty()) {
            // Target is a plain identifier: push it as the object to modify
            emit(LOAD_VAR(baseName))
            return Triple(baseName, segs, emptyList())
        }
        val objVars = loadChainObjects(baseName, segs)
        // Push target object to modify
        val (leafSeg, leafDyn) = segs.last()
        val parentVar = objVars.last()
        emit(LOAD_VAR(parentVar))
        loadKey(leafSeg, leafDyn)
        accessWithKeyLoaded(leafSeg)
        return Triple(baseName, segs, objVars)
    }

    private fun unwrapKey(key: ObjKey): Any = when (key) {
        is ObjKey.Name -> key.v
        is I32 -> key.v
        is I64 -> key.v
        is IBig -> key.v
    }

    private fun unwrapNum(value: NumValue): Any = when (value) {
        is I32 -> value.v
        is I64 -> value.v
        is IBig -> value.v
        is Dec -> value.v
    }

    private fun emitStoreRoot(baseName: String): Int {
        val localIndex = localIndexByName[baseName]
        val hasLocal = useLocals && localIndex != null
        if (hasLocal) emit(DUP)
        emit(STORE_VAR(baseName))
        val storeIndex = instructions.lastIndex
        if (hasLocal) emit(STORE_LOCAL(localIndex!!))
        return storeIndex
    }

    private fun recordPathWrite(storeIndex: Int, meta: PathWriteMeta) {
        pathWrites[storeIndex] = meta
    }

    private fun toPathSegments(segs: List<Pair<AccessSeg, String?>>): List<PathSegmentMeta> {
        if (segs.isEmpty()) return emptyList()
        val out = ArrayList<PathSegmentMeta>(segs.size)
        for ((seg, dynName) in segs) {
            when (seg) {
                is AccessSeg.Static -> out.add(PathSegmentMeta.Static(seg.key))
                is AccessSeg.Dynamic -> {
                    val temp = dynName ?: error("Dynamic segment missing temp variable")
                    out.add(PathSegmentMeta.Dynamic(temp))
                }
            }
        }
        return out
    }

    // === Code Generation Helpers ===

    private fun emit(instruction: Instruction) {
        instructions.add(instruction)
    }

    private var labelCounter = 0

    private fun generateLabel(prefix: String): String {
        return "${prefix}_${labelCounter++}"
    }

    private fun setLabel(labelName: String) {
        jumpTable[labelName] = instructions.size
    }

    private fun addUnresolvedJump(instructionIndex: Int, labelName: String, jumpType: String) {
        unresolvedJumps.add(UnresolvedJump(instructionIndex, labelName, jumpType))
    }

    private fun resolveJumps() {
        for (jump in unresolvedJumps) {
            val address = jumpTable[jump.labelName]
                ?: error("Undefined label: ${jump.labelName}")

            val resolvedInstruction = when (val instruction = instructions[jump.instructionIndex]) {
                is JUMP -> JUMP(address)
                is JUMP_IF_TRUE -> JUMP_IF_TRUE(address)
                is JUMP_IF_FALSE -> JUMP_IF_FALSE(address)
                is JUMP_IF_NULL -> JUMP_IF_NULL(address)
                is TRY_START -> TRY_START(address)
                is INIT_FOREACH -> {
                    if (jump.jumpType == "INIT_FOREACH") {
                        INIT_FOREACH(instruction.varName, address)
                    } else {
                        instruction
                    }
                }
                is NEXT_FOREACH -> {
                    when (jump.jumpType) {
                        "NEXT_FOREACH_START" -> {
                            NEXT_FOREACH(address, instruction.jumpToEnd)
                        }
                        "NEXT_FOREACH_END" -> {
                            NEXT_FOREACH(instruction.jumpToStart, address)
                        }
                        else -> instruction
                    }
                }
                else -> instruction
            }

            instructions[jump.instructionIndex] = resolvedInstruction
        }
    }

    /**
     * Simple peephole optimization to remove no-ops and redundant patterns.
     */
    private data class OptimizedProgram(
        val instructions: List<Instruction>,
        val indexMap: IntArray,
    )

    private fun optimize(ins: List<Instruction>): OptimizedProgram {
        val out = ArrayList<Instruction>(ins.size)
        val indexMap = IntArray(ins.size) { -1 }
        var i = 0
        while (i < ins.size) {
            val a = ins[i]
            if (i + 1 < ins.size) {
                val b = ins[i + 1]
                // Elide PUSH(x); POP
                if (a is PUSH && b === POP) {
                    i += 2
                    continue
                }
                // Elide DUP; POP
                if (a === DUP && b === POP) {
                    i += 2
                    continue
                }
                // Elide SWAP; SWAP
                if (a === SWAP && b === SWAP) {
                    i += 2
                    continue
                }
            }
            indexMap[i] = out.size
            out.add(a)
            i += 1
        }
        return OptimizedProgram(out, indexMap)
    }

    private fun <T> remapEventMap(source: Map<Int, MutableList<T>>, indexMap: IntArray): Map<Int, List<T>> {
        if (source.isEmpty()) return emptyMap()
        val out = mutableMapOf<Int, MutableList<T>>()
        for ((oldIndex, events) in source) {
            if (oldIndex < 0 || oldIndex >= indexMap.size) continue
            val newIndex = indexMap[oldIndex]
            if (newIndex < 0) continue
            val bucket = out.getOrPut(newIndex) { mutableListOf() }
            bucket.addAll(events)
        }
        if (out.isEmpty()) return emptyMap()
        return out.mapValues { (_, value) -> value.toList() }
    }

    private fun remapPathWrites(source: Map<Int, PathWriteMeta>, indexMap: IntArray): Map<Int, PathWriteMeta> {
        if (source.isEmpty()) return emptyMap()
        val out = mutableMapOf<Int, PathWriteMeta>()
        for ((oldIndex, meta) in source) {
            if (oldIndex < 0 || oldIndex >= indexMap.size) continue
            val newIndex = indexMap[oldIndex]
            if (newIndex < 0) continue
            out[newIndex] = meta
        }
        return out
    }
}
