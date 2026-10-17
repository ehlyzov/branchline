package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*

// Detach every collection-bearing AST node. Scalar nodes/tokens are immutable.
internal fun snapshotFunctions(funcs: Map<String, FuncDecl>): Map<String, FuncDecl> =
    funcs.mapValues { (_, fn) -> fn.copy(params = fn.params.toList(), body = snapshotBody(fn.body)) }

private fun snapshotBody(body: FuncBody): FuncBody = when (body) {
    is ExprBody -> body.copy(expr = snapshotExpr(body.expr))
    is BlockBody -> body.copy(block = snapshotBlock(body.block))
}

private fun snapshotBlock(block: CodeBlock): CodeBlock = block.copy(statements = block.statements.map(::snapshotStmt))

private fun snapshotProperty(property: Property): Property = when (property) {
    is LiteralProperty -> property.copy(value = snapshotExpr(property.value))
    is ComputedProperty -> property.copy(keyExpr = snapshotExpr(property.keyExpr), value = snapshotExpr(property.value))
}

internal fun snapshotExpr(expr: Expr): Expr = when (expr) {
    is IdentifierExpr, is StringExpr, is NumberLiteral, is NullLiteral, is BoolExpr, is SharedStateAwaitExpr -> expr
    is AccessExpr -> expr.copy(base = snapshotExpr(expr.base), segs = expr.segs.map {
        when (it) {
            is AccessSeg.Static -> it
            is AccessSeg.Dynamic -> it.copy(keyExpr = snapshotExpr(it.keyExpr))
        }
    })
    is ObjectExpr -> expr.copy(fields = expr.fields.map(::snapshotProperty))
    is CallExpr -> expr.copy(args = expr.args.map(::snapshotExpr))
    is InvokeExpr -> expr.copy(target = snapshotExpr(expr.target), args = expr.args.map(::snapshotExpr))
    is BinaryExpr -> expr.copy(left = snapshotExpr(expr.left), right = snapshotExpr(expr.right))
    is UnaryExpr -> expr.copy(expr = snapshotExpr(expr.expr))
    is LambdaExpr -> expr.copy(params = expr.params.toList(), body = snapshotBody(expr.body))
    is ArrayExpr -> expr.copy(elements = expr.elements.map(::snapshotExpr))
    is ArrayCompExpr -> expr.copy(iterable = snapshotExpr(expr.iterable), mapExpr = snapshotExpr(expr.mapExpr),
        where = expr.where?.let(::snapshotExpr))
    is IfElseExpr -> expr.copy(condition = snapshotExpr(expr.condition), thenBranch = snapshotExpr(expr.thenBranch),
        elseBranch = snapshotExpr(expr.elseBranch))
    is TryCatchExpr -> expr.copy(tryExpr = snapshotExpr(expr.tryExpr), fallbackExpr = snapshotExpr(expr.fallbackExpr))
    is CaseExpr -> expr.copy(whens = expr.whens.map { it.copy(condition = snapshotExpr(it.condition), result = snapshotExpr(it.result)) },
        elseBranch = snapshotExpr(expr.elseBranch))
}

private fun snapshotStmt(stmt: Stmt): Stmt = when (stmt) {
    is LetStmt -> stmt.copy(expr = snapshotExpr(stmt.expr))
    is SetStmt -> stmt.copy(target = snapshotExpr(stmt.target) as AccessExpr, value = snapshotExpr(stmt.value))
    is SetVarStmt -> stmt.copy(value = snapshotExpr(stmt.value))
    is PlusAssignStmt -> stmt.copy(target = snapshotExpr(stmt.target) as AccessExpr, value = snapshotExpr(stmt.value))
    is PlusAssignVarStmt -> stmt.copy(value = snapshotExpr(stmt.value))
    is SharedWriteStmt -> stmt.copy(key = stmt.key?.let(::snapshotExpr), value = snapshotExpr(stmt.value))
    is ModifyStmt -> stmt.copy(target = snapshotExpr(stmt.target) as AccessExpr, updates = stmt.updates.map(::snapshotProperty))
    is OutputStmt -> stmt.copy(template = snapshotExpr(stmt.template))
    is IfStmt -> stmt.copy(condition = snapshotExpr(stmt.condition), thenBlock = snapshotBlock(stmt.thenBlock),
        elseBlock = stmt.elseBlock?.let(::snapshotBlock))
    is ForEachStmt -> stmt.copy(iterable = snapshotExpr(stmt.iterable), body = snapshotBlock(stmt.body),
        where = stmt.where?.let(::snapshotExpr))
    is TryCatchStmt -> stmt.copy(tryExpr = snapshotExpr(stmt.tryExpr), fallbackExpr = stmt.fallbackExpr?.let(::snapshotExpr),
        fallbackAbort = stmt.fallbackAbort?.let { snapshotStmt(it) as AbortStmt })
    is CodeBlock -> snapshotBlock(stmt)
    is ReturnStmt -> stmt.copy(value = stmt.value?.let(::snapshotExpr))
    is AbortStmt -> stmt.copy(value = stmt.value?.let(::snapshotExpr))
    is ExprStmt -> stmt.copy(expr = snapshotExpr(stmt.expr))
    is NodeDecl -> stmt.copy(target = snapshotExpr(stmt.target) as CallExpr)
    is Connection, is GraphOutput -> stmt
}
