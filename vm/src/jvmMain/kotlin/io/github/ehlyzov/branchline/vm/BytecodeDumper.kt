package io.github.ehlyzov.branchline.vm

import java.time.Instant
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.ObjKey
import java.nio.file.Files
import java.nio.file.Path

/**
 * Utility to format and persist VM bytecode for inspection.
 */
object BytecodeDumper {
    /**
     * Pretty-print bytecode using reconstructed instructions. If [includeOpcodes] is true,
     * also append a raw opcodes view for the full program.
     */
    fun format(bytecode: Bytecode, includeOpcodes: Boolean = false): String {
        val sb = StringBuilder()
        sb.appendLine("; Bytecode dump @ ${Instant.now()}")
        sb.appendLine("; instructions=${bytecode.size()}")
        // small histogram by opcode simple name
        val counts = LinkedHashMap<String, Int>()
        for (i in 0 until bytecode.size()) {
            val ins = bytecode.getInstruction(i)
            val k = when (ins) {
                is Instruction.LOAD_LOCAL -> "LOAD_LOCAL"
                is Instruction.STORE_LOCAL -> "STORE_LOCAL"
                else -> ins::class.simpleName ?: "?"
            }
            counts[k] = (counts[k] ?: 0) + 1
        }
        sb.appendLine("; histogram:")
        counts.entries.sortedByDescending { it.value }.forEach { (k, v) ->
            sb.append(";  ").append(k).append(": ").append(v).append('\n')
        }
        sb.appendLine()
        for (i in 0 until bytecode.size()) {
            val ins = bytecode.getInstruction(i)
            val rendered = renderInstruction(ins)
            // Allow multi-line renderings (e.g., LAMBDA pretty body), indent subsequent lines
            val lines = rendered.split('\n')
            if (lines.size == 1) {
                sb.append(i.toString().padStart(5)).append(": ").append(rendered).append('\n')
            } else {
                sb.append(i.toString().padStart(5)).append(": ").append(lines.first()).append('\n')
                for (k in 1 until lines.size) {
                    sb.append("      ").append(lines[k]).append('\n')
                }
            }
        }

        if (includeOpcodes) {
            sb.appendLine().appendLine("; --- Program (binary) ---")
            val ops = Opcode.values()
            // .opcodes section (ordinal with mnemonic comment)
            sb.appendLine(".section opcodes (${bytecode.size()})")
            for (i in 0 until bytecode.size()) {
                val op = ops[bytecode.getOpcode(i)]
                sb.append(i.toString().padStart(5))
                    .append(": ")
                    .append(op.ordinal)
                    .append(" ; ")
                    .append(op.name)
                    .append('\n')
            }

            // .int_operands and offsets
            if (bytecode.intOperands.isNotEmpty()) {
                sb.appendLine().appendLine(".section int_operands (${bytecode.intOperands.size})")
                sb.append("  [").append(bytecode.intOperands.joinToString()).appendLine("]")
            }
            if (bytecode.intOperandOffsets.isNotEmpty()) {
                sb.appendLine(".section int_offsets (${bytecode.intOperandOffsets.size})")
                sb.append("  [").append(bytecode.intOperandOffsets.joinToString()).appendLine("]")
            }

            // .string_operands and offsets
            if (bytecode.stringOperands.isNotEmpty()) {
                sb.appendLine().appendLine(".section string_operands (${bytecode.stringOperands.size})")
                sb.append("  [").append(bytecode.stringOperands.joinToString { '"' + it + '"' }).appendLine("]")
            }
            if (bytecode.stringOperandOffsets.isNotEmpty()) {
                sb.appendLine(".section string_offsets (${bytecode.stringOperandOffsets.size})")
                sb.append("  [").append(bytecode.stringOperandOffsets.joinToString()).appendLine("]")
            }

            // .objkey_operands and offsets
            if (bytecode.objKeyOperands.isNotEmpty()) {
                sb.appendLine().appendLine(".section objkey_operands (${bytecode.objKeyOperands.size})")
                sb.append("  [")
                sb.append(bytecode.objKeyOperands.joinToString { renderKey(it) })
                sb.appendLine("]")
            }
            if (bytecode.objKeyOperandOffsets.isNotEmpty()) {
                sb.appendLine(".section objkey_offsets (${bytecode.objKeyOperandOffsets.size})")
                sb.append("  [").append(bytecode.objKeyOperandOffsets.joinToString()).appendLine("]")
            }

            // .const_operands and offsets and constants pool
            if (bytecode.constOperands.isNotEmpty()) {
                sb.appendLine().appendLine(".section const_operands (${bytecode.constOperands.size})")
                sb.append("  [").append(bytecode.constOperands.joinToString()).appendLine("]")
            }
            if (bytecode.constOperandOffsets.isNotEmpty()) {
                sb.appendLine(".section const_offsets (${bytecode.constOperandOffsets.size})")
                sb.append("  [").append(bytecode.constOperandOffsets.joinToString()).appendLine("]")
            }
            if (bytecode.constants.isNotEmpty()) {
                sb.appendLine().appendLine(".section constants (${bytecode.constants.size})")
                for ((idx, c) in bytecode.constants.withIndex()) {
                    sb.append("  [").append(idx).append("] = ").append(renderValue(c)).append('\n')
                }
            }

            // .debug_info (if present)
            if (bytecode.debugInfo.isNotEmpty()) {
                sb.appendLine().appendLine(".section debug_info (${bytecode.debugInfo.size})")
                for ((pc, info) in bytecode.debugInfo.entries.sortedBy { it.key }) {
                    sb.append("  ").append(pc).append(" -> ").append(info).append('\n')
                }
            }
        }
        return sb.toString()
    }

