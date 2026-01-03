package io.github.ehlyzov.branchline

public fun lowerCaseExpr(expr: CaseExpr): Expr {
    var lowered: Expr = expr.elseBranch
    for (whenBranch in expr.whens.asReversed()) {
        lowered = IfElseExpr(
            condition = whenBranch.condition,
            thenBranch = whenBranch.result,
            elseBranch = lowered,
            token = expr.token,
        )
    }
    return lowered
}
