package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ObjKey

/** A dispatch plan over canonical source PCs, never a rewritten bytecode stream. */
internal sealed class ExperimentalFusion {
    abstract val startPc: Int
    abstract val span: Int

    /** The load and every access retain their ordinary VM helper semantics. */
    data class StaticPath(
        override val startPc: Int,
        val load: Opcode,
        val name: String?,
        val localIndex: Int?,
        val keys: List<ObjKey>,
    ) : ExperimentalFusion() {
        override val span: Int get() = keys.size + 1
    }

    /** Keep DUP's stack limit check and store the environment before the local. */
    data class MirroredStore(
        override val startPc: Int,
        val name: String,
        val localIndex: Int,
    ) : ExperimentalFusion() {
        override val span: Int get() = 3
    }
}

internal class ExperimentalFusionPlan private constructor(
    val byPc: Array<ExperimentalFusion?>,
) {
    companion object {
        fun prepare(bytecode: Bytecode): ExperimentalFusionPlan {
            val size = bytecode.size()
            val entry = BooleanArray(size)
            fun mark(address: Int) {
                if (address in 0 until size) entry[address] = true
            }
            for (pc in 0 until size) {
                when (bytecode.getOpcode(pc)) {
                    Opcode.JUMP.ordinal,
                    Opcode.JUMP_IF_TRUE.ordinal,
                    Opcode.JUMP_IF_FALSE.ordinal,
                    Opcode.JUMP_IF_NULL.ordinal,
                    Opcode.INIT_FOREACH.ordinal,
                    Opcode.TRY_START.ordinal -> mark(bytecode.getIntOperand(pc))
                    Opcode.NEXT_FOREACH.ordinal -> {
                        mark(bytecode.getIntOperand(pc, 0))
                        mark(bytecode.getIntOperand(pc, 1))
                    }
                }
            }

            val byPc = arrayOfNulls<ExperimentalFusion>(size)
            var pc = 0
            while (pc < size) {
                val opcode = bytecode.getOpcode(pc)
                val load = when (opcode) {
                    Opcode.LOAD_VAR.ordinal -> Opcode.LOAD_VAR
                    Opcode.LOAD_SCOPE.ordinal -> Opcode.LOAD_SCOPE
                    Opcode.LOAD_LOCAL.ordinal -> Opcode.LOAD_LOCAL
                    else -> null
                }
                val fusion = if (load != null) {
                    var end = pc + 1
                    while (end < size && !entry[end] &&
                        bytecode.getOpcode(end) == Opcode.ACCESS_STATIC.ordinal
                    ) end++
                    if (end == pc + 1) null else ExperimentalFusion.StaticPath(
                        startPc = pc,
                        load = load,
                        name = if (load == Opcode.LOAD_LOCAL) null else bytecode.getStringOperand(pc),
                        localIndex = if (load == Opcode.LOAD_LOCAL) bytecode.getIntOperand(pc) else null,
                        keys = (pc + 1 until end).map { bytecode.getObjKeyOperand(it) },
                    )
                } else if (opcode == Opcode.DUP.ordinal && pc + 2 < size &&
                    !entry[pc + 1] && !entry[pc + 2] &&
                    bytecode.getOpcode(pc + 1) == Opcode.STORE_VAR.ordinal &&
                    bytecode.getOpcode(pc + 2) == Opcode.STORE_LOCAL.ordinal
                ) {
                    ExperimentalFusion.MirroredStore(
                        pc, bytecode.getStringOperand(pc + 1), bytecode.getIntOperand(pc + 2),
                    )
                } else null
                byPc[pc] = fusion
                pc += fusion?.span ?: 1
            }
            return ExperimentalFusionPlan(byPc)
        }
    }
}
