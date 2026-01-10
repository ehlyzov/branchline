package io.github.ehlyzov.branchline.std

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mapdb.DB
import org.mapdb.DBMaker
import org.mapdb.Serializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent SharedStore backed by MapDB.
 */
class MapDBSharedStore(path: File) : SharedStore, SharedStoreSync {
    private val db: DB = DBMaker.fileDB(path).fileMmapEnableIfSupported().transactionEnable().closeOnJvmShutdown().make()

    private val kinds = db.hashMap("__kinds__", Serializer.STRING, Serializer.STRING).createOrOpen()

    // In-memory awaiters; persistence is not required for wait queues
    private val awaiters = ConcurrentHashMap<String, MutableList<CompletableDeferred<Any?>>>()
    private val mutex = Mutex()

    override suspend fun get(resource: String, key: String): Any? {
        val map = mapOfResource(resource)
        return map[key]
    }

    override suspend fun setOnce(resource: String, key: String, value: Any?): Boolean {
        val kind = kindOf(resource)
        return mutex.withLock {
            val map = mapOfResource(resource)
            if (kind == SharedResourceKind.SINGLE && map.containsKey(key)) return@withLock false
            map[key] = value
            db.commit()
            notifyAwaiters(resource, key, value)
            true
        }
    }

    override suspend fun put(resource: String, key: String, value: Any?) {
        mutex.withLock {
            val map = mapOfResource(resource)
            map[key] = value
            db.commit()
            notifyAwaiters(resource, key, value)
        }
    }

    override fun snapshot(): Map<String, Map<String, Any?>> {
        val out = LinkedHashMap<String, Map<String, Any?>>()
        for (r in kinds.keys) {
            val map = mapOfResource(r)
            @Suppress("UNCHECKED_CAST")
            out[r] = HashMap(map) as Map<String, Any?>
        }
        return out
    }

    override suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>) {
        mutex.withLock {
            for ((r, d) in delta) {
                val map = mapOfResource(r)
                for ((k, v) in d) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
            db.commit()
        }
    }

    override suspend fun await(resource: String, key: String): Any? {
        val cur = get(resource, key)
        if (cur != null) return cur
        val def = CompletableDeferred<Any?>()
        val id = "$resource\u0000$key"
        mutex.withLock {
            val again = get(resource, key)
            if (again != null) return again
            awaiters.computeIfAbsent(id) { mutableListOf() }.add(def)
        }
        return def.await()
    }

    override fun hasResource(resource: String): Boolean = kinds.containsKey(resource)

    override fun addResource(resource: String, kind: SharedResourceKind) {
        kinds[resource] = kind.name
        db.hashMap(resource, Serializer.STRING, Serializer.JAVA).createOrOpen()
        db.commit()
    }

    override fun setOnceSync(resource: String, key: String, value: Any?): Boolean =
        kotlinx.coroutines.runBlocking { setOnce(resource, key, value) }

    override fun putSync(resource: String, key: String, value: Any?) {
        kotlinx.coroutines.runBlocking { put(resource, key, value) }
    }

    private fun kindOf(resource: String): SharedResourceKind =
        kinds[resource]?.let { SharedResourceKind.valueOf(it) }
            ?: throw IllegalArgumentException("Unknown shared resource: $resource")

    @Suppress("UNCHECKED_CAST")
    private fun mapOfResource(resource: String): MutableMap<String, Any?> =
        db.hashMap(resource, Serializer.STRING, Serializer.JAVA).createOrOpen() as MutableMap<String, Any?>

    private fun notifyAwaiters(resource: String, key: String, value: Any?) {
        val id = "$resource\u0000$key"
        awaiters.remove(id)?.forEach { it.complete(value) }
    }
}
