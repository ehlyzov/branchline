package v2.sema

import v2.AbortStmt
import v2.AccessSeg
import v2.AccessExpr
import v2.AppendToStmt
import v2.AppendToVarStmt
import v2.ArrayExpr
import v2.ArrayCompExpr
import v2.BinaryExpr
import v2.BlockBody
import v2.CallExpr
import v2.CaseExpr
import v2.CodeBlock
import v2.ComputedProperty
import v2.Connection
import v2.Expr
import v2.ExprBody
import v2.ExprStmt
import v2.ForEachStmt
import v2.FuncDecl
import v2.GraphOutput
import v2.I32
import v2.I64
import v2.IBig
import v2.IdentifierExpr
import v2.IfElseExpr
import v2.IfStmt
import v2.InvokeExpr
import v2.Mode
import v2.LetStmt
import v2.LambdaExpr
import v2.LiteralProperty
import v2.ModifyStmt
import v2.NodeDecl
import v2.ObjKey
import v2.ObjectExpr
import v2.OutputDecl
import v2.OutputStmt
import v2.Program
import v2.ReturnStmt
import v2.SourceDecl
import v2.SetStmt
import v2.SetVarStmt
import v2.SharedDecl
import v2.Stmt
import v2.Token
import v2.TokenType
import v2.TopLevelDecl
import v2.TransformDecl
import v2.TransformSignature
import v2.TryCatchExpr
import v2.TryCatchStmt
import v2.TypeDecl
import v2.TypeKind
import v2.TypeRef
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.ArrayTypeRef
import v2.RecordFieldType
import v2.RecordTypeRef
import v2.UnionTypeRef
import v2.EnumTypeRef
import v2.NamedTypeRef
import v2.UnaryExpr
import v2.DEFAULT_INPUT_ALIAS
import v2.COMPAT_INPUT_ALIASES

class SemanticException(msg: String, val token: Token) :
    RuntimeException("[${token.line}:${token.column}] $msg near '${token.lexeme}'")

data class SemanticWarning(val message: String, val token: Token)

