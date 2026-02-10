package io.github.ehlyzov.branchline.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.ParseException
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.Program
import io.github.ehlyzov.branchline.SharedDecl
import io.github.ehlyzov.branchline.SharedKind
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.contract.ContractCoercion
import io.github.ehlyzov.branchline.contract.ContractEnforcerV3
import io.github.ehlyzov.branchline.contract.ContractValidationMode
import io.github.ehlyzov.branchline.contract.ContractViolationV2
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.contract.TransformContractV2
import io.github.ehlyzov.branchline.contract.TransformContractV3
import io.github.ehlyzov.branchline.contract.TransformContractV3Adapter
import io.github.ehlyzov.branchline.std.SharedResourceHandle
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider
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
    private val sharedDecls: List<SharedDecl>
    private val namedDescriptors: Map<String, TransformDescriptor>
    private val hostFnMeta: Map<String, io.github.ehlyzov.branchline.std.HostFnMetadata>
    private val contractBuilder: TransformContractBuilder

    init {
        val tokens = _root_ide_package_.io.github.ehlyzov.branchline.Lexer(source).lex()
        try {
            program = Parser(tokens, source).parse()
        } catch (ex: ParseException) {
            throw CliException(ex.message ?: "Parser error", kind = CliErrorKind.INPUT)
        }
        hostFns = StdLib.fns
        hostFnMeta = StdLib.meta
        _root_ide_package_.io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys).analyze(program)
        funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        transforms = program.decls.filterIsInstance<TransformDecl>()
        typeDecls = program.decls.filterIsInstance<TypeDecl>()
        sharedDecls = program.decls.filterIsInstance<SharedDecl>()
        if (transforms.isEmpty()) {
            throw CliException("Program must declare at least one TRANSFORM block", kind = CliErrorKind.INPUT)
        }
        registerSharedDecls()
        val typeResolver = _root_ide_package_.io.github.ehlyzov.branchline.sema.TypeResolver(typeDecls)
        contractBuilder = _root_ide_package_.io.github.ehlyzov.branchline.contract.TransformContractBuilder(typeResolver, hostFns.keys)
        namedDescriptors = _root_ide_package_.io.github.ehlyzov.branchline.ir.buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
    }

    fun selectTransform(name: String?): TransformDecl {
        if (name == null) return transforms.first()
        val match = namedDescriptors[name]?.decl
        if (match != null) return match
        throw CliException("Transform '$name' not found", kind = CliErrorKind.INPUT)
    }

    fun execute(transform: TransformDecl, input: Map<String, Any?>): Any? {
        val ir = compileIr(transform)
        val exec = _root_ide_package_.io.github.ehlyzov.branchline.ir.Exec(
            ir = ir,
            hostFns = hostFns,
            hostFnMeta = hostFnMeta,
            funcs = funcs,
            tracer = tracer,
            sharedStore = SharedStoreProvider.store,
            source = source,
            runtimeContextEnabled = true,
        )
        val env = buildEnv(transform, input)
        return exec.run(env, stringifyKeys = true)
    }

    fun executeWithContracts(
        transform: TransformDecl,
        input: Map<String, Any?>,
        mode: ContractValidationMode,
    ): ContractExecutionResult {
        if (mode == ContractValidationMode.OFF) {
            return ContractExecutionResult(execute(transform, input), emptyList())
        }
        val contractV3 = contractV3ForTransform(transform)
        val contractV2Compat = TransformContractV3Adapter.toV2(contractV3)
        val coercedInput = ContractCoercion.coerceInputBytes(contractV2Compat.input, input)
        val inputViolations = ContractEnforcerV3.enforceInput(mode, contractV3.input, coercedInput)
        val output = execute(transform, coercedInput)
        val outputViolations = ContractEnforcerV3.enforceOutput(mode, contractV3.output, output)
        return ContractExecutionResult(output, inputViolations + outputViolations)
    }

    fun compileBytecode(transform: TransformDecl): Bytecode {
        val ir = compileIr(transform)
        val compiler = _root_ide_package_.io.github.ehlyzov.branchline.vm.Compiler(funcs, hostFns)
        return compiler.compile(ir)
    }

    fun prepareVmExec(transform: TransformDecl, bytecode: Bytecode): VMExec {
        val ir = compileIr(transform)
        return _root_ide_package_.io.github.ehlyzov.branchline.vm.VMExec(
            ir = ir,
            tracer = tracer,
            hostFns = hostFns,
            hostFnMeta = hostFnMeta,
            funcs = funcs,
            precompiled = bytecode,
        )
    }

    fun executeVmWithContracts(
        transform: TransformDecl,
        input: Map<String, Any?>,
        vmExec: VMExec,
        mode: ContractValidationMode,
    ): ContractExecutionResult {
        if (mode == ContractValidationMode.OFF) {
            val env = buildEnv(transform, input)
            return ContractExecutionResult(vmExec.run(env, stringifyKeys = true), emptyList())
        }
        val contractV3 = contractV3ForTransform(transform)
        val contractV2Compat = TransformContractV3Adapter.toV2(contractV3)
        val coercedInput = ContractCoercion.coerceInputBytes(contractV2Compat.input, input)
        val inputViolations = ContractEnforcerV3.enforceInput(mode, contractV3.input, coercedInput)
        val env = buildEnv(transform, coercedInput)
        val output = vmExec.run(env, stringifyKeys = true)
        val outputViolations = ContractEnforcerV3.enforceOutput(mode, contractV3.output, output)
        return ContractExecutionResult(output, inputViolations + outputViolations)
    }

    fun renderTransforms(): List<String> = transforms.map { it.name ?: "<anonymous>" }

    fun rebuildTransform(name: String?): TransformDecl = selectTransform(name)

    fun source(): String = source

    fun typeDecls(): List<TypeDecl> = program.decls.filterIsInstance<TypeDecl>()

    fun sharedDecls(): List<SharedDecl> = allSharedDecls()

    fun contractForTransform(transform: TransformDecl): TransformContract =
        contractBuilder.build(transform)

    fun contractV2ForTransform(transform: TransformDecl): TransformContractV2 =
        contractBuilder.buildV2(transform)

    fun contractV3ForTransform(transform: TransformDecl): TransformContractV3 =
        contractBuilder.buildV3(transform)

    private fun compileIr(transform: TransformDecl) = _root_ide_package_.io.github.ehlyzov.branchline.ir.ToIR(funcs, hostFns).compile(transform.body.statements)

    fun buildEnv(transform: TransformDecl, input: Map<String, Any?>): MutableMap<String, Any?> {
        val mergedSharedDecls = mergeSharedDecls(transform)
        return HashMap<String, Any?>(input.size + mergedSharedDecls.size + 1).apply {
            this[DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
            for (decl in mergedSharedDecls) {
                if (!containsKey(decl.name)) {
                    this[decl.name] = SharedResourceHandle(decl.name)
                }
            }
        }
    }

    private fun registerSharedDecls() {
        val store = SharedStoreProvider.store ?: return
        for (decl in allSharedDecls()) {
            if (store.hasResource(decl.name)) continue
            val kind = when (decl.kind) {
                SharedKind.SINGLE -> SharedResourceKind.SINGLE
                SharedKind.MANY -> SharedResourceKind.MANY
            }
            store.addResource(decl.name, kind)
        }
    }

    private fun mergeSharedDecls(transform: TransformDecl): List<SharedDecl> {
        if (transform.options.shared.isEmpty()) return sharedDecls
        val merged = LinkedHashMap<String, SharedDecl>()
        for (decl in sharedDecls) {
            merged[decl.name] = decl
        }
        for (decl in transform.options.shared) {
            merged[decl.name] = decl
        }
        return merged.values.toList()
    }

    private fun allSharedDecls(): List<SharedDecl> {
        val merged = LinkedHashMap<String, SharedDecl>()
        for (decl in sharedDecls) {
            merged[decl.name] = decl
        }
        for (transform in transforms) {
            for (decl in transform.options.shared) {
                merged[decl.name] = decl
            }
        }
        return merged.values.toList()
    }
}

