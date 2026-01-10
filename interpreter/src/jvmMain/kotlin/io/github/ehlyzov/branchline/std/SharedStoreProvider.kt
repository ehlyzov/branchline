package io.github.ehlyzov.branchline.std

import kotlin.jvm.Volatile

/**
 * Global provider for SharedStore to be used by VM host functions.
 * Tests or embedders should set [store] before running code that uses AWAIT_SHARED.
 */
actual object SharedStoreProvider {
    @Volatile
    actual var store: SharedStore? = null
}
