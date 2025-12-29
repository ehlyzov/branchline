package io.branchline.cli

import v2.ArrayTypeRef
import v2.EnumTypeRef
import v2.Lexer
import v2.NamedTypeRef
import v2.Parser
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.RecordTypeRef
import v2.TransformDecl
import v2.TransformSignature
import v2.TypeDecl
import v2.TypeRef
import v2.UnionTypeRef
import v2.contract.AccessPath
import v2.contract.AccessSegment
import v2.contract.DynamicAccess
import v2.contract.DynamicField
import v2.contract.FieldConstraint
import v2.contract.FieldShape
import v2.contract.SchemaGuarantee
import v2.contract.SchemaRequirement
import v2.contract.TransformContract
import v2.contract.TransformContractBuilder
import v2.contract.ValueShape
import v2.sema.SemanticAnalyzer
import v2.sema.SemanticWarning
import v2.sema.TypeResolver
import v2.std.StdLib
import v2.vm.BytecodeIO

enum class PlatformKind { JVM, JS }

enum class InputFormat { JSON, XML;
    companion object {
        fun parse(value: String): InputFormat = when (value.lowercase()) {
            "json" -> JSON
            "xml" -> XML
            else -> throw CliException("Unknown input format '$value'")
        }
    }
}

data class RunOptions(
    val scriptPath: String,
    val inputPath: String?,
    val inputFormat: InputFormat,
    val transformName: String?,
)

data class CompileOptions(
    val scriptPath: String,
    val outputPath: String?,
    val transformName: String?,
)

data class ExecOptions(
    val artifactPath: String,
    val inputPath: String?,
    val inputFormat: InputFormat,
    val transformOverride: String?,
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
    val nullabilityStyle: v2.schema.NullabilityStyle,
)

public enum class CliCommand { RUN, COMPILE, EXECUTE, INSPECT, SCHEMA }

object BranchlineCli {
    fun run(args: List<String>, platform: PlatformKind, defaultCommand: CliCommand? = null): Int {
        if (args.isEmpty()) {
            printHelp(defaultCommand)
            return 1
        }
        if (args.size == 1 && (args[0] == "--help" || args[0] == "-h" || args[0] == "help")) {
            printHelp(defaultCommand)
            return 0
        }

        val parseResult = try {
            parseArgs(args, defaultCommand)
        } catch (ex: CliException) {
            printError(ex.message ?: "CLI error")
            return 1
        }

        return when (val command = parseResult.command) {
            CliCommand.RUN -> executeRun(parseResult.run!!, platform)
            CliCommand.COMPILE -> executeCompile(parseResult.compile!!)
            CliCommand.EXECUTE -> executeExec(parseResult.exec!!)
            CliCommand.INSPECT -> executeInspect(parseResult.inspect!!)
            CliCommand.SCHEMA -> executeSchema(parseResult.schema!!)
        }
    }

    private fun printHelp(defaultCommand: CliCommand?) {
        val defaultHint = when (defaultCommand) {
            CliCommand.RUN -> " (default: run)"
            CliCommand.COMPILE -> " (default: compile)"
            CliCommand.EXECUTE -> " (default: exec)"
            CliCommand.SCHEMA -> " (default: schema)"
            null -> ""
        }
        println(
            """
            Branchline CLI$defaultHint
            
            Usage:
              bl [run] <script.bl> [--input <path>|-] [--input-format json|xml] [--transform <name>]
              blc <script.bl> [--output <artifact.blc>] [--transform <name>]
              blvm <artifact.blc> [--input <path>|-] [--input-format json|xml] [--transform <name>]
              bl inspect <script.bl> --contracts [--transform <name>]
              bl schema <script.bl> <TYPE_NAME> [--nullable] [--output <schema.json>]
              bl schema <script.bl> --all [--nullable] [--output <schema.json>]
              bl schema --import <schema.json> --name <TYPE_NAME> [--output <types.bl>]
            
            Commands:
              run       Compile + execute a Branchline script and print JSON output. Default when no subcommand is provided.
              compile   Compile a script to a bytecode artifact (.blc) that embeds the chosen transform.
              exec      Execute a compiled artifact through the VM. Use --transform to override the embedded transform.
              inspect   Show inferred and explicit contract information for transforms.
              schema    Emit JSON Schema for TYPE declarations or generate TYPE declarations from a JSON Schema file.
            
            Options:
              --input PATH        Read JSON/XML input from PATH; use '-' to read from stdin.
              --input-format FMT  'json' (default) or 'xml'.
              --transform NAME    Choose a TRANSFORM block by name; defaults to the first transform in the script.
              --output PATH       (compile) write the compiled artifact to PATH. Prints to stdout when omitted.
              --contracts         (inspect) print explicit vs inferred contracts with mismatch warnings.
              --all               (schema) emit a ${'$'}defs block with all TYPE declarations.
              --nullable          (schema) use 'nullable: true' instead of 'type: [\"null\", ...]'.
              --import PATH       (schema) read a JSON Schema document and emit TYPE declarations.
              --name NAME         (schema) name for the imported schema's TYPE declaration.
            
            Examples:
              bl examples/hello.bl --input fixtures/hello.json
              bl run pipeline.bl --transform Trace --input-format xml --input -
              blc scripts/order.bl --output build/order.blc
              blvm build/order.blc --input data.json
              bl inspect scripts/order.bl --contracts
              bl schema examples/types.bl Customer
              bl schema examples/types.bl --all --output build/types.schema.json
              bl schema --import schemas/customer.json --name Customer
            """.trimIndent(),
        )
    }

