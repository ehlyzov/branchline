@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

/** Independent, opt-in treatments. The public bytecode representation is unchanged. */
@ExperimentalVMApi
data class VMExperimentalOptions(
    val slots: Boolean = false,
    val fusion: Boolean = false,
    val collectDiagnostics: Boolean = false,
)

@ExperimentalVMApi
data class VMExperimentalReport(
    val requested: VMExperimentalOptions,
    val effective: VMExperimentalOptions,
    val fallbackReason: String? = null,
    val slotReads: Long = 0,
    val slotWrites: Long = 0,
    val loopSlotWrites: Long = 0,
    val mirroredStoresElided: Long = 0,
    val fusedDispatches: Long = 0,
    val instructionsElided: Long = 0,
    val hostBarriers: Long = 0,
)

@ExperimentalVMApi
class VMExperimentalPlan internal constructor(
    internal val bytecode: Bytecode,
    val options: VMExperimentalOptions,
    val fallbackReason: String?,
    internal val slots: ExperimentalSlotPlan?,
    internal val fusion: ExperimentalFusionPlan?,
) {
    val slotNameCount: Int get() = slots?.names?.size ?: 0
    val mirroredStoreSites: Int get() = slots?.mirrorAt?.count { it >= 0 } ?: 0
    val fusedSites: Int get() = fusion?.byPc?.count { it != null } ?: 0
}

internal class ExperimentalSlotPlan(bytecode: Bytecode) {
    val names: List<String>
    val byPc = IntArray(bytecode.size()) { -1 }
    val aliases: IntArray
    // A mirror is removed only when every write to both bindings agrees.
    val mirrorAt = IntArray(bytecode.size()) { -1 }
    val targets = BooleanArray(bytecode.size())

    init {
        val indices = LinkedHashMap<String, Int>()
        val stores = HashMap<String, MutableList<Int>>()
        val localStores = HashMap<Int, MutableList<Int>>()
        val loopNames = HashSet<String>()
        var maxLocal = -1
        for (pc in 0 until bytecode.size()) {
            when (Opcode.entries[bytecode.getOpcode(pc)]) {
                Opcode.LOAD_VAR, Opcode.LOAD_SCOPE, Opcode.STORE_VAR, Opcode.INIT_FOREACH -> {
                    val name = bytecode.getStringOperand(pc)
                    byPc[pc] = indices.getOrPut(name) { indices.size }
                    if (Opcode.entries[bytecode.getOpcode(pc)] == Opcode.STORE_VAR) stores.getOrPut(name) { ArrayList() }.add(pc)
                    if (Opcode.entries[bytecode.getOpcode(pc)] == Opcode.INIT_FOREACH) loopNames.add(name)
                }
                Opcode.LOAD_LOCAL, Opcode.STORE_LOCAL -> {
                    val index = bytecode.getIntOperand(pc)
                    maxLocal = maxOf(maxLocal, index)
                    if (Opcode.entries[bytecode.getOpcode(pc)] == Opcode.STORE_LOCAL) localStores.getOrPut(index) { ArrayList() }.add(pc)
                }
                else -> Unit
            }
            fun target(address: Int) { if (address in targets.indices) targets[address] = true }
            when (Opcode.entries[bytecode.getOpcode(pc)]) {
                Opcode.JUMP, Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE, Opcode.JUMP_IF_NULL, Opcode.INIT_FOREACH, Opcode.TRY_START -> target(bytecode.getIntOperand(pc))
                Opcode.NEXT_FOREACH -> { target(bytecode.getIntOperand(pc)); target(bytecode.getIntOperand(pc, 1)) }
                else -> Unit
            }
        }
        names = indices.keys.toList()
        aliases = IntArray(maxLocal + 1) { -1 }
        for ((name, pcs) in stores) {
            if (name in loopNames) continue
            var index = -1
            var valid = true
            for (pc in pcs) {
                if (pc == 0 || pc + 1 >= bytecode.size() ||
                    Opcode.entries[bytecode.getOpcode(pc - 1)] != Opcode.DUP ||
                    Opcode.entries[bytecode.getOpcode(pc + 1)] != Opcode.STORE_LOCAL || targets[pc] || targets[pc + 1]) { valid = false; break }
                val candidate = bytecode.getIntOperand(pc + 1)
                if (index != -1 && index != candidate) { valid = false; break }
                index = candidate
            }
            if (!valid || index < 0 || localStores[index]?.size != pcs.size) continue
            if (localStores[index]!!.any { it - 1 !in pcs }) continue
            aliases[index] = indices.getValue(name)
            for (pc in pcs) mirrorAt[pc - 1] = indices.getValue(name)
        }
    }
}

/** Deferred ordinary-map writes, with presence and insertion-order semantics. */
internal class ExperimentalSlotFrame(private val plan: ExperimentalSlotPlan, private val env: MutableMap<String, Any?>) {
    private val values = arrayOfNulls<Any?>(plan.names.size)
    private val loaded = BooleanArray(plan.names.size)
    private val present = BooleanArray(plan.names.size)
    private val dirty = BooleanArray(plan.names.size)
    // The insertion sequence is independent of static bytecode/name order.
    private val insertOrder = IntArray(plan.names.size)
    private val removed = BooleanArray(plan.names.size)
    private var serial = 0

    fun contains(index: Int): Boolean { initialize(index); return present[index] }
    fun read(index: Int, required: Boolean = true): Any? {
        initialize(index)
        if (required && !present[index]) throw VMException.VariableNotFound(plan.names[index])
        return values[index]
    }
    fun write(index: Int, value: Any?) {
        initialize(index)
        if (!present[index]) insertOrder[index] = ++serial
        present[index] = true
        values[index] = value
        dirty[index] = true
    }
    fun remove(index: Int) {
        initialize(index)
        if (present[index]) removed[index] = true
        present[index] = false
        values[index] = null
        dirty[index] = true
    }
    private fun initialize(index: Int) {
        if (loaded[index]) return
        val name = plan.names[index]
        values[index] = env[name]
        present[index] = values[index] != null || env.containsKey(name)
        loaded[index] = true
    }
    fun flush() {
        for (index in dirty.indices) if (dirty[index] && (removed[index] || !present[index])) env.remove(plan.names[index])
        for (index in dirty.indices) if (dirty[index] && present[index] && insertOrder[index] == 0) env[plan.names[index]] = values[index]
        val inserted = dirty.indices.filter { dirty[it] && present[it] && insertOrder[it] != 0 }.sortedBy { insertOrder[it] }
        for (index in inserted) env[plan.names[index]] = values[index]
        dirty.fill(false)
        removed.fill(false)
        insertOrder.fill(0)
        serial = 0
    }
    fun invalidate() { loaded.fill(false) }
}

internal class ExperimentalDiagnostics {
    var slotReads = 0L
    var slotWrites = 0L
    var loopWrites = 0L
    var mirrors = 0L
    var fused = 0L
    var elided = 0L
    var hostBarriers = 0L
}
