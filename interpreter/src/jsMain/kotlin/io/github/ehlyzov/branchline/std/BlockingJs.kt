package io.github.ehlyzov.branchline.std

actual fun blockingAwait(store: SharedStore, resource: String, key: String): Any? {
    val resourceData = store.snapshot()[resource]
        ?: error("Unknown shared resource: $resource")
    return resourceData[key]
}
