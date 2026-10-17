package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.ToIR
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateCollectionScopeTest {
    private fun compile(body: String, hosts: Map<String, (List<Any?>) -> Any?> = emptyMap()): Bytecode {
        val program = Parser(Lexer("TRANSFORM T { $body }").lex()).parse()
        val transform = program.decls.filterIsInstance<TransformDecl>().single()
        return Compiler(hostFns = hosts).compile(ToIR(emptyMap(), hosts).compile(transform.body.statements))
    }

    @Test
    fun unrelated_outer_roots_remain_private_across_sibling_and_nested_scopes() {
        val bytecode = compile("""
            LET first = []; LET second = []; LET object = {}
            IF TRUE THEN { first += 1 }
            IF TRUE THEN {
                FOR EACH value IN [2, 3] { second += value }
                IF TRUE THEN { SET object.last = 4 }
            }
            IF TRUE THEN { first += 5 }
            OUTPUT { first: first, second: second, object: object }
        """.trimIndent())
        assertEquals(3, bytecode.instructions.count { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(1, bytecode.instructions.count { it is Instruction.PRIVATE_SET_STATIC })
        assertEquals(mapOf("first" to listOf(1, 5), "second" to listOf(2, 3), "object" to mapOf("last" to 4)),
            VM().execute(bytecode))
    }

    @Test
    fun branch_local_candidate_does_not_escape_into_a_sibling_scope() {
        val bytecode = compile("""
            IF TRUE THEN { LET local = []; local += 1 }
            IF TRUE THEN { local += 2 }
            LET alias = local
            local += 3
            OUTPUT { values: local, alias: alias }
        """.trimIndent())
        assertEquals(1, bytecode.instructions.count { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(2, bytecode.instructions.count { it === Instruction.PLUS_ASSIGN })
        assertEquals(mapOf("values" to listOf(1, 2, 3), "alias" to listOf(1, 2)), VM().execute(bytecode))
    }

    @Test
    fun call_in_a_nested_branch_invalidates_outer_roots_and_preserves_later_fresh_roots() {
        val env = mutableMapOf<String, Any?>()
        var observed: Any? = null
        val hosts = mapOf<String, (List<Any?>) -> Any?>("observe" to { observed = env["outer"]; null })
        val bytecode = compile("""
            LET outer = []
            IF TRUE THEN {
                IF TRUE THEN { observe() }
                outer += 1
                LET fresh = []; fresh += 2
            }
            outer += 3
            OUTPUT { outer: outer, fresh: fresh }
        """.trimIndent(), hosts)
        assertEquals(1, bytecode.instructions.count { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(2, bytecode.instructions.count { it === Instruction.PLUS_ASSIGN })
        assertEquals(mapOf("outer" to listOf(1, 3), "fresh" to listOf(2)), VM(hosts).execute(bytecode, env))
        assertEquals(emptyList<Any?>(), observed)
    }
}
