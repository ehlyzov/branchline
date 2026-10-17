@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

/** Guards perform no user callbacks. Each admitted get executes once, including access-order maps. */
@ExperimentalVMApi
object LocalLoopSupport {
    private val emptyListClass = emptyList<Any?>()::class.java
    private val singletonListClass = listOf<Any?>(null)::class.java
    private val arrayViewClass = listOf(null, null)::class.java

    @JvmStatic
    fun safeStatic(value: Any?, key: Any): Boolean {
        if (value == null) return false
        val type = value.javaClass
        if (value is Map<*, *>) return type == HashMap::class.java || type == LinkedHashMap::class.java
        if (value !is List<*>) return false
        if (type != ArrayList::class.java && type != emptyListClass && type != singletonListClass && type != arrayViewClass) return false
        val index = when (key) { is Int -> key.toLong(); is Long -> key; else -> return false }
        return index >= 0 && index <= Int.MAX_VALUE && index < value.size
    }

    @JvmStatic
    fun readStatic(value: Any, key: Any): Any? = when (value) {
        is Map<*, *> -> value[key]
        is List<*> -> value[(key as Number).toInt()]
        else -> error("Local-loop guard was not applied")
    }
}
