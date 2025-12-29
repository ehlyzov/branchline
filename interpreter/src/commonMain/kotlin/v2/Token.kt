package v2

import kotlinx.serialization.Serializable

/** A token with positional metadata (line & column for diagnostics). */
@Serializable
public data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int,
)

@Serializable
public data class TokenSpan(
    val start: Token,
    val end: Token,
)
