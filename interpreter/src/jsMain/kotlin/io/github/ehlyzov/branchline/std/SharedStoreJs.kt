package io.github.ehlyzov.branchline.std

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal const val LOCAL_STORAGE_PREFIX = "branchline.shared."

private val json = Json { ignoreUnknownKeys = true }

private fun hasLocalStorage(): Boolean =
    js("typeof localStorage !== 'undefined' && localStorage != null") as Boolean

private fun localStorage(): dynamic = js("localStorage")

private fun storageKey(resource: String): String = "$LOCAL_STORAGE_PREFIX$resource"

private class SharedResource(
    private val name: String,
    private val kind: SharedResourceKind,
    private val storage: dynamic?,
) {
    private val data = mutableMapOf<String, Any?>()
    private val writtenKeys = mutableSetOf<String>()
    private val pendingAwaiters = mutableMapOf<String, MutableList<CompletableDeferred<Any?>>>()
    private val mutex = Mutex()

    init {
        load()
    }

    suspend fun get(key: String): Any? = data[key]

    suspend fun setOnce(key: String, value: Any?): Boolean = mutex.withLock {
        if (kind == SharedResourceKind.SINGLE && writtenKeys.contains(key)) {
            return false
        }
        data[key] = value
        if (kind == SharedResourceKind.SINGLE) writtenKeys.add(key)
        notifyAwaiters(key, value)
        persist()
        true
    }

    suspend fun put(key: String, value: Any?) = mutex.withLock {
        data[key] = value
        if (kind == SharedResourceKind.SINGLE) writtenKeys.add(key)
        notifyAwaiters(key, value)
        persist()
    }

    suspend fun await(key: String): Any? {
        data[key]?.let { return it }
        val deferred = CompletableDeferred<Any?>()
        mutex.withLock {
            data[key]?.let { deferred.complete(it); return@withLock }
            pendingAwaiters.getOrPut(key) { mutableListOf() }.add(deferred)
        }
        return deferred.await()
    }

    fun snapshot(): Map<String, Any?> = data.toMap()

    suspend fun applyDelta(delta: Map<String, Any?>) = mutex.withLock {
        for ((key, value) in delta) {
            if (value == null) {
                data.remove(key)
                writtenKeys.remove(key)
            } else {
                data[key] = value
                if (kind == SharedResourceKind.SINGLE) writtenKeys.add(key)
            }
        }
        persist()
    }

    private fun notifyAwaiters(key: String, value: Any?) {
        val awaiters = pendingAwaiters.remove(key) ?: return
        awaiters.forEach { it.complete(value) }
    }

    private fun load() {
        val storage = storage ?: return
        val raw = storage.getItem(storageKey(name)) as? String ?: return
        val element = json.parseToJsonElement(raw)
        val parsed = fromJsonElement(element)
        val obj = parsed as? Map<*, *> ?: return
        for ((k, v) in obj) {
            val key = k?.toString() ?: continue
            data[key] = v
            if (kind == SharedResourceKind.SINGLE) {
                writtenKeys.add(key)
            }
        }
    }

    private fun persist() {
        val storage = storage ?: return
        val element = toJsonElement(data)
        storage.setItem(storageKey(name), json.encodeToString(element))
    }
}

class DefaultSharedStore : SharedStore {
    private val resources = mutableMapOf<String, SharedResource>()
    private val storage = if (hasLocalStorage()) localStorage() else null

    override suspend fun get(resource: String, key: String): Any? =
        resources[resource]?.get(key) ?: error("Unknown shared resource: $resource")

    override suspend fun setOnce(resource: String, key: String, value: Any?): Boolean =
        (resources[resource] ?: error("Unknown shared resource: $resource")).setOnce(key, value)

    override suspend fun put(resource: String, key: String, value: Any?) {
        (resources[resource] ?: error("Unknown shared resource: $resource")).put(key, value)
    }

    override fun snapshot(): Map<String, Map<String, Any?>> =
        resources.mapValues { (_, res) -> res.snapshot() }

    override suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>) {
        for ((name, update) in delta) {
            resources[name]?.applyDelta(update)
        }
    }

    override suspend fun await(resource: String, key: String): Any? =
        (resources[resource] ?: error("Unknown shared resource: $resource")).await(key)

    override fun hasResource(resource: String): Boolean = resources.containsKey(resource)

    override fun addResource(resource: String, kind: SharedResourceKind) {
        resources[resource] = SharedResource(resource, kind, storage)
    }
}

private fun fromJsonElement(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> fromPrimitive(element)
    is JsonObject -> LinkedHashMap<String, Any?>().apply {
        for ((k, v) in element) {
            this[k] = fromJsonElement(v)
        }
    }
    is JsonArray -> ArrayList<Any?>(element.size).apply {
        element.forEach { add(fromJsonElement(it)) }
    }
}

private fun fromPrimitive(primitive: JsonPrimitive): Any? {
    primitive.booleanOrNull?.let { return it }
    primitive.longOrNull?.let { return it }
    primitive.doubleOrNull?.let { return it }
    return primitive.content
}

private fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Byte -> JsonPrimitive(value)
    is Short -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(buildMap {
        value.entries.forEach { (k, v) ->
            val key = k?.toString() ?: "null"
            put(key, toJsonElement(v))
        }
    })
    is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
    is Array<*> -> JsonArray(value.map { toJsonElement(it) })
    is Sequence<*> -> JsonArray(value.map { toJsonElement(it) }.toList())
    else -> JsonPrimitive(value.toString())
}
