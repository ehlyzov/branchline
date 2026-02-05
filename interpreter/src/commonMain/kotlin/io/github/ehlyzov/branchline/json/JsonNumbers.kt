package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.bitLength

public enum class JsonNumberMode(val id: String) {
    STRICT("strict"),
    SAFE("safe"),
    EXTENDED("extended");

    public companion object {
        public fun parse(value: String): JsonNumberMode = when (value.lowercase()) {
            "strict" -> STRICT
            "safe" -> SAFE
            "extended" -> EXTENDED
            else -> throw IllegalArgumentException("Unknown JSON number mode '$value'")
        }
    }
}

public data class JsonParseOptions(
    val numberMode: JsonNumberMode = JsonNumberMode.SAFE,
)

public data class JsonOutputOptions(
    val numberMode: JsonNumberMode = JsonNumberMode.SAFE,
)

public const val JSON_SAFE_INTEGER_MAX: Long = 9_007_199_254_740_991L

public fun isJsonSafeInteger(value: Long): Boolean =
    value in -JSON_SAFE_INTEGER_MAX..JSON_SAFE_INTEGER_MAX

public fun isJsonSafeInteger(value: BLBigInt): Boolean =
    value.bitLength() <= 53
