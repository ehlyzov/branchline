package io.github.ehlyzov.branchline.ir

internal fun buildErrorValue(error: Throwable): Map<String, Any?> {
    val message = error.message ?: error.toString()
    val type = error::class.simpleName ?: "Exception"
    return mapOf(
        "message" to message,
        "type" to type,
    )
}

internal fun <T> withCatchBinding(
    env: Env,
    name: String,
    errorValue: Map<String, Any?>,
    block: () -> T,
): T {
    val scope = env.resolveScope(name)
    val had = scope != null
    val prev = if (had) scope.getLocal(name) else null
    val target = scope ?: env
    target.setLocal(name, errorValue)
    return try {
        block()
    } finally {
        if (had) {
            target.setLocal(name, prev)
        } else {
            env.removeLocal(name)
        }
    }
}
