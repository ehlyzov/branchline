package v2.ir

import v2.FuncDecl
import v2.Mode
import v2.DEFAULT_INPUT_ALIAS
import v2.COMPAT_INPUT_ALIASES

actual class TransformRegistry actual constructor(
    private val funcs: Map<String, FuncDecl>,
    private val hostFns: Map<String, (List<Any?>) -> Any?>,
    private val transforms: Map<String, TransformDescriptor>,
) {
    private val cache = mutableMapOf<String, (Map<String, Any?>) -> Any?>()

    actual fun get(name: String): (Map<String, Any?>) -> Any? =
        cache.getOrPut(name) {
            val descriptor = transforms[name] ?: error("Transform '$name' not found")
            val decl = descriptor.decl
            require(decl.mode == Mode.BUFFER) { "Only buffer mode is supported" }
            val ir = ToIR(funcs, hostFns).compile(decl.body.statements)
            val evalFn = makeEval(hostFns, funcs, this)
            val exec = Exec(ir, evalFn)
            val runner: (Map<String, Any?>) -> Any? = { input: Map<String, Any?> ->
                val env = HashMap<String, Any?>().apply {
                    this[DEFAULT_INPUT_ALIAS] = input
                    putAll(input)
                    for (alias in COMPAT_INPUT_ALIASES) {
                        this[alias] = input
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