    private fun renderInstruction(i: Instruction): String = when (i) {
        is Instruction.PUSH -> "PUSH ${renderValue(i.value)}"
        is Instruction.DUP -> "DUP"
        is Instruction.POP -> "POP"
        is Instruction.SWAP -> "SWAP"
        is Instruction.LOAD_VAR -> "LOAD_VAR ${i.name}"
        is Instruction.STORE_VAR -> "STORE_VAR ${i.name}"
        is Instruction.LOAD_SCOPE -> "LOAD_SCOPE ${i.name}"
        is Instruction.LOAD_LOCAL -> "LOAD_LOCAL ${i.index}"
        is Instruction.STORE_LOCAL -> "STORE_LOCAL ${i.index}"
        is Instruction.ADD -> "ADD"
        is Instruction.SUB -> "SUB"
        is Instruction.MUL -> "MUL"
        is Instruction.DIV -> "DIV"
        is Instruction.IDIV -> "IDIV"
        is Instruction.MOD -> "MOD"
        is Instruction.NEG -> "NEG"
        is Instruction.EQ -> "EQ"
        is Instruction.NEQ -> "NEQ"
        is Instruction.LT -> "LT"
        is Instruction.LE -> "LE"
        is Instruction.GT -> "GT"
        is Instruction.GE -> "GE"
        is Instruction.AND -> "AND"
        is Instruction.OR -> "OR"
        is Instruction.NOT -> "NOT"
        is Instruction.COALESCE -> "COALESCE"
        is Instruction.MAKE_OBJECT -> "MAKE_OBJECT ${i.fieldCount}"
        is Instruction.MAKE_ARRAY -> "MAKE_ARRAY ${i.elementCount}"
        is Instruction.ACCESS_STATIC -> "ACCESS_STATIC ${i.key}"
        is Instruction.ACCESS_DYNAMIC -> "ACCESS_DYNAMIC"
        is Instruction.SET_STATIC -> "SET_STATIC ${i.key}"
        is Instruction.SET_DYNAMIC -> "SET_DYNAMIC"
        is Instruction.APPEND -> "APPEND"
        is Instruction.CONCAT -> "CONCAT"
        is Instruction.JUMP -> "JUMP ${i.address}"
        is Instruction.JUMP_IF_TRUE -> "JUMP_IF_TRUE ${i.address}"
        is Instruction.JUMP_IF_FALSE -> "JUMP_IF_FALSE ${i.address}"
        is Instruction.JUMP_IF_NULL -> "JUMP_IF_NULL ${i.address}"
        is Instruction.CALL -> "CALL ${i.name} ${i.argCount}"
        is Instruction.CALL_LAMBDA -> "CALL_LAMBDA ${i.argCount}"
        is Instruction.CALL_HOST -> "CALL_HOST ${i.index} ${i.name} ${i.argCount}"
        is Instruction.CALL_FN -> "CALL_FN ${i.name} ${i.argCount}"
        is Instruction.RETURN -> "RETURN"
        is Instruction.RETURN_VALUE -> "RETURN_VALUE"
        is Instruction.OUTPUT -> "OUTPUT ${i.fieldCount}"
        Instruction.OUTPUT_1 -> "OUTPUT_1"
        Instruction.OUTPUT_2 -> "OUTPUT_2"
        is Instruction.MODIFY -> "MODIFY ${i.updateCount}"
        is Instruction.INIT_FOREACH -> "INIT_FOREACH ${i.varName} ${i.jumpToEnd}"
        is Instruction.NEXT_FOREACH -> "NEXT_FOREACH ${i.jumpToStart} ${i.jumpToEnd}"
        is Instruction.TRY_START -> "TRY_START ${i.catchAddress}"
        is Instruction.TRY_END -> "TRY_END"
        is Instruction.CATCH -> "CATCH ${i.exceptionVar} ${i.retryCount}"
        is Instruction.ABORT -> "ABORT"
        is Instruction.TRACE -> "TRACE ${i.event}"
        is Instruction.BREAKPOINT -> "BREAKPOINT ${i.info}"
        is Instruction.NOP -> "NOP"
        is Instruction.SUSPEND -> "SUSPEND"
        is Instruction.LINE -> "LINE ${i.lineNumber}"
    }

    private fun renderValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> '"' + v + '"'
        is LambdaTemplate -> renderLambda(v)
        else -> v.toString()
    }

    private fun renderLambda(t: LambdaTemplate): String {
        val bc = t.bytecode
        val inner = StringBuilder()
        inner.append("LAMBDA(params=").append(t.params).append(") {")
            .append('\n')
        for (i in 0 until bc.size()) {
            val ins = bc.getInstruction(i)
            inner.append("  ")
                .append(i)
                .append(": ")
                .append(renderInstruction(ins))
                .append('\n')
        }
        inner.append('}')
        return inner.toString()
    }

    private fun renderKey(k: ObjKey): String = when (k) {
        is ObjKey.Name -> "\"${k.v}\""
        is I32 -> k.v.toString()
        is I64 -> k.v.toString()
        is IBig -> k.v.toString()
    }

    fun dumpToFile(bytecode: Bytecode, dir: Path, fileName: String, includeOpcodes: Boolean = false) {
        Files.createDirectories(dir)
        val path = dir.resolve(fileName)
        Files.writeString(path, format(bytecode, includeOpcodes))
    }
}
