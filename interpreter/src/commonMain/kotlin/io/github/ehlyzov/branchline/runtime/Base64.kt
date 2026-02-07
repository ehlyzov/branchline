package io.github.ehlyzov.branchline.runtime

public expect fun base64Encode(bytes: ByteArray): String

public expect fun base64Decode(text: String): ByteArray
