package io.branchline.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import v2.FuncDecl
import v2.Lexer
import v2.ParseException
import v2.Parser
import v2.Program
import v2.TransformDecl
import v2.TypeDecl
import v2.DEFAULT_INPUT_ALIAS
import v2.COMPAT_INPUT_ALIASES
import v2.ir.Exec
import v2.ir.ToIR
import v2.ir.TransformRegistry
import v2.ir.makeEval
import v2.sema.SemanticAnalyzer
import v2.std.StdLib
import v2.vm.Bytecode
import v2.vm.BytecodeIO
import v2.vm.Compiler
import v2.vm.VMExec

class BranchlineProgram(private val source: String) {
    private val program: Program
    private val hostFns: Map<String, (List<Any?>) -> Any?>
    private val funcs: Map<String, FuncDecl>
    private val transforms: List<TransformDecl>
    private val namedTransforms: Map<String, TransformDecl>
    private val registry: TransformRegistry
    private val eval: (v2.Expr, MutableMap<String, Any?>) -> Any?

    init {
        val tokens = Lexer(source).lex()
        try {
            program = Parser(tokens, source).parse()
        } catch (ex: ParseException) {
            throw CliException(ex.message ?: "Parser error")
        }
        hostFns = StdLib.fns
        SemanticAnalyzer(hostFns.keys).analyze(program)
        funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        transforms = program.decls.filterIsInstance<TransformDecl>()
        if (transforms.isEmpty()) {
            throw CliException("Program must declare at least one TRANSFORM block")
        }
        namedTransforms = transforms.mapNotNull { decl -> decl.name?.let { it to decl } }.toMap()
        registry = TransformRegistry(funcs, hostFns, namedTransforms)
        eval = makeEval(hostFns, funcs, registry, tracer = null)
    }

    fun selectTransform(name: String?): TransformDecl {
        if (name == null) return transforms.first()
        val match = namedTransforms[name]
        if (match != null) return match
        throw CliException("Transform '$name' not found")
    }

    fun execute(transform: TransformDecl, input: Map<String, Any?>): Any? {
        val ir = compileIr(transform)
        val exec = Exec(ir, eval)
        val env = HashMap<String, Any?>(input.size + 1).apply {
            this[DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        return exec.run(env, stringifyKeys = true)
    }

    fun compileBytecode(transform: TransformDecl): Bytecode {
        val ir = compileIr(transform)
        val compiler = Compiler(funcs, hostFns)
        return compiler.compile(ir)
    }

    fun prepareVmExec(transform: TransformDecl, bytecode: Bytecode): VMExec {
        val ir = compileIr(transform)
        return VMExec(ir, eval, tracer = null, hostFns = hostFns, funcs = funcs, precompiled = bytecode)
    }

    fun renderTransforms(): List<String> = transforms.map { it.name ?: "<anonymous>" }

    fun rebuildTransform(name: String?): TransformDecl = selectTransform(name)

    fun source(): String = source

    fun typeDecls(): List<TypeDecl> = program.decls.filterIsInstance<TypeDecl>()

    private fun compileIr(transform: TransformDecl) = ToIR(funcs, hostFns).compile(transform.body.statements)
}

@Serializable
data class CompiledArtifact(
    val version: Int = 1,
    val transform: String?,
    val script: String,
    val bytecode: BytecodeIO.SerializedBytecode,
)

object ArtifactCodec {
    private val json = Json { prettyPrint = true }

    fun encode(artifact: CompiledArtifact): String =
        json.encodeToString(CompiledArtifact.serializer(), artifact)

    fun decode(raw: String): CompiledArtifact =
        json.decodeFromString(CompiledArtifact.serializer(), raw)
}

class CliException(message: String) : RuntimeException(message)
