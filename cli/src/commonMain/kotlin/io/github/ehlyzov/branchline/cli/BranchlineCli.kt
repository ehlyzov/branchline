package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.ArrayTypeRef
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.EnumTypeRef
import io.github.ehlyzov.branchline.NamedTypeRef
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.PrimitiveType
import io.github.ehlyzov.branchline.PrimitiveTypeRef
import io.github.ehlyzov.branchline.RecordTypeRef
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TransformSignature
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.TypeRef
import io.github.ehlyzov.branchline.UnionTypeRef
import io.github.ehlyzov.branchline.contract.AccessPath
import io.github.ehlyzov.branchline.contract.AccessSegment
import io.github.ehlyzov.branchline.contract.DynamicAccess
import io.github.ehlyzov.branchline.contract.DynamicField
import io.github.ehlyzov.branchline.contract.FieldConstraint
import io.github.ehlyzov.branchline.contract.FieldShape
import io.github.ehlyzov.branchline.contract.SchemaGuarantee
import io.github.ehlyzov.branchline.contract.SchemaRequirement
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.contract.ValueShape
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.TraceReport
import io.github.ehlyzov.branchline.schema.NullabilityStyle
import io.github.ehlyzov.branchline.sema.SemanticWarning
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.BytecodeIO

public enum class PlatformKind { JVM, JS }

public enum class InputFormat { JSON, XML;
    companion object {
        fun parse(value: String): InputFormat = when (value.lowercase()) {
            "json" -> JSON
            "xml" -> XML
            else -> throw CliException("Unknown input format '$value'", kind = CliErrorKind.USAGE)
        }
    }
}

public data class RunOptions(
    val scriptPath: String,
    val inputPath: String?,
    val inputFormat: InputFormat,
    val transformName: String?,
    val outputFormat: OutputFormat,
    val traceFormat: TraceFormat?,
)

public data class CompileOptions(
    val scriptPath: String,
    val outputPath: String?,
    val transformName: String?,
    val outputFormat: OutputFormat,
)

public data class ExecOptions(
    val artifactPath: String,
    val inputPath: String?,
    val inputFormat: InputFormat,
    val transformOverride: String?,
    val outputFormat: OutputFormat,
    val traceFormat: TraceFormat?,
)

public data class InspectOptions(
    val scriptPath: String,
    val showContracts: Boolean,
    val transformName: String?,
)

public data class SchemaOptions(
    val scriptPath: String?,
    val typeName: String?,
    val allTypes: Boolean,
    val outputPath: String?,
    val importPath: String?,
    val importName: String?,
    val nullabilityStyle: NullabilityStyle,
)

public data class ContractDiffOptions(
    val oldPath: String,
    val newPath: String,
    val typeName: String?,
)

public enum class CliCommand { RUN, COMPILE, EXECUTE, INSPECT, SCHEMA, CONTRACT_DIFF }

public object BranchlineCli {
    fun run(args: List<String>, platform: PlatformKind, defaultCommand: CliCommand? = null): Int {
        val (normalizedArgs, globalOptions) = try {
            parseGlobalOptions(args)
        } catch (ex: CliException) {
            return handleCliError(
                CliError(ex.message ?: "CLI error", ex.kind, null),
                ErrorFormat.TEXT,
            )
        }

        if (normalizedArgs.isEmpty()) {
            return handleCliError(
                CliError("No arguments provided", CliErrorKind.USAGE, null),
                globalOptions.errorFormat,
            )
        }

        if (normalizedArgs.size == 1 && (normalizedArgs[0] == "--help" || normalizedArgs[0] == "-h" || normalizedArgs[0] == "help")) {
            printHelp(defaultCommand)
            return ExitCode.SUCCESS.code
        }
        if (normalizedArgs.size == 1 && (normalizedArgs[0] == "--version" || normalizedArgs[0] == "-v")) {
            printVersion()
            return ExitCode.SUCCESS.code
        }

        val errorFormat = globalOptions.errorFormat

        val parseResult = try {
            parseArgs(normalizedArgs, defaultCommand)
        } catch (ex: CliException) {
            return handleCliError(
                CliError(ex.message ?: "CLI error", ex.kind, null),
                errorFormat,
            )
        }

        return try {
            when (val command = parseResult.command) {
                CliCommand.RUN -> executeRun(parseResult.run!!, platform)
                CliCommand.COMPILE -> executeCompile(parseResult.compile!!)
                CliCommand.EXECUTE -> executeExec(parseResult.exec!!)
                CliCommand.INSPECT -> executeInspect(parseResult.inspect!!)
                CliCommand.SCHEMA -> executeSchema(parseResult.schema!!)
                CliCommand.CONTRACT_DIFF -> executeContractDiff(parseResult.contractDiff!!)
            }
        } catch (ex: CliException) {
            handleCliError(
                CliError(ex.message ?: "CLI error", ex.kind, parseResult.command),
                errorFormat,
            )
        } catch (ex: Exception) {
            handleCliError(
                CliError(ex.message ?: ex.toString(), CliErrorKind.RUNTIME, parseResult.command),
                errorFormat,
            )
        }
    }

