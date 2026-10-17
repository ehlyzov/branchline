@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt

/**
 * Opt-in lowering of a structurally owned bytecode snapshot. Constant values keep
 * their original identities; callers must not mutate those values during use.
 * Prepared instruction/layout arrays are internal and never mutated by runners.
 */
@ExperimentalVMApi
class PreparedBlockPlan private constructor(
    internal val bytecode: Bytecode,
    val optimize: Boolean,
    val fallbackReason: String?,
    internal val instructions: Array<PreparedBlockInstruction?>,
    internal val names: List<String>,
    internal val aliases: IntArray,
    internal val blocks: List<PreparedBasicBlock>,
    val directPathCount: Int,
    val mirrorStoreCount: Int,
) {
    val sourceInstructionCount: Int get() = bytecode.size()
    val blockCount: Int get() = blocks.size
    val slotNameCount: Int get() = names.size

    companion object {
        internal fun prepare(source: Bytecode, optimize: Boolean): PreparedBlockPlan {
            val code = source.copy(
                opcodes = source.opcodes.copyOf(),
                intOperands = source.intOperands.copyOf(), stringOperands = source.stringOperands.copyOf(),
                objKeyOperands = source.objKeyOperands.copyOf(), constOperands = source.constOperands.copyOf(),
                intOperandOffsets = source.intOperandOffsets.copyOf(),
                stringOperandOffsets = source.stringOperandOffsets.copyOf(),
                objKeyOperandOffsets = source.objKeyOperandOffsets.copyOf(),
                constOperandOffsets = source.constOperandOffsets.copyOf(),
                constants = source.constants.toList(), debugInfo = source.debugInfo.toMap(),
                traceMetadata = TraceMetadata(
                    source.traceMetadata.enterEvents.mapValues { it.value.toList() },
                    source.traceMetadata.exitEvents.mapValues { it.value.toList() },
                    source.traceMetadata.pathWrites.mapValues { (_, value) ->
                        value.copy(segments = value.segments.toList(), displayPath = value.displayPath?.toList())
                    },
                ),
            )
            fun fallback(reason: String) = PreparedBlockPlan(code, optimize, reason,
                arrayOfNulls(code.size()), emptyList(), IntArray(0), emptyList(), 0, 0)

            val resolved = arrayOfNulls<PreparedBlockInstruction>(code.size())
            for (pc in resolved.indices) {
                val op = Opcode.entries.getOrNull(code.getOpcode(pc))
                    ?: return fallback("unknown opcode at $pc")
                if (op in UNSUPPORTED) return fallback("unsupported opcode $op at $pc")
                if (!operandsPresent(code, pc, op)) return fallback("malformed operands at $pc")
                try {
                    if (op == Opcode.PUSH && code.getConstOperand(pc) is LambdaTemplate) return fallback("closure at $pc")
                    val instruction = resolve(code, pc, op)
                    val key = instruction.key
                    if (key is IBig && key.v::class != BLBigInt::class) return fallback("custom numeric key at $pc")
                    resolved[pc] = instruction
                } catch (_: Exception) {
                    // Let canonical dispatch preserve the original failure/effect order.
                    return fallback("malformed operands at $pc")
                }
            }

            val entries = BooleanArray(code.size() + 1)
            entries[0] = true
            entries[code.size()] = true
            fun entry(pc: Int) { if (pc in entries.indices) entries[pc] = true }
            for (instruction in resolved.filterNotNull()) {
                when (instruction.kind) {
                    PreparedBlockOp.JUMP, PreparedBlockOp.JUMP_IF_TRUE, PreparedBlockOp.JUMP_IF_FALSE,
                    PreparedBlockOp.JUMP_IF_NULL, PreparedBlockOp.INIT_FOREACH -> entry(instruction.a)
                    PreparedBlockOp.NEXT_FOREACH -> { entry(instruction.a); entry(instruction.b) }
                    else -> Unit
                }
                if (instruction.kind in BLOCK_ENDS) entry(instruction.nextPc)
            }
            val boundaries = entries.indices.filter { entries[it] }
            val blocks = boundaries.zipWithNext { start, end ->
                val last = resolved[end - 1]!!
                val successors = when (last.kind) {
                    PreparedBlockOp.JUMP -> listOf(last.a)
                    PreparedBlockOp.JUMP_IF_TRUE, PreparedBlockOp.JUMP_IF_FALSE,
                    PreparedBlockOp.JUMP_IF_NULL, PreparedBlockOp.INIT_FOREACH -> listOf(last.a, end).distinct()
                    PreparedBlockOp.NEXT_FOREACH -> listOf(last.a, last.b).distinct()
                    PreparedBlockOp.RETURN, PreparedBlockOp.RETURN_VALUE, PreparedBlockOp.ABORT -> emptyList()
                    else -> listOf(end)
                }
                PreparedBasicBlock(start, end, successors,
                    (start until end).filter { resolved[it]!!.observation != PreparedBlockObservation.NONE })
            }
            if (!optimize) return PreparedBlockPlan(code, false, null, resolved,
                emptyList(), IntArray(0), blocks, 0, 0)

            val slots = LinkedHashMap<String, Int>()
            val stores = HashMap<String, MutableList<Int>>()
            val localStores = HashMap<Int, MutableList<Int>>()
            val loops = HashSet<String>()
            var maxLocal = -1
            for (instruction in resolved.filterNotNull()) {
                when (instruction.kind) {
                    PreparedBlockOp.LOAD_VAR, PreparedBlockOp.LOAD_SCOPE, PreparedBlockOp.STORE_VAR,
                    PreparedBlockOp.INIT_FOREACH -> {
                        val name = instruction.name!!
                        slots.getOrPut(name) { slots.size }
                        if (instruction.kind == PreparedBlockOp.STORE_VAR) stores.getOrPut(name) { ArrayList() }.add(instruction.sourcePc)
                        if (instruction.kind == PreparedBlockOp.INIT_FOREACH) loops.add(name)
                    }
                    PreparedBlockOp.LOAD_LOCAL, PreparedBlockOp.STORE_LOCAL -> {
                        maxLocal = maxOf(maxLocal, instruction.a)
                        if (instruction.kind == PreparedBlockOp.STORE_LOCAL) localStores.getOrPut(instruction.a) { ArrayList() }.add(instruction.sourcePc)
                    }
                    else -> Unit
                }
            }
            val aliases = IntArray(maxLocal + 1) { -1 }
            val mirrorAt = IntArray(code.size()) { -1 }
            for ((name, pcs) in stores) {
                if (name in loops) continue
                var local = -1
                var valid = true
                for (pc in pcs) {
                    val previous = resolved.getOrNull(pc - 1)
                    val next = resolved.getOrNull(pc + 1)
                    if (previous?.kind != PreparedBlockOp.DUP || next?.kind != PreparedBlockOp.STORE_LOCAL ||
                        entries[pc] || entries[pc + 1] || next.a < 0 || (local >= 0 && local != next.a)
                    ) { valid = false; break }
                    local = next.a
                }
                if (!valid || local < 0 || localStores[local]?.size != pcs.size ||
                    localStores[local]!!.any { it - 1 !in pcs }
                ) continue
                aliases[local] = slots.getValue(name)
                for (pc in pcs) mirrorAt[pc - 1] = slots.getValue(name)
            }
            for (pc in resolved.indices) {
                val instruction = resolved[pc]!!
                resolved[pc] = when (instruction.kind) {
                    PreparedBlockOp.LOAD_VAR -> instruction.copy(kind = PreparedBlockOp.SLOT_LOAD, a = slots.getValue(instruction.name!!))
                    PreparedBlockOp.LOAD_SCOPE -> instruction.copy(kind = PreparedBlockOp.SLOT_SCOPE, a = slots.getValue(instruction.name!!))
                    PreparedBlockOp.STORE_VAR -> instruction.copy(kind = PreparedBlockOp.SLOT_STORE, a = slots.getValue(instruction.name!!))
                    PreparedBlockOp.LOAD_LOCAL -> {
                        val alias = aliases.getOrElse(instruction.a) { -1 }
                        if (alias < 0) instruction else instruction.copy(kind = PreparedBlockOp.ALIAS_LOAD, b = alias)
                    }
                    PreparedBlockOp.INIT_FOREACH -> instruction.copy(b = slots.getValue(instruction.name!!))
                    else -> instruction
                }
            }

            var paths = 0
            var mirrors = 0
            for (block in blocks) {
                var pc = block.startPc
                while (pc < block.endPc) {
                    val instruction = resolved[pc]!!
                    if (mirrorAt[pc] >= 0) {
                        resolved[pc] = PreparedBlockInstruction(PreparedBlockOp.MIRROR_STORE, pc, pc + 3,
                            a = mirrorAt[pc], b = resolved[pc + 2]!!.a, name = resolved[pc + 1]!!.name)
                        resolved[pc + 1] = null
                        resolved[pc + 2] = null
                        mirrors++
                        pc += 3
                    } else if (instruction.kind in PATH_LOADS) {
                        var end = pc + 1
                        while (end < block.endPc && resolved[end]?.kind == PreparedBlockOp.ACCESS_STATIC) end++
                        if (end > pc + 1) {
                            val keys = (pc + 1 until end).map { resolved[it]!!.key!! }
                            resolved[pc] = PreparedBlockInstruction(PreparedBlockOp.STATIC_PATH, pc, end,
                                keys = keys, load = instruction, observation = PreparedBlockObservation.STATIC_CONTAINER)
                            for (interior in pc + 1 until end) resolved[interior] = null
                            paths++
                        }
                        pc = end
                    } else pc++
                }
            }
            return PreparedBlockPlan(code, true, null, resolved, slots.keys.toList(), aliases, blocks, paths, mirrors)
        }

        private val UNSUPPORTED = setOf(
            Opcode.CALL, Opcode.CALL_FN, Opcode.CALL_LAMBDA, Opcode.TRY_START, Opcode.TRY_END, Opcode.CATCH,
            Opcode.SUSPEND, Opcode.SET_STATIC, Opcode.SET_DYNAMIC, Opcode.PRIVATE_SET_STATIC,
            Opcode.PRIVATE_SET_DYNAMIC, Opcode.MODIFY, Opcode.APPEND, Opcode.PLUS_ASSIGN, Opcode.CONCAT,
        )
        private val BLOCK_ENDS = setOf(
            PreparedBlockOp.JUMP, PreparedBlockOp.JUMP_IF_TRUE, PreparedBlockOp.JUMP_IF_FALSE,
            PreparedBlockOp.JUMP_IF_NULL, PreparedBlockOp.INIT_FOREACH, PreparedBlockOp.NEXT_FOREACH,
            PreparedBlockOp.RETURN, PreparedBlockOp.RETURN_VALUE, PreparedBlockOp.ABORT,
            PreparedBlockOp.CALL_HOST, PreparedBlockOp.TRACE, PreparedBlockOp.BREAKPOINT, PreparedBlockOp.LINE,
        )
        private val PATH_LOADS = setOf(PreparedBlockOp.SLOT_LOAD, PreparedBlockOp.SLOT_SCOPE,
            PreparedBlockOp.LOAD_LOCAL, PreparedBlockOp.ALIAS_LOAD)

        private fun operandsPresent(code: Bytecode, pc: Int, op: Opcode): Boolean {
            val types = InstructionTable.operandTypes[op.ordinal]
            fun range(offsets: IntArray, size: Int, type: OperandType): Boolean {
                val count = types.count { it == type }
                if (count == 0) return true
                if (pc !in offsets.indices) return false
                val start = offsets[pc]
                return start >= 0 && start <= size - count
            }
            if (!range(code.intOperandOffsets, code.intOperands.size, OperandType.INT) ||
                !range(code.stringOperandOffsets, code.stringOperands.size, OperandType.STRING) ||
                !range(code.objKeyOperandOffsets, code.objKeyOperands.size, OperandType.OBJ_KEY) ||
                !range(code.constOperandOffsets, code.constOperands.size, OperandType.CONST)
            ) return false
            val constants = types.count { it == OperandType.CONST }
            if (constants > 0) {
                val start = code.constOperandOffsets[pc]
                for (index in start until start + constants) if (code.constOperands[index] !in code.constants.indices) return false
            }
            return true
        }

        private fun resolve(code: Bytecode, pc: Int, op: Opcode): PreparedBlockInstruction {
            val kind = PreparedBlockOp.valueOf(op.name)
            var a = 0
            var b = 0
            var name: String? = null
            var value: Any? = null
            var key: ObjKey? = null
            when (op) {
                Opcode.PUSH -> value = code.getConstOperand(pc)
                Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.STORE_VAR, Opcode.TRACE, Opcode.BREAKPOINT -> name = code.getStringOperand(pc)
                Opcode.LOAD_LOCAL, Opcode.STORE_LOCAL, Opcode.MAKE_OBJECT, Opcode.MAKE_ARRAY, Opcode.OUTPUT,
                Opcode.JUMP, Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE, Opcode.JUMP_IF_NULL, Opcode.LINE -> a = code.getIntOperand(pc)
                Opcode.ACCESS_STATIC -> key = code.getObjKeyOperand(pc)
                Opcode.INIT_FOREACH -> { name = code.getStringOperand(pc); a = code.getIntOperand(pc) }
                Opcode.NEXT_FOREACH -> { a = code.getIntOperand(pc); b = code.getIntOperand(pc, 1) }
                Opcode.CALL_HOST -> { a = code.getIntOperand(pc); b = code.getIntOperand(pc, 1); name = code.getStringOperand(pc) }
                else -> Unit
            }
            val observation = when (op) {
                Opcode.ACCESS_STATIC -> PreparedBlockObservation.STATIC_CONTAINER
                Opcode.ACCESS_DYNAMIC -> PreparedBlockObservation.DYNAMIC_CONTAINER
                Opcode.INIT_FOREACH, Opcode.NEXT_FOREACH -> PreparedBlockObservation.ITERABLE
                Opcode.ARRAY_BUILDER_ADD, Opcode.PRIVATE_ARRAY_APPEND -> PreparedBlockObservation.ARRAY_BUILDER
                Opcode.ADD, Opcode.SUB, Opcode.MUL, Opcode.DIV, Opcode.IDIV, Opcode.MOD, Opcode.AND, Opcode.OR,
                Opcode.EQ, Opcode.NEQ, Opcode.LT, Opcode.LE, Opcode.GT, Opcode.GE -> PreparedBlockObservation.SCALAR_PAIR
                Opcode.NEG, Opcode.NOT, Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE -> PreparedBlockObservation.SCALAR_ONE
                Opcode.MAKE_OBJECT, Opcode.OUTPUT -> PreparedBlockObservation.OBJECT_KEYS
                Opcode.CALL_HOST -> PreparedBlockObservation.HOST
                else -> PreparedBlockObservation.NONE
            }
            return PreparedBlockInstruction(kind, pc, pc + 1, a, b, name, value, key, observation = observation)
        }
    }
}

