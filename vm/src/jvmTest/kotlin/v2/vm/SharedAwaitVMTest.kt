package v2.vm

import v2.ExecutionEngine
import v2.testutils.compileProgramAndRun
import v2.std.DefaultSharedStore
import v2.std.SharedResourceKind
import v2.std.SharedStoreProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedAwaitVMTest {

    @AfterTest
    fun cleanup() { SharedStoreProvider.store = null }

    @Test
    fun await_shared_returns_preexisting_value() {
        val store = DefaultSharedStore().apply {
            addResource("cache", SharedResourceKind.SINGLE)
        }
        // Prepopulate so await returns immediately
        kotlinx.coroutines.runBlocking { store.setOnce("cache", "k", 123) }
        SharedStoreProvider.store = store

        val out = compileProgramAndRun(
            """
            SHARED cache SINGLE;
            TRANSFORM T { LET v = AWAIT cache.k;
                OUTPUT { v: v }
            }
            """.trimIndent(),
            engine = ExecutionEngine.VM
        )
        assertEquals(mapOf("v" to 123), out)
    }
}
