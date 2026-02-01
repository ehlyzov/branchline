package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.std.HostFnMetadata
import io.github.ehlyzov.branchline.std.SharedResourceHandle
import io.github.ehlyzov.branchline.std.SharedResourceKind
import io.github.ehlyzov.branchline.std.SharedStoreProvider

actual class TransformRegistry actual constructor(
    private val funcs: Map<String, FuncDecl>,
    private val hostFns: Map<String, (List<Any?>) -> Any?>,
    private val hostFnMeta: Map<String, HostFnMetadata>,
    private val transforms: Map<String, TransformDescriptor>,
) {
    private val cache = mutableMapOf<String, (Map<String, Any?>) -> Any?>()

    actual fun get(name: String): (Map<String, Any?>) -> Any? =
        cache.getOrPut(name) {
            val descriptor = transforms[name] ?: error("Transform '$name' not found")
            val decl = descriptor.decl
            val sharedDecls = decl.options.shared
            SharedStoreProvider.store?.let { store ->
                for (sharedDecl in sharedDecls) {
                    val kind = when (sharedDecl.kind) {
                        io.github.ehlyzov.branchline.SharedKind.SINGLE -> SharedResourceKind.SINGLE
                        io.github.ehlyzov.branchline.SharedKind.MANY -> SharedResourceKind.MANY
                    }
                    if (!store.hasResource(sharedDecl.name)) {
                        store.addResource(sharedDecl.name, kind)
                    }
                }
            }
            val ir = ToIR(funcs, hostFns).compile(decl.body.statements)
            val exec = Exec(
                ir = ir,
                hostFns = hostFns,
                hostFnMeta = hostFnMeta,
                funcs = funcs,
                sharedStore = SharedStoreProvider.store,
            )
            val runner: (Map<String, Any?>) -> Any? = { input: Map<String, Any?> ->
                val env = HashMap<String, Any?>().apply {
                    this[DEFAULT_INPUT_ALIAS] = input
                    putAll(input)
                    for (alias in COMPAT_INPUT_ALIASES) {
                        this[alias] = input
                    }
                    for (sharedDecl in sharedDecls) {
                        if (!containsKey(sharedDecl.name)) {
                            this[sharedDecl.name] = SharedResourceHandle(sharedDecl.name)
                        }
                    }
                }
                val produced = exec.run(env)
                produced ?: error("No OUTPUT")
            }
            runner
        }

    actual fun getOrNull(name: String): ((Map<String, Any?>) -> Any?)? =
        if (transforms.containsKey(name)) get(name) else null

    actual fun exists(name: String): Boolean = transforms.containsKey(name)

    actual fun descriptor(name: String): TransformDescriptor? = transforms[name]
}
