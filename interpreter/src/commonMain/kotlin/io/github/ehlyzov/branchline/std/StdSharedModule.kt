package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.std.blockingAwait

class StdSharedModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("AWAIT_SHARED") { args ->
            require(args.size == 2) { "AWAIT_SHARED(resource, key)" }
            val resource = args[0] as? String ?: error("AWAIT_SHARED: resource must be string")
            val key = args[1] as? String ?: error("AWAIT_SHARED: key must be string")
            val store = SharedStoreProvider.store ?: error("SharedStore is not configured")
            blockingAwait(store, resource, key)
        }
        r.fn("SHARED_WRITE") { args ->
            require(args.size == 3) { "SHARED_WRITE(resource, key, value)" }
            val resource = args[0] as? String ?: error("SHARED_WRITE: resource must be string")
            val key = args[1] as? String ?: error("SHARED_WRITE: key must be string")
            val value = args[2]
            val store = SharedStoreProvider.store ?: error("SharedStore is not configured")
            val sync = store as? SharedStoreSync ?: error("SharedStore does not support sync writes")
            val ok = sync.setOnceSync(resource, key, value)
            if (!ok) {
                error("SHARED_WRITE: $resource[$key] is already set")
            }
            value
        }
    }
}
