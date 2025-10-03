package v2.std

class StdSharedModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("AWAIT_SHARED") { args ->
            require(args.size == 2) { "AWAIT_SHARED(resource, key)" }
            val resource = args[0] as? String ?: error("AWAIT_SHARED: resource must be string")
            val key = args[1] as? String ?: error("AWAIT_SHARED: key must be string")
            val store = SharedStoreProvider.store ?: error("SharedStore is not configured")
            blockingAwait(store, resource, key)
        }
    }
}

