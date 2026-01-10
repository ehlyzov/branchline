package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.AccessExpr
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.Property

sealed interface IRNode
data class IRLet(val name: String, val expr: Expr) : IRNode
data class IRModify(val target: AccessExpr, val updates: List<Property>) : IRNode
data class IROutput(val fields: List<Property>) : IRNode
data class IRForEach(
    val varName: String,
    val iterable: Expr,
    val where: Expr?,
    val body: List<IRNode>
) : IRNode
data class IRIf(
    val condition: Expr,
    val thenBody: List<IRNode>,
    val elseBody: List<IRNode>?
) : IRNode
data class IRSet(val target: AccessExpr, val value: Expr) : IRNode
data class IRAppendTo(val target: AccessExpr, val value: Expr, val init: Expr?) : IRNode
data class IRTryCatch(
    val tryExpr: Expr,
    val exceptionName: String,
    val retry: Int,
    val fallbackExpr: Expr?,
    val fallbackAbort: Expr?
) : IRNode
data class IRReturn(val value: Expr?) : IRNode
data class IRAbort(val value: Expr?) : IRNode
data class IRExprOutput(val expr: Expr) : IRNode
data class IRExprStmt(val expr: Expr) : IRNode
data class IRSetVar(val name: String, val value: Expr) : IRNode
data class IRAppendVar(val name: String, val value: Expr, val init: Expr?) : IRNode
