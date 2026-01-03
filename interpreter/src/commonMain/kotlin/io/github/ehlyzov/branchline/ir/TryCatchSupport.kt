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
    env: MutableMap<String, Any?>,
    name: String,
    errorValue: Map<String, Any?>,
    block: () -> T,
): T {
    val had = env.containsKey(name)
    val prev = env[name]
    env[name] = errorValue
    return try {
        block()
    } finally {
        if (had) {
            env[name] = prev
        } else {
            env.remove(name)
        }
    }
}