    private fun printHelp(defaultCommand: CliCommand?) {
        val defaultHint = when (defaultCommand) {
            CliCommand.RUN -> " (default: run)"
            CliCommand.COMPILE -> " (default: compile)"
            CliCommand.EXECUTE -> " (default: exec)"
            CliCommand.INSPECT -> " (default: inspect)"
            CliCommand.SCHEMA -> " (default: schema)"
            CliCommand.CONTRACT_DIFF -> " (default: contract-diff)"
            null -> ""
        }
        println(
            """
            Branchline CLI$defaultHint
            
            Usage:
              bl [run] <script.bl> [--input <path>|-] [--input-format json|xml] [--transform <name>] [--output-format json|json-compact] [--trace] [--trace-format text|json]
              blc <script.bl> [--output <artifact.blc>] [--transform <name>] [--output-format json|json-compact]
              blvm <artifact.blc> [--input <path>|-] [--input-format json|xml] [--transform <name>] [--output-format json|json-compact] [--trace] [--trace-format text|json]
              bl inspect <script.bl> --contracts [--transform <name>]
              bl schema <script.bl> <TYPE_NAME> [--nullable] [--output <schema.json>]
              bl schema <script.bl> --all [--nullable] [--output <schema.json>]
              bl schema --import <schema.json> --name <TYPE_NAME> [--output <types.bl>]
              bl contract-diff <old> <new> [--type <TYPE_NAME>]
            
            Commands:
              run       Compile + execute a Branchline script and print JSON output. Default when no subcommand is provided.
              compile   Compile a script to a bytecode artifact (.blc) that embeds the chosen transform.
              exec      Execute a compiled artifact through the VM. Use --transform to override the embedded transform.
              inspect   Show inferred and explicit contract information for transforms.
              schema    Emit JSON Schema for TYPE declarations or generate TYPE declarations from a JSON Schema file.
              contract-diff  Compare two TYPE declarations or schema files for breaking changes.
            
            Options:
              --input PATH        Read JSON/XML input from PATH; use '-' to read from stdin.
              --input-format FMT  'json' (default) or 'xml'.
              --transform NAME    Choose a TRANSFORM block by name; defaults to the first transform in the script.
              --output PATH       (compile) write the compiled artifact to PATH. Prints to stdout when omitted.
              --output-format FMT Format for CLI JSON output ('json' or 'json-compact').
              --trace             Emit a trace summary to stderr after execution.
              --trace-format FMT  Trace output format ('text' or 'json').
              --error-format FMT  Error output format ('text' or 'json').
              --contracts         (inspect) print explicit vs inferred contracts with mismatch warnings.
              --all               (schema) emit a ${'$'}defs block with all TYPE declarations.
              --nullable          (schema) use 'nullable: true' instead of 'type: [\"null\", ...]'.
              --import PATH       (schema) read a JSON Schema document and emit TYPE declarations.
              --name NAME         (schema) name for the imported schema's TYPE declaration.
              --type NAME         (contract-diff) TYPE declaration name when comparing Branchline scripts.
              --version           Print the CLI version.
            
            Examples:
              bl examples/hello.bl --input fixtures/hello.json
              bl run pipeline.bl --transform Trace --input-format xml --input -
              blc scripts/order.bl --output build/order.blc
              blvm build/order.blc --input data.json
              bl inspect scripts/order.bl --contracts
              bl schema examples/types.bl Customer
              bl schema examples/types.bl --all --output build/types.schema.json
              bl schema --import schemas/customer.json --name Customer
              bl contract-diff schemas/order-v1.json schemas/order-io.github.ehlyzov.branchline.json
            """.trimIndent(),
        )
    }