internal enum class PreparedBlockOp {
    PUSH, DUP, POP, SWAP, LOAD_VAR, STORE_VAR, LOAD_SCOPE, LOAD_LOCAL, STORE_LOCAL,
    ADD, SUB, MUL, DIV, IDIV, MOD, NEG, EQ, NEQ, LT, LE, GT, GE, AND, OR, NOT, COALESCE,
    MAKE_OBJECT, MAKE_ARRAY, ACCESS_STATIC, ACCESS_DYNAMIC, ARRAY_BUILDER_INIT, ARRAY_BUILDER_ADD,
    ARRAY_BUILDER_FINISH, PRIVATE_ARRAY_APPEND, JUMP, JUMP_IF_TRUE, JUMP_IF_FALSE, JUMP_IF_NULL,
    CALL_HOST, RETURN, RETURN_VALUE, OUTPUT, INIT_FOREACH, NEXT_FOREACH, ABORT, TRACE, BREAKPOINT, LINE, NOP,
    SLOT_LOAD, SLOT_SCOPE, SLOT_STORE, ALIAS_LOAD, MIRROR_STORE, STATIC_PATH,
}

internal enum class PreparedBlockObservation {
    NONE, STATIC_CONTAINER, DYNAMIC_CONTAINER, ITERABLE, ARRAY_BUILDER, SCALAR_PAIR, SCALAR_ONE, OBJECT_KEYS, HOST,
}

internal data class PreparedBlockInstruction(
    val kind: PreparedBlockOp,
    val sourcePc: Int,
    val nextPc: Int,
    val a: Int = 0,
    val b: Int = 0,
    val name: String? = null,
    val value: Any? = null,
    val key: ObjKey? = null,
    val keys: List<ObjKey>? = null,
    val load: PreparedBlockInstruction? = null,
    val observation: PreparedBlockObservation = PreparedBlockObservation.NONE,
)

internal data class PreparedBasicBlock(
    val startPc: Int,
    val endPc: Int,
    val successors: List<Int>,
    val observationPcs: List<Int>,
)
