package io.github.ehlyzov.branchline.runtime

import java.util.Base64

public actual fun base64Encode(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

public actual fun base64Decode(text: String): ByteArray =
    Base64.getDecoder().decode(text)
