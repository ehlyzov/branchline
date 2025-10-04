@file:Suppress("UnsafeCastFromDynamic")

package v2.runtime.io

private fun resolveWriteText(): dynamic = js(
    "(function() {" +
        "if (typeof globalThis !== 'undefined' && typeof globalThis.__blWriteText === 'function') {" +
            "return globalThis.__blWriteText;" +
        "}" +
        "var req = null;" +
        "try {" +
            "if (typeof globalThis !== 'undefined' && typeof globalThis.require === 'function') { req = globalThis.require; }" +
            "else if (typeof require === 'function') { req = require; }" +
            "else if (typeof globalThis !== 'undefined' && typeof globalThis.eval === 'function') { req = globalThis.eval('require'); }" +
        "} catch (e) { req = null; }" +
        "if (req) {" +
            "var fs = req('fs');" +
            "var p = req('path');" +
            "var fn = function(path, text) {" +
                "var dir = p.dirname(path);" +
                "if (!fs.existsSync(dir)) { fs.mkdirSync(dir, { recursive: true }); }" +
                "fs.writeFileSync(path, text, 'utf8');" +
            "};" +
            "if (typeof globalThis !== 'undefined') { globalThis.__blWriteText = fn; }" +
            "return fn;" +
        "}" +
        "return null;" +
    "})();"
)

private fun resolveReadText(): dynamic = js(
    "(function() {" +
        "if (typeof globalThis !== 'undefined' && typeof globalThis.__blReadText === 'function') {" +
            "return globalThis.__blReadText;" +
        "}" +
        "var req = null;" +
        "try {" +
            "if (typeof globalThis !== 'undefined' && typeof globalThis.require === 'function') { req = globalThis.require; }" +
            "else if (typeof require === 'function') { req = require; }" +
            "else if (typeof globalThis !== 'undefined' && typeof globalThis.eval === 'function') { req = globalThis.eval('require'); }" +
        "} catch (e) { req = null; }" +
        "if (req) {" +
            "var fs = req('fs');" +
            "var fn = function(path) { return fs.readFileSync(path, 'utf8'); };" +
            "if (typeof globalThis !== 'undefined') { globalThis.__blReadText = fn; }" +
            "return fn;" +
        "}" +
        "return null;" +
    "})();"
)

actual fun writeText(path: String, text: String) {
    val fn = resolveWriteText()
    if (fn != null) {
        fn(path, text)
    } else {
        throw UnsupportedOperationException("writeText is not available in this JS runtime")
    }
}

actual fun readText(path: String): String {
    val fn = resolveReadText()
    if (fn != null) {
        val res = fn(path)
        return res as String
    } else {
        throw UnsupportedOperationException("readText is not available in this JS runtime")
    }
}
