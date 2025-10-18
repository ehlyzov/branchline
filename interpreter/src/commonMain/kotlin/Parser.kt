package v2

import v2.runtime.bignum.blBigDecOfLong
import v2.runtime.bignum.blBigDecParse
import v2.runtime.bignum.blBigIntOfLong
import v2.runtime.bignum.blBigIntParse

class ParseException(msg: String, val token: Token, val snippet: String? = null) :
    RuntimeException(
        "[${token.line}:${token.column}] $msg near '${token.lexeme}'" +
            (snippet?.let { "\n$it" } ?: "")
    )

object NumOps {
    fun isIntegral(n: NumValue) = n !is Dec

    /** Снимает обёртку в Any-число нативного типа. */
    fun unwrap(n: NumValue): Any = when (n) {
        is I32 -> n.v
        is I64 -> n.v
        is IBig -> n.v
        is Dec -> n.v
    }

    fun promote(a: NumValue, b: NumValue): Pair<NumValue, NumValue> {
        // пример политики: I32->I64->IBig->Dec
        val rank = when (a) {
            is Dec -> 3
            is IBig -> 2
            is I64 -> 1
            is I32 -> 0
        }.coerceAtLeast(
            when (b) {
                is Dec -> 3
                is IBig -> 2
                is I64 -> 1
                is I32 -> 0
            }
        )

        fun lift(x: NumValue): NumValue = when (rank) {
            3 -> when (x) {
                is Dec -> x
                is IBig -> Dec(blBigDecParse(x.v.toString()))
                is I64 -> Dec(blBigDecOfLong(x.v))
                is I32 -> Dec(blBigDecOfLong(x.v.toLong()))
            }

            2 -> when (x) {
                is Dec -> x // не опускаем
                is IBig -> x
                is I64 -> IBig(blBigIntOfLong(x.v))
                is I32 -> IBig(blBigIntOfLong(x.v.toLong()))
            }

            1 -> when (x) {
                is Dec -> x
                is IBig -> x // не опускаем BLBigInt
                is I64 -> x
                is I32 -> I64(x.v.toLong())
            }

            else -> x
        }
        return lift(a) to lift(b)
    }
}

class Parser(tokens: List<Token>, private val source: String? = null) {
    private val toks = tokens
    private var current = 0

    fun parse(): Program {
        // Версию пока не парсим: она приходит строкой-комментарием // branchline 0.2
        val version: VersionDecl? = null
        val decls = mutableListOf<TopLevelDecl>()
        while (!isAtEnd()) {
            when {
                check(TokenType.SOURCE) -> decls += parseSource()
                check(TokenType.TRANSFORM) -> decls += parseTransform()
                check(TokenType.OUTPUT) -> decls += parseOutput()
                check(TokenType.SHARED) -> decls += parseShared()
                check(TokenType.FUNC) -> decls += parseFunc()
                check(TokenType.TYPE) -> decls += parseType()
                check(TokenType.EOF) -> break
                else -> error(peek(), "Unexpected token at top-level")
            }
        }
        consume(TokenType.EOF, "Expect end of file")
        return Program(version, decls)
    }

    // ------------------- top‑level parsers --------------------------

    private fun parseSource(): SourceDecl {
        val start = advance() // SOURCE
        val nameTok = consume(TokenType.IDENTIFIER, "Expect source identifier")
        val adapter = if (match(TokenType.USING)) parseAdapter() else null
        optionalSemicolon()
        return SourceDecl(nameTok.lexeme, adapter, start)
    }

    private fun parseOutput(): OutputDecl {
        val start = advance() // OUTPUT
        val adapter = if (match(TokenType.USING)) parseAdapter() else null
        val template = parseExpression()
        return OutputDecl(adapter, template, start)
    }

    private fun parseTransform(): TransformDecl {
        val start = advance() // TRANSFORM
        val nameTok = if (check(TokenType.IDENTIFIER)) advance() else null

        val params = mutableListOf<String>()
        if (nameTok != null && match(TokenType.LEFT_PAREN)) {
            // список параметров внутри скобок
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    val pTok = consume(TokenType.IDENTIFIER, "Expect parameter name")
                    params += pTok.lexeme
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.RIGHT_PAREN, "Expect ')' after transform parameters")
        }

