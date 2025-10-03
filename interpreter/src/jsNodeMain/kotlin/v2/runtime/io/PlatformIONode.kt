@file:Suppress("UnsafeCastFromDynamic")

package v2.runtime.io

actual fun writeText(path: String, text: String) {
    val fs: dynamic = js("require('fs')")
    val pathMod: dynamic = js("require('path')")
    val dir = pathMod.dirname(path)
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, js("({ recursive: true })"))
    }
    fs.writeFileSync(path, text, "utf8")
}

actual fun readText(path: String): String {
    val fs: dynamic = js("require('fs')")
    return fs.readFileSync(path, "utf8")
}

