package io.github.ehlyzov.branchline.std

private const val REGEX_CACHE_LIMIT = 64
private val regexCache: MutableMap<String, Regex> = HashMap()

// MATCH/REPLACE are also called from compiled CLI fan-out workers.
internal actual fun cachedRegex(pat: String): Regex = synchronized(regexCache) {
    val cached = regexCache[pat]
    if (cached != null) return@synchronized cached
    if (regexCache.size >= REGEX_CACHE_LIMIT) regexCache.clear()
    val compiled = pat.toRegex()
    regexCache[pat] = compiled
    compiled
}
