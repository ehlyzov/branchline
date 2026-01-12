package io.github.ehlyzov.branchline.std

/** Multiplatform shared-state store interface. JVM provides DefaultSharedStore. */
interface SharedStore {
    suspend fun lookup(resource: String, key: String): Any?
    suspend fun setOnce(resource: String, key: String, value: Any?): Boolean
    suspend fun put(resource: String, key: String, value: Any?)
    fun snapshot(): Map<String, Map<String, Any?>>
    suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>)
    suspend fun await(resource: String, key: String): Any?
    fun hasResource(resource: String): Boolean
    fun addResource(resource: String, kind: SharedResourceKind)
}

const val DEFAULT_SHARED_KEY = "value"

interface SharedStoreSync {
    fun setOnceSync(resource: String, key: String, value: Any?): Boolean
    fun putSync(resource: String, key: String, value: Any?)
}

enum class SharedResourceKind { SINGLE, MANY }

expect object SharedStoreProvider {
    var store: SharedStore?
}

expect fun createDefaultSharedStore(): SharedStore
