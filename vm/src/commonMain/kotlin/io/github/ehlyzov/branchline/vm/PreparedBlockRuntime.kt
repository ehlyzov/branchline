@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

/** One VM-owned frame, reused sequentially for one prepared plan. Never shared by plan readers. */
internal class PreparedBlockFrame(val plan: PreparedBlockPlan) {
    private val values = arrayOfNulls<Any?>(plan.names.size)
    private val state = ByteArray(plan.names.size)
    private val dirty = BooleanArray(plan.names.size)
    private val dirtyIndices = IntArray(plan.names.size)
    private val removed = BooleanArray(plan.names.size)
    private val inserted = BooleanArray(plan.names.size)
    private val previous = IntArray(plan.names.size)
    private val next = IntArray(plan.names.size)
    val initializedLocals = BooleanArray(plan.aliases.size)
    var aliasesAttached = true
    var logicalLocalCapacity = 0
    private var dirtyCount = 0
    private var head = -1
    private var tail = -1
    private var environment: MutableMap<String, Any?>? = null

    fun reset(env: MutableMap<String, Any?>) {
        environment = env
        aliasesAttached = true
        logicalLocalCapacity = 0
        initializedLocals.fill(false)
    }

    private fun initialize(index: Int) {
        if (state[index] != UNLOADED) return
        val env = environment!!
        val value = env[plan.names[index]]
        values[index] = value
        state[index] = if (value != null || env.containsKey(plan.names[index])) PRESENT else ABSENT
    }

    fun read(index: Int, required: Boolean): Any? {
        initialize(index)
        if (required && state[index] != PRESENT) throw VMException.VariableNotFound(plan.names[index])
        return values[index]
    }

    fun contains(index: Int): Boolean { initialize(index); return state[index] == PRESENT }

    private fun markDirty(index: Int) {
        if (!dirty[index]) {
            dirty[index] = true
            dirtyIndices[dirtyCount++] = index
        }
    }

    fun write(index: Int, value: Any?) {
        initialize(index)
        if (state[index] != PRESENT) {
            inserted[index] = true
            previous[index] = tail
            next[index] = -1
            if (tail == -1) head = index else next[tail] = index
            tail = index
        }
        state[index] = PRESENT
        values[index] = value
        markDirty(index)
    }

    fun remove(index: Int) {
        initialize(index)
        if (state[index] == PRESENT) {
            removed[index] = true
            if (inserted[index]) {
                val before = previous[index]
                val after = next[index]
                if (before == -1) head = after else next[before] = after
                if (after == -1) tail = before else previous[after] = before
                inserted[index] = false
            }
        }
        state[index] = ABSENT
        values[index] = null
        markDirty(index)
    }

    /** No temporary collection/sort: logical insertions form a preallocated linked order. */
    fun flush() {
        val env = environment ?: return
        for (at in 0 until dirtyCount) {
            val index = dirtyIndices[at]
            if (removed[index] || state[index] != PRESENT) env.remove(plan.names[index])
        }
        for (at in 0 until dirtyCount) {
            val index = dirtyIndices[at]
            if (state[index] == PRESENT && !inserted[index]) env[plan.names[index]] = values[index]
        }
        var index = head
        while (index != -1) {
            env[plan.names[index]] = values[index]
            inserted[index] = false
            index = next[index]
        }
        for (at in 0 until dirtyCount) {
            val dirtyIndex = dirtyIndices[at]
            dirty[dirtyIndex] = false
            removed[dirtyIndex] = false
        }
        dirtyCount = 0
        head = -1
        tail = -1
    }

    /** Host effects change named bindings but leave canonical VM locals separate. */
    fun invalidate() { state.fill(UNLOADED); values.fill(null) }

    fun clear() {
        environment = null
        values.fill(null)
        state.fill(UNLOADED)
        dirty.fill(false)
        removed.fill(false)
        inserted.fill(false)
        initializedLocals.fill(false)
        dirtyCount = 0
        head = -1
        tail = -1
    }

    private companion object {
        const val UNLOADED: Byte = 0
        const val ABSENT: Byte = 1
        const val PRESENT: Byte = 2
    }
}

internal object PreparedBlockGuards {
    // Capture standard container classes once.
    private val emptyListClass = emptyList<Any?>()::class
    private val singletonListClass = listOf<Any?>(null)::class
    private val arrayListViewClass = listOf(null, null)::class

    fun ordinaryContainer(value: Any?, environment: Any): Boolean = value !== environment && when (value) {
        is Map<*, *> -> value::class == HashMap::class || value::class == LinkedHashMap::class
        is List<*> -> value::class == ArrayList::class || value::class == emptyListClass ||
            value::class == singletonListClass || value::class == arrayListViewClass
        else -> true
    }
}
