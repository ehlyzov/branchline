package io.github.ehlyzov.branchline.sema

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.Program

private fun compile(src: String): Program =
    Parser(Lexer(src).lex()).parse()

class SemanticAnalyzerTest {

    @Test
    fun `duplicate func throws`() {
        val code = """
            FUNC f() = 1;
            FUNC f() = 2;
        """
        assertThrows<SemanticException> {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test
    fun `undefined identifier in return`() {
        val code = """
            FUNC g() {
                RETURN x + 1 ;
            }
        """
        val ex = assertThrows<SemanticException> {
            SemanticAnalyzer().analyze(compile(code))
        }
        assertTrue(ex.message!!.contains("Undefined identifier 'x'"))
    }

    @Test
    fun `shadow let allowed in inner block`() {
        val code = """
            FUNC f() {
                LET a = 1;
                IF true THEN {
                    LET b = 2;
                    RETURN b;
                } ELSE {}
            }
        """
        SemanticAnalyzer().analyze(compile(code)) // не бросает
    }

    @Test
    fun `func parameters visible in block body`() {
        val code = """
            FUNC toNumber(value) {
                IF value == NULL THEN {
                    RETURN 0;
                } ELSE {
                    RETURN value;
                }
            }
        """

        assertDoesNotThrow {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test
    fun `duplicate let in same scope throws`() {
        val code = """
            FUNC f() {
                LET a = 1;
                LET a = 2;
            }
        """
        assertThrows<SemanticException> {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test
    fun `func parameters visible in expression body`() {
        val code = """
            FUNC identity(value) = value;
        """

        assertDoesNotThrow {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test
    fun `global symbols unique`() {
        val code = """
            SHARED cache SINGLE;
            TYPE cache = enum {A};
        """
        assertThrows<SemanticException> {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test
    fun `for each var visible in body`() {
        val code = """
        TRANSFORM T { FOR EACH r IN input.rows {
                RETURN r.id ;
            }
        }
    """
        assertDoesNotThrow {
            SemanticAnalyzer().analyze(compile(code))
        }
    }

    @Test fun `shadow let throws`() {
        val code = """
        FUNC f() {
            LET a = 1;
            IF true THEN {
                LET a = 2;
            } ELSE {}
        }
    """
        assertThrows<SemanticException> {
            SemanticAnalyzer().analyze(compile(code))
        }
    }
}
