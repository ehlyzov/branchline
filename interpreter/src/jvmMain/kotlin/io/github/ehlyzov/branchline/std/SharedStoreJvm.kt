package io.github.ehlyzov.branchline.std

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class SharedResource(
    val name: String,
    val kind: SharedResourceKind
) {
    private val data = ConcurrentHashMap<String, Any?>()
    private val writtenKeys = ConcurrentHashMap.newKeySet<String>()
    private val pendingAwaiters = ConcurrentHashMap<String, MutableList<CompletableDeferred<Any?>>>()
    private val mutex = Mutex()

    suspend fun get(key: String): Any? = data[key]

    suspend fun setOnce(key: String, value: Any?): Boolean = mutex.withLock {
        if (kind == SharedResourceKind.SINGLE && writtenKeys.contains(key)) {
            false
        } else {
            data[key] = value
            if (kind == SharedResourceKind.SINGLE) writtenKeys.add(key)
            pendingAwaiters[key]?.let { awaiters ->
                awaiters.forEach { it.complete(value) }
                pendingAwaiters.remove(key)
            }
            true
        }
    }

    suspend fun put(key: String, value: Any?) = mutex.withLock {
        data[key] = value
        pendingAwaiters[key]?.let { awaiters ->
            awaiters.forEach { it.complete(value) }
            pendingAwaiters.remove(key)
        }
    }

    suspend fun await(key: String): Any? {
        data[key]?.let { return it }
        val deferred = CompletableDeferred<Any?>()
        mutex.withLock {
            data[key]?.let { deferred.complete(it); return@withLock }
            pendingAwaiters.computeIfAbsent(key) { mutableListOf() }.add(deferred)
        }
        return deferred.await()
    }

    fun snapshot(): Map<String, Any?> = data.toMap()

    suspend fun applyDelta(delta: Map<String, Any?>) = mutex.withLock {
        for ((key, value) in delta) {
            if (value == null) {
                data.remove(key)
                if (kind == SharedResourceKind.SINGLE) writtenKeys.remove(key)
            } else {
                data[key] = value
                if (kind == SharedResourceKind.SINGLE) writtenKeys.add(key)
            }
        }
    }
}

class DefaultSharedStore : SharedStore, SharedStoreSync {
    private val resources = ConcurrentHashMap<String, SharedResource>()
    private val globalLock = ReentrantReadWriteLock()

    override suspend fun get(resource: String, key: String): Any? =
        resources[resource]?.get(key) ?: throw IllegalArgumentException("Unknown shared resource: $resource")

    override suspend fun setOnce(resource: String, key: String, value: Any?): Boolean =
        (resources[resource] ?: throw IllegalArgumentException("Unknown shared resource: $resource")).setOnce(key, value)

    override suspend fun put(resource: String, key: String, value: Any?) {
        (resources[resource] ?: throw IllegalArgumentException("Unknown shared resource: $resource")).put(key, value)
    }

    override fun snapshot(): Map<String, Map<String, Any?>> = globalLock.read {
        resources.mapValues { (_, res) -> res.snapshot() }
    }

    override suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>) {
        globalLock.write {
            for ((name, d) in delta) resources[name]?.applyDelta(d)
        }
    }

    override suspend fun await(resource: String, key: String): Any? =
        (resources[resource] ?: throw IllegalArgumentException("Unknown shared resource: $resource")).await(key)

    override fun hasResource(resource: String): Boolean = resources.containsKey(resource)

    override fun addResource(resource: String, kind: SharedResourceKind) {
        resources[resource] = SharedResource(resource, kind)
    }

    override fun setOnceSync(resource: String, key: String, value: Any?): Boolean =
        kotlinx.coroutines.runBlocking { setOnce(resource, key, value) }

    override fun putSync(resource: String, key: String, value: Any?) {
        kotlinx.coroutines.runBlocking { put(resource, key, value) }
    }
}

actual fun createDefaultSharedStore(): SharedStore = DefaultSharedStore()
