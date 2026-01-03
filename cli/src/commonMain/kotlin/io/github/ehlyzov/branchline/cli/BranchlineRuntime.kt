package io.github.ehlyzov.branchline.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.ParseException
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.Program
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.Expr
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.ir.TransformRegistry
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.Bytecode
import io.github.ehlyzov.branchline.vm.BytecodeIO
import io.github.ehlyzov.branchline.vm.VMExec
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.TransformDescriptor

public class BranchlineProgram(
    private val source: String,
    private val tracer: Tracer? = null,
) {
    private val program: Program
    private val hostFns: Map<String, (List<Any?>) -> Any?>
    private val funcs: Map<String, FuncDecl>
    private val transforms: List<TransformDecl>
    private val typeDecls: List<TypeDecl>
    private val namedDescriptors: Map<String, TransformDescriptor>
    private val registry: TransformRegistry
    private val eval: (Expr, MutableMap<String, Any?>) -> Any?
    private val contractBuilder: TransformContractBuilder

    init {
        val tokens = _root_ide_package_.io.github.ehlyzov.branchline.Lexer(source).lex()
        try {
            program = Parser(tokens, source).parse()
        } catch (ex: ParseException) {
            throw CliException(ex.message ?: "Parser error", kind = CliErrorKind.INPUT)
        }
        hostFns = StdLib.fns
        _root_ide_package_.io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(program)
        funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        transforms = program.decls.filterIsInstance<TransformDecl>()
        typeDecls = program.decls.filterIsInstance<TypeDecl>()
        if (transforms.isEmpty()) {
            throw CliException("Program must declare at least one TRANSFORM block", kind = CliErrorKind.INPUT)
        }
        val typeResolver = _root_ide_package_.io.github.ehlyzov.branchline.sema.TypeResolver(typeDecls)
        contractBuilder = _root_ide_package_.io.github.ehlyzov.branchline.contract.TransformContractBuilder(typeResolver, hostFns.keys)
        namedDescriptors = _root_ide_package_.io.github.ehlyzov.branchline.ir.buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
        registry = TransformRegistry(funcs, hostFns, namedDescriptors)
        eval = _root_ide_package_.io.github.ehlyzov.branchline.ir.makeEval(hostFns, funcs, registry, tracer = tracer)
    }

    fun selectTransform(name: String?): TransformDecl {
        if (name == null) return transforms.first()
        val match = namedDescriptors[name]?.decl
        if (match != null) return match
        throw CliException("Transform '$name' not found", kind = CliErrorKind.INPUT)
    }

    fun execute(transform: TransformDecl, input: Map<String, Any?>): Any? {
        val ir = compileIr(transform)
        val exec = _root_ide_package_.io.github.ehlyzov.branchline.ir.Exec(ir, eval, tracer)
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
        val compiler = _root_ide_package_.io.github.ehlyzov.branchline.vm.Compiler(funcs, hostFns)
        return compiler.compile(ir)
    }

    fun prepareVmExec(transform: TransformDecl, bytecode: Bytecode): VMExec {
        val ir = compileIr(transform)
        return _root_ide_package_.io.github.ehlyzov.branchline.vm.VMExec(
            ir,
            eval,
            tracer = tracer,
            hostFns = hostFns,
            funcs = funcs,
            precompiled = bytecode
        )
    }

    fun renderTransforms(): List<String> = transforms.map { it.name ?: "<anonymous>" }

    fun rebuildTransform(name: String?): TransformDecl = selectTransform(name)

    fun source(): String = source

    fun typeDecls(): List<TypeDecl> = program.decls.filterIsInstance<TypeDecl>()

    fun contractForTransform(transform: TransformDecl): TransformContract =
        contractBuilder.build(transform)

    private fun compileIr(transform: TransformDecl) = _root_ide_package_.io.github.ehlyzov.branchline.ir.ToIR(funcs, hostFns).compile(transform.body.statements)
}

@Serializable
public data class CompiledArtifact(
    val version: Int = 1,
    val transform: String?,
    val script: String,
    val bytecode: BytecodeIO.SerializedBytecode,
    val contract: TransformContract? = null,
)

public object ArtifactCodec {
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json

    fun encode(artifact: CompiledArtifact, pretty: Boolean = true): String {
        val serializer = if (pretty) prettyJson else compactJson
        return serializer.encodeToString(CompiledArtifact.serializer(), artifact)
    }

    fun decode(raw: String): CompiledArtifact =
        compactJson.decodeFromString(CompiledArtifact.serializer(), raw)
}

public class CliException(
    message: String,
    val kind: CliErrorKind = CliErrorKind.USAGE,
) : RuntimeException(message)
