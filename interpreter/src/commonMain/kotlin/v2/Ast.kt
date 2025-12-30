package v2

sealed interface Ast

sealed interface TransformBody : Ast {
    val statements: List<Stmt>
}

sealed interface AccessSeg {
    data class Static(val key: ObjKey) : AccessSeg
    data class Dynamic(val keyExpr: Expr) : AccessSeg
}

data class GraphBody(
    override val statements: List<Stmt>,
) : TransformBody

data class NodeDecl(
    val alias: String,
    val target: CallExpr,
    override val token: Token,
) : Stmt

data class Connection(
    val from: String,
    val to: String,
    val input: String?,
    override val token: Token,
) : Stmt

data class GraphOutput(
    val alias: String,
    override val token: Token,
) : Stmt

data class Program(val version: VersionDecl?, val decls: List<TopLevelDecl>) : Ast

data class VersionDecl(val version: String) : Ast

sealed interface TopLevelDecl : Ast

data class SharedDecl(
    val name: String,
    val kind: SharedKind,
    val token: Token,
) : TopLevelDecl

enum class SharedKind { SINGLE, MANY }

data class FuncDecl(
    val name: String,
    val params: List<String>,
    val body: FuncBody,
    val token: Token,
) : TopLevelDecl

sealed interface FuncBody : Ast { val token: Token }

data class ExprBody(val expr: Expr, override val token: Token) : FuncBody
data class BlockBody(val block: CodeBlock, override val token: Token) : FuncBody

data class TypeDecl(
    val name: String,
    val kind: TypeKind,
    val defs: List<String>,
    val token: Token,
) : TopLevelDecl

enum class TypeKind { ENUM, UNION }

data class SourceDecl(val name: String, val adapter: AdapterSpec?, val token: Token) : TopLevelDecl

data class OutputDecl(val adapter: AdapterSpec?, val template: Expr, val token: Token) : TopLevelDecl

data class TransformDecl(
    val name: String?,
    val params: List<String>,
    val signature: TransformSignature?,
    val mode: Mode,
    val body: TransformBody,
    val token: Token,
) : TopLevelDecl

public data class TransformSignature(
    val input: TypeRef?,
    val output: TypeRef?,
    val tokenSpan: TokenSpan,
)

enum class Mode { STREAM, BUFFER }

data class AdapterSpec(val name: String, val args: List<Expr>, val token: Token) : Ast

public sealed interface TypeRef : Ast {
    val token: Token
}

public enum class PrimitiveType {
    TEXT,
    NUMBER,
    BOOLEAN,
    NULL,
    ANY,
    ANY_NULLABLE,
}

public data class PrimitiveTypeRef(
    val kind: PrimitiveType,
    override val token: Token,
) : TypeRef

public data class ArrayTypeRef(
    val elementType: TypeRef,
    override val token: Token,
) : TypeRef

public data class RecordFieldType(
    val name: String,
    val type: TypeRef,
    val optional: Boolean,
    val token: Token,
)

public data class RecordTypeRef(
    val fields: List<RecordFieldType>,
    override val token: Token,
) : TypeRef

public data class UnionTypeRef(
    val members: List<TypeRef>,
    override val token: Token,
) : TypeRef

public data class EnumTypeRef(
    val values: List<String>,
    override val token: Token,
) : TypeRef

public data class NamedTypeRef(
    val name: String,
    override val token: Token,
) : TypeRef

sealed class Property

data class LiteralProperty(val key: ObjKey, val value: Expr) : Property()
data class ComputedProperty(val keyExpr: Expr, val value: Expr) : Property()

sealed interface Stmt : Ast { val token: Token }

data class LetStmt(val name: String, val expr: Expr, override val token: Token) : Stmt

data class SetStmt(
    val target: AccessExpr,
    val value: Expr,
    override val token: Token
) : Stmt

data class SetVarStmt(
    val name: String,
    val value: Expr,
    override val token: Token,
) : Stmt

data class AppendToStmt(
    val target: AccessExpr,
    val value: Expr,
    val init: Expr?,
    override val token: Token
) : Stmt

data class AppendToVarStmt(
    val name: String,
    val value: Expr,
    val init: Expr?,
    override val token: Token
) : Stmt

data class ModifyStmt(val target: AccessExpr, val updates: List<Property>, override val token: Token) : Stmt

data class OutputStmt(val template: Expr, override val token: Token) : Stmt

data class IfStmt(
    val condition: Expr,
    val thenBlock: CodeBlock,
    val elseBlock: CodeBlock?,
    override val token: Token,
) : Stmt

data class ForEachStmt(
    val varName: String,
    val iterable: Expr,
    val body: CodeBlock,
    val where: Expr?,
    override val token: Token,
) : Stmt

data class TryCatchStmt(
    val tryExpr: Expr,
    val exceptionName: String,
    val retry: Int?,
    val backoff: String?,
    val fallbackExpr: Expr?,
    val fallbackAbort: AbortStmt?,
    override val token: Token,
) : Stmt

data class CodeBlock(override val statements: List<Stmt>, override val token: Token) : Stmt, TransformBody

data class ReturnStmt(
    val value: Expr?,
    override val token: Token,
) : Stmt

data class AbortStmt(val value: Expr?, override val token: Token) : Stmt

data class ExprStmt(val expr: Expr, override val token: Token) : Stmt

sealed interface Expr : Ast { val token: Token }

data class IdentifierExpr(val name: String, override val token: Token) : Expr
data class StringExpr(val value: String, override val token: Token) : Expr

data class AccessExpr(
    val base: Expr,
    val segs: List<AccessSeg>,
    override val token: Token
) : Expr

data class NumberLiteral(val value: NumValue, override val token: Token) : Expr

data class NullLiteral(override val token: Token) : Expr

data class ObjectExpr(val fields: List<Property>, override val token: Token) : Expr

data class CallExpr(val callee: IdentifierExpr, val args: List<Expr>, override val token: Token) : Expr

data class InvokeExpr(
    val target: Expr,
    val args: List<Expr>,
    override val token: Token
) : Expr

data class BinaryExpr(val left: Expr, override val token: Token, val right: Expr) : Expr
data class UnaryExpr(val expr: Expr, override val token: Token) : Expr
data class BoolExpr(val value: Boolean, override val token: Token) : Expr

data class LambdaExpr(
    val params: List<String>,
    val body: FuncBody,
    override val token: Token,
) : Expr

data class ArrayExpr(
    val elements: List<Expr>,
    override val token: Token,
) : Expr

data class ArrayCompExpr(
    val varName: String,
    val iterable: Expr,
    val mapExpr: Expr,
    val where: Expr?,
    override val token: Token,
) : Expr

data class IfElseExpr(
    val condition: Expr,
    val thenBranch: Expr,
    val elseBranch: Expr,
    override val token: Token,
) : Expr

data class TryCatchExpr(
    val tryExpr: Expr,
    val exceptionName: String,
    val retry: Int?,
    val backoff: String?,
    val fallbackExpr: Expr,
    override val token: Token,
) : Expr

public data class CaseWhen(
    val condition: Expr,
    val result: Expr,
)

public data class CaseExpr(
    val whens: List<CaseWhen>,
    val elseBranch: Expr,
    override val token: Token,
) : Expr

data class SharedStateAwaitExpr(
    val resource: String,
    val key: String,
    override val token: Token,
) : Expr
