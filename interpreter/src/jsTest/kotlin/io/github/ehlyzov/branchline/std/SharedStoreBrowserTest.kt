package io.github.ehlyzov.branchline.std

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedStoreBrowserTest {

    private fun hasLocalStorage(): Boolean =
        js("typeof localStorage !== 'undefined' && localStorage != null") as Boolean

    private fun localStorage(): dynamic = js("localStorage")

    private fun clearSharedKeys() {
        if (!hasLocalStorage()) return
        val storage = localStorage()
        val keys = mutableListOf<String>()
        val length = storage.length as Int
        for (i in 0 until length) {
            val key = storage.key(i) as? String ?: continue
            if (key.startsWith(LOCAL_STORAGE_PREFIX)) {
                keys.add(key)
            }
        }
        keys.forEach { storage.removeItem(it) }
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun `browser shared store writes to localStorage`() = GlobalScope.promise {
        if (!hasLocalStorage()) return@promise
        clearSharedKeys()

        val store = DefaultSharedStore()
        store.addResource("session", SharedResourceKind.MANY)
        store.put("session", "user", "nova")

        val raw = localStorage().getItem("${LOCAL_STORAGE_PREFIX}session") as? String
        assertNotNull(raw)
        assertTrue(raw.contains("\"user\""))
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun `browser shared store reads from localStorage`() = GlobalScope.promise {
        if (!hasLocalStorage()) return@promise
        clearSharedKeys()

        val writer = DefaultSharedStore()
        writer.addResource("session", SharedResourceKind.MANY)
        writer.put("session", "user", "aria")

        val reader = DefaultSharedStore()
        reader.addResource("session", SharedResourceKind.MANY)
        val value = reader.get("session", "user")
        assertEquals("aria", value)
    }
}
