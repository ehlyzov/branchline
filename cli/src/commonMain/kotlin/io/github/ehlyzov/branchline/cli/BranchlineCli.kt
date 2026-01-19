package io.github.ehlyzov.branchline.cli

import io.github.ehlyzov.branchline.ArrayTypeRef
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
import io.github.ehlyzov.branchline.ir.RuntimeErrorWithContext
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
    val outputPath: String?,
    val outputRaw: Boolean,
    val outputFile: String?,
    val outputLines: String?,
    val writeOutput: Boolean,
    val writeOutputDir: String?,
    val writeOutputFiles: Map<String, String>,
    val sharedOptions: SharedOptions,
    val jobs: Int,
    val summaryTransform: String?,
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
    val outputPath: String?,
    val outputRaw: Boolean,
    val outputFile: String?,
    val outputLines: String?,
    val writeOutput: Boolean,
    val writeOutputDir: String?,
    val writeOutputFiles: Map<String, String>,
    val sharedOptions: SharedOptions,
    val jobs: Int,
    val summaryTransform: String?,
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
                CliError(formatRuntimeException(ex), CliErrorKind.RUNTIME, parseResult.command),
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
              bl [run] <script.bl> [--input <path>|-] [--input-format json|xml] [--transform <name>] [--output-format json|json-compact]
                  [--output-path <path>] [--output-raw] [--output-file <path>] [--output-lines <path>] [--write-output]
                  [--write-output-dir <dir>] [--write-output-file <name>=<path>]
                  [--shared-file <resource>=<path>] [--shared-dir <resource>=<dir>] [--shared-glob <resource>=<glob>]
                  [--shared-format json|xml|text] [--shared-key relative|basename|custom]
                  [--jobs <n>] [--summary-transform <name>] [--trace] [--trace-format text|json]
              blc <script.bl> [--output <artifact.blc>] [--transform <name>] [--output-format json|json-compact]
              blvm <artifact.blc> [--input <path>|-] [--input-format json|xml] [--transform <name>] [--output-format json|json-compact]
                  [--output-path <path>] [--output-raw] [--output-file <path>] [--output-lines <path>] [--write-output]
                  [--write-output-dir <dir>] [--write-output-file <name>=<path>]
                  [--shared-file <resource>=<path>] [--shared-dir <resource>=<dir>] [--shared-glob <resource>=<glob>]
                  [--shared-format json|xml|text] [--shared-key relative|basename|custom]
                  [--jobs <n>] [--summary-transform <name>] [--trace] [--trace-format text|json]
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
              --output-path PATH Select a field from the JSON output (e.g., 'summary.status' or 'files[0].name').
              --output-raw      Print selected output without JSON encoding (useful for plain strings).
              --output-file PATH Write the selected output to a file (directory defaults to output.json).
              --output-lines PATH Write output as JSON Lines (directory defaults to output.jsonl).
              --write-output    Write output files from OUTPUT.files entries.
              --write-output-dir DIR Write OUTPUT.files entries to DIR using their names.
              --write-output-file MAP Map OUTPUT.files entries by name (name=path). Repeatable.
              --shared-file MAP Map a local file into a SHARED resource (resource=path).
              --shared-dir MAP  Map files under a directory into a SHARED resource (resource=dir).
              --shared-glob MAP Map glob-matched files into a SHARED resource (resource=glob).
              --shared-format FMT Parse shared files as json|xml|text before storing.
              --shared-key MODE Key naming: relative (default), basename, or custom (resource=key=path).
              --jobs N          Fan out transforms across shared inputs with N parallel jobs.
              --summary-transform NAME Run a summary transform after fan-out, fed { reports, manifest }.
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
        return withSharedStore { store ->
            val source = readTextFileOrThrow(options.scriptPath)
            val runtime = BranchlineProgram(source, traceSession?.tracer)
            val transform = runtime.selectTransform(options.transformName)
            val input = loadInput(options.inputPath, options.inputFormat)
            val sharedSeed = seedSharedStore(options.sharedOptions, runtime.sharedDecls(), store)
            val summaryTransform = options.summaryTransform?.let { runtime.selectTransform(it) }
            val result = runWithFanout(
                jobs = options.jobs,
                sharedSeed = sharedSeed,
                baseInput = input,
                summaryTransform = summaryTransform,
                runTransform = { data -> runtime.execute(transform, data) },
                runSummary = { data ->
                    summaryTransform?.let { runtime.execute(it, data) }
                },
            ) ?: runtime.execute(transform, input)
            emitOutput(
                result = result,
                format = options.outputFormat,
                outputPath = options.outputPath,
                outputRaw = options.outputRaw,
                outputFile = options.outputFile,
                outputLines = options.outputLines,
                writeOutput = options.writeOutput,
                writeOutputDir = options.writeOutputDir,
                writeOutputMap = options.writeOutputFiles,
            )
            traceSession?.emit()
            ExitCode.SUCCESS.code
        }
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
        return withSharedStore { store ->
            val raw = readTextFileOrThrow(options.artifactPath)
            val artifact = ArtifactCodec.decode(raw)
            val runtime = BranchlineProgram(artifact.script, traceSession?.tracer)
            val transformName = options.transformOverride ?: artifact.transform
            val transform = runtime.selectTransform(transformName)
            val bytecode = BytecodeIO.deserializeBytecode(artifact.bytecode)
            val vmExec = runtime.prepareVmExec(transform, bytecode)
            val input = loadInput(options.inputPath, options.inputFormat)
            val sharedSeed = seedSharedStore(options.sharedOptions, runtime.sharedDecls(), store)
            val summaryTransform = options.summaryTransform?.let { runtime.selectTransform(it) }
            val result = runWithFanout(
                jobs = options.jobs,
                sharedSeed = sharedSeed,
                baseInput = input,
                summaryTransform = summaryTransform,
                runTransform = { data ->
                    val env = runtime.buildEnv(data)
                    vmExec.run(env, stringifyKeys = true)
                },
                runSummary = { data ->
                    summaryTransform?.let { runtime.execute(it, data) }
                },
            ) ?: run {
                val env = runtime.buildEnv(input)
                vmExec.run(env, stringifyKeys = true)
            }
            emitOutput(
                result = result,
                format = options.outputFormat,
                outputPath = options.outputPath,
                outputRaw = options.outputRaw,
                outputFile = options.outputFile,
                outputLines = options.outputLines,
                writeOutput = options.writeOutput,
                writeOutputDir = options.writeOutputDir,
                writeOutputMap = options.writeOutputFiles,
            )
            traceSession?.emit()
            ExitCode.SUCCESS.code
        }
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
        var outputPath: String? = null
        var outputRaw = false
        var outputFile: String? = null
        var outputLines: String? = null
        var writeOutput = false
        var writeOutputDir: String? = null
        val writeOutputFiles = linkedMapOf<String, String>()
        val sharedInputs = mutableListOf<SharedInputSpec>()
        var sharedFormat = SharedInputFormat.TEXT
        var sharedKeyMode = SharedKeyMode.RELATIVE
        var jobs = 0
        var summaryTransform: String? = null
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
                token == "--output-path" && idx + 1 < args.size -> {
                    outputPath = args[idx + 1]
                    idx += 2
                }
                token == "--output-raw" -> {
                    outputRaw = true
                    idx += 1
                }
                token == "--output-file" && idx + 1 < args.size -> {
                    outputFile = args[idx + 1]
                    idx += 2
                }
                token == "--output-lines" && idx + 1 < args.size -> {
                    outputLines = args[idx + 1]
                    idx += 2
                }
                token == "--write-output" -> {
                    writeOutput = true
                    idx += 1
                }
                token == "--write-output-dir" && idx + 1 < args.size -> {
                    writeOutputDir = args[idx + 1]
                    idx += 2
                }
                token == "--write-output-file" && idx + 1 < args.size -> {
                    val (name, path) = parseWriteOutputFileSpec(args[idx + 1])
                    if (writeOutputFiles.containsKey(name)) {
                        throw CliException("Duplicate write-output file mapping for '$name'", kind = CliErrorKind.USAGE)
                    }
                    writeOutputFiles[name] = path
                    idx += 2
                }
                token == "--shared-file" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.FILE, args[idx + 1])
                    idx += 2
                }
                token == "--shared-dir" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.DIR, args[idx + 1])
                    idx += 2
                }
                token == "--shared-glob" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.GLOB, args[idx + 1])
                    idx += 2
                }
                token == "--shared-format" && idx + 1 < args.size -> {
                    sharedFormat = SharedInputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--shared-key" && idx + 1 < args.size -> {
                    sharedKeyMode = SharedKeyMode.parse(args[idx + 1])
                    idx += 2
                }
                token == "--jobs" && idx + 1 < args.size -> {
                    jobs = parseJobs(args[idx + 1])
                    idx += 2
                }
                token == "--summary-transform" && idx + 1 < args.size -> {
                    summaryTransform = args[idx + 1]
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
                token == "--output-path" -> throw CliException(
                    "--output-path requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--output-file" -> throw CliException(
                    "--output-file requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--output-lines" -> throw CliException(
                    "--output-lines requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--write-output-dir" -> throw CliException(
                    "--write-output-dir requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--write-output-file" -> throw CliException(
                    "--write-output-file requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-file" || token == "--shared-dir" || token == "--shared-glob" -> throw CliException(
                    "$token requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-format" -> throw CliException(
                    "--shared-format requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-key" -> throw CliException(
                    "--shared-key requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--jobs" -> throw CliException(
                    "--jobs requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--summary-transform" -> throw CliException(
                    "--summary-transform requires a value",
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
        if (summaryTransform != null && jobs == 0) {
            jobs = 1
        }
        if (writeOutputDir != null || writeOutputFiles.isNotEmpty()) {
            writeOutput = true
        }
        if (writeOutput && writeOutputDir == null && writeOutputFiles.isEmpty()) {
            throw CliException("write-output requires --write-output-dir or --write-output-file", kind = CliErrorKind.USAGE)
        }
        val sharedOptions = SharedOptions(sharedInputs, sharedFormat, sharedKeyMode)
        validateSharedSpecs(sharedOptions)
        return RunOptions(
            scriptPath = script,
            inputPath = input,
            inputFormat = format,
            transformName = transform,
            outputFormat = outputFormat,
            outputPath = outputPath,
            outputRaw = outputRaw,
            outputFile = outputFile,
            outputLines = outputLines,
            writeOutput = writeOutput,
            writeOutputDir = writeOutputDir,
            writeOutputFiles = writeOutputFiles,
            sharedOptions = sharedOptions,
            jobs = jobs,
            summaryTransform = summaryTransform,
            traceFormat = traceFormat,
        )
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
        var outputPath: String? = null
        var outputRaw = false
        var outputFile: String? = null
        var outputLines: String? = null
        var writeOutput = false
        var writeOutputDir: String? = null
        val writeOutputFiles = linkedMapOf<String, String>()
        val sharedInputs = mutableListOf<SharedInputSpec>()
        var sharedFormat = SharedInputFormat.TEXT
        var sharedKeyMode = SharedKeyMode.RELATIVE
        var jobs = 0
        var summaryTransform: String? = null
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
                token == "--output-path" && idx + 1 < args.size -> {
                    outputPath = args[idx + 1]
                    idx += 2
                }
                token == "--output-raw" -> {
                    outputRaw = true
                    idx += 1
                }
                token == "--output-file" && idx + 1 < args.size -> {
                    outputFile = args[idx + 1]
                    idx += 2
                }
                token == "--output-lines" && idx + 1 < args.size -> {
                    outputLines = args[idx + 1]
                    idx += 2
                }
                token == "--write-output" -> {
                    writeOutput = true
                    idx += 1
                }
                token == "--write-output-dir" && idx + 1 < args.size -> {
                    writeOutputDir = args[idx + 1]
                    idx += 2
                }
                token == "--write-output-file" && idx + 1 < args.size -> {
                    val (name, path) = parseWriteOutputFileSpec(args[idx + 1])
                    if (writeOutputFiles.containsKey(name)) {
                        throw CliException("Duplicate write-output file mapping for '$name'", kind = CliErrorKind.USAGE)
                    }
                    writeOutputFiles[name] = path
                    idx += 2
                }
                token == "--shared-file" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.FILE, args[idx + 1])
                    idx += 2
                }
                token == "--shared-dir" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.DIR, args[idx + 1])
                    idx += 2
                }
                token == "--shared-glob" && idx + 1 < args.size -> {
                    sharedInputs += parseSharedSpec(SharedInputKind.GLOB, args[idx + 1])
                    idx += 2
                }
                token == "--shared-format" && idx + 1 < args.size -> {
                    sharedFormat = SharedInputFormat.parse(args[idx + 1])
                    idx += 2
                }
                token == "--shared-key" && idx + 1 < args.size -> {
                    sharedKeyMode = SharedKeyMode.parse(args[idx + 1])
                    idx += 2
                }
                token == "--jobs" && idx + 1 < args.size -> {
                    jobs = parseJobs(args[idx + 1])
                    idx += 2
                }
                token == "--summary-transform" && idx + 1 < args.size -> {
                    summaryTransform = args[idx + 1]
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
                token == "--output-path" -> throw CliException(
                    "--output-path requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--output-file" -> throw CliException(
                    "--output-file requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--output-lines" -> throw CliException(
                    "--output-lines requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--write-output-dir" -> throw CliException(
                    "--write-output-dir requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--write-output-file" -> throw CliException(
                    "--write-output-file requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-file" || token == "--shared-dir" || token == "--shared-glob" -> throw CliException(
                    "$token requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-format" -> throw CliException(
                    "--shared-format requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--shared-key" -> throw CliException(
                    "--shared-key requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--jobs" -> throw CliException(
                    "--jobs requires a value",
                    kind = CliErrorKind.USAGE,
                )
                token == "--summary-transform" -> throw CliException(
                    "--summary-transform requires a value",
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
        if (summaryTransform != null && jobs == 0) {
            jobs = 1
        }
        if (writeOutputDir != null || writeOutputFiles.isNotEmpty()) {
            writeOutput = true
        }
        if (writeOutput && writeOutputDir == null && writeOutputFiles.isEmpty()) {
            throw CliException("write-output requires --write-output-dir or --write-output-file", kind = CliErrorKind.USAGE)
        }
        val sharedOptions = SharedOptions(sharedInputs, sharedFormat, sharedKeyMode)
        validateSharedSpecs(sharedOptions)
        return ExecOptions(
            artifactPath = artifact,
            inputPath = input,
            inputFormat = format,
            transformOverride = transform,
            outputFormat = outputFormat,
            outputPath = outputPath,
            outputRaw = outputRaw,
            outputFile = outputFile,
            outputLines = outputLines,
            writeOutput = writeOutput,
            writeOutputDir = writeOutputDir,
            writeOutputFiles = writeOutputFiles,
            sharedOptions = sharedOptions,
            jobs = jobs,
            summaryTransform = summaryTransform,
            traceFormat = traceFormat,
        )
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

private fun <T> withSharedStore(block: (io.github.ehlyzov.branchline.std.SharedStore) -> T): T {
    val previous = io.github.ehlyzov.branchline.std.SharedStoreProvider.store
    val store = io.github.ehlyzov.branchline.std.createDefaultSharedStore()
    io.github.ehlyzov.branchline.std.SharedStoreProvider.store = store
    return try {
        block(store)
    } finally {
        io.github.ehlyzov.branchline.std.SharedStoreProvider.store = previous
    }
}

private fun runWithFanout(
    jobs: Int,
    sharedSeed: SharedSeedResult,
    baseInput: Map<String, Any?>,
    summaryTransform: TransformDecl?,
    runTransform: (Map<String, Any?>) -> Any?,
    runSummary: (Map<String, Any?>) -> Any?,
): Any? {
    if (jobs <= 0) return null
    if (sharedSeed.entries.isEmpty()) {
        throw CliException("Fan-out requires shared inputs", kind = CliErrorKind.INPUT)
    }
    val resources = sharedSeed.entries.map { it.resource }.distinct()
    if (resources.size > 1) {
        throw CliException("Fan-out supports one shared resource at a time", kind = CliErrorKind.USAGE)
    }
    val resource = resources.first()
    val entries = sharedSeed.entriesFor(resource)
    val parallelism = jobs.coerceAtLeast(1)
    val reports = parallelMap(parallelism, entries) { entry ->
        val input = HashMap<String, Any?>(baseInput.size + 2).apply {
            putAll(baseInput)
            this["key"] = entry.key
            this["resource"] = entry.resource
        }
        val result = runTransform(input)
        buildFanoutEntry(entry, result)
    }
    if (summaryTransform == null) return reports
    val summaryInput = HashMap<String, Any?>(baseInput.size + 2).apply {
        putAll(baseInput)
        this["reports"] = reports
        this["manifest"] = mapOf(
            "resource" to resource,
            "keys" to entries.map { it.key },
        )
    }
    return runSummary.invoke(summaryInput) ?: reports
}

private fun buildFanoutEntry(entry: SharedEntry, result: Any?): Map<String, Any?> {
    val payload = LinkedHashMap<String, Any?>()
    payload["path"] = entry.key
    payload["resource"] = entry.resource
    if (result is Map<*, *>) {
        for ((k, v) in result) {
            payload[k?.toString() ?: "null"] = v
        }
    } else {
        payload["value"] = result
    }
    return payload
}

private fun emitOutput(
    result: Any?,
    format: OutputFormat,
    outputPath: String?,
    outputRaw: Boolean,
    outputFile: String?,
    outputLines: String?,
    writeOutput: Boolean,
    writeOutputDir: String?,
    writeOutputMap: Map<String, String>,
) {
    if (writeOutput) {
        writeOutputFiles(result, writeOutputDir, writeOutputMap)
    }
    val selected = if (outputPath == null) result else selectOutputByPath(result, outputPath)
    val output = formatOutputValue(selected, format.pretty, outputRaw)
    println(output)
    if (outputFile != null) {
        writeOutputFile(selected, format, outputRaw, outputFile)
    }
    if (outputLines != null) {
        writeOutputLines(selected, outputRaw, outputLines)
    }
}

private fun writeOutputFiles(
    result: Any?,
    outputDir: String?,
    outputFiles: Map<String, String>,
) {
    val output = result as? Map<*, *> ?: throw CliException(
        "write-output expects OUTPUT to be an object",
        kind = CliErrorKind.RUNTIME,
    )
    val files = output.entries.firstOrNull { it.key?.toString() == "files" }?.value ?: throw CliException(
        "write-output expects OUTPUT.files",
        kind = CliErrorKind.RUNTIME,
    )
    val specs = parseOutputFileSpecs(files)
    val seen = mutableSetOf<String>()
    for (spec in specs) {
        if (!seen.add(spec.name)) {
            throw CliException("write-output expects unique file names ('${spec.name}')", kind = CliErrorKind.RUNTIME)
        }
        val target = resolveWriteOutputTarget(spec.name, outputDir, outputFiles)
        writeTextFileOrThrow(target, spec.contents)
    }
}

private fun writeOutputFile(value: Any?, format: OutputFormat, outputRaw: Boolean, path: String) {
    val target = resolveOutputTarget(path, defaultName = "output.json")
    val payload = formatOutputValue(value, format.pretty, outputRaw)
    writeTextFileOrThrow(target, payload + "\n")
}

private fun writeOutputLines(value: Any?, outputRaw: Boolean, path: String) {
    val target = resolveOutputTarget(path, defaultName = "output.jsonl")
    val items = if (value is List<*>) value else listOf(value)
    val lines = items.joinToString("\n") { formatOutputValue(it, pretty = false, raw = outputRaw) }
    val content = if (lines.isBlank()) "" else lines + "\n"
    writeTextFileOrThrow(target, content)
}

private fun resolveOutputTarget(path: String, defaultName: String): String {
    val trimmed = path.trim()
    if (trimmed.isBlank()) {
        throw CliException("Output path must not be blank", kind = CliErrorKind.USAGE)
    }
    val isDirHint = trimmed.endsWith("/") || trimmed.endsWith("\\")
    val base = trimmed.trimEnd('/', '\\')
    return when {
        isDirHint -> if (base.isBlank()) defaultName else "$base/$defaultName"
        isDirectory(trimmed) -> "$trimmed/$defaultName"
        else -> trimmed
    }
}

private data class OutputFileSpec(
    val name: String,
    val contents: String,
)

private fun parseOutputFileSpecs(files: Any?): List<OutputFileSpec> = when (files) {
    is Map<*, *> -> {
        files.map { (key, value) ->
            val name = key?.toString()?.trim()
                ?: throw CliException("write-output expects files{} keys", kind = CliErrorKind.RUNTIME)
            if (name.isBlank()) {
                throw CliException("write-output expects files{} keys", kind = CliErrorKind.RUNTIME)
            }
            OutputFileSpec(name, value?.toString() ?: "")
        }
    }
    is List<*> -> {
        files.map { entry ->
            val spec = entry as? Map<*, *> ?: throw CliException(
                "write-output expects files[] objects",
                kind = CliErrorKind.RUNTIME,
            )
            val name = spec["name"]?.toString()?.trim()
                ?: spec["id"]?.toString()?.trim()
                ?: throw CliException("write-output expects files[].name", kind = CliErrorKind.RUNTIME)
            if (name.isBlank()) {
                throw CliException("write-output expects files[].name", kind = CliErrorKind.RUNTIME)
            }
            val contents = spec["contents"]?.toString() ?: ""
            OutputFileSpec(name, contents)
        }
    }
    else -> throw CliException(
        "write-output expects files[] or files{}",
        kind = CliErrorKind.RUNTIME,
    )
}

private fun resolveWriteOutputTarget(
    name: String,
    outputDir: String?,
    outputFiles: Map<String, String>,
): String {
    val mapped = outputFiles[name]
    if (mapped != null) {
        return resolveOutputTarget(mapped, defaultName = name)
    }
    if (outputDir != null) {
        return resolveWriteOutputDirTarget(outputDir, name)
    }
    throw CliException(
        "write-output requires --write-output-dir or --write-output-file for '$name'",
        kind = CliErrorKind.USAGE,
    )
}

private fun resolveWriteOutputDirTarget(outputDir: String, name: String): String {
    val base = outputDir.trim()
    if (base.isBlank()) {
        throw CliException("write-output-dir must not be blank", kind = CliErrorKind.USAGE)
    }
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) {
        throw CliException("write-output expects non-empty file names", kind = CliErrorKind.RUNTIME)
    }
    if (isAbsolutePath(trimmedName) || containsParentSegment(trimmedName)) {
        throw CliException("write-output-dir rejects absolute or parent paths: '$name'", kind = CliErrorKind.RUNTIME)
    }
    val separator = if (base.endsWith("/") || base.endsWith("\\")) "" else "/"
    return base + separator + trimmedName
}

private fun isAbsolutePath(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    if (normalized.startsWith("/")) {
        return true
    }
    if (normalized.length >= 2 && normalized[1] == ':') {
        return true
    }
    return false
}

private fun containsParentSegment(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    return normalized.split('/').any { it == ".." }
}

private fun parseWriteOutputFileSpec(raw: String): Pair<String, String> {
    val separator = raw.indexOf('=')
    if (separator == -1) {
        throw CliException("write-output-file expects name=path", kind = CliErrorKind.USAGE)
    }
    val name = raw.substring(0, separator).trim()
    val path = raw.substring(separator + 1).trim()
    if (name.isBlank() || path.isBlank()) {
        throw CliException("write-output-file expects name=path", kind = CliErrorKind.USAGE)
    }
    return name to path
}

private fun parseSharedSpec(kind: SharedInputKind, raw: String): SharedInputSpec {
    val first = raw.indexOf('=')
    if (first == -1) throw CliException("Shared mapping must be resource=path", kind = CliErrorKind.USAGE)
    val second = raw.indexOf('=', startIndex = first + 1)
    return if (second == -1) {
        SharedInputSpec(kind, raw.substring(0, first), raw.substring(first + 1))
    } else {
        SharedInputSpec(
            kind,
            raw.substring(0, first),
            raw.substring(second + 1),
            raw.substring(first + 1, second),
        )
    }
}

private fun validateSharedSpecs(sharedOptions: SharedOptions) {
    if (sharedOptions.keyMode != SharedKeyMode.CUSTOM) return
    val missing = sharedOptions.inputs.firstOrNull { it.customKey.isNullOrBlank() }
    if (missing != null) {
        throw CliException(
            "Shared key mode custom requires '<resource>=<key>=<path>'",
            kind = CliErrorKind.USAGE,
        )
    }
}

private fun parseJobs(raw: String): Int {
    val value = raw.toIntOrNull() ?: throw CliException("Invalid jobs value '$raw'", kind = CliErrorKind.USAGE)
    if (value < 1) {
        throw CliException("jobs must be >= 1", kind = CliErrorKind.USAGE)
    }
    return value
}

internal fun readTextFileOrThrow(path: String): String {
    return try {
        readTextFile(path)
    } catch (ex: Exception) {
        throw CliException("Failed to read file '$path': ${ex.message ?: ex}", kind = CliErrorKind.IO)
    }
}

internal fun writeTextFileOrThrow(path: String, contents: String) {
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

private fun formatRuntimeException(ex: Exception): String {
    val base = ex.message ?: ex.toString()
    val ctx = ex as? RuntimeErrorWithContext ?: return base
    val snippet = ctx.snippet
    return buildString {
        append(base)
        appendLine()
        append("at [").append(ctx.token.line).append(":").append(ctx.token.column).append("]")
        if (!snippet.isNullOrBlank()) {
            appendLine()
            append(snippet)
        }
    }
}

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
