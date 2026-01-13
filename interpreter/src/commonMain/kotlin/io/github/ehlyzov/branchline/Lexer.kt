package io.github.ehlyzov.branchline

class Lexer(private val source: String) {
    private var index = 0
    private var line = 1
    private var column = 1

    private val tokens = mutableListOf<Token>()

    private val keywords = mapOf(
        "SOURCE" to TokenType.SOURCE,
        "OUTPUT" to TokenType.OUTPUT,
        "USING" to TokenType.USING,
        "TRANSFORM" to TokenType.TRANSFORM,
        "STREAM" to TokenType.STREAM,
        "BUFFER" to TokenType.BUFFER,
        "FOR" to TokenType.FOR,
        "EACH" to TokenType.EACH,
        "CASE" to TokenType.CASE,
        "WHEN" to TokenType.WHEN,
        "IF" to TokenType.IF,
        "THEN" to TokenType.THEN,
        "ELSE" to TokenType.ELSE,
        "AS" to TokenType.AS,
        "MODIFY" to TokenType.MODIFY,
        "ENUM" to TokenType.ENUM,
        "LET" to TokenType.LET,
        "IN" to TokenType.IN,
        "AWAIT" to TokenType.AWAIT,
        "SUSPEND" to TokenType.SUSPEND,
        "CALL" to TokenType.CALL,
        "TRUE" to TokenType.TRUE,
        "FALSE" to TokenType.FALSE,
        "NULL" to TokenType.NULL,
        "FOREACH" to TokenType.FOREACH,
        "ABORT" to TokenType.ABORT,
        "THROW" to TokenType.THROW,
        "TRY" to TokenType.TRY,
        "CATCH" to TokenType.CATCH,
        "RETRY" to TokenType.RETRY,
        "TIMES" to TokenType.TIMES,
        "BACKOFF" to TokenType.BACKOFF,
        "SHARED" to TokenType.SHARED,
        "SINGLE" to TokenType.SINGLE,
        "MANY" to TokenType.MANY,
        "FUNC" to TokenType.FUNC,
        "TYPE" to TokenType.TYPE,
        "RETURN" to TokenType.RETURN,
        "UNION" to TokenType.UNION,
        "WHERE" to TokenType.WHERE,
        "SET" to TokenType.SET,
        "APPEND" to TokenType.APPEND,
        "TO" to TokenType.TO,
        "INIT" to TokenType.INIT,
    )

    fun lex(): List<Token> {
        while (!isAtEnd()) {
            val startLine = line
            val startCol = column
            val c = advance()
            when (c) {
                '$' -> add(TokenType.DOLLAR, "$", startLine, startCol)
                '(' -> add(TokenType.LEFT_PAREN, "(", startLine, startCol)
                ')' -> add(TokenType.RIGHT_PAREN, ")", startLine, startCol)
                '{' -> add(TokenType.LEFT_BRACE, "{", startLine, startCol)
                '}' -> add(TokenType.RIGHT_BRACE, "}", startLine, startCol)
                '[' -> add(TokenType.LEFT_BRACKET, "[", startLine, startCol)
                ']' -> add(TokenType.RIGHT_BRACKET, "]", startLine, startCol)
                ',' -> add(TokenType.COMMA, ",", startLine, startCol)
                '.' -> add(TokenType.DOT, ".", startLine, startCol)
                ';' -> add(TokenType.SEMICOLON, ";", startLine, startCol)
                ':' -> add(TokenType.COLON, ":", startLine, startCol)
                '?' -> if (match('?')) {
                    add(TokenType.COALESCE, "??", startLine, startCol)
                } else {
                    add(TokenType.QUESTION, "?", startLine, startCol)
                }
                '+' -> {
                    if (!isAtEnd() && peek() == '+') {
                        advance()
                        add(TokenType.CONCAT, "++", startLine, startCol)
                    } else {
                        add(TokenType.PLUS, "+", startLine, startCol)
                    }
                }
                '-' -> if (match('>')) {
                    add(TokenType.ARROW, "->", startLine, startCol)
                } else {
                    add(TokenType.MINUS, "-", startLine, startCol)
                }
                '*' -> add(TokenType.STAR, "*", startLine, startCol)
                '%' -> add(TokenType.PERCENT, "%", startLine, startCol)
                '!' -> add(
                    if (match('=')) TokenType.NEQ else TokenType.BANG,
                    if (peekPrev() == '!') "!=" else "!",
                    startLine,
                    startCol
                )
                '=' -> when {
                    match('>') -> add(TokenType.ARROW, "=>", startLine, startCol)
                    match('=') -> add(TokenType.EQ, "==", startLine, startCol)
                    else -> add(TokenType.ASSIGN, "=", startLine, startCol)
                }
                '<' -> add(
                    if (match('=')) TokenType.LE else TokenType.LT,
                    if (peekPrev() == '=') "<=" else "<",
                    startLine,
                    startCol
                )
                '>' -> add(
                    if (match('=')) TokenType.GE else TokenType.GT,
                    if (peekPrev() == '=') ">=" else ">",
                    startLine,
                    startCol
                )
                '&' -> if (match('&')) {
                    add(TokenType.AND, "&&", startLine, startCol)
                } else {
                    errorToken("Unexpected '&'", startLine, startCol)
                }
                '|' -> when (peek()) {
                    '|' -> {
                        advance()
                        add(TokenType.OR, "||", startLine, startCol)
                    }
                    else -> add(TokenType.PIPE, "|", startLine, startCol)
                }
                '/' -> when {
                    match('/') -> while (!isAtEnd() && peek() != '\n') advance()
                    match('*') -> blockComment(startLine, startCol)
                    else -> add(TokenType.SLASH, "/", startLine, startCol)
                }
                '`' -> backtickIdentifier(startLine, startCol)
                '"' -> string(startLine, startCol)
                '\n' -> { line++; column = 1 }
                ' ', '\r', '\t' -> {}
                else -> {
                    if (c.isDigit()) {
                        number(c, startLine, startCol)
                    } else if (c.isIdentifierStart()) {
                        identifier(c, startLine, startCol)
                    } else {
                        errorToken("Unexpected character '$c'", startLine, startCol)
                    }
                }
            }
        }
        add(TokenType.EOF, "", line, column)
        return tokens
    }

