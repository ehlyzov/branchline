package v2.std

import kotlin.collections.LinkedHashMap

typealias StdFn = (List<Any?>) -> Any?

class StdRegistry internal constructor() {
    private val map = LinkedHashMap<String, StdFn>()

    fun fn(name: String, impl: StdFn) {
        require(name.isNotBlank()) { "Std fn name must be non-blank" }
        require(map.putIfAbsent(name, impl) == null) { "Std fn '$name' already defined" }
    }

    internal fun build(): Map<String, StdFn> = map.toMap()
}

interface StdModule { fun register(r: StdRegistry) }

expect fun loadStdModules(registry: StdRegistry)

object StdLib {
    val fns: Map<String, StdFn> by lazy {
        val registry = StdRegistry()
        loadStdModules(registry)
        registry.build()
    }

    val names: Set<String> get() = fns.keys
}

