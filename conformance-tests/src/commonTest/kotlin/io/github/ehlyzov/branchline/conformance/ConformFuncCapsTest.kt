package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ir.buildRunnerFromProgramMP
import io.github.ehlyzov.branchline.std.DEFAULT_SHARED_KEY
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStore
import io.github.ehlyzov.branchline.std.SharedStoreProvider
import io.github.ehlyzov.branchline.std.SharedStoreSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConformFuncCapsTest {

    @Test
    fun func_can_mutate_object_and_return() {
        val program = """
            FUNC build() {
                LET x = { a: 0 };
                SET x.a = 1;
                RETURN x;
            }
            TRANSFORM T { OUTPUT { v: build() } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("v" to mapOf("a" to 1)), out)
    }

    @Test
    fun func_can_call_pure_host_functions() {
        val program = """
            FUNC keysOf(x) { RETURN KEYS(x); }
            TRANSFORM T { OUTPUT { k: keysOf({ a: 1, b: 2 }) } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("k" to listOf("a", "b")), out)
    }

    @Test
    fun func_cannot_output() {
        val program = """
            FUNC bad() { OUTPUT { x: 1 }; RETURN 1; }
            TRANSFORM T { OUTPUT { v: bad() } }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        assertFailsWith<IllegalStateException> {
            runner(emptyMap())
        }
    }

    @Test
    fun func_can_use_shared() {
        val store = TestSharedStore()
        withSharedStore(store) {
            store.addResource("cache", SharedResourceKind.SINGLE)
            store.setOnceSync("cache", DEFAULT_SHARED_KEY, "ok")
            val program = """
                SHARED cache SINGLE;
                FUNC ok() { RETURN AWAIT_SHARED("cache", "value"); }
                TRANSFORM T { OUTPUT { keys: ok() } }
            """.trimIndent()
            val runner = buildRunnerFromProgramMP(program)
            val out = runner(emptyMap())
            assertEquals(mapOf("keys" to "ok"), out)
        }
    }

    @Test
    fun lambda_mutates_outer_env() {
        val program = """
            TRANSFORM T {
                LET x = 1;
                LET f = () -> { SET x = x + 1; RETURN x; };
                LET y = f();
                OUTPUT { x: x, y: y };
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        val out = runner(emptyMap())
        assertEquals(mapOf("x" to 2, "y" to 2), out)
    }

    @Test
    fun lambda_can_use_shared() {
        val store = TestSharedStore()
        withSharedStore(store) {
            store.addResource("cache", SharedResourceKind.SINGLE)
            store.setOnceSync("cache", DEFAULT_SHARED_KEY, "ok")
            val program = """
                SHARED cache SINGLE;
                TRANSFORM T {
                    LET f = () -> KEYS(cache);
                    OUTPUT { keys: f() };
                }
            """.trimIndent()
            val runner = buildRunnerFromProgramMP(program)
            val out = runner(emptyMap())
            assertEquals(mapOf("keys" to listOf(DEFAULT_SHARED_KEY)), out)
        }
    }

    @Test
    fun lambda_cannot_output() {
        val program = """
            TRANSFORM T {
                LET f = () -> { OUTPUT { x: 1 }; RETURN 1; };
                OUTPUT { v: f() };
            }
        """.trimIndent()
        val runner = buildRunnerFromProgramMP(program)
        assertFailsWith<IllegalStateException> {
            runner(emptyMap())
        }
    }
}

private fun <T> withSharedStore(store: SharedStore, block: () -> T): T {
    val previous = SharedStoreProvider.store
    SharedStoreProvider.store = store
    return try {
        block()
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
