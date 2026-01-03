package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.*

/** Линейно превращает список Stmt в список IR-нод. */
class ToIR(
    private val funcs: Map<String, FuncDecl>,
    private val hostFns: Map<String, (List<Any?>) -> Any?>,
) {

    fun compile(stmts: List<Stmt>): List<IRNode> =
        stmts.flatMap { stmt -> compileStmt(stmt) }

    private fun compileStmt(s: Stmt): List<IRNode> = when (s) {
        is LetStmt -> listOf(IRLet(s.name, s.expr))
        is SetStmt -> listOf(IRSet(s.target, s.value))
        is AppendToStmt -> listOf(IRAppendTo(s.target, s.value, s.init))
        is OutputStmt -> {
            when (val tpl = s.template) {
                is ObjectExpr -> listOf(IROutput(tpl.fields))
                else -> listOf(IRExprOutput(tpl))
            }
        }

        is ModifyStmt -> {
            // ModifyStmt уже должен нести target: PathExpr и updates: List<Property>
            listOf(IRModify(s.target, s.updates))
        }

        is IfStmt -> listOf(
            IRIf(
                s.condition,
                compile(s.thenBlock.statements),
                s.elseBlock?.let { compile(it.statements) }
            )
        )

        is ForEachStmt -> listOf(
            IRForEach(s.varName, s.iterable, s.where, compile(s.body.statements))
        )

        is TryCatchStmt -> listOf(
            IRTryCatch(
                s.tryExpr,
                s.exceptionName,
                s.retry ?: 0,
                s.fallbackExpr,
                s.fallbackAbort?.value
            )
        )

        is AbortStmt -> listOf(IRAbort(s.value))
        is ReturnStmt -> listOf(IRReturn(s.value))
        is ExprStmt -> listOf(IRExprStmt(s.expr))
        is SetVarStmt -> listOf(IRSetVar(s.name, s.value))
        is AppendToVarStmt -> listOf(IRAppendVar(s.name, s.value, s.init))
        else -> error("Stmt kind ${s::class.simpleName} unsupported in STREAM")
    }
}
