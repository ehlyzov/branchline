package v2.vm

import v2.FuncDecl
import v2.Lexer
import v2.Parser
import v2.TransformDecl
import v2.ir.ToIR
import v2.std.StdLib
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

public fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Output path is required" }
    val outputPath = Path.of(args[0])
    val generatedAt = Instant.now().toString()
    val snapshots = listOf(
        snapshot("pathExpressions", VMTransformPrograms.pathExpressions),
        snapshot("arrayComprehension", VMTransformPrograms.arrayComprehension),
        snapshot("typicalTransform", VMTransformPrograms.typicalTransform),
    )
    Files.createDirectories(outputPath.parent)
    Files.writeString(outputPath, renderReport(generatedAt, snapshots))
}

private data class OpcodeSnapshot(
    val name: String,
    val instructions: Int,
    val histogram: Map<String, Int>,
)

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

private fun renderReport(generatedAt: String, snapshots: List<OpcodeSnapshot>): String {
    val builder = StringBuilder()
    builder.append("{\n")
    builder.append("  \"generatedAt\": \"").append(escapeJson(generatedAt)).append("\",\n")
    builder.append("  \"benchmarks\": [\n")
    snapshots.forEachIndexed { index, snap ->
        builder.append("    {\n")
        builder.append("      \"name\": \"").append(escapeJson(snap.name)).append("\",\n")
        builder.append("      \"instructions\": ").append(snap.instructions).append(",\n")
        builder.append("      \"histogram\": {\n")
        val entries = snap.histogram.entries.toList()
        entries.forEachIndexed { idx, entry ->
            builder.append("        \"").append(escapeJson(entry.key)).append("\": ").append(entry.value)
            if (idx < entries.lastIndex) builder.append(",")
            builder.append('\n')
        }
        builder.append("      }\n")
        builder.append("    }")
        if (index < snapshots.lastIndex) builder.append(',')
        builder.append('\n')
    }
    builder.append("  ]\n")
    builder.append("}\n")
    return builder.toString()
}

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
