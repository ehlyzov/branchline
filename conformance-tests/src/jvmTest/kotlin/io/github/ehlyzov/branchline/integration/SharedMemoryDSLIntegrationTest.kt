package io.github.ehlyzov.branchline.integration

import io.github.ehlyzov.branchline.Parser
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.std.*

class SharedMemoryDSLIntegrationTest {
    
    @Test
    fun `test SharedStore operations work correctly`() {
        val sharedStore = DefaultSharedStore()
        
        // Add resources
        sharedStore.addResource("singleRes", SharedResourceKind.SINGLE)
        sharedStore.addResource("manyRes", SharedResourceKind.MANY)
        
        runBlocking {
            // Test SINGLE semantics
            assertTrue(sharedStore.setOnce("singleRes", "key1", "value1"))
            assertFalse(sharedStore.setOnce("singleRes", "key1", "value2"))
            assertEquals("value1", sharedStore.get("singleRes", "key1"))
            
            // Test MANY semantics  
            sharedStore.put("manyRes", "key1", "value1")
            assertEquals("value1", sharedStore.get("manyRes", "key1"))
            sharedStore.put("manyRes", "key1", "value2")
            assertEquals("value2", sharedStore.get("manyRes", "key1"))
        }
    }
    
    @Test
    fun `test parsing of await expressions in complex DSL`() {
        val dslProgram = """
            SHARED cache SINGLE;
            SHARED buf MANY;
            
            TRANSFORM ComplexAwait { // Test simple AWAIT
                LET parent = AWAIT cache.parentKey;
                
                // Test AWAIT in expressions
                LET combined = (AWAIT cache.key1) + (AWAIT buf.key2);
                
                // AWAIT prohibited in IF condition
                LET isEnabled = AWAIT cache.enabled;
                IF isEnabled THEN {
                    OUTPUT { result: combined };
                } ELSE {
                    OUTPUT { result: "disabled" };
                }
            }
        """.trimIndent()
        
        // Parse the DSL
        val tokens = Lexer(dslProgram).lex()
        val program = Parser(tokens, dslProgram).parse()
        
        // Verify structure
        assertEquals(3, program.decls.size)
        
        val transform = program.decls[2] as TransformDecl
        val body = transform.body as CodeBlock
        
        // Find await expressions
        var awaitCount = 0
        
        fun findAwaitExpressions(ast: Ast) {
            when (ast) {
                is SharedStateAwaitExpr -> {
                    awaitCount++
                    assertTrue(ast.resource in listOf("cache", "buf"))
                    assertTrue(ast.key in listOf("parentKey", "key1", "key2", "enabled"))
                }
                is LetStmt -> {
                    findAwaitExpressions(ast.expr)
                }
                is BinaryExpr -> {
                    findAwaitExpressions(ast.left)
                    findAwaitExpressions(ast.right)
                }
                is IfStmt -> {
                    findAwaitExpressions(ast.condition)
                    findAwaitExpressions(ast.thenBlock)
                    ast.elseBlock?.let { findAwaitExpressions(it) }
                }
                is CodeBlock -> {
                    ast.statements.forEach { findAwaitExpressions(it) }
                }
                is OutputStmt -> {
                    findAwaitExpressions(ast.template)
                }
                is ObjectExpr -> {
                    ast.fields.forEach { 
                        when (it) {
                            is LiteralProperty -> findAwaitExpressions(it.value)
                            is ComputedProperty -> {
                                findAwaitExpressions(it.keyExpr)
                                findAwaitExpressions(it.value)
                            }
                        }
                    }
                }
                else -> {
                    // Handle other AST nodes we don't care about for this test
                }
            }
        }
        
        findAwaitExpressions(body)
        
        // Should find 4 await expressions total (parent, key1, key2, isEnabled)
        assertEquals(4, awaitCount)
    }
    
    @Test
    fun `test SHARED declarations parsing`() {
        val dslProgram = """
            SHARED singleResource SINGLE;
            SHARED manyResource MANY;
            
            TRANSFORM TestSemantics { OUTPUT { test: "parsed" };
            }
        """.trimIndent()
        
        // Parse the program
        val tokens = Lexer(dslProgram).lex()
        val program = Parser(tokens, dslProgram).parse()
        
        // Verify structure
        assertEquals(3, program.decls.size)
        
        // Check SINGLE declaration
        val singleDecl = program.decls[0] as SharedDecl
        assertEquals("singleResource", singleDecl.name)
        assertEquals(SharedKind.SINGLE, singleDecl.kind)
        
        // Check MANY declaration
        val manyDecl = program.decls[1] as SharedDecl
        assertEquals("manyResource", manyDecl.name)
        assertEquals(SharedKind.MANY, manyDecl.kind)
        
        // Check transform
        val transform = program.decls[2] as TransformDecl
        assertEquals("TestSemantics", transform.name)
        assertEquals(Mode.BUFFER, transform.mode)
    }
}
