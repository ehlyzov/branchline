package v2.vm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import v2.FuncDecl
import v2.Lexer
import v2.Parser
import v2.TransformDecl
import v2.ir.ToIR
import v2.std.StdLib
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Serializable
private data class OpcodeSnapshot(
    val name: String,
    val instructions: Int,
    val histogram: Map<String, Int>,
)

@Serializable
private data class OpcodeReport(
    val generatedAt: String,
    @SerialName("benchmarks")
    val snapshots: List<OpcodeSnapshot>,
)

public fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Output path is required" }
    val outputPath = Path.of(args[0])
    val report = OpcodeReport(
        generatedAt = Instant.now().toString(),
        snapshots = listOf(
            snapshot("pathExpressions", VMTransformPrograms.pathExpressions),
            snapshot("arrayComprehension", VMTransformPrograms.arrayComprehension),
            snapshot("typicalTransform", VMTransformPrograms.typicalTransform),
        ),
    )
    val json = Json { prettyPrint = true }.encodeToString(report)
    Files.createDirectories(outputPath.parent)
    Files.writeString(outputPath, json)
}

private fun snapshot(name: String, program: String): OpcodeSnapshot {
    val hostFns = StdLib.fns
    val funcs: Map<String, FuncDecl> = emptyMap()
    val tokens = Lexer(program).lex()
    val parsed = Parser(tokens, program).parse()
    val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
    val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
    val bytecode = Compiler(funcs, hostFns).compile(ir)
    val histogram = buildHistogram(bytecode)
    return OpcodeSnapshot(
        name = name,
        instructions = bytecode.size(),
        histogram = histogram,
    )
}

private fun buildHistogram(bytecode: Bytecode): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    val opcodes = Opcode.values()
    for (pc in 0 until bytecode.size()) {
        val opcode = opcodes[bytecode.getOpcode(pc)]
        val key = opcode.name
        val count = counts[key] ?: 0
        counts[key] = count + 1
    }
    return counts
}