        // { STREAM }  или  { BUFFER }
        consume(TokenType.LEFT_BRACE, "Expect '{' before mode")
        val modeTok = advance()
        val mode = when (modeTok.type) {
            TokenType.STREAM -> Mode.STREAM
            TokenType.BUFFER -> Mode.BUFFER
            else -> error(modeTok, "Expect STREAM or BUFFER")
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after mode")

        val body: TransformBody = parseBlock()

        return TransformDecl(nameTok?.lexeme, params, mode, body, start)
    }

    private fun parseAdapter(): AdapterSpec {
        val nameTok = consume(TokenType.IDENTIFIER, "Expect adapter name after USING")
        val args = mutableListOf<Expr>()
        if (match(TokenType.LEFT_PAREN)) {
            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    args += parseExpression()
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.RIGHT_PAREN, "Expect ')' after adapter args")
        }
        return AdapterSpec(nameTok.lexeme, args, nameTok)
    }

    // --------------------------------------------------------------------
    // SHARED cache SINGLE ;
    private fun parseShared(): SharedDecl {
        val sharedTok = consume(TokenType.SHARED, "Expect 'SHARED'.")
        val nameTok = consume(TokenType.IDENTIFIER, "Expect shared memory name.")

        val kind = when {
            match(TokenType.SINGLE) -> SharedKind.SINGLE
            match(TokenType.MANY) -> SharedKind.MANY
            else -> error(peek(), "Expect SINGLE or MANY after shared name")
        }
        optionalSemicolon()
        return SharedDecl(nameTok.lexeme, kind, sharedTok)
    }

    // --------------------------------------------------------------------
    // FUNC newId(prefix) = expr ;     |     FUNC sum(a,b) { … }
    private fun parseFunc(): FuncDecl {
        val funcTok = consume(TokenType.FUNC, "Expect 'FUNC'.")
        val nameTok = consume(TokenType.IDENTIFIER, "Expect function name.")

        // ( param, … )
        consume(TokenType.LEFT_PAREN, "Expect '(' after function name.")
        val params = mutableListOf<String>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                params += consume(TokenType.IDENTIFIER, "Expect parameter name.").lexeme
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        // тело:  '=' expr ';'   ИЛИ   блок '{ … }'
        val body: FuncBody = if (match(TokenType.ASSIGN)) {
            val expr = parseExpression()
            optionalSemicolon()
            ExprBody(expr, funcTok)
        } else {
            val block = parseBlock() // parseBlock съедает '{ … }'
            BlockBody(block, funcTok)
        }

        return FuncDecl(nameTok.lexeme, params, body, funcTok)
    }

    // --------------------------------------------------------------------
    // TYPE Color = enum { RED, GREEN } ;     |     TYPE Id = union String | Number ;
    private fun parseType(): TypeDecl {
        val typeTok = consume(TokenType.TYPE, "Expect 'TYPE'.")
        val nameTok = consume(TokenType.IDENTIFIER, "Expect type name.")
        consume(TokenType.ASSIGN, "Expect '=' after type name.")

        val defs = mutableListOf<String>()
        val kind: TypeKind = when {
            match(TokenType.ENUM) -> {
                consume(TokenType.LEFT_BRACE, "Expect '{' after 'enum'.")
                do {
                    defs += consume(TokenType.IDENTIFIER, "Expect enum label.").lexeme
                } while (match(TokenType.COMMA))
                consume(TokenType.RIGHT_BRACE, "Expect '}' after enum body.")
                TypeKind.ENUM
            }

            match(TokenType.UNION) -> {
                // последовательно <Ident> ( '|' <Ident> )*
                do {
                    defs += consume(TokenType.IDENTIFIER, "Expect union member type.").lexeme
                } while (match(TokenType.PIPE))
                TypeKind.UNION
            }

            else -> error(peek(), "Expect 'enum' or 'union' after '='")
        }

        optionalSemicolon()
        return TypeDecl(nameTok.lexeme, kind, defs, typeTok)
    }

    // ------------------- statements & block -------------------------

    private fun parseBlock(): CodeBlock {
        val lbrace = consume(TokenType.LEFT_BRACE, "Expect '{' to start block")
        val stmts = mutableListOf<Stmt>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            stmts += parseStatement()
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block")
        return CodeBlock(stmts, lbrace)
    }

    private fun parseStatement(): Stmt {
        return when {
            match(TokenType.LET) -> parseLet()
            match(TokenType.MODIFY) -> parseModify()
            match(TokenType.SET) -> parseSet()
            match(TokenType.APPEND) -> parseAppendTo()
            match(TokenType.OUTPUT) -> parseOutputStmt()
            match(TokenType.IF) -> parseIf(previous())
            match(TokenType.FOR) -> parseForEach(previous())
            match(TokenType.TRY) -> parseTryCatch(previous())
            match(TokenType.RETURN) -> parseReturn()
            match(TokenType.ABORT) -> parseAbort()
            else -> {
                val start = peek()
                val expr = parseExpression()
                optionalSemicolon()
                ExprStmt(expr, start)
            }
        }
    }

    // --------------- внутри Parser -----------------

    private fun parseLet(): LetStmt {
        val nameTok = consume(TokenType.IDENTIFIER, "Expect identifier after LET")
        consume(TokenType.ASSIGN, "Expect '=' in LET statement")
        val expr = parseExpression()
        optionalSemicolon()
        return LetStmt(nameTok.lexeme, expr, nameTok)
    }

    private fun parseModify(): ModifyStmt {
        // Цель — статический путь: IdentifierExpr или PathExpr (без IndexExpr)
        val targetExpr = parsePrimaryPostfix()
        val targetPath: AccessExpr = when (targetExpr) {
            is IdentifierExpr -> AccessExpr(targetExpr, emptyList(), targetExpr.token)
            is AccessExpr -> targetExpr
            else -> error(
                previous(),
                "Expect static object path after MODIFY (identifier or dotted path without '[ ]')"
            )
        }
        check(targetPath.segs.all { it is AccessSeg.Static }) {
            "Expect static object path after MODIFY (identifier or dotted path without '[ ]')"
        }

        val lbrace = consume(TokenType.LEFT_BRACE, "Expect '{' after MODIFY target")
        val updates = mutableListOf<Property>()

        if (!check(TokenType.RIGHT_BRACE)) {
            do {
                when {
                    // a) computed property: [expr] : expr
                    match(TokenType.LEFT_BRACKET) -> {
                        val keyExpr = parseExpression()
                        consume(TokenType.RIGHT_BRACKET, "Expect ']' after computed property name")
                        consume(TokenType.COLON, "Expect ':' after computed property name")
                        val valueExpr = parseExpression()
                        updates += ComputedProperty(keyExpr, valueExpr)
                    }
                    // b) literal property: IDENTIFIER | STRING | NUMBER : expr
                    else -> {
                        val keyTok = when {
                            match(TokenType.STRING) -> previous()
                            match(TokenType.IDENTIFIER) -> previous()
                            match(TokenType.NUMBER) -> previous()
                            // дружелюбная ошибка для отрицательных числовых ключей
                            check(TokenType.MINUS) -> {
                                advance()
                                val nTok = consume(
                                    TokenType.NUMBER,
                                    "Numeric field key must be a non-negative integer"
                                )
                                error(
                                    nTok,
                                    "Numeric field key must be a non-negative integer, got '-${nTok.lexeme}'"
                                )
                            }

                            else -> error(peek(), "Expect field key in MODIFY")
                        }

                        val key: ObjKey = when (keyTok.type) {
                            TokenType.STRING -> ObjKey.Name(keyTok.lexeme.trim('"'))
                            TokenType.IDENTIFIER -> ObjKey.Name(keyTok.lexeme)
                            TokenType.NUMBER -> parseIntKey(keyTok.lexeme) // I32/I64/IBig
                            else -> error(keyTok, "Invalid field key in MODIFY")
                        }

                        consume(TokenType.COLON, "Expect ':' after field key")
                        val valueExpr = parseExpression()
                        updates += LiteralProperty(key, valueExpr)
                    }
                }
            } while (match(TokenType.COMMA) || match(TokenType.SEMICOLON))
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after MODIFY block")
        return ModifyStmt(targetPath, updates, lbrace)
    }

    private fun parseAppendTo(): Stmt {
        val start = previous() // 'APPEND'
        consume(TokenType.TO, "Expect TO after APPEND")
        val target = parsePrimaryPostfix(allowCall = false)
        val value = parseExpression()
        val initExpr = if (match(TokenType.INIT)) parseExpression() else null
        optionalSemicolon()
        return when (target) {
            is IdentifierExpr -> AppendToVarStmt(target.name, value, initExpr, start)
            is AccessExpr -> AppendToStmt(target, value, initExpr, start)
            else -> error(start, "APPEND TO target must be identifier or path")
        }
    }

    private fun parseSet(): Stmt {
        val start = previous() // 'SET'
        val target = parsePrimaryPostfix()
        consume(TokenType.ASSIGN, "Expect '=' after SET target")
        val value = parseExpression()
        optionalSemicolon()
        return when (target) {
            is IdentifierExpr -> SetVarStmt(target.name, value, start)
            is AccessExpr -> SetStmt(target, value, start)
            else -> error(start, "SET target must be identifier or path")
        }
    }

    private fun parseOutputStmt(): OutputStmt {
        val outTok = previous()
        val template = parseExpression()
        // optional semicolon in example omitted inside transform
        if (match(TokenType.SEMICOLON)) {
            // consume semicolon
        }
        return OutputStmt(template, outTok)
    }

    // IF / ELSE -------------------------------------------------------
    private fun parseIf(ifTok: Token): IfStmt {
        val condition = parseExpression()
        consume(TokenType.THEN, "Expect THEN after IF condition")
        val thenBlock = parseBlock()
        val elseBlock = if (match(TokenType.ELSE)) parseBlock() else null
        return IfStmt(condition, thenBlock, elseBlock, ifTok)
    }

    // FOR EACH --------------------------------------------------------
    private fun parseForEach(forTok: Token): ForEachStmt {
        match(TokenType.EACH)
        val varTok = consume(TokenType.IDENTIFIER, "Expect loop variable name")
        consume(TokenType.IN, "Expect IN after loop variable")
        val iterable = parseExpression()
        val whereExpr = if (match(TokenType.WHERE)) parseExpression() else null
        val body = parseBlock()
        return ForEachStmt(
            varName = varTok.lexeme,
            iterable = iterable,
            body = body,
            where = whereExpr,
            token = forTok
        )
    }

    // TRY / CATCH -----------------------------------------------------
    private fun parseTryCatch(tryTok: Token): TryCatchStmt {
        val tryExpr = parseExpression()
        consume(TokenType.CATCH, "Expect CATCH after TRY expression")
        consume(TokenType.LEFT_PAREN, "Expect '(' after CATCH")
        val excTok = consume(TokenType.IDENTIFIER, "Expect exception variable")
        if (match(TokenType.COLON)) consume(TokenType.IDENTIFIER, "Expect type name after ':'")
        consume(TokenType.RIGHT_PAREN, "Expect ')' after CATCH")
        var retry: Int? = null
        var backoff: String? = null
        if (match(TokenType.RETRY)) {
            val numTok = consume(TokenType.NUMBER, "Expect retry count")
            retry = numTok.lexeme.toInt()
            consume(TokenType.TIMES, "Expect TIMES after retry count")
            if (match(TokenType.BACKOFF)) {
                val strTok = consume(TokenType.STRING, "Expect backoff string")
                backoff = strTok.lexeme.trim('"')
            }
        }
        consume(TokenType.ARROW, "Expect '->' after TRY/CATCH block")
        var fbExpr: Expr? = null
        var fbAbort: AbortStmt? = null

        if (match(TokenType.ABORT)) {
            fbAbort = parseAbort() // мы уже написали parseAbort()
        } else {
            fbExpr = parseExpression()
        }
        match(TokenType.SEMICOLON)
        return TryCatchStmt(
            tryExpr,
            excTok.lexeme,
            retry,
            backoff,
            fbExpr,
            fbAbort,
            tryTok
        )
    }

    // RETURN expr? ;
    private fun parseReturn(): ReturnStmt {
        val kw = previous() // сам токен RETURN уже съели
        val value = if (shouldParseReturnExpr(kw)) parseExpression() else null
        optionalSemicolon()
        return ReturnStmt(value, kw)
    }

    private fun parseAbort(): AbortStmt {
        val kw = previous() // токен ABORT
        val expr = if (shouldParseReturnExpr(kw)) parseExpression() else null
        optionalSemicolon()
        return AbortStmt(expr, kw)
    }

    private fun shouldParseReturnExpr(kw: Token): Boolean {
        val next = peek()
        if (next.type == TokenType.SEMICOLON) return false
        if (next.line != kw.line) return false
        if (isStatementBoundary(next.type)) return false
        return true
    }

    // ------------------- expressions --------------------------------

    private fun parseExpression(): Expr = parseIfElse()

    private fun parseIfElse(): Expr {
        if (!match(TokenType.IF)) return parseCoalesce() // как раньше

        val ifTok = previous()
        val cond = parseExpression()
        consume(TokenType.THEN, "expect THEN")
        val thenE = parseExpression()
        consume(TokenType.ELSE, "expect ELSE")
        val elseE = parseExpression()
        return IfElseExpr(cond, thenE, elseE, ifTok)
    }

    /*   a ?? b   */
    private fun parseCoalesce(): Expr {
        var expr = parseOr()
        while (match(TokenType.COALESCE)) {
            val op = previous()
            val right = parseOr()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   a || b   */
    private fun parseOr(): Expr {
        var expr = parseAnd()
        while (match(TokenType.OR)) {
            val op = previous()
            val right = parseAnd()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   a && b   */
    private fun parseAnd(): Expr {
        var expr = parseEquality()
        while (match(TokenType.AND)) {
            val op = previous()
            val right = parseEquality()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   a == b, a != b   */
    private fun parseEquality(): Expr {
        var expr = parseComparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = previous()
            val right = parseComparison()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   <  <=  >  >=   */
    private fun parseComparison(): Expr {
        var expr = parseConcat()
        while (match(TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)) {
            val op = previous()
            val right = parseConcat()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun parseConcat(): Expr {
        var expr = parseTerm()
        while (match(TokenType.CONCAT)) {
            val op = previous()
            val right = parseTerm()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   +  -   */
    private fun parseTerm(): Expr {
        var expr = parseFactor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous()
            val right = parseFactor()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   *  /  %   */
    private fun parseFactor(): Expr {
        var expr = parseUnary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous()
            val right = parseUnary()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    /*   -x   !flag   await expr   suspend expr   */
    private fun parseUnary(): Expr {
        if (match(TokenType.AWAIT)) {
            val awaitTok = previous()
            // Parse the expression that follows await
            val right = parsePrimaryPostfix()
            
            // Check if it's a dot access (like resource.key)
            return if (right is AccessExpr &&
                       right.base is IdentifierExpr &&
                       right.segs.size == 1 && 
                       right.segs[0] is AccessSeg.Static &&
                       (right.segs[0] as AccessSeg.Static).key is ObjKey.Name) {
                // This looks like AWAIT resource.key - treat as SharedState await
                val resourceName = (right.base as IdentifierExpr).name
                val keyName = ((right.segs[0] as AccessSeg.Static).key as ObjKey.Name).v
                SharedStateAwaitExpr(resourceName, keyName, awaitTok)
            } else {
                // Regular await expression
                UnaryExpr(right, awaitTok)
            }
        }
        if (match(TokenType.MINUS, TokenType.BANG, TokenType.SUSPEND)) {
            val op = previous()
            val right = parseUnary()
            return UnaryExpr(right, op)
        }
        return parsePrimaryPostfix()
    }

    private fun parsePrimaryPostfix(allowCall: Boolean = true): Expr {
        var expr = parsePrimary()
        val segs = mutableListOf<AccessSeg>()
        var tokenForNode: Token = expr.token

        fun flush(): Expr {
            return if (segs.isEmpty()) expr else AccessExpr(expr, segs.toList(), tokenForNode)
        }

        loop@ while (true) {
            when {
                // ---- .[ simple.path ] ----
                check(TokenType.DOT) && checkNext(TokenType.LEFT_BRACKET) -> {
                    advance() // '.'
                    val lbr = consume(TokenType.LEFT_BRACKET, "Expect '[' after '.'")
                    val first = consume(TokenType.IDENTIFIER, "Expect identifier after '.['")

                    // строим базу пути-ключа
                    var keyExpr: Expr = IdentifierExpr(first.lexeme, first)
                    val keySegs = mutableListOf<AccessSeg>()

                    // далее читаем .(IDENT|NUMBER)* внутри скобок
                    while (match(TokenType.DOT)) {
                        val segTok = when {
                            match(TokenType.IDENTIFIER) -> previous()
                            match(TokenType.NUMBER) -> previous()
                            else -> error(peek(), "Expect name or index inside '.[' … ']'")
                        }
                        val seg = when (segTok.type) {
                            TokenType.IDENTIFIER -> AccessSeg.Static(ObjKey.Name(segTok.lexeme))
                            TokenType.NUMBER -> AccessSeg.Static(parseIntKey(segTok.lexeme))
                            else -> error(segTok, "Invalid segment inside '.[' … ']'")
                        }
                        keySegs += seg
                    }

                    consume(TokenType.RIGHT_BRACKET, "Expect ']' after .[ … ]")

                    // если были дополнительные сегменты — завернём в AccessExpr; иначе оставим просто IdentifierExpr
                    if (keySegs.isNotEmpty()) {
                        keyExpr = AccessExpr(keyExpr, keySegs.toList(), first)
                    }

                    // добавляем ОДИН динамический сегмент к текущему base-выражению
                    segs += AccessSeg.Dynamic(keyExpr)
                    tokenForNode = lbr
                }

                // ---- .segment (IDENTIFIER | NUMBER) ----
                match(TokenType.DOT) -> {
                    val segTok = when {
                        match(TokenType.IDENTIFIER) -> previous()
                        match(TokenType.NUMBER) -> previous()
                        else -> error(peek(), "Expect property name or index after '.'")
                    }
                    segs += when (segTok.type) {
                        TokenType.IDENTIFIER -> AccessSeg.Static(ObjKey.Name(segTok.lexeme))
                        TokenType.NUMBER -> AccessSeg.Static(parseIntKey(segTok.lexeme))
                        else -> error(segTok, "Invalid path segment after '.'")
                    }
                    tokenForNode = segTok
                }

                // ---- [ expr ] ----
                match(TokenType.LEFT_BRACKET) -> {
                    val keyExpr = parseExpression()
                    consume(TokenType.RIGHT_BRACKET, "Expect ']' after index")
                    segs += AccessSeg.Dynamic(keyExpr)
                    tokenForNode = keyExpr.token
                }

                // ( args ) — только если allowCall == true
                check(TokenType.LEFT_PAREN) && !allowCall -> {
                    break@loop
                }

                // ---- ( args ) ----
                match(TokenType.LEFT_PAREN) -> {
                    expr = flush() // сначала закрываем накопленный путь
                    val lpar = previous()
                    val args = mutableListOf<Expr>()
                    if (!check(TokenType.RIGHT_PAREN)) {
                        do { args += parseExpression() } while (match(TokenType.COMMA))
                    }
                    consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments")
                    val callee = when (expr) {
                        is IdentifierExpr -> CallExpr(expr, args, lpar)
                        else -> InvokeExpr(expr, args, lpar)
                    }
                    expr = callee
                    segs.clear()
                    tokenForNode = callee.token
                }

                else -> break@loop
            }
        }
        return flush()
    }

    private fun parsePrimary(): Expr {
        val tok = advance()
        return when (tok.type) {
            TokenType.APPEND -> {
                val t = previous()
                // опционально: подсказка, если дальше не '('
                if (!check(TokenType.LEFT_PAREN)) {
                    error(t, "Expect '(' to call function APPEND")
                }
                IdentifierExpr("APPEND", t)
            }
            TokenType.IDENTIFIER -> IdentifierExpr(tok.lexeme, tok)
            TokenType.STRING -> StringExpr(tok.lexeme.trim('"'), tok)
            TokenType.NUMBER -> {
                val tok = previous()
                NumberLiteral(parseNumValue(tok.lexeme), tok)
            }
            TokenType.NULL -> NullLiteral(tok)
            TokenType.TRUE -> BoolExpr(true, tok)
            TokenType.FALSE -> BoolExpr(false, tok)
            TokenType.LEFT_BRACE -> parseObject(tok)
            TokenType.LEFT_BRACKET -> parseArray(tok)
            TokenType.LEFT_PAREN -> {
                if (looksLikeLambdaAt(current)) {
                    parseLambdaAfterLParen() // НЕ требует save/restore
                } else {
                    val inner = parseExpression()
                    consume(TokenType.RIGHT_PAREN, "Expect ')' after expression")
                    inner
                }
            }

            else -> error(tok, "Unexpected token in expression")
        }
    }

    private fun looksLikeLambdaAt(idxStart: Int = current): Boolean {
        var i = idxStart
        if (i >= toks.size) return false
        if (toks[i].type == TokenType.RIGHT_PAREN) {
            i++ // ')'
            return i < toks.size && toks[i].type == TokenType.ARROW
        }
        if (toks[i].type != TokenType.IDENTIFIER) return false
        i++ // first param
        while (i < toks.size && toks[i].type == TokenType.COMMA) {
            i++
            if (i >= toks.size || toks[i].type != TokenType.IDENTIFIER) return false
            i++
        }
        if (i >= toks.size || toks[i].type != TokenType.RIGHT_PAREN) return false
        i++ // ')'
        return i < toks.size && toks[i].type == TokenType.ARROW
    }

    private fun parseLambdaAfterLParen(): Expr {
        val lparen = previous()
        val params = mutableListOf<String>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do { params += consume(TokenType.IDENTIFIER, "Expect parameter name").lexeme }
            while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters")
        val arrow = consume(TokenType.ARROW, "Expect '->' after parameters")
        val body: FuncBody =
            if (check(TokenType.LEFT_BRACE)) {
                val block = parseBlock() // consumes { ... }
                BlockBody(block, arrow)
            } else {
                val expr = parseExpression() // no ';' here
                ExprBody(expr, arrow)
            }
        return LambdaExpr(params, body, lparen)
    }

    private fun parseIntKey(lex: String): ObjKey.Index {
        require(!lex.startsWith("-") && !lex.contains('.') && !lex.contains('e', ignoreCase = true)) {
            "Numeric field key must be a non-negative integer, got '$lex'"
        }
        lex.toIntOrNull()?.let { return I32(it) }
        lex.toLongOrNull()?.let { return I64(it) }
        return IBig(blBigIntParse(lex))
    }

    private fun parseNumValue(lex: String): NumValue =
        if (lex.contains('.') || lex.contains('e', true)) {
            Dec(blBigDecParse(lex))
        } else {
            lex.toIntOrNull()?.let(::I32)
                ?: lex.toLongOrNull()?.let(::I64)
                ?: IBig(blBigIntParse(lex))
        }

    private fun parseArray(lbr: Token): Expr {
        // пустой []
        if (match(TokenType.RIGHT_BRACKET)) {
            return ArrayExpr(emptyList(), lbr)
        }

        // запоминаем первую часть до возможного 'FOR'
        val firstExpr = parseExpression()

        // comprehension?
        return if (match(TokenType.FOR)) {
            consume(TokenType.EACH, "Expect EACH after FOR")
            val varTok = consume(TokenType.IDENTIFIER, "Expect loop variable")
            consume(TokenType.IN, "Expect IN after loop variable")
            val iter = parseExpression()
            val whereExpr = if (match(TokenType.WHERE)) parseExpression() else null
            consume(TokenType.RIGHT_BRACKET, "Expect ']' after array comprehension")
            ArrayCompExpr(
                varName = varTok.lexeme,
                iterable = iter,
                mapExpr = firstExpr,
                where = whereExpr,
                token = lbr
            )
        } else {
            val elems = mutableListOf<Expr>().apply { add(firstExpr) }
            while (match(TokenType.COMMA)) elems += parseExpression()
            consume(TokenType.RIGHT_BRACKET, "Expect ']' after array literal")
            ArrayExpr(elems, lbr)
        }
    }

    private fun parseObject(lbrace: Token): ObjectExpr {
        val fields = mutableListOf<Property>()
        if (!check(TokenType.RIGHT_BRACE)) {
            do {
                when {
                    // {[expr] : value}
                    match(TokenType.LEFT_BRACKET) -> {
                        val keyExpr = parseExpression()
                        consume(TokenType.RIGHT_BRACKET, "Expect ']' after computed property name")
                        consume(TokenType.COLON, "Expect ':' after property name")
                        val valueExpr = parseExpression()
                        fields += ComputedProperty(keyExpr, valueExpr)
                    }

                    else -> {
                        // literal key: IDENT | STRING | NUMBER (целое ≥ 0)
                        val keyTok = when {
                            match(TokenType.STRING) -> previous()
                            match(TokenType.IDENTIFIER) -> previous()
                            match(TokenType.NUMBER) -> previous()
                            // дружелюбная ошибка для отрицательных числовых ключей
                            check(TokenType.MINUS) -> {
                                advance() // consume '-'
                                val nTok = consume(
                                    TokenType.NUMBER,
                                    "Numeric field key must be a non-negative integer"
                                )
                                error(
                                    nTok,
                                    "Numeric field key must be a non-negative integer, got '-${nTok.lexeme}'"
                                )
                            }

                            else -> error(peek(), "Expect field key in object literal")
                        }

                        val key: ObjKey = when (keyTok.type) {
                            TokenType.STRING -> ObjKey.Name(keyTok.lexeme.trim('"'))
                            TokenType.IDENTIFIER -> ObjKey.Name(keyTok.lexeme)
                            TokenType.NUMBER -> parseIntKey(keyTok.lexeme) // I32 | I64 | IBig
                            else -> error(keyTok, "Invalid object key")
                        }

                        consume(TokenType.COLON, "Expect ':' after field key")
                        val valueExpr = parseExpression()
                        fields += LiteralProperty(key, valueExpr)
                    }
                }
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after object literal")
        return ObjectExpr(fields, lbrace)
    }

    // ------------------- helpers ------------------------------------

    private fun optionalSemicolon() {
        if (match(TokenType.SEMICOLON)) return
        val next = peek()
        if (next.line != previous().line) return
        if (isStatementBoundary(next.type)) return
        error(next, "Expect ';' or newline between statements")
    }

    private fun isStatementBoundary(type: TokenType): Boolean {
        return when (type) {
            TokenType.EOF,
            TokenType.RIGHT_BRACE -> true
            else -> false
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean = peek().type == type
    private fun checkNext(type: TokenType): Boolean = peekNext().type == type

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        error(peek(), message)
    }

    private fun error(token: Token, message: String): Nothing {
        val line = token.line
        val col = token.column
        val lines = source?.lines() ?: toks.joinToString("\n") {
            it.lexeme
        }.lines() // если храните исходник — лучше используйте его
        val pointer = " ".repeat((col - 1).coerceAtLeast(0)) + "^"
        val snippet = if (source != null) {
            val before = if (line - 2 >= 0) lines[line - 2] else null
            val at = lines.getOrNull(line - 1) ?: ""
            val after = lines.getOrNull(line) ?: ""

            buildString {
                appendLine(message)
                if (before != null) appendLine(before)
                appendLine(at)
                appendLine(pointer)
                appendLine(after)
            }
        } else {
            pointer
        }

        throw ParseException(message, token, snippet)
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
    private fun peek(): Token = toks[current]
    private fun previous(): Token = toks[current - 1]

    // в начале класса Parser
    private fun peekNext(): Token =
        if (current + 1 < toks.size) toks[current + 1] else Token(TokenType.EOF, "", 0, 0)
}
