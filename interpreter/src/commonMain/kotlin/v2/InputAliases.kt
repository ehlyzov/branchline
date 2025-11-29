package v2

/**
 * Canonical input binding name plus compatibility aliases.
 * - `DEFAULT_INPUT_ALIAS` should be used for new programs and helpers.
 * - `COMPAT_INPUT_ALIASES` are kept to avoid breaking existing scripts.
 */
const val DEFAULT_INPUT_ALIAS: String = "input"
val COMPAT_INPUT_ALIASES: Set<String> = setOf("row")