    private fun executeRun(options: RunOptions, platform: PlatformKind): Int {
        val traceSession = createTraceSession(options.traceFormat)
        val source = readTextFileOrThrow(options.scriptPath)
        val runtime = BranchlineProgram(source, traceSession?.tracer)
        val transform = runtime.selectTransform(options.transformName)
        val input = loadInput(options.inputPath, options.inputFormat)
        val result = runtime.execute(transform, input)
        println(formatJson(result, pretty = options.outputFormat.pretty))
        traceSession?.emit()
        return ExitCode.SUCCESS.code
    }

    private fun executeCompile(options: CompileOptions): Int {
        val source = readTextFileOrThrow(options.scriptPath)
        val runtime = BranchlineProgram(source)
        val transform = runtime.selectTransform(options.transformName)
        val contract = runtime.contractForTransform(transform)
        val bytecode = runtime.compileBytecode(transform)
        val artifact = CompiledArtifact(
            version = 1,
            transform = transform.name,
            script = source,
            bytecode = BytecodeIO.serializeBytecode(bytecode),
            contract = contract,
        )
        val payload = ArtifactCodec.encode(artifact, pretty = options.outputFormat.pretty)
        if (options.outputPath != null) {
            writeTextFileOrThrow(options.outputPath, payload)
        } else {
            println(payload)
        }
        return ExitCode.SUCCESS.code
    }

    private fun executeExec(options: ExecOptions): Int {
        val traceSession = createTraceSession(options.traceFormat)
        val raw = readTextFileOrThrow(options.artifactPath)
        val artifact = ArtifactCodec.decode(raw)
        val runtime = BranchlineProgram(artifact.script, traceSession?.tracer)
        val transformName = options.transformOverride ?: artifact.transform
        val transform = runtime.selectTransform(transformName)
        val bytecode = BytecodeIO.deserializeBytecode(artifact.bytecode)
        val vmExec = runtime.prepareVmExec(transform, bytecode)
        val input = loadInput(options.inputPath, options.inputFormat)
        val env = HashMap<String, Any?>(input.size + 1).apply {
            this[DEFAULT_INPUT_ALIAS] = input
            putAll(input)
            for (alias in COMPAT_INPUT_ALIASES) {
                this[alias] = input
            }
        }
        val result = vmExec.run(env as MutableMap<String, Any?>, stringifyKeys = true)
        println(formatJson(result, pretty = options.outputFormat.pretty))
        traceSession?.emit()
        return ExitCode.SUCCESS.code
    }

    private fun executeInspect(options: InspectOptions): Int {
        if (!options.showContracts) {
            throw CliException("Inspect requires --contracts", kind = CliErrorKind.USAGE)
        }
        val source = readTextFileOrThrow(options.scriptPath)
        val tokens = _root_ide_package_.io.github.ehlyzov.branchline.Lexer(source).lex()
        val program = Parser(tokens, source).parse()
        val hostFns = StdLib.fns
        val analyzer = _root_ide_package_.io.github.ehlyzov.branchline.sema.SemanticAnalyzer(hostFns.keys)
        analyzer.analyze(program)
        val transforms = program.decls.filterIsInstance<TransformDecl>()
        if (transforms.isEmpty()) {
            throw CliException("Program must declare at least one TRANSFORM block", kind = CliErrorKind.INPUT)
        }
        val typeDecls = program.decls.filterIsInstance<TypeDecl>()
        val contractBuilder = _root_ide_package_.io.github.ehlyzov.branchline.contract.TransformContractBuilder(
            _root_ide_package_.io.github.ehlyzov.branchline.sema.TypeResolver(typeDecls),
            hostFns.keys
        )
        val selected = selectTransforms(transforms, options.transformName)
        val report = renderContractInspection(selected, contractBuilder, analyzer.warnings)
        println(report)
        return ExitCode.SUCCESS.code
    }

