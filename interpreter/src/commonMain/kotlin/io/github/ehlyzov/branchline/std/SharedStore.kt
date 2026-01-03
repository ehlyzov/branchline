package io.github.ehlyzov.branchline.std

/** Multiplatform shared-state store interface. JVM provides DefaultSharedStore. */
interface SharedStore {
    suspend fun get(resource: String, key: String): Any?
    suspend fun setOnce(resource: String, key: String, value: Any?): Boolean
    suspend fun put(resource: String, key: String, value: Any?)
    fun snapshot(): Map<String, Map<String, Any?>>
    suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>)
    suspend fun await(resource: String, key: String): Any?
    fun hasResource(resource: String): Boolean
    fun addResource(resource: String, kind: SharedResourceKind)
}

enum class SharedResourceKind { SINGLE, MANY }

expect object SharedStoreProvider {
    var store: SharedStore?
}