    private fun add(type: TokenType, lexeme: String, l: Int, c: Int) {
        tokens += Token(type, lexeme, l, c)
    }

    private fun errorToken(message: String, l: Int, c: Int): Nothing {
        throw IllegalArgumentException("[Line $l, Col $c] $message")
    }

    private fun advance(): Char {
        val ch = source[index++]
        column++
        return ch
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[index] != expected) return false
        index++
        column++
        return true
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[index]
    private fun peekNext(): Char = if (index + 1 >= source.length) '\u0000' else source[index + 1]
    private fun peekPrev(): Char = if (index < 2) '\u0000' else source[index - 2]
    private fun isAtEnd(): Boolean = index >= source.length

    private fun blockComment(startLine: Int, startCol: Int) {
        while (!isAtEnd()) {
            when (peek()) {
                '\n' -> { advance(); line++; column = 1 }
                '*' -> if (peekNext() == '/') { advance(); advance(); return } else advance()
                else -> advance()
            }
        }
        errorToken("Unterminated block comment", startLine, startCol)
    }

    private fun string(startLine: Int, startCol: Int) {
        val sb = StringBuilder()
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\n') errorToken("Unterminated string", startLine, startCol)
            if (peek() == '\\') {
                val escapeLine = line
                val escapeCol = column
                advance() // consume backslash
                if (isAtEnd()) throw unterminatedStringEscape(escapeLine, escapeCol)
                val escaped = advance()
                sb.append(when (escaped) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '\\' -> '\\'
                    '"' -> '"'
                    'u' -> readUnicodeEscape(escapeLine, escapeCol)
                    else -> escaped // pass through unknown escapes
                })
            } else {
                sb.append(advance())
            }
        }
        if (isAtEnd()) errorToken("Unterminated string", startLine, startCol)
        advance()
        add(TokenType.STRING, "\"${sb}\"", startLine, startCol)
    }

    private fun readUnicodeEscape(startLine: Int, startCol: Int): Char {
        var value = 0
        repeat(4) {
            if (isAtEnd()) throw unterminatedUnicodeEscape(startLine, startCol)
            val ch = peek()
            if (ch == '\n' || ch == '\r' || ch == '"') throw unterminatedUnicodeEscape(startLine, startCol)
            val digit = ch.digitToIntOrNull(16)
                ?: errorToken("Invalid unicode escape", startLine, startCol)
            advance()
            value = value * 16 + digit
        }
        return value.toChar()
    }

    private fun unterminatedStringEscape(line: Int, col: Int): Nothing {
        throw StringIndexOutOfBoundsException("[Line $line, Col $col] Unterminated string escape")
    }

    private fun unterminatedUnicodeEscape(line: Int, col: Int): Nothing {
        throw StringIndexOutOfBoundsException("[Line $line, Col $col] Unterminated unicode escape")
    }

    private fun backtickIdentifier(startLine: Int, startCol: Int) {
        if (isAtEnd()) errorToken("Unterminated identifier", startLine, startCol)
        val sb = StringBuilder()
        while (!isAtEnd() && peek() != '`') {
            if (peek() == '\n') errorToken("Unterminated identifier", startLine, startCol)
            sb.append(advance())
        }
        if (isAtEnd()) errorToken("Unterminated identifier", startLine, startCol)
        advance()
        if (sb.isEmpty()) errorToken("Empty identifier", startLine, startCol)
        add(TokenType.IDENTIFIER, sb.toString(), startLine, startCol)
    }

    private fun number(first: Char, startLine: Int, startCol: Int) {
        val sb = StringBuilder().append(first)
        while (!isAtEnd() && peek().isDigit()) sb.append(advance())
        if (!isAtEnd() && peek() == '.' && source.getOrNull(index + 1)?.isDigit() == true) {
            sb.append(advance())
            while (!isAtEnd() && peek().isDigit()) sb.append(advance())
        }
        add(TokenType.NUMBER, sb.toString(), startLine, startCol)
    }

    private fun identifier(first: Char, startLine: Int, startCol: Int) {
        val sb = StringBuilder().append(first)
        while (!isAtEnd() && peek().isIdentifierPart()) sb.append(advance())
        val text = sb.toString()
        val type = keywords[text.uppercase()] ?: TokenType.IDENTIFIER
        add(type, text, startLine, startCol)
    }

    private fun Char.isIdentifierStart(): Boolean = this == '_' || this.isLetter()
    private fun Char.isIdentifierPart(): Boolean = isIdentifierStart() || this.isDigit()
}