    private fun executeSchema(options: SchemaOptions): Int {
        if (options.importPath != null) {
            val rawSchema = readTextFileOrThrow(options.importPath)
            val declarations = JsonSchemaCliInterop.importSchema(
                rawSchema = rawSchema,
                importName = options.importName!!,
                options = options,
            )
            val output = declarations.joinToString("\n\n") { renderTypeDecl(it) }
            if (options.outputPath != null) {
                writeTextFileOrThrow(options.outputPath, output)
            } else {
                println(output)
            }
        } else {
            val scriptPath = options.scriptPath
                ?: throw CliException("Script path is required for schema export", kind = CliErrorKind.USAGE)
            val source = readTextFileOrThrow(scriptPath)
            val runtime = BranchlineProgram(source)
            val typeDecls = runtime.typeDecls()
            val outputSchema = JsonSchemaCliInterop.exportSchema(
                typeDecls = typeDecls,
                typeName = options.typeName,
                options = options,
            )
            if (options.outputPath != null) {
                writeTextFileOrThrow(options.outputPath, outputSchema)
            } else {
                println(outputSchema)
            }
        }
        return ExitCode.SUCCESS.code
    }

    private fun executeContractDiff(options: ContractDiffOptions): Int {
        return try {
            val oldDefinition = loadTypeDefinition(options.oldPath, options.typeName)
            val newDefinition = loadTypeDefinition(options.newPath, options.typeName)
            val report = renderContractDiff(oldDefinition, newDefinition)
            println(report)
            0
        } catch (ex: CliException) {
            printError(ex.message ?: "CLI error")
            1
        } catch (ex: Exception) {
            printError(ex.message ?: ex.toString())
            1
        }
    }

    private data class ParsedArgs(
        val command: CliCommand,
        val run: RunOptions? = null,
        val compile: CompileOptions? = null,
        val exec: ExecOptions? = null,
        val inspect: InspectOptions? = null,
        val schema: SchemaOptions? = null,
        val contractDiff: ContractDiffOptions? = null,
    )

    private data class GlobalOptions(
        val errorFormat: ErrorFormat,
    )

    private data class TraceSession(
        val tracer: CollectingTracer,
        val format: TraceFormat,
    ) {
        fun emit() {
            val payload = renderTrace(tracer, format)
            if (payload.isNotBlank()) {
                printTrace(payload)
            }
        }
    }