public data class ContractExecutionResult(
    val output: Any?,
    val violations: List<ContractViolationV2>,
)

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

    fun encode(artifact: CompiledArtifact, format: OutputFormat): String = when (format) {
        OutputFormat.JSON -> encode(artifact, pretty = true)
        OutputFormat.JSON_COMPACT -> encode(artifact, pretty = false)
        OutputFormat.JSON_CANONICAL -> encodeCanonical(artifact)
        OutputFormat.XML, OutputFormat.XML_COMPACT -> throw CliException(
            "XML output format is not supported for compiled artifacts",
            kind = CliErrorKind.USAGE,
        )
    }

    fun encode(artifact: CompiledArtifact, pretty: Boolean = true): String {
        val serializer = if (pretty) prettyJson else compactJson
        return serializer.encodeToString(CompiledArtifact.serializer(), artifact)
    }

    fun encodeCanonical(artifact: CompiledArtifact): String {
        val element = compactJson.encodeToJsonElement(CompiledArtifact.serializer(), artifact)
        return io.github.ehlyzov.branchline.json.formatCanonicalJson(element)
    }

    fun decode(raw: String): CompiledArtifact =
        compactJson.decodeFromString(CompiledArtifact.serializer(), raw)
}

public class CliException(
    message: String,
    val kind: CliErrorKind = CliErrorKind.USAGE,
) : RuntimeException(message)
