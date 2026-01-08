package io.github.ehlyzov.branchline.std

import kotlin.collections.LinkedHashMap

typealias StdFn = (List<Any?>) -> Any?

class StdRegistry internal constructor() {
    private val map = LinkedHashMap<String, StdFn>()
    private val meta = LinkedHashMap<String, HostFnMetadata>()

    fun fn(name: String, impl: StdFn) {
        register(name, impl, null)
    }

    fun fn(name: String, impl: StdFn, sharedAccess: (List<Any?>) -> Boolean) {
        register(name, impl, HostFnMetadata(sharedAccess))
    }

    fun fnShared(name: String, impl: StdFn) {
        register(name, impl, HostFnMetadata.SHARED_ALWAYS)
    }

    private fun register(name: String, impl: StdFn, metadata: HostFnMetadata?) {
        require(name.isNotBlank()) { "Std fn name must be non-blank" }
        require(!map.containsKey(name)) { "Std fn '$name' already defined" }
        map[name] = impl
        if (metadata != null) {
            meta[name] = metadata
        }
    }

    internal fun build(): Map<String, StdFn> = map.toMap()

    internal fun buildMeta(): Map<String, HostFnMetadata> = meta.toMap()
}

interface StdModule { fun register(r: StdRegistry) }

expect fun loadStdModules(registry: StdRegistry)

object StdLib {
    private data class Bundle(
        val fns: Map<String, StdFn>,
        val meta: Map<String, HostFnMetadata>,
    )

    private val bundle: Bundle by lazy {
        val registry = StdRegistry()
        loadStdModules(registry)
        Bundle(
            fns = registry.build(),
            meta = registry.buildMeta(),
        )
    }

    val fns: Map<String, StdFn> get() = bundle.fns

    val meta: Map<String, HostFnMetadata> get() = bundle.meta

    val names: Set<String> get() = bundle.fns.keys
}
