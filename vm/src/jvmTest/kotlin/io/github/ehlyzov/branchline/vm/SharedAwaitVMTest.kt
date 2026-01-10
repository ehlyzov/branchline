package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.compileProgramAndRun
import io.github.ehlyzov.branchline.std.DefaultSharedStore
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider
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