    private fun parseGlobalOptions(args: List<String>): Pair<List<String>, GlobalOptions> {
        var errorFormat = ErrorFormat.TEXT
        val remaining = mutableListOf<String>()
        var idx = 0
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--error-format" && idx + 1 < args.size -> {
                    errorFormat = ErrorFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--error-format" -> throw CliException(
                    "--error-format requires a value",
                    kind = CliErrorKind.USAGE,
                )
                else -> {
                    remaining += token
                    idx += 1
                }
            }
        }
        return remaining to GlobalOptions(errorFormat)
    }

    private fun parseArgs(args: List<String>, defaultCommand: CliCommand?): ParsedArgs {
        val (command, startIndex) = determineCommand(args, defaultCommand)
        return when (command) {
            CliCommand.RUN -> ParsedArgs(command, run = parseRunArgs(args, startIndex))
            CliCommand.COMPILE -> ParsedArgs(command, compile = parseCompileArgs(args, startIndex))
            CliCommand.EXECUTE -> ParsedArgs(command, exec = parseExecArgs(args, startIndex))
            CliCommand.INSPECT -> ParsedArgs(command, inspect = parseInspectArgs(args, startIndex))
            CliCommand.SCHEMA -> ParsedArgs(command, schema = parseSchemaArgs(args, startIndex))
            CliCommand.CONTRACT_DIFF -> ParsedArgs(command, contractDiff = parseContractDiffArgs(args, startIndex))
        }
    }

    private fun determineCommand(args: List<String>, defaultCommand: CliCommand?): Pair<CliCommand, Int> {
        if (args.isEmpty()) {
            if (defaultCommand != null) return defaultCommand to 0
            throw CliException("No arguments provided")
        }
        val first = args[0]
        return when (first) {
            "run" -> CliCommand.RUN to 1
            "compile", "c" -> CliCommand.COMPILE to 1
            "exec", "execute" -> CliCommand.EXECUTE to 1
            "inspect" -> CliCommand.INSPECT to 1
            "schema" -> CliCommand.SCHEMA to 1
            "contract-diff" -> CliCommand.CONTRACT_DIFF to 1
            else -> (defaultCommand ?: CliCommand.RUN) to 0
        }
    }

    private fun parseRunArgs(args: List<String>, startIndex: Int): RunOptions {
        var script: String? = null
        var input: String? = null
        var transform: String? = null
        var format = InputFormat.JSON
        var outputFormat = OutputFormat.JSON
        var traceFormat: TraceFormat? = null
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--input" && idx + 1 < args.size -> {
                    input = args[idx + 1]
                    idx += 2
                }
                token == "--input-format" && idx + 1 < args.size -> {
                    format = InputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--transform" && idx + 1 < args.size -> {
                    transform = args[idx + 1]
                    idx += 2
                }
                token == "--output-format" && idx + 1 < args.size -> {
                    outputFormat = OutputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--trace" -> {
                    if (traceFormat == null) {
                        traceFormat = TraceFormat.TEXT
                    }
                    idx += 1
                }
                token == "--trace-format" && idx + 1 < args.size -> {
                    traceFormat = TraceFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--trace-format" -> throw CliException(
                    "--trace-format requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null -> {
                    script = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (script == null) throw CliException("Script path is required")
        return RunOptions(script, input, format, transform, outputFormat, traceFormat)
    }

    private fun parseCompileArgs(args: List<String>, startIndex: Int): CompileOptions {
        var script: String? = null
        var output: String? = null
        var transform: String? = null
        var outputFormat = OutputFormat.JSON
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--output" && idx + 1 < args.size -> {
                    output = args[idx + 1]
                    idx += 2
                }
                token == "--transform" && idx + 1 < args.size -> {
                    transform = args[idx + 1]
                    idx += 2
                }
                token == "--output-format" && idx + 1 < args.size -> {
                    outputFormat = OutputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null -> {
                    script = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (script == null) throw CliException("Script path is required")
        return CompileOptions(script, output, transform, outputFormat)
    }

    private fun parseExecArgs(args: List<String>, startIndex: Int): ExecOptions {
        var artifact: String? = null
        var input: String? = null
        var format = InputFormat.JSON
        var transform: String? = null
        var outputFormat = OutputFormat.JSON
        var traceFormat: TraceFormat? = null
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--input" && idx + 1 < args.size -> {
                    input = args[idx + 1]
                    idx += 2
                }
                token == "--input-format" && idx + 1 < args.size -> {
                    format = InputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--transform" && idx + 1 < args.size -> {
                    transform = args[idx + 1]
                    idx += 2
                }
                token == "--output-format" && idx + 1 < args.size -> {
                    outputFormat = OutputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--trace" -> {
                    if (traceFormat == null) {
                        traceFormat = TraceFormat.TEXT
                    }
                    idx += 1
                }
                token == "--trace-format" && idx + 1 < args.size -> {
                    traceFormat = TraceFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--trace-format" -> throw CliException(
                    "--trace-format requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                artifact == null -> {
                    artifact = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (artifact == null) throw CliException("Artifact path is required")
        return ExecOptions(artifact, input, format, transform, outputFormat, traceFormat)
    }

    private fun parseInspectArgs(args: List<String>, startIndex: Int): InspectOptions {
        var script: String? = null
        var showContracts = false
        var transform: String? = null
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--contracts" -> {
                    showContracts = true
                    idx += 1
                }
                token == "--transform" && idx + 1 < args.size -> {
                    transform = args[idx + 1]
                    idx += 2
                }
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null -> {
                    script = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (script == null) throw CliException("Script path is required")
        return InspectOptions(
            scriptPath = script,
            showContracts = showContracts,
            transformName = transform,
        )
    }

    private fun parseSchemaArgs(args: List<String>, startIndex: Int): SchemaOptions {
        var script: String? = null
        var typeName: String? = null
        var output: String? = null
        var importPath: String? = null
        var importName: String? = null
        var allTypes = false
        var nullabilityStyle = NullabilityStyle.TYPE_UNION
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--all" -> {
                    allTypes = true
                    idx += 1
                }
                token == "--nullable" -> {
                    nullabilityStyle = NullabilityStyle.NULLABLE_KEYWORD
                    idx += 1
                }
                token == "--output" && idx + 1 < args.size -> {
                    output = args[idx + 1]
                    idx += 2
                }
                token == "--import" && idx + 1 < args.size -> {
                    importPath = args[idx + 1]
                    idx += 2
                }
                token == "--name" && idx + 1 < args.size -> {
                    importName = args[idx + 1]
                    idx += 2
                }
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null && importPath == null -> {
                    script = token
                    idx += 1
                }
                typeName == null && importPath == null -> {
                    typeName = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (importPath != null) {
            if (script != null || typeName != null) {
                throw CliException("Schema import does not accept script or type arguments")
            }
            if (importName == null) throw CliException("Schema import requires --name")
        } else {
            if (script == null) throw CliException("Script path is required")
            if (!allTypes && typeName == null) {
                throw CliException("Schema export requires a TYPE name or --all")
            }
            if (allTypes && typeName != null) {
                throw CliException("Schema export cannot combine --all with a TYPE name")
            }
        }
        return SchemaOptions(
            scriptPath = script,
            typeName = typeName,
            allTypes = allTypes,
            outputPath = output,
            importPath = importPath,
            importName = importName,
            nullabilityStyle = nullabilityStyle,
        )
    }

    private fun parseContractDiffArgs(args: List<String>, startIndex: Int): ContractDiffOptions {
        var oldPath: String? = null
        var newPath: String? = null
        var typeName: String? = null
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--type" && idx + 1 < args.size -> {
                    typeName = args[idx + 1]
                    idx += 2
                }
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                oldPath == null -> {
                    oldPath = token
                    idx += 1
                }
                newPath == null -> {
                    newPath = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (oldPath == null || newPath == null) {
            throw CliException("contract-diff requires <old> and <new> inputs")
        }
        return ContractDiffOptions(
            oldPath = oldPath,
            newPath = newPath,
            typeName = typeName,
        )
    }

    private fun printVersion() {
        println("Branchline CLI ${CliVersion.CURRENT}")
    }

    private fun createTraceSession(format: TraceFormat?): TraceSession? {
        if (format == null) return null
        return TraceSession(
            tracer = CollectingTracer(TraceOptions()),
            format = format,
        )
    }
}

