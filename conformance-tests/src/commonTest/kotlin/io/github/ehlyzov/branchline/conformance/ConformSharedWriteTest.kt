package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.std.DEFAULT_SHARED_KEY
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import io.github.ehlyzov.branchline.std.SharedStoreSync
import kotlin.test.Test
import kotlin.test.assertEquals

class ConformSharedWriteTest {

    @Test
    fun shared_write_populates_store_and_keys() {
        val store = TestSharedStore()
        val previous = SharedStoreProvider.store
        SharedStoreProvider.store = store
        try {
            val program = """
                SHARED cache SINGLE;
                TRANSFORM T {
                    cache[] = input.value;
                    OUTPUT { keys: KEYS(cache) };
                }
            """.trimIndent()
            val runner = buildRunnerFromProgramMP(program)
            val result = runner(mapOf("value" to "ok")) as Map<*, *>
            assertEquals(listOf(DEFAULT_SHARED_KEY), result["keys"])
            val stored = store.snapshot()["cache"]?.get(DEFAULT_SHARED_KEY)
            assertEquals("ok", stored)
        } finally {
            SharedStoreProvider.store = previous
        }
    }

    private class TestSharedStore : SharedStore, SharedStoreSync {
        private val kinds = LinkedHashMap<String, SharedResourceKind>()
        private val data = LinkedHashMap<String, LinkedHashMap<String, Any?>>()
        private val written = LinkedHashMap<String, MutableSet<String>>()

        override suspend fun lookup(resource: String, key: String): Any? =
            data[resource]?.get(key) ?: error("Unknown shared resource: $resource")

        override suspend fun setOnce(resource: String, key: String, value: Any?): Boolean =
            setOnceSync(resource, key, value)

        override suspend fun put(resource: String, key: String, value: Any?) {
            putSync(resource, key, value)
        }

        override fun snapshot(): Map<String, Map<String, Any?>> =
            data.mapValues { (_, value) -> LinkedHashMap(value) }

        override suspend fun commit(snapshot: Map<String, Map<String, Any?>>, delta: Map<String, Map<String, Any?>>) {
            for ((resource, updates) in delta) {
                val target = data.getOrPut(resource) { LinkedHashMap() }
                for ((key, value) in updates) {
                    if (value == null) {
                        target.remove(key)
                        written[resource]?.remove(key)
                    } else {
                        target[key] = value
                        if (kinds[resource] == SharedResourceKind.SINGLE) {
                            written.getOrPut(resource) { mutableSetOf() }.add(key)
                        }
                    }
                }
            }
        }

        override suspend fun await(resource: String, key: String): Any? = lookup(resource, key)

        override fun hasResource(resource: String): Boolean = kinds.containsKey(resource)

        override fun addResource(resource: String, kind: SharedResourceKind) {
            kinds[resource] = kind
            data.getOrPut(resource) { LinkedHashMap() }
        }

        override fun setOnceSync(resource: String, key: String, value: Any?): Boolean {
            val kind = kinds[resource] ?: error("Unknown shared resource: $resource")
            val already = written.getOrPut(resource) { mutableSetOf() }
            if (kind == SharedResourceKind.SINGLE && key in already) {
                return false
            }
            data.getOrPut(resource) { LinkedHashMap() }[key] = value
            if (kind == SharedResourceKind.SINGLE) {
                already.add(key)
            }
            return true
        }

        override fun putSync(resource: String, key: String, value: Any?) {
            if (!kinds.containsKey(resource)) {
                error("Unknown shared resource: $resource")
            }
            data.getOrPut(resource) { LinkedHashMap() }[key] = value
        }
    }
}
