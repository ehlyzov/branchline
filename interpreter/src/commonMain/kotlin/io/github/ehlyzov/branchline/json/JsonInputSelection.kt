package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.*

/** A conservative prototype plan. Keys are copied both on input and on observation. */
public class JsonInputSelectionPlan internal constructor(
    public val reason: String,
    keys: Set<String>?,
) {
    private val keys = keys?.toSet()
    public val supported: Boolean get() = keys != null
    public val selectedRootKeys: Set<String> get() = keys?.toSet() ?: emptySet()
}

/** Complete top-level values only; unsupported syntax always requests eager input. */
public fun planJsonInputSelection(program: Program, transform: TransformDecl): JsonInputSelectionPlan {
    fun fallback(reason: String) = JsonInputSelectionPlan(reason, null)
    if (program.decls.any { it is FuncDecl }) return fallback("USER_FUNCTIONS")
    if (program.decls.any { it is SharedDecl } || transform.options.shared.isNotEmpty()) {
        return fallback("SHARED_RESOURCES")
    }
    if (transform.options.inputAdapter != null || transform.options.outputAdapter != null) {
        return fallback("ADAPTERS")
    }
    val block = transform.body as? CodeBlock ?: return fallback("UNSUPPORTED_BODY")
    if (block.statements.lastOrNull() !is OutputStmt) return fallback("UNSUPPORTED_STATEMENTS")
    val aliases = COMPAT_INPUT_ALIASES + DEFAULT_INPUT_ALIAS
    val locals = HashSet<String>()
    // buildEnv installs input before putAll; an actual input field shadows that binding.
    val keys = linkedSetOf(DEFAULT_INPUT_ALIAS)
    fun supported(expr: Expr): Boolean = when (expr) {
        is StringExpr, is NumberLiteral, is NullLiteral, is BoolExpr -> true
        is IdentifierExpr -> expr.name in locals
        is AccessExpr -> {
            val base = expr.base as? IdentifierExpr
            if (base == null || expr.segs.isEmpty() || expr.segs.any { it !is AccessSeg.Static }) false
            else if (base.name in locals) true
            else if (base.name in aliases) {
                val first = (expr.segs.first() as AccessSeg.Static).key as? ObjKey.Name
                if (first == null) false else { keys.add(first.v); true }
            } else false
        }
        is ObjectExpr -> expr.fields.all { it is LiteralProperty && supported(it.value) }
        is ArrayExpr -> expr.elements.all { supported(it) }
        // SUSPEND is unary too, but observes the complete environment in its serialized snapshot.
        // Admit only scalar operators; orchestration operators require eager input.
        is UnaryExpr -> (expr.token.type == TokenType.MINUS || expr.token.type == TokenType.BANG) &&
            supported(expr.expr)
        is BinaryExpr -> supported(expr.left) && supported(expr.right)
        is CallExpr -> expr.callee.name == "LENGTH" && "LENGTH" !in locals &&
            expr.args.size == 1 && supported(expr.args.single())
        else -> false
    }
    for ((index, statement) in block.statements.withIndex()) {
        when (statement) {
            is LetStmt -> {
                if (statement.name in aliases || statement.name == "LENGTH" || statement.name in locals ||
                    !supported(statement.expr)) return fallback("UNSUPPORTED_SYNTAX")
                locals.add(statement.name)
            }
            is OutputStmt -> if (index != block.statements.lastIndex || !supported(statement.template)) {
                return fallback("UNSUPPORTED_SYNTAX")
            }
            else -> return fallback("UNSUPPORTED_SYNTAX")
        }
    }
    return JsonInputSelectionPlan("STATIC_TOP_LEVEL", keys)
}
