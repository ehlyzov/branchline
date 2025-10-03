package v2.runtime.io

actual fun writeText(path: String, text: String) {
    throw UnsupportedOperationException("writeText is not supported in browser JS")
}

actual fun readText(path: String): String {
    throw UnsupportedOperationException("readText is not supported in browser JS")
}

