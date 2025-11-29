package v2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ParserTest {

    private fun parse(src: String): Program = Parser(Lexer(src).lex()).parse()

    private fun parseExpr(src: String): Expr {
        // строим минимальную программу и парсим
        val program = Parser(
            Lexer("FUNC f() = $src").lex()
        ).parse()

        // единственная декларация — FuncDecl c ExprBody
        val func = program.decls[0] as FuncDecl
        return (func.body as ExprBody).expr
    }

    // ------------------------------------------------------------------
    // BASIC POSITIVE CASES
    // ------------------------------------------------------------------

    @Test
    fun `parses full example`() {
        val program = parse(
            """
            // branchline 0.2
            TRANSFORM NormaliseOne { LET customer = {id:newId("C"), email:row.email}
                OUTPUT { customer:customer }
            }
            """.trimIndent()
        )
        assertEquals(1, program.decls.size)
        val tr = program.decls[0] as TransformDecl
        assertEquals("NormaliseOne", tr.name)
        assertEquals(Mode.BUFFER, tr.mode)
        assertEquals(2, tr.body.statements.size)
        println(program)
    }

    @Test
    fun `adapter spec parsed`() {
        val program = parse("OUTPUT USING kafka(\"topic\", \"dlq\") {}")
        val output = program.decls.first() as OutputDecl
        val adapter = output.adapter!!
        assertEquals("kafka", adapter.name)
        assertEquals(2, adapter.args.size)
        assertTrue(adapter.args[0] is StringExpr)
    }

    @Test
    fun `let path expression parsed`() {
        val prg = parse("""
            TRANSFORM { LET x = row.email
            }
        """.trimIndent())
        val tr = prg.decls[0] as TransformDecl
        val let = tr.body.statements[0] as LetStmt
        assertTrue(let.expr is AccessExpr)
    }

    // ------------------------------------------------------------------
    // ADVANCED POSITIVE CASES
    // ------------------------------------------------------------------

    @Test
    fun `nested object literal parsed`() {
        val prg = parse("""
            TRANSFORM { LET obj = {a:1, b:{c:2}}
            }
        """.trimIndent())
        val objLet = ((prg.decls[0] as TransformDecl).body.statements[0] as LetStmt).expr as ObjectExpr
        assertEquals(2, objLet.fields.size)
        val inner = (objLet.fields[1] as LiteralProperty).value as ObjectExpr
        assertEquals(1, inner.fields.size)
        assertEquals("c", ((inner.fields[0] as LiteralProperty).key as ObjKey.Name).v)
    }

    @Test
    fun `object literal allows trailing comma`() {
        val prg = parse("""
            TRANSFORM { LET obj = {a: 1,}
            }
        """.trimIndent())
        val letStmt = (prg.decls[0] as TransformDecl).body.statements[0] as LetStmt
        val obj = letStmt.expr as ObjectExpr
        assertEquals(1, obj.fields.size)
        val field = obj.fields.first() as LiteralProperty
        assertEquals("a", (field.key as ObjKey.Name).v)
        assertTrue(field.value is NumberLiteral)
    }

    @Test
    fun `output object allows trailing comma`() {
        val prg = parse("""
            TRANSFORM { OUTPUT {hello: world,}
            }
        """.trimIndent())
        val output = (prg.decls[0] as TransformDecl).body.statements[0] as OutputStmt
        val tpl = output.template
        assertTrue(tpl is ObjectExpr)
        tpl as ObjectExpr
        val field = tpl.fields.single() as LiteralProperty
        assertEquals("hello", (field.key as ObjKey.Name).v)
        val value = field.value as IdentifierExpr
        assertEquals("world", value.name)
    }

    @Test
    fun `call expression with multiple args`() {
        val prg = parse("""
            TRANSFORM { LET id = genId("X", 42)
            }
        """.trimIndent())
        val call = ((prg.decls[0] as TransformDecl).body.statements[0] as LetStmt).expr as CallExpr
        assertEquals("genId", call.callee.name)
        assertEquals(2, call.args.size)
        assertTrue(call.args[1] is NumberLiteral)
    }

    // ------------------------------------------------------------------
    // NEGATIVE / ERROR CASES
    // ------------------------------------------------------------------

    @Test
    fun `statements in block do not require semicolons`() {
        val prg = parse("""
            TRANSFORM { LET x = 1
                SET x = x + 1
                OUTPUT x
            }
        """.trimIndent())
        val stmts = (prg.decls[0] as TransformDecl).body.statements
        assertEquals(3, stmts.size)
        assertTrue(stmts[0] is LetStmt)
        assertTrue(stmts[1] is SetVarStmt)
        assertTrue(stmts[2] is OutputStmt)
    }

    @Test
    fun `expression statements split by newline parse`() {
        val prg = parse("""
            TRANSFORM { callOne()
                callTwo()
            }
        """.trimIndent())
        val stmts = (prg.decls[0] as TransformDecl).body.statements
        assertEquals(2, stmts.size)
        assertTrue(stmts[0] is ExprStmt)
        assertTrue(stmts[1] is ExprStmt)
    }

    @Test
    fun `transform parses without source`() {
        val program = parse("""
            TRANSFORM { }
        """.trimIndent())
        assertTrue(program.decls.first() is TransformDecl)
    }

    @Test
    fun `unclosed transform block throws`() {
        assertThrows<ParseException> {
            parse("""
                TRANSFORM { LET x = 1
            """.trimIndent())
        }
    }

    @Test
    fun `unclosed transform block throws2`() {
        assertThrows<ParseException> {
            val prg = parse(
                """
                TRANSFORM { IF cond THEN { LET x = 1 } ELSE { LET x = 2 }
                    x = x + 1
                }
                """.trimIndent()
            )
        }
    }

    // ------------------------------------------------------------------
    // PERFORMANCE / SCALABILITY
    // ------------------------------------------------------------------

    @Tag("performance")
    @Test
    fun `parser completes within 1s on large input`() {
        val builder = StringBuilder().apply {
            append("TRANSFORM { \n")
            repeat(10_000) { append("LET a$it = 1\n") }
            append("}")
        }
        assertTimeoutPreemptively(1.seconds.toJavaDuration()) {
            parse(builder.toString())
        }
    }

    // ---------------------------------------------------------------
    // IF / ELSE positive
    // ---------------------------------------------------------------
    @Test
    fun `if else parsed`() {
        val prg = parse(
            """
            TRANSFORM { IF cond THEN { LET x = 1 } ELSE { LET x = 2 }
            }
            """.trimIndent()
        )
        val tr = prg.decls[0] as TransformDecl
        val ifStmt = tr.body.statements[0] as IfStmt
        assertNotNull(ifStmt.elseBlock)
        assertEquals(1, ifStmt.thenBlock.statements.size)
    }

    // missing THEN should throw
    @Test
    fun `if missing THEN throws`() {
        assertThrows<ParseException> {
            parse("""
                TRANSFORM { IF cond { LET x = 1 } }
            """.trimIndent())
        }
    }

    // ---------------------------------------------------------------
    // FOR EACH positive
    // ---------------------------------------------------------------
    @Test
    fun `for each parsed`() {
        val prg = parse("""
            TRANSFORM { FOR EACH item IN list { LET x = item }
            }
        """.trimIndent())
        val forStmt = (prg.decls[0] as TransformDecl).body.statements[0] as ForEachStmt
        assertEquals("item", forStmt.varName)
        assertTrue(forStmt.iterable is IdentifierExpr)
        assertEquals(1, forStmt.body.statements.size)
    }

    // missing IN should throw
    @Test
    fun `for each missing IN throws`() {
        assertThrows<ParseException> {
            parse("""
                TRANSFORM { FOR EACH item list { LET x = 1 } }
            """.trimIndent())
        }
    }

    // ---------------------------------------------------------------
    // MUTATING STATEMENTS AND OUTPUT
    // ---------------------------------------------------------------

    @Test
    fun `set statement parsed`() {
        val prg = parse("""
            TRANSFORM { SET obj.name = "X"
            }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0] as SetStmt
        val base = stmt.target.base as IdentifierExpr
        assertEquals("obj", base.name)
        assertTrue(stmt.value is StringExpr)
    }

    @Test
    fun `append to with init parsed`() {
        val prg = parse("""
            TRANSFORM { APPEND TO obj.items item INIT []
            }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0] as AppendToStmt
        assertEquals("obj", (stmt.target.base as IdentifierExpr).name)
        assertNotNull(stmt.init)
    }

    @Test
    fun `modify statement parsed`() {
        val prg = parse("""
            TRANSFORM { MODIFY customer { name:"Bob", [k]:42 } }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0] as ModifyStmt
        assertEquals(2, stmt.updates.size)
        assertTrue(stmt.updates[0] is LiteralProperty)
        assertTrue(stmt.updates[1] is ComputedProperty)
    }

    @Test
    fun `nested output statement parsed`() {
        val prg = parse("""
            TRANSFORM { OUTPUT {a:1}
            }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0] as OutputStmt
        assertTrue(stmt.template is ObjectExpr)
    }

    @Test
    fun `abort statement parsed`() {
        val prg = parse("""
            TRANSFORM { ABORT "fail"
            }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0] as AbortStmt
        assertTrue(stmt.value is StringExpr)
    }

    @Test
    fun `expression statement parsed`() {
        val prg = parse("""
            TRANSFORM { uuid()
            }
        """.trimIndent())
        val stmt = (prg.decls[0] as TransformDecl).body.statements[0]
        assertTrue(stmt is ExprStmt)
    }

    // ---------------------------------------------------------------
    // TRY / CATCH positive with retry/backoff
    // ---------------------------------------------------------------
    @Test
    fun `try catch retry parsed`() {
        val src = """
            TRANSFORM { TRY doWork() CATCH (e) RETRY 3 TIMES BACKOFF "100ms" -> fallback()
            }
            """.trimIndent()
        val prg = parse(src)
        val tryStmt = (prg.decls[0] as TransformDecl).body.statements[0] as TryCatchStmt
        assertEquals(3, tryStmt.retry)
        assertEquals("100ms", tryStmt.backoff)
        assertEquals("e", tryStmt.exceptionName)
    }

    // missing CATCH should throw
    @Test
    fun `try without catch throws`() {
        assertThrows<ParseException> {
            parse("""
                TRANSFORM { TRY x -> y }
            """.trimIndent())
        }
    }

    // ──────────────────────── SHARED ──────────────────────────

    @Test
    fun `shared SINGLE parsed`() {
        val prog = parse("SHARED cache SINGLE")
        val d = prog.decls.single() as SharedDecl
        assertEquals("cache", d.name)
        assertEquals(SharedKind.SINGLE, d.kind)
    }

    @Test
    fun `shared without separator errors`() {
        assertThrows<ParseException> {
            parse("SHARED dict MANY TRANSFORM { }")
        }
    }

    // ───────────────────────── FUNC ───────────────────────────

    @Test
    fun `func expression body parsed`() {
        val prog = parse("FUNC newId(prefix) = concat(prefix, uuid())")
        val f = prog.decls.single() as FuncDecl
        assertEquals("newId", f.name)
        assertEquals(listOf("prefix"), f.params)
        assertTrue(f.body is ExprBody)
    }

    @Test
    fun `func block body with return parsed`() {
        val prog = parse(
            """
            FUNC sum(a,b) {
                RETURN a + b
            }
            """.trimIndent()
        )
        val f = prog.decls.single() as FuncDecl
        val block = (f.body as BlockBody).block
        assertTrue(block.statements.first() is ReturnStmt)
    }

    // ───────────────────────── TYPE ───────────────────────────

    @Test
    fun `type enum parsed`() {
        val prog = parse("TYPE Color = enum { RED , GREEN }")
        val t = prog.decls.single() as TypeDecl
        assertEquals(TypeKind.ENUM, t.kind)
        assertEquals(listOf("RED", "GREEN"), t.defs)
    }

    @Test
    fun `type union parsed`() {
        val prog = parse("TYPE Id = union String | Number")
        val t = prog.decls.single() as TypeDecl
        assertEquals(TypeKind.UNION, t.kind)
        assertEquals(listOf("String", "Number"), t.defs)
    }

    @Test
    fun `type unknown kind throws`() {
        assertThrows<ParseException> {
            parse("TYPE Foo = something {A}")
        }
    }

    // ---------------------------------------------------------------
    // EXPRESSIONS
    // ---------------------------------------------------------------

    @Test
    fun `if else expression parsed`() {
        val e = parseExpr("IF flag THEN 1 ELSE 2")
        assertTrue(e is IfElseExpr)
    }

    @Test
    fun `coalesce expression parsed`() {
        val e = parseExpr("a ?? b") as BinaryExpr
        assertEquals(TokenType.COALESCE, e.token.type)
    }

    @Test
    fun `array comprehension parsed`() {
        val e = parseExpr("[x FOR EACH x IN items WHERE x > 0]") as ArrayCompExpr
        assertEquals("x", e.varName)
        assertNotNull(e.where)
    }

    @Test
    fun `lambda expression parsed`() {
        val e = parseExpr("(a,b) -> a + b") as LambdaExpr
        assertEquals(listOf("a", "b"), e.params)
        assertTrue(e.body is ExprBody)
    }

    // ────────────────── комплексный тайм-аут ──────────────────

    @Tag("performance")
    @Test
    fun `parser passes declarations stress within 1s`() {
        val many = buildString {
            appendLine("// branchline stress")
            repeat(5000) {
                appendLine("SHARED s$it SINGLE")
            }
        }
        assertTimeoutPreemptively(1.seconds.toJavaDuration()) {
            parse(many)
        }
    }

    // ────────────----────── приоритеты ─----─────────────────

    @Test
    fun `operator precedence`() {
        val e = parseExpr("1 + 2 * 3") as BinaryExpr
        assertEquals(TokenType.PLUS, e.token.type) // корневой '+'
        val rhs = e.right as BinaryExpr
        assertEquals(TokenType.STAR, rhs.token.type)
    }
}
