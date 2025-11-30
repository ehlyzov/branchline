package io.branchline.cli

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

enum class CliCommand { RUN, COMPILE, EXECUTE }

object BranchlineCli {
    fun run(args: List<String>, platform: PlatformKind, defaultCommand: CliCommand? = null): Int {
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
        }
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
            val bytecode = runtime.compileBytecode(transform)
            val artifact = CompiledArtifact(
                version = 1,
                transform = transform.name,
                script = source,
                bytecode = BytecodeIO.serializeBytecode(bytecode),
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

    private data class ParsedArgs(
        val command: CliCommand,
        val run: RunOptions? = null,
        val compile: CompileOptions? = null,
        val exec: ExecOptions? = null,
    )

    private fun parseArgs(args: List<String>, defaultCommand: CliCommand?): ParsedArgs {
        val (command, startIndex) = determineCommand(args, defaultCommand)
        return when (command) {
            CliCommand.RUN -> ParsedArgs(command, run = parseRunArgs(args, startIndex))
            CliCommand.COMPILE -> ParsedArgs(command, compile = parseCompileArgs(args, startIndex))
            CliCommand.EXECUTE -> ParsedArgs(command, exec = parseExecArgs(args, startIndex))
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
