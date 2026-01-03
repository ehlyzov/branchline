package io.github.ehlyzov.branchline.std

actual object SharedStoreProvider {
    actual var store: SharedStore? = DefaultSharedStore()
}
