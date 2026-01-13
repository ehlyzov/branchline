package io.github.ehlyzov.branchline

/*
 * Branchline DSL – Core Grammar and Reference Lexer (updated)
 * --------------------------------------------------------
 * Added control‑flow keywords ABORT and THROW for coroutine termination
 * and exception escalation. Introduced DOLLAR (`$`), PIPE (`|`) and
 * CONCAT (`++`) tokens for variable references, piping and
 * concatenation.
 * --------------------------------------------------------
 * 1. Grammar excerpt (only deltas shown)
 * --------------------------------------------------------
 * statement       ::= letStmt | ifStmt | forStmt | tryStmt | callStmt
 *                  | sharedWrite | suspendStmt | abortStmt | throwStmt
 *                  | nestedOutput | expressionStmt | ';' ;
 *
 * abortStmt       ::= 'ABORT' expression? ';' ;
 * throwStmt       ::= 'THROW' expression  ';' ;
 *
 * unary           ::= ( '!' | '-' | 'AWAIT' | 'SUSPEND' ) unary | primary ;
 * specialToken    ::= '$' | '|' | '++' ;
 * --------------------------------------------------------
 */

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.lang.StringBuilder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LexerTest {

    private fun lex(src: String): List<Token> = Lexer(src).lex()

    @Test
    fun `tokenises ABORT and THROW`() {
        val src = "ABORT \"oops\"; THROW error;"
        val types = Lexer(src).lex().map { it.type }
        val expected = listOf(
            TokenType.ABORT,
            TokenType.STRING,
            TokenType.SEMICOLON,
            TokenType.THROW,
            TokenType.IDENTIFIER,
            TokenType.SEMICOLON,
            TokenType.EOF
        )
        assertEquals(expected, types)
    }

    // ------------------------------------------------------------------
    // BASIC TOKEN RECOGNITION
    // ------------------------------------------------------------------

    @Test
    fun `recognizes single-character tokens`() {
        val input = "(){}[],.;:?+-*/%<> ="
        val expected = listOf(
            TokenType.LEFT_PAREN, TokenType.RIGHT_PAREN,
            TokenType.LEFT_BRACE, TokenType.RIGHT_BRACE,
            TokenType.LEFT_BRACKET, TokenType.RIGHT_BRACKET,
            TokenType.COMMA, TokenType.DOT, TokenType.SEMICOLON,
            TokenType.COLON, TokenType.QUESTION,
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR,
            TokenType.SLASH, TokenType.PERCENT,
            TokenType.LT, TokenType.GT, TokenType.ASSIGN,
            TokenType.EOF
        )
        val tokens = lex(input).map { it.type }
        assertEquals(expected, tokens)
    }

    @Test
    fun `recognizes composite tokens`() {
        val input = "== != <= >= -> => && || ??"
        val expected = listOf(
            TokenType.EQ, TokenType.NEQ, TokenType.LE, TokenType.GE,
            TokenType.ARROW, TokenType.ARROW, TokenType.AND, TokenType.OR, TokenType.COALESCE,
            TokenType.EOF
        )
        val actual = lex(input).map { it.type }
        assertEquals(expected, actual)
    }

    @Test
    fun `recognizes dollar pipe and concat tokens`() {
        val tokens = lex("$ | ++").map { it.type }
        val expected = listOf(
            TokenType.DOLLAR,
            TokenType.PIPE,
            TokenType.CONCAT,
            TokenType.EOF
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun `keywords are case-insensitive`() {
        val tokens = lex("source SOURCE Source")
        assertEquals(TokenType.SOURCE, tokens[0].type)
        assertEquals(TokenType.SOURCE, tokens[1].type)
        assertEquals(TokenType.SOURCE, tokens[2].type)
    }

    // ------------------------------------------------------------------
    // STRING LITERALS
    // ------------------------------------------------------------------

    @Test
    fun `string with escape sequences`() {
        val token = lex("\"a\\n\\t\\\\b\"")[0]
        assertEquals(TokenType.STRING, token.type)
        assertEquals("\"a\n\t\\b\"", token.lexeme)
    }

    @Test
    fun `unicode escape inside string`() {
        val token = lex("\"\\u0041\"")[0]
        assertEquals(TokenType.STRING, token.type)
        assertEquals("\"\u0041\"", token.lexeme)
    }

    @Test
    fun `string ending with escape character throws`() {
        assertThrows<StringIndexOutOfBoundsException> {
            Lexer("\"abc\\").lex()
        }
    }

    @Test
    fun `newline inside string literal should throw`() {
        assertTimeoutPreemptively(200.milliseconds.toJavaDuration()) {
            assertThrows<IllegalArgumentException> {
                Lexer("\"line1\nline2\"").lex()
            }
        }
    }

    // ------------------------------------------------------------------
    // COMMENTS & POSITION TRACKING
    // ------------------------------------------------------------------

    @Test
    fun `multi-line block comment is ignored and line counting preserved`() {
        val src = """/*
                       a
                       multi
                       line
                       comment
                     */
                     SOURCE x;
        """.trimIndent()
        val tokens = lex(src)
        val sourceToken = tokens.first { it.type == TokenType.SOURCE }
        assertEquals(7, sourceToken.line)
    }

    @Test
    fun `positions column tracking`() {
        val src = "  foo\n    bar"
        val tokens = lex(src)
        val foo = tokens[0]
        val bar = tokens.first { it.lexeme == "bar" }
        assertEquals(1, foo.line)
        assertEquals(3, foo.column)
        assertEquals(2, bar.line)
        assertEquals(5, bar.column)
    }

    @Test
    fun `unterminated block comment throws`() {
        assertThrows<IllegalArgumentException> { Lexer("/* unterminated").lex() }
    }

    // ------------------------------------------------------------------
    // NUMERIC LITERALS & EDGE CASES
    // ------------------------------------------------------------------

    @Test
    fun `numeric literals integer and decimal`() {
        val tokens = lex("42 3.14")
        assertEquals("42", tokens[0].lexeme)
        assertEquals("3.14", tokens[1].lexeme)
    }

    @Test
    fun `dot-prefixed and dot-suffixed numbers split correctly`() {
        val tokens = lex(".5 1.")
        val types = tokens.map { it.type }
        assertEquals(
            listOf(
                TokenType.DOT,
                TokenType.NUMBER, // . 5
                TokenType.NUMBER,
                TokenType.DOT, // 1 .
                TokenType.EOF
            ),
            types
        )
        assertEquals("5", tokens[1].lexeme)
        assertEquals("1", tokens[2].lexeme)
    }

    // ------------------------------------------------------------------
    // MISCELLANEOUS ADVANCED
    // ------------------------------------------------------------------

    @Test
    fun `arrow and coalesce tokens with surrounding whitespace`() {
        val tokens = lex("foo ?? bar -> baz")
        val expected = listOf(
            TokenType.IDENTIFIER,
            TokenType.COALESCE,
            TokenType.IDENTIFIER,
            TokenType.ARROW,
            TokenType.IDENTIFIER,
            TokenType.EOF
        )
        assertEquals(expected, tokens.map { it.type })
    }

    @Test
    fun `identifier with underscores and digits`() {
        val tokens = lex("_abc123").map { it.lexeme }
        assertEquals(listOf("_abc123", ""), tokens) // last lexeme is "" for EOF
    }

    @Test
    fun `single ampersand throws`() {
        assertThrows<IllegalArgumentException> { Lexer("&").lex() }
    }

    @Test
    fun `invalid character throws`() {
        assertThrows<IllegalArgumentException> { Lexer("@").lex() }
    }

    @Test
    fun `deeply nested braces timeout safe`() {
        val builder = StringBuilder().apply { repeat(20_000) { append('{') } }
        assertTimeoutPreemptively(1.seconds.toJavaDuration()) {
            Lexer(builder.toString()).lex()
        }
    }
}