private fun loadInput(path: String?, format: InputFormat): Map<String, Any?> {
    val text = when {
        path == null -> ""
        path == "-" -> readStdinOrThrow()
        else -> readTextFileOrThrow(path)
    }
    if (text.isBlank()) return emptyMap()
    return try {
        when (format) {
            InputFormat.JSON -> parseJsonInput(text)
            InputFormat.XML -> parseXmlInput(text)
        }
    } catch (ex: CliException) {
        throw ex
    } catch (ex: Exception) {
        throw CliException(ex.message ?: "Invalid input", kind = CliErrorKind.INPUT)
    }
}

private fun readTextFileOrThrow(path: String): String {
    return try {
        readTextFile(path)
    } catch (ex: Exception) {
        throw CliException("Failed to read file '$path': ${ex.message ?: ex}", kind = CliErrorKind.IO)
    }
}

private fun writeTextFileOrThrow(path: String, contents: String) {
    try {
        writeTextFile(path, contents)
    } catch (ex: Exception) {
        throw CliException("Failed to write file '$path': ${ex.message ?: ex}", kind = CliErrorKind.IO)
    }
}

private fun readStdinOrThrow(): String {
    return try {
        readStdin()
    } catch (ex: Exception) {
        throw CliException("Failed to read stdin: ${ex.message ?: ex}", kind = CliErrorKind.IO)
    }
}

private fun selectTransforms(
    transforms: List<TransformDecl>,
    name: String?,
): List<TransformDecl> {
    if (name == null) return transforms
    val matches = transforms.filter { it.name == name }
    if (matches.isEmpty()) {
        throw CliException("Transform '$name' not found", kind = CliErrorKind.INPUT)
    }
    return matches
}

private fun renderContractInspection(
    transforms: List<TransformDecl>,
    contractBuilder: TransformContractBuilder,
    warnings: List<SemanticWarning>,
): String {
    val sections = mutableListOf<String>()
    transforms.forEach { transform ->
        sections += renderTransformContractBlock(transform, contractBuilder)
    }
    sections += renderContractWarnings(warnings)
    return sections.joinToString("\n\n").trim()
}

private fun renderTransformContractBlock(
    transform: TransformDecl,
    contractBuilder: TransformContractBuilder,
): String {
    val lines = mutableListOf<String>()
    val name = transform.name ?: "<anonymous>"
    lines += "Transform: $name"
    val explicitContract = contractBuilder.buildExplicitContract(transform)
    if (explicitContract == null) {
        lines += "  Explicit contract: none (no signature)"
    } else {
        lines += "  Explicit contract:"
        lines += "    Signature: ${renderSignature(transform.signature)}"
        lines += renderContractDetails(explicitContract, "    ")
    }
    val inferredContract = contractBuilder.buildInferredContract(transform)
    lines += "  Inferred contract:"
    lines += renderContractDetails(inferredContract, "    ")
    return lines.joinToString("\n")
}

