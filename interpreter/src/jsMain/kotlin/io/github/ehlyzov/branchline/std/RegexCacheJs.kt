package io.github.ehlyzov.branchline.std

private const val REGEX_CACHE_LIMIT = 64
private val regexCache: MutableMap<String, Regex> = HashMap()

internal actual fun cachedRegex(pat: String): Regex {
    val cached = regexCache[pat]
    if (cached != null) return cached
    if (regexCache.size >= REGEX_CACHE_LIMIT) regexCache.clear()
    val compiled = pat.toRegex()
    regexCache[pat] = compiled
    return compiled
}
