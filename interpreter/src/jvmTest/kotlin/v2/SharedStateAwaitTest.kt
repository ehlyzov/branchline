package v2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SharedStateAwaitTest {

    private fun parseExpr(input: String): Expr {
        val tokens = Lexer(input).lex()
        val parser = Parser(tokens, input)
        return parser.parse().decls.let { decls ->
            // Extract expression from a simple transform
            val transform = decls.find { it is TransformDecl } as TransformDecl
            val body = transform.body as CodeBlock
            val stmt = body.statements.first() as ExprStmt
            stmt.expr
        }
    }

    private fun parseProgram(input: String): Program {
        val tokens = Lexer(input).lex()
        return Parser(tokens, input).parse()
    }

    @Test
    fun `test await SharedState parsing`() {
        val program = """
            SHARED nodeParents SINGLE;
            TRANSFORM test { await nodeParents.someKey;
            }
        """.trimIndent()

        val parsed = parseProgram(program)

        // Verify SHARED declaration
        val sharedDecl = parsed.decls[0] as SharedDecl
        assertEquals("nodeParents", sharedDecl.name)
        assertEquals(SharedKind.SINGLE, sharedDecl.kind)

        // Verify await expression
        val transform = parsed.decls[1] as TransformDecl
        val body = transform.body as CodeBlock
        val stmt = body.statements[0] as ExprStmt
        val awaitExpr = stmt.expr as SharedStateAwaitExpr

        assertEquals("nodeParents", awaitExpr.resource)
        assertEquals("someKey", awaitExpr.key)
    }

    @Test
    fun `test multiple await expressions`() {
        val program = """
            SHARED cache SINGLE;
            SHARED store MANY;
            TRANSFORM test { LET parent = await cache.parentKey;
                LET child = await store.childKey;
                OUTPUT { parent: parent, child: child };
            }
        """.trimIndent()

        val parsed = parseProgram(program)
        val transform = parsed.decls[2] as TransformDecl
        val body = transform.body as CodeBlock

        // First await expression
        val letStmt1 = body.statements[0] as LetStmt
        val awaitExpr1 = letStmt1.expr as SharedStateAwaitExpr
        assertEquals("cache", awaitExpr1.resource)
        assertEquals("parentKey", awaitExpr1.key)

        // Second await expression
        val letStmt2 = body.statements[1] as LetStmt
        val awaitExpr2 = letStmt2.expr as SharedStateAwaitExpr
        assertEquals("store", awaitExpr2.resource)
        assertEquals("childKey", awaitExpr2.key)
    }

    @Test
    fun `test await in complex expressions`() {
        val program = """
            SHARED data SINGLE;
            TRANSFORM test { LET result = (await data.key1) + (await data.key2);
            }
        """.trimIndent()

        val parsed = parseProgram(program)
        val transform = parsed.decls[1] as TransformDecl
        val body = transform.body as CodeBlock
        val letStmt = body.statements[0] as LetStmt

        // Should be a binary expression with two await expressions
        val binaryExpr = letStmt.expr as BinaryExpr
        assertEquals(TokenType.PLUS, binaryExpr.token.type)

        val leftAwait = binaryExpr.left as SharedStateAwaitExpr
        assertEquals("data", leftAwait.resource)
        assertEquals("key1", leftAwait.key)

        val rightAwait = binaryExpr.right as SharedStateAwaitExpr
        assertEquals("data", rightAwait.resource)
        assertEquals("key2", rightAwait.key)
    }

    @Test
    fun `test await not used directly in IF condition`() {
        val program = """
            SHARED flags SINGLE;
            TRANSFORM test { LET isEnabled = await flags.enabled;
                IF isEnabled THEN {
                    OUTPUT { status: "enabled" };
                }
            }
        """.trimIndent()

        val parsed = parseProgram(program)
        val transform = parsed.decls[1] as TransformDecl
        val body = transform.body as CodeBlock
        val ifStmt = body.statements[1] as IfStmt

        // Condition must be an identifier (no await in IF condition)
        val cond = ifStmt.condition as IdentifierExpr
        assertEquals("isEnabled", cond.name)
    }

    @Test
    fun `test invalid await syntax throws parse error`() {
        // Missing dot
        assertThrows(ParseException::class.java) {
            parseProgram(
                """
                SHARED data SINGLE;
                TRANSFORM test { await data key;
                }
                """.trimIndent()
            )
        }

        // Missing resource name
        assertThrows(ParseException::class.java) {
            parseProgram(
                """
                TRANSFORM test { await .key;
                }
                """.trimIndent()
            )
        }

        // Missing key name
        assertThrows(ParseException::class.java) {
            parseProgram(
                """
                SHARED data SINGLE;
                TRANSFORM test { await data.;
                }
                """.trimIndent()
            )
        }
    }
}