/** Один прогон поверх готового AST. Создаёт таблицу символов и валидирует. */
class SemanticAnalyzer(
    private val hostFns: Set<String> = emptySet(),
    private val strictContracts: Boolean = false,
) {

    public val warnings: MutableList<SemanticWarning> = mutableListOf()

    /** для top-level: FUNC / SHARED / TYPE / SOURCE / TRANSFORM / OUTPUT */
    private val globals = mutableMapOf<String, TopLevelDecl>()

    /** вложенные блоки → собственная “scope” LET-переменных */
    private val scopes = ArrayDeque<MutableSet<String>>() // LIFO

    private var currentTransformMode: Mode? = null
    private var currentTransformSignature: TransformSignature? = null
    private var currentInputContract: TypeRef? = null
    private var currentOutputContract: TypeRef? = null

    private val resolvedTypeCache = mutableMapOf<String, TypeRef>()
    private val resolvingTypeNames = mutableSetOf<String>()

    /** Запуск. Бросает SemanticException при первой найденной ошибке. */
    fun analyze(prog: Program) {
        warnings.clear()
        collectGlobals(prog)
        prog.decls.forEach { checkTopLevel(it) }
    }

    // -------- Pass 0: глобальная таблица ---------------------------------

    private fun collectGlobals(prog: Program) {
        for (decl in prog.decls) {
            val name = when (decl) {
                is FuncDecl -> decl.name
                is SharedDecl -> decl.name
                is TypeDecl -> decl.name
                is SourceDecl -> decl.name
                is TransformDecl -> decl.name ?: continue
                is OutputDecl -> continue
            }
            //if (globals.containsKey(name)) continue
            //globals.putIfAbsent(name, decl)?.let { prev ->
            val prev = globals[name]
            if (prev == null) {
                globals[name] = decl
                continue
            }
            val tok = when (prev) {
                is FuncDecl -> prev.token
                is SharedDecl -> prev.token
                is TypeDecl -> prev.token
                is SourceDecl -> prev.token
                is TransformDecl -> prev.token
                else -> error("should not be here") // fallback
            }
            throw SemanticException("Duplicate symbol '$name'", tok)
        }
    }

    // -------- Pass 1: локальные проверки ---------------------------------

    private fun checkTopLevel(decl: TopLevelDecl) = when (decl) {
        is TransformDecl -> {
            val prev = currentTransformMode // save
            val prevSignature = currentTransformSignature
            val prevInput = currentInputContract
            val prevOutput = currentOutputContract
            currentTransformMode = decl.mode
            currentTransformSignature = decl.signature
            currentInputContract = decl.signature?.input?.let { resolveTypeRef(it) }
            currentOutputContract = decl.signature?.output?.let { resolveTypeRef(it) }
            decl.body as CodeBlock
            checkBlock(decl.body)
            currentTransformMode = prev // restore
            currentTransformSignature = prevSignature
            currentInputContract = prevInput
            currentOutputContract = prevOutput
        }

        is FuncDecl -> when (val b = decl.body) {
            is ExprBody -> withScope(decl.params) { checkExpr(b.expr) }
            is BlockBody -> checkBlock(b.block, decl.params)
        }

        else -> Unit // SOURCE / SHARED / TYPE / OUTPUT – пока ничего
    }

    private fun checkBlock(block: CodeBlock, initialNames: Collection<String> = emptyList()) {
        scopes.addLast(initialNames.toMutableSet())
        for (stmt in block.statements) checkStmt(stmt)
        scopes.removeLast()
    }

    private inline fun <T> withScope(initialNames: Collection<String>, action: () -> T): T {
        scopes.addLast(initialNames.toMutableSet())
        return try {
            action()
        } finally {
            scopes.removeLast()
        }
    }

    private fun checkStmt(stmt: Stmt) = when (stmt) {
        is LetStmt -> {
            val scope = scopes.last()
            if (scope.contains(stmt.name)) {
                throw SemanticException("Duplicate LET '${stmt.name}' in same block", stmt.token)
            }
            if (scopes.dropLast(1).any { stmt.name in it }) {
                throw SemanticException(
                    "Shadowing variable '${stmt.name}' is not allowed",
                    stmt.token
                )
            }
            scope += stmt.name
            checkExpr(stmt.expr)
        }

        is SetStmt -> {
            val baseIdent = stmt.target.base as? IdentifierExpr
                ?: throw SemanticException("SET target must start with identifier", stmt.token)

            val inScope = scopes.any { baseIdent.name in it }
            if (!inScope && !isSharedResource(baseIdent.name)) {
                throw SemanticException("Unknown variable '${baseIdent.name}' in same block", stmt.token)
            }

            // Можно дополнительно проверить, что target не пустой (есть хотя бы один сегмент)
            if (stmt.target.segs.isEmpty()) {
                throw SemanticException("SET target must have at least one segment", stmt.token)
            }

            // Проверяем RHS
            checkExpr(stmt.value)
            true
        }

        is ModifyStmt -> {
            // цель MODIFY — статический путь: PathExpr(root, parts)
            val targetPath = stmt.target.base as? IdentifierExpr ?: throw SemanticException(
                "MODIFY required identifier", stmt.token
            )
            val rootName = targetPath.name

            val notInScope = scopes.none { rootName in it }
            if (notInScope) {
                throw SemanticException("Unknown variable name '$rootName' in same block", stmt.token)
            }

            // страховка: в целевом пути не должно быть динамических индексов — только ObjKey (Name | Index.*)
            // (Parser уже это обеспечивает; оставим явную проверку на будущее)
            stmt.target.segs.forEach { seg ->
                when (seg) {
                    is AccessSeg.Static -> Unit
                    else -> throw SemanticException("MODIFY target must be a static path", stmt.token)
                }
            }

            // лёгкая проверка: в одном MODIFY не должно быть дублирующихся literal-ключей (по точному типу)
            val seen = HashSet<Any>()
            stmt.updates.forEach { prop ->
                when (prop) {
                    is LiteralProperty -> {
                        val raw = when (val k = prop.key) {
                            is ObjKey.Name -> k.v
                            is I32 -> k.v
                            is I64 -> k.v
                            is IBig -> k.v
                        }
                        if (!seen.add(raw)) {
                            throw SemanticException("Duplicate literal key in MODIFY: $raw", stmt.token)
                        }
                    }

                    is ComputedProperty -> {
                        // пропускаем: вычисляемый ключ известен только на рантайме
                    }
                }
            }

            true
        }

        is AppendToStmt -> {
            val baseIdent = stmt.target.base as? IdentifierExpr
                ?: throw SemanticException("APPEND TO target must start with identifier", stmt.token)
            val inScope = scopes.any { baseIdent.name in it }
            if (!inScope && !isSharedResource(baseIdent.name)) {
                throw SemanticException("Unknown variable '${baseIdent.name}'", stmt.token)
            }
            checkExpr(stmt.value)
            stmt.init?.let { checkExpr(it) }
            true
        }

        is OutputStmt -> {
            checkExpr(stmt.template)
            validateOutputTemplate(stmt.template)
        }
        is IfStmt -> {
            checkExpr(stmt.condition)
            checkBlock(stmt.thenBlock)
            stmt.elseBlock?.let(::checkBlock)
        }

        is ForEachStmt -> {
            checkExpr(stmt.iterable)
            scopes.addLast(mutableSetOf(stmt.varName))
            stmt.where?.let { checkExpr(it) }
            checkBlock(stmt.body)
            scopes.removeLast()
        }

        is TryCatchStmt -> {
            checkExpr(stmt.tryExpr)
            checkTryCatchFallback(stmt.exceptionName, stmt.fallbackExpr, stmt.fallbackAbort)
            Unit
            // retry/backoff sanity already parsed; nothing else yet
        }

        is ReturnStmt -> stmt.value?.let(::checkExpr)
        is AbortStmt -> stmt.value?.let(::checkExpr)
        is CodeBlock -> true
        is Connection -> true
        is GraphOutput -> true
        is NodeDecl -> true
        is ExprStmt -> true
        is AppendToVarStmt -> true
        is SetVarStmt -> true
    }

    private fun checkExpr(expr: Expr) {
        when (expr) {
            is IdentifierExpr -> {
                if (!isIdentifierVisible(expr.name)) {
                    throw SemanticException("Undefined identifier '${expr.name}'", expr.token)
                }
            }

            is CallExpr -> {
                checkExpr(expr.callee)
                expr.args.forEach { checkExpr(it) }
            }

            is BinaryExpr -> {
                checkExpr(expr.left)
                checkExpr(expr.right)
            }

            is IfElseExpr -> {
                checkExpr(expr.condition)
                checkExpr(expr.thenBranch)
                checkExpr(expr.elseBranch)
            }

            is CaseExpr -> {
                expr.whens.forEach { whenExpr ->
                    checkExpr(whenExpr.condition)
                    checkExpr(whenExpr.result)
                }
                checkExpr(expr.elseBranch)
            }

            is TryCatchExpr -> {
                checkExpr(expr.tryExpr)
                checkTryCatchFallback(expr.exceptionName, expr.fallbackExpr, null)
            }

            is UnaryExpr -> {
                if (expr.token.type == TokenType.SUSPEND &&
                    currentTransformMode == Mode.STREAM
                ) {
                    throw SemanticException(
                        "Cannot use 'suspend' inside stream transform",
                        expr.token
                    )
                }
                checkExpr(expr.expr)
            }

            is ObjectExpr -> expr.fields.forEach { property ->
                when (property) {
                    is LiteralProperty -> checkExpr(property.value)
                    is ComputedProperty -> {
                        checkExpr(property.keyExpr)
                        checkExpr(property.value)
                    }
                }
            }

            is ArrayCompExpr -> {
                checkExpr(expr.iterable)
                scopes.add(mutableSetOf(expr.varName))
                expr.where?.let { checkExpr(it) }
                checkExpr(expr.mapExpr)
                scopes.removeLast()
            }

            is AccessExpr -> {
                checkExpr(expr.base)
                expr.segs.forEach { seg ->
                    if (seg is AccessSeg.Dynamic) {
                        checkExpr(seg.keyExpr)
                    }
                }
                validateInputAccess(expr)
            }

            is InvokeExpr -> {
                checkExpr(expr.target)
                expr.args.forEach { checkExpr(it) }
            }

            is LambdaExpr -> {
                when (val body = expr.body) {
                    is ExprBody -> withScope(expr.params) { checkExpr(body.expr) }
                    is BlockBody -> checkBlock(body.block, expr.params)
                }
            }

            is ArrayExpr -> expr.elements.forEach { checkExpr(it) }
            // StringExpr / NumberExpr – ничего проверять
            else -> Unit
        }
    }

    private fun checkTryCatchFallback(
        exceptionName: String,
        fallbackExpr: Expr?,
        fallbackAbort: AbortStmt?,
    ) {
        withScope(listOf(exceptionName)) {
            fallbackExpr?.let { checkExpr(it) }
            fallbackAbort?.value?.let { checkExpr(it) }
        }
    }

    private fun isIdentifierVisible(name: String): Boolean =
        name == "$" || // глобальный JSON-корень
                name == DEFAULT_INPUT_ALIAS ||
                name in COMPAT_INPUT_ALIASES ||
                scopes.any { name in it } || // LET / var цикла
                name in globals || // SOURCE / SHARED / TYPE / FUNC
                name in hostFns

    private fun isSharedResource(name: String): Boolean =
        globals[name] is SharedDecl

    private fun resolveTypeRef(typeRef: TypeRef): TypeRef =
        resolveTypeRef(typeRef, resolvingTypeNames)

    private fun resolveTypeRef(typeRef: TypeRef, resolving: MutableSet<String>): TypeRef = when (typeRef) {
        is PrimitiveTypeRef -> typeRef
        is EnumTypeRef -> typeRef
        is ArrayTypeRef -> ArrayTypeRef(
            elementType = resolveTypeRef(typeRef.elementType, resolving),
            token = typeRef.token,
        )
        is RecordTypeRef -> {
            val resolvedFields = typeRef.fields.map { field ->
                RecordFieldType(
                    name = field.name,
                    type = resolveTypeRef(field.type, resolving),
                    optional = field.optional,
                    token = field.token,
                )
            }
            RecordTypeRef(
                fields = resolvedFields,
                token = typeRef.token,
            )
        }
        is UnionTypeRef -> {
            val resolvedMembers = typeRef.members.map { member ->
                resolveTypeRef(member, resolving)
            }
            UnionTypeRef(
                members = resolvedMembers,
                token = typeRef.token,
            )
        }
        is NamedTypeRef -> resolveNamedType(typeRef, resolving)
    }

    private fun resolveNamedType(typeRef: NamedTypeRef, resolving: MutableSet<String>): TypeRef {
        resolvedTypeCache[typeRef.name]?.let { return it }
        val decl = globals[typeRef.name] as? TypeDecl
            ?: throw SemanticException("Unknown type '${typeRef.name}'", typeRef.token)
        if (!resolving.add(typeRef.name)) {
            throw SemanticException("Cyclic type reference '${typeRef.name}'", typeRef.token)
        }
        val resolved = resolveTypeDecl(decl, resolving)
        resolving.remove(typeRef.name)
        resolvedTypeCache[typeRef.name] = resolved
        return resolved
    }

    private fun resolveTypeDecl(typeDecl: TypeDecl, resolving: MutableSet<String>): TypeRef = when (typeDecl.kind) {
        TypeKind.ENUM -> EnumTypeRef(typeDecl.defs, typeDecl.token)
        TypeKind.UNION -> {
            val members = typeDecl.defs.map { name ->
                resolveTypeRef(typeRefFromName(name, typeDecl.token), resolving)
            }
            UnionTypeRef(
                members = members,
                token = typeDecl.token,
            )
        }
    }

    private fun typeRefFromName(name: String, token: Token): TypeRef {
        val normalized = name.lowercase()
        return when (normalized) {
            "string", "text" -> PrimitiveTypeRef(PrimitiveType.TEXT, token)
            "number" -> PrimitiveTypeRef(PrimitiveType.NUMBER, token)
            "boolean" -> PrimitiveTypeRef(PrimitiveType.BOOLEAN, token)
            "null" -> PrimitiveTypeRef(PrimitiveType.NULL, token)
            "any" -> PrimitiveTypeRef(PrimitiveType.ANY, token)
            else -> NamedTypeRef(name, token)
        }
    }

    private fun validateInputAccess(expr: AccessExpr) {
        val signature = currentTransformSignature ?: return
        val inputType = currentInputContract ?: return
        val baseIdent = expr.base as? IdentifierExpr ?: return
        if (!isInputAlias(baseIdent.name)) return

        var currentType = inputType
        for (seg in expr.segs) {
            if (seg is AccessSeg.Dynamic) return
            val key = (seg as AccessSeg.Static).key
            currentType = when (val resolved = resolveTypeRef(currentType)) {
                is PrimitiveTypeRef -> {
                    if (resolved.kind == PrimitiveType.ANY || resolved.kind == PrimitiveType.ANY_NULLABLE) {
                        return
                    }
                    reportContractMismatch(
                        "Input contract does not allow access via ${renderKey(key)}",
                        signature.tokenSpan.start,
                    )
                    return
                }

                is EnumTypeRef -> {
                    reportContractMismatch(
                        "Input contract does not allow access via ${renderKey(key)}",
                        signature.tokenSpan.start,
                    )
                    return
                }

                is RecordTypeRef -> {
                    if (key !is ObjKey.Name) {
                        reportContractMismatch(
                            "Input contract expects object fields, got index access ${renderKey(key)}",
                            signature.tokenSpan.start,
                        )
                        return
                    }
                    val field = resolved.fields.firstOrNull { it.name == key.v }
                    if (field == null) {
                        reportContractMismatch(
                            "Input contract does not declare field '${key.v}'",
                            signature.tokenSpan.start,
                        )
                        return
                    }
                    field.type
                }

                is ArrayTypeRef -> {
                    if (key is ObjKey.Name) {
                        reportContractMismatch(
                            "Input contract expects list access, got field '${key.v}'",
                            signature.tokenSpan.start,
                        )
                        return
                    }
                    resolved.elementType
                }

                is UnionTypeRef -> return
                is NamedTypeRef -> return
            }
        }
    }

    private fun validateOutputTemplate(expr: Expr) {
        val signature = currentTransformSignature ?: return
        val outputType = currentOutputContract ?: return
        val resolved = resolveTypeRef(outputType)
        when (resolved) {
            is RecordTypeRef -> validateOutputObject(expr, resolved, signature.tokenSpan.end)
            is ArrayTypeRef -> validateOutputArray(expr, resolved, signature.tokenSpan.end)
            else -> Unit
        }
    }

    private fun validateOutputObject(expr: Expr, typeRef: RecordTypeRef, token: Token) {
        val obj = when (expr) {
            is ObjectExpr -> expr
            is TryCatchExpr -> {
                validateOutputObject(expr.tryExpr, typeRef, token)
                validateOutputObject(expr.fallbackExpr, typeRef, token)
                return
            }
            else -> {
                reportContractMismatch("Output contract expects an object", token)
                return
            }
        }
        val declaredFields = typeRef.fields.associateBy { it.name }
        val literalFields = obj.fields.mapNotNull { property ->
            when (property) {
                is LiteralProperty -> (property.key as? ObjKey.Name)?.v
                is ComputedProperty -> null
            }
        }.toSet()

        typeRef.fields.filter { !it.optional }.forEach { field ->
            if (field.name !in literalFields) {
                reportContractMismatch(
                    "Output contract is missing required field '${field.name}'",
                    token,
                )
            }
        }

        literalFields.filter { it !in declaredFields }.forEach { extra ->
            reportContractMismatch(
                "Output contract does not declare field '$extra'",
                token,
            )
        }

        obj.fields.forEach { property ->
            val literal = property as? LiteralProperty ?: return@forEach
            val name = (literal.key as? ObjKey.Name)?.v ?: return@forEach
            val fieldType = declaredFields[name]?.type ?: return@forEach
            val resolvedFieldType = resolveTypeRef(fieldType)
            when (resolvedFieldType) {
                is RecordTypeRef -> validateOutputObject(literal.value, resolvedFieldType, token)
                is ArrayTypeRef -> validateOutputArray(literal.value, resolvedFieldType, token)
                else -> Unit
            }
        }
    }

    private fun validateOutputArray(expr: Expr, typeRef: ArrayTypeRef, token: Token) {
        when (expr) {
            is ArrayExpr -> {
                val elementType = resolveTypeRef(typeRef.elementType)
                if (elementType is RecordTypeRef) {
                    expr.elements.forEach { element ->
                        validateOutputObject(element, elementType, token)
                    }
                }
            }
            is TryCatchExpr -> {
                validateOutputArray(expr.tryExpr, typeRef, token)
                validateOutputArray(expr.fallbackExpr, typeRef, token)
            }
            is ArrayCompExpr -> {
                val elementType = resolveTypeRef(typeRef.elementType)
                if (elementType is RecordTypeRef && expr.mapExpr !is ObjectExpr) {
                    reportContractMismatch(
                        "Output contract expects object elements in list output",
                        token,
                    )
                }
            }
            else -> reportContractMismatch("Output contract expects a list", token)
        }
    }

    private fun reportContractMismatch(message: String, token: Token) {
        if (strictContracts) {
            throw SemanticException(message, token)
        }
        warnings += SemanticWarning(message, token)
    }

    private fun renderKey(key: ObjKey): String = when (key) {
        is ObjKey.Name -> "'${key.v}'"
        is ObjKey.Index -> "index"
    }

    private fun isInputAlias(name: String): Boolean =
        name == DEFAULT_INPUT_ALIAS || name in COMPAT_INPUT_ALIASES
}