    private fun executeRun(options: RunOptions, platform: PlatformKind): Int {
        return try {
            val source = readTextFile(options.scriptPath)
            val runtime = BranchlineProgram(source)
            val transform = runtime.selectTransform(options.transformName)
            val input = loadInput(options.inputPath, options.inputFormat)
            val result = runtime.execute(transform, input)
            println(formatJson(result, pretty = true))
            0
        } catch (ex: CliException) {
            printError(ex.message ?: "CLI error")
            1
        } catch (ex: Exception) {
            printError(ex.message ?: ex.toString())
            1
        }
    }

    private fun executeCompile(options: CompileOptions): Int {
        return try {
            val source = readTextFile(options.scriptPath)
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
            val payload = ArtifactCodec.encode(artifact)
            if (options.outputPath != null) {
                writeTextFile(options.outputPath, payload)
            } else {
                println(payload)
            }
            0
        } catch (ex: CliException) {
            printError(ex.message ?: "CLI error")
            1
        } catch (ex: Exception) {
            printError(ex.message ?: ex.toString())
            1
        }
    }

    private fun executeExec(options: ExecOptions): Int {
        return try {
            val raw = readTextFile(options.artifactPath)
            val artifact = ArtifactCodec.decode(raw)
            val runtime = BranchlineProgram(artifact.script)
            val transformName = options.transformOverride ?: artifact.transform
            val transform = runtime.selectTransform(transformName)
            val bytecode = BytecodeIO.deserializeBytecode(artifact.bytecode)
            val vmExec = runtime.prepareVmExec(transform, bytecode)
            val input = loadInput(options.inputPath, options.inputFormat)
            val env = HashMap<String, Any?>(input.size + 1).apply {
                this[v2.DEFAULT_INPUT_ALIAS] = input
                putAll(input)
                for (alias in v2.COMPAT_INPUT_ALIASES) {
                    this[alias] = input
                }
            }
            val result = vmExec.run(env as MutableMap<String, Any?>, stringifyKeys = true)
            println(formatJson(result, pretty = true))
            0
        } catch (ex: CliException) {
            printError(ex.message ?: "CLI error")
            1
        } catch (ex: Exception) {
            printError(ex.message ?: ex.toString())
            1
        }
    }

