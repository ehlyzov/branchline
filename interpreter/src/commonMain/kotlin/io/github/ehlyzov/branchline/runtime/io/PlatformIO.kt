package io.github.ehlyzov.branchline.runtime.io

/**
 * Minimal portable I/O facade for writing text files.
 * Implemented per platform via expect/actual.
 */
expect fun writeText(path: String, text: String)
expect fun readText(path: String): String
