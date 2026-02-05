package io.github.ehlyzov.branchline.runtime

@Suppress("UNCHECKED_CAST")
public actual fun base64Encode(bytes: ByteArray): String {
    val builder = StringBuilder(bytes.size)
    for (b in bytes) {
        builder.append((b.toInt() and 0xFF).toChar())
    }
    val btoa = js("btoa") as (String) -> String
    return btoa(builder.toString())
}

@Suppress("UNCHECKED_CAST")
public actual fun base64Decode(text: String): ByteArray {
    val atob = js("atob") as (String) -> String
    val decoded = try {
        atob(text)
    } catch (_: dynamic) {
        throw IllegalArgumentException("Invalid base64 input")
    }
    val out = ByteArray(decoded.length)
    for (i in decoded.indices) {
        out[i] = decoded[i].code.toByte()
    }
    return out
}