private fun renderSignature(signature: TransformSignature?): String {
    if (signature == null) return "none"
    val input = signature.input?.let { renderTypeRef(it) } ?: "unspecified"
    val output = signature.output?.let { renderTypeRef(it) } ?: "unspecified"
    return "input $input -> output $output"
}

private fun renderContractDetails(contract: TransformContract, indent: String): List<String> {
    val lines = mutableListOf<String>()
    lines += "Input:"
    lines += renderSchemaRequirementLines(contract.input).map { line -> "  $line" }
    lines += "Output:"
    lines += renderSchemaGuaranteeLines(contract.output).map { line -> "  $line" }
    return indentLines(lines, indent)
}

private fun renderSchemaRequirementLines(requirement: SchemaRequirement): List<String> {
    val lines = mutableListOf<String>()
    lines += "open: ${requirement.open}"
    if (requirement.fields.isEmpty()) {
        lines += "fields: (none)"
    } else {
        lines += "fields:"
        requirement.fields.forEach { (name, constraint) ->
            lines += "  - ${renderInputField(name, constraint)}"
        }
    }
    lines += renderDynamicAccessLines(requirement.dynamicAccess)
    return lines
}

private fun renderSchemaGuaranteeLines(guarantee: SchemaGuarantee): List<String> {
    val lines = mutableListOf<String>()
    lines += "mayEmitNull: ${guarantee.mayEmitNull}"
    if (guarantee.fields.isEmpty()) {
        lines += "fields: (none)"
    } else {
        lines += "fields:"
        guarantee.fields.forEach { (name, shape) ->
            lines += "  - ${renderOutputField(name, shape)}"
        }
    }
    lines += renderDynamicFieldLines(guarantee.dynamicFields)
    return lines
}

private fun renderInputField(name: String, constraint: FieldConstraint): String {
    val required = if (constraint.required) "required" else "optional"
    return "$name ($required): ${renderValueShape(constraint.shape)}"
}

private fun renderOutputField(name: String, shape: FieldShape): String {
    val required = if (shape.required) "required" else "optional"
    val origin = shape.origin.name.lowercase()
    return "$name ($required, origin=$origin): ${renderValueShape(shape.shape)}"
}