    private fun executeInspect(options: InspectOptions): Int {
        return try {
            if (!options.showContracts) {
                throw CliException("Inspect requires --contracts")
            }
            val source = readTextFile(options.scriptPath)
            val tokens = Lexer(source).lex()
            val program = Parser(tokens, source).parse()
            val hostFns = StdLib.fns
            val analyzer = SemanticAnalyzer(hostFns.keys)
            analyzer.analyze(program)
            val transforms = program.decls.filterIsInstance<TransformDecl>()
            if (transforms.isEmpty()) {
                throw CliException("Program must declare at least one TRANSFORM block")
            }
            val typeDecls = program.decls.filterIsInstance<TypeDecl>()
            val contractBuilder = TransformContractBuilder(TypeResolver(typeDecls), hostFns.keys)
            val selected = selectTransforms(transforms, options.transformName)
            val report = renderContractInspection(selected, contractBuilder, analyzer.warnings)
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

    private fun executeSchema(options: SchemaOptions): Int {
        return try {
            if (options.importPath != null) {
                val rawSchema = readTextFile(options.importPath)
                val declarations = JsonSchemaCliInterop.importSchema(
                    rawSchema = rawSchema,
                    importName = options.importName!!,
                    options = options,
                )
                val output = declarations.joinToString("\n\n") { renderTypeDecl(it) }
                if (options.outputPath != null) {
                    writeTextFile(options.outputPath, output)
                } else {
                    println(output)
                }
            } else {
                val scriptPath = options.scriptPath
                    ?: throw CliException("Script path is required for schema export")
                val source = readTextFile(scriptPath)
                val runtime = BranchlineProgram(source)
                val typeDecls = runtime.typeDecls()
                val outputSchema = JsonSchemaCliInterop.exportSchema(
                    typeDecls = typeDecls,
                    typeName = options.typeName,
                    options = options,
                )
                if (options.outputPath != null) {
                    writeTextFile(options.outputPath, outputSchema)
                } else {
                    println(outputSchema)
                }
            }
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
    )

    private fun parseArgs(args: List<String>, defaultCommand: CliCommand?): ParsedArgs {
        val (command, startIndex) = determineCommand(args, defaultCommand)
        return when (command) {
            CliCommand.RUN -> ParsedArgs(command, run = parseRunArgs(args, startIndex))
            CliCommand.COMPILE -> ParsedArgs(command, compile = parseCompileArgs(args, startIndex))
            CliCommand.EXECUTE -> ParsedArgs(command, exec = parseExecArgs(args, startIndex))
            CliCommand.INSPECT -> ParsedArgs(command, inspect = parseInspectArgs(args, startIndex))
            CliCommand.SCHEMA -> ParsedArgs(command, schema = parseSchemaArgs(args, startIndex))
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
            else -> (defaultCommand ?: CliCommand.RUN) to 0
        }
    }

    private fun parseRunArgs(args: List<String>, startIndex: Int): RunOptions {
        var script: String? = null
        var input: String? = null
        var transform: String? = null
        var format = InputFormat.JSON
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
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null -> {
                    script = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (script == null) throw CliException("Script path is required")
        return RunOptions(script, input, format, transform)
    }

    private fun parseCompileArgs(args: List<String>, startIndex: Int): CompileOptions {
        var script: String? = null
        var output: String? = null
        var transform: String? = null
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
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                script == null -> {
                    script = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (script == null) throw CliException("Script path is required")
        return CompileOptions(script, output, transform)
    }

    private fun parseExecArgs(args: List<String>, startIndex: Int): ExecOptions {
        var artifact: String? = null
        var input: String? = null
        var format = InputFormat.JSON
        var transform: String? = null
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
                token.startsWith("--") -> throw CliException("Unknown option '$token'")
                artifact == null -> {
                    artifact = token
                    idx += 1
                }
                else -> throw CliException("Unexpected argument '$token'")
            }
        }
        if (artifact == null) throw CliException("Artifact path is required")
        return ExecOptions(artifact, input, format, transform)
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
        var nullabilityStyle = v2.schema.NullabilityStyle.TYPE_UNION
        var idx = startIndex
        while (idx < args.size) {
            val token = args[idx]
            when {
                token == "--all" -> {
                    allTypes = true
                    idx += 1
                }
                token == "--nullable" -> {
                    nullabilityStyle = v2.schema.NullabilityStyle.NULLABLE_KEYWORD
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
}

private fun loadInput(path: String?, format: InputFormat): Map<String, Any?> {
    val text = when {
        path == null -> ""
        path == "-" -> readStdin()
        else -> readTextFile(path)
    }
    if (text.isBlank()) return emptyMap()
    return when (format) {
        InputFormat.JSON -> parseJsonInput(text)
        InputFormat.XML -> parseXmlInput(text)
    }
}

private fun selectTransforms(
    transforms: List<TransformDecl>,
    name: String?,
): List<TransformDecl> {
    if (name == null) return transforms
    val matches = transforms.filter { it.name == name }
    if (matches.isEmpty()) {
        throw CliException("Transform '$name' not found")
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
