package v2.sema

import v2.AbortStmt
import v2.AccessSeg
import v2.AppendToStmt
import v2.AppendToVarStmt
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
import v2.Mode
import v2.LetStmt
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
import v2.TryCatchStmt
import v2.TypeDecl
import v2.UnaryExpr
import v2.DEFAULT_INPUT_ALIAS
import v2.COMPAT_INPUT_ALIASES

class SemanticException(msg: String, val token: Token) :
    RuntimeException("[${token.line}:${token.column}] $msg near '${token.lexeme}'")

/** Один прогон поверх готового AST. Создаёт таблицу символов и валидирует. */
class SemanticAnalyzer(
    private val hostFns: Set<String> = emptySet(),
) {

    /** для top-level: FUNC / SHARED / TYPE / SOURCE / TRANSFORM / OUTPUT */
    private val globals = mutableMapOf<String, TopLevelDecl>()

    /** вложенные блоки → собственная “scope” LET-переменных */
    private val scopes = ArrayDeque<MutableSet<String>>() // LIFO

    private var currentTransformMode: Mode? = null

    /** Запуск. Бросает SemanticException при первой найденной ошибке. */
    fun analyze(prog: Program) {
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
            currentTransformMode = decl.mode
            decl.body as CodeBlock
            checkBlock(decl.body)
            currentTransformMode = prev // restore
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
            if (!inScope) throw SemanticException("Unknown variable '${baseIdent.name}' in same block", stmt.token)

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
            if (!inScope) throw SemanticException("Unknown variable '${baseIdent.name}'", stmt.token)
            checkExpr(stmt.value)
            stmt.init?.let { checkExpr(it) }
            true
        }

        is OutputStmt -> checkExpr(stmt.template)
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
            if (stmt.fallbackExpr != null) {
                checkExpr(stmt.fallbackExpr)
            }
            if (stmt.fallbackAbort != null && stmt.fallbackAbort.value != null) {
                checkExpr(stmt.fallbackAbort.value)
            }
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
            // StringExpr / NumberExpr – ничего проверять
            else -> Unit
        }
    }

    private fun isIdentifierVisible(name: String): Boolean =
        name == "$" || // глобальный JSON-корень
                name == DEFAULT_INPUT_ALIAS ||
                name in COMPAT_INPUT_ALIASES ||
                scopes.any { name in it } || // LET / var цикла
                name in globals || // SOURCE / SHARED / TYPE / FUNC
                name in hostFns
}