private fun renderDynamicAccessLines(accesses: List<DynamicAccess>): List<String> {
    if (accesses.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    lines += "dynamic access:"
    accesses.forEach { access ->
        val suffix = access.valueShape?.let { shape -> " -> ${renderValueShape(shape)}" } ?: ""
        val reason = access.reason.name.lowercase()
        lines += "  - ${renderAccessPath(access.path)} ($reason)$suffix"
    }
    return lines
}

private fun renderDynamicFieldLines(fields: List<DynamicField>): List<String> {
    if (fields.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    lines += "dynamic fields:"
    fields.forEach { field ->
        val suffix = field.valueShape?.let { shape -> " -> ${renderValueShape(shape)}" } ?: ""
        val reason = field.reason.name.lowercase()
        lines += "  - ${renderAccessPath(field.path)} ($reason)$suffix"
    }
    return lines
}

private fun renderValueShape(shape: ValueShape): String = when (shape) {
    ValueShape.Unknown -> "unknown"
    ValueShape.Null -> "null"
    ValueShape.BooleanShape -> "boolean"
    ValueShape.NumberShape -> "number"
    ValueShape.TextShape -> "text"
    is ValueShape.ArrayShape -> "array<${renderValueShape(shape.element)}>"
    is ValueShape.ObjectShape -> renderObjectShape(shape.schema, shape.closed)
    is ValueShape.Union -> shape.options.joinToString(" | ") { option -> renderValueShape(option) }
}

private fun renderObjectShape(schema: SchemaGuarantee, closed: Boolean): String {
    val fields = if (schema.fields.isEmpty()) {
        ""
    } else {
        schema.fields.keys.joinToString(", ", prefix = "{", postfix = "}")
    }
    val openness = if (closed) "closed" else "open"
    return "object$fields ($openness)"
}

private fun renderAccessPath(path: AccessPath): String {
    if (path.segments.isEmpty()) return "<root>"
    return path.segments.joinToString(separator = ".") { segment -> renderAccessSegment(segment) }
}

private fun renderAccessSegment(segment: AccessSegment): String = when (segment) {
    is AccessSegment.Field -> segment.name
    is AccessSegment.Index -> "[${segment.index}]"
    AccessSegment.Dynamic -> "*"
}

private fun renderTypeRef(typeRef: TypeRef): String = when (typeRef) {
    is PrimitiveTypeRef -> renderPrimitiveType(typeRef.kind)
    is EnumTypeRef -> "enum{${typeRef.values.joinToString(", ")}}"
    is ArrayTypeRef -> "array<${renderTypeRef(typeRef.elementType)}>"
    is RecordTypeRef -> {
        val fields = typeRef.fields.joinToString(", ") { field ->
            val optional = if (field.optional) "?" else ""
            "${field.name}$optional: ${renderTypeRef(field.type)}"
        }
        "{${fields}}"
    }
    is UnionTypeRef -> typeRef.members.joinToString(" | ") { member -> renderTypeRef(member) }
    is NamedTypeRef -> typeRef.name
}

private fun renderPrimitiveType(kind: PrimitiveType): String = when (kind) {
    PrimitiveType.TEXT -> "text"
    PrimitiveType.NUMBER -> "number"
    PrimitiveType.BOOLEAN -> "boolean"
    PrimitiveType.NULL -> "null"
    PrimitiveType.ANY -> "any"
    PrimitiveType.ANY_NULLABLE -> "any?"
}

private fun renderContractWarnings(warnings: List<SemanticWarning>): String {
    if (warnings.isEmpty()) {
        return "Warnings: none"
    }
    val lines = mutableListOf("Warnings (contract mismatches):")
    warnings.forEach { warning ->
        val token = warning.token
        lines += "  - [${token.line}:${token.column}] ${warning.message}"
    }
    return lines.joinToString("\n")
}

private fun indentLines(lines: List<String>, indent: String): List<String> =
    lines.map { line -> "$indent$line" }

private fun handleCliError(error: CliError, format: ErrorFormat): Int {
    val payload = when (format) {
        ErrorFormat.TEXT -> error.message
        ErrorFormat.JSON -> formatJson(
            mapOf(
                "error" to mapOf(
                    "message" to error.message,
                    "kind" to error.kind.id,
                    "exitCode" to error.kind.exitCode.code,
                    "command" to error.command?.name?.lowercase(),
                ),
                "version" to CliVersion.CURRENT,
            ),
            pretty = false,
        )
    }
    printError(payload)
    return error.kind.exitCode.code
}

private fun renderTrace(tracer: CollectingTracer, format: TraceFormat): String {
    return when (format) {
        TraceFormat.TEXT -> tracer.humanize("Trace")
        TraceFormat.JSON -> {
            val report = TraceReport.from(tracer)
            formatJson(traceReportToMap(report), pretty = false)
        }
    }
}

private fun traceReportToMap(report: TraceReport.TraceReportData): Map<String, Any?> {
    val timelineSample = report.timelineSample.map { entry ->
        mapOf(
            "at" to entry.at.toString(),
            "kind" to entry.kind,
            "label" to entry.label,
        )
    }
    val hotspots = report.hotspots.map { hotspot ->
        mapOf(
            "kind" to hotspot.kind,
            "name" to hotspot.name,
            "calls" to hotspot.calls,
            "total" to hotspot.total.toString(),
            "mean" to hotspot.mean.toString(),
        )
    }
    val watches = report.watches.mapValues { (_, info) ->
        mapOf(
            "last" to info.last,
            "changes" to info.changes,
        )
    }
    val checkpoints = report.checkpoints.map { checkpoint ->
        mapOf(
            "at" to checkpoint.at.toString(),
            "label" to checkpoint.label,
        )
    }
    return mapOf(
        "totalEvents" to report.totalEvents,
        "duration" to report.duration.toString(),
        "timelineSample" to timelineSample,
        "hotspots" to hotspots,
        "watches" to watches,
        "checkpoints" to checkpoints,
        "explanations" to report.explanations,
        "instructions" to report.instructions,
    )
}
