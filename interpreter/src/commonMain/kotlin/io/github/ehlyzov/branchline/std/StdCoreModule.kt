package io.github.ehlyzov.branchline.std

import kotlin.collections.ArrayDeque
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt

class StdCoreModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("KEYS", ::fnKEYS)
        r.fn("VALUES", ::fnVALUES)
        r.fn("ENTRIES", ::fnENTRIES)
        r.fn("PUT", ::fnPUT)
        r.fn("DELETE", ::fnDELETE)
        r.fn("WALK", ::fnWALK)
        r.fn("APPEND", ::fnAPPEND)
        r.fn("PREPEND", ::fnPREPEND)
        r.fn("COLLECT", ::fnCOLLECT)
        r.fn("PRINT", ::fnPRINT)
        r.fn("IS_OBJECT", ::fnIS_OBJECT)
        r.fn("LISTIFY", ::fnLISTIFY)
        r.fn("GET", ::fnGET)
    }
}

private fun fnKEYS(args: List<Any?>): Any {
    require(args.size == 1) { "KEYS(coll)" }
    return when (val coll = args[0]) {
        is List<*> -> coll.indices.toList()
        is Map<*, *> -> coll.keys.toList()
        is SharedResourceHandle -> {
            val store = SharedStoreProvider.store ?: error("SharedStore is not configured")
            val snapshot = store.snapshot()[coll.name]
                ?: error("Unknown shared resource: ${coll.name}")
            snapshot.keys.toList()
        }
        else -> error("KEYS: arg must be list or object")
    }
}

private fun fnVALUES(args: List<Any?>): Any {
    require(args.size == 1) { "VALUES(obj)" }
    val m = args[0] as? Map<*, *> ?: error("VALUES: arg must be object")
    return m.values.toList()
}

private fun fnENTRIES(args: List<Any?>): Any {
    require(args.size == 1) { "ENTRIES(obj)" }
    val m = args[0] as? Map<*, *> ?: error("ENTRIES: arg must be object")
    return m.entries.map { e -> mapOf("key" to e.key!!, "value" to e.value) }
}

private fun fnPUT(args: List<Any?>): Any {
    require(args.size == 3) { "PUT(coll, key, value)" }
    val coll = args[0]
    val key = args[1]
    val value = args[2]
    return when (coll) {
        is Map<*, *> -> clonePut(coll, asObjectKey(key), value)
        is List<*> -> {
            val i = asIndex(key)
            when {
                i < coll.size -> ArrayList<Any?>(coll.size).apply {
                    addAll(coll)
                    this[i] = value
                }

                i == coll.size -> ArrayList<Any?>(coll.size + 1).apply {
                    addAll(coll)
                    add(value)
                }

                else -> error("PUT: index $i out of bounds 0..${coll.size}")
            }
        }

        else -> error("PUT: unsupported collection")
    }
}

private fun fnDELETE(args: List<Any?>): Any {
    require(args.size == 2) { "DELETE(coll, key)" }
    val coll = args[0]
    val key = args[1]
    return when (coll) {
        is Map<*, *> -> cloneDelete(coll, asObjectKey(key))
        is List<*> -> {
            val i = asIndex(key)
            require(i in 0 until coll.size) { "DELETE: index $i out of bounds 0..${coll.size - 1}" }
            ArrayList<Any?>(coll.size - 1).apply {
                addAll(coll.subList(0, i))
                addAll(coll.subList(i + 1, coll.size))
            }
        }

        else -> error("DELETE: unsupported collection")
    }
}

