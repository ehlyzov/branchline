package io.github.ehlyzov.branchline.ir

public class Env(
    private val locals: MutableMap<String, Any?> = LinkedHashMap(),
    private val parent: Env? = null,
) {
    public fun get(name: String): Any? =
        if (locals.containsKey(name)) locals[name] else parent?.get(name)

    public fun getLocal(name: String): Any? = locals[name]

    public fun contains(name: String): Boolean =
        locals.containsKey(name) || parent?.contains(name) == true

    public fun resolveScope(name: String): Env? =
        if (locals.containsKey(name)) this else parent?.resolveScope(name)

    public fun setLocal(name: String, value: Any?) {
        locals[name] = value
    }

    public fun setExisting(name: String, value: Any?): Boolean {
        val scope = resolveScope(name) ?: return false
        scope.locals[name] = value
        return true
    }

    public fun setOrDefine(name: String, value: Any?) {
        if (!setExisting(name, value)) {
            locals[name] = value
        }
    }

    public fun removeLocal(name: String) {
        locals.remove(name)
    }
}