private fun fnWALK(args: List<Any?>): Any {
    require(args.size == 1) { "WALK(tree)" }
    val root = args[0]

    fun entry(path: List<Any>, key: Any?, value: Any?, depth: Int, isLeaf: Boolean) =
        mapOf("path" to path, "key" to key, "value" to value, "depth" to depth, "isLeaf" to isLeaf)

    fun children(v: Any?): Iterator<*>? = when (v) {
        is Map<*, *> -> v.entries.iterator()
        is List<*> -> v.indices.iterator()
        else -> null
    }

    return sequence {
        data class Frame(val value: Any?, val it: Iterator<*>, val path: List<Any>)

        val initIt = children(root)
        if (initIt == null) {
            yield(entry(emptyList(), null, root, 0, true))
        } else {
            yield(entry(emptyList(), null, root, 0, !initIt.hasNext()))
            val stack = ArrayDeque<Frame>()
            stack.addLast(Frame(root, initIt, emptyList()))

            while (stack.isNotEmpty()) {
                val top = stack.last()
                val it = top.it
                if (!it.hasNext()) {
                    stack.removeLast()
                    continue
                }
                when (val ch = it.next()) {
                    is Map.Entry<*, *> -> {
                        val k = ch.key ?: error("WALK: null object key")
                        require(k is String || k is Int || k is Long || k is BLBigInt) {
                            "WALK: object key must be String|Int|Long|BigInt, got ${k::class.simpleName}"
                        }
                        val v = ch.value
                        val newPath = top.path + k
                        val cit = children(v)
                        if (cit == null || !cit.hasNext()) {
                            yield(entry(newPath, k, v, newPath.size, true))
                        } else {
                            stack.addLast(Frame(v, cit, newPath))
                            yield(entry(newPath, k, v, newPath.size, false))
                        }
                    }

                    is Int -> {
                        val idx = ch

                        @Suppress("UNCHECKED_CAST")
                        val arr = top.value as List<*>
                        val v = arr[idx]
                        val newPath = top.path + idx
                        val cit = children(v)
                        if (cit == null || !cit.hasNext()) {
                            yield(entry(newPath, idx, v, newPath.size, true))
                        } else {
                            stack.addLast(Frame(v, cit, newPath))
                            yield(entry(newPath, idx, v, newPath.size, false))
                        }
                    }

                    else -> error("WALK: unexpected child element ${ch?.let { it::class.simpleName }}")
                }
            }
        }
    }
}

private fun fnAPPEND(args: List<Any?>): Any {
    require(args.size == 2) { "APPEND(list, value)" }
    val src = args[0] as? List<*> ?: error("APPEND: first arg must be list")
    return ArrayList<Any?>(src.size + 1).apply {
        addAll(src)
        add(args[1])
    }
}

private fun fnPREPEND(args: List<Any?>): Any {
    require(args.size == 2) { "PREPEND(list, value)" }
    val src = args[0] as? List<*> ?: error("PREPEND: first arg must be list")
    return ArrayList<Any?>(src.size + 1).apply {
        add(args[1])
        addAll(src)
    }
}

private fun fnCOLLECT(args: List<Any?>): Any {
    require(args.size == 1) { "COLLECT(iterable)" }
    return when (val v = args[0]) {
        is Sequence<*> -> v.toList()
        is Iterable<*> -> v.toList()
        else -> error("COLLECT: arg must be iterable/sequence, got ${v?.let { it::class.simpleName } ?: "null"}")
    }
}

private fun fnPRINT(args: List<Any?>): Any? {
    args.forEach { arg ->
        when (arg) {
            is List<*> -> println(arg.joinToString(", "))
            is Map<*, *> -> println(arg.toList().joinToString(", "))
            else -> println(arg.toString())
        }
    }
    return null
}

private fun fnIS_OBJECT(args: List<Any?>): Boolean {
    val x = args.getOrNull(0)
    return x is Map<*, *>
}

private fun fnLISTIFY(args: List<Any?>): Any {
    require(args.size == 1) { "LISTIFY(x)" }
    return when (val v = args[0]) {
        null -> emptyList<Any?>()
        is List<*> -> v
        is Map<*, *> -> listOf(v)
        else -> error("LISTIFY: arg must be list, object, or null")
    }
}

private fun fnGET(args: List<Any?>): Any? {
    require(args.size == 2 || args.size == 3) { "GET(obj, key[, default])" }
    val obj = args[0] as? Map<*, *> ?: error("GET: first arg must be object")
    val key = asObjectKey(args[1])
    val fallback = if (args.size == 3) args[2] else null
    return if (obj.containsKey(key)) obj[key] else fallback
}
