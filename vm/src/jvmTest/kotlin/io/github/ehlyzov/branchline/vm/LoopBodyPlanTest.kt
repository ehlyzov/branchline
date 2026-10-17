@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.ir.ToIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopBodyPlanTest {
    private fun compile(body: String, hosts: Map<String, (List<Any?>) -> Any?> = emptyMap()): Bytecode {
        val source = "TRANSFORM Independent { $body }"
        val transform = Parser(Lexer(source).lex()).parse().decls.filterIsInstance<TransformDecl>().single()
        return Compiler(hostFns = hosts).compile(ToIR(emptyMap(), hosts).compile(transform.body.statements))
    }

    private val projection = """
        OUTPUT {rows: [{id: row.id, calc: row.amount * 2 + row.id, first: row.parts[0]}
            FOR EACH row IN input.rows WHERE row.amount > 3]}
    """.trimIndent()

    @Test
    fun a_non_fixture_program_generates_and_enters_a_whole_loop() {
        val code = compile(projection)
        val plan = JvmLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        assertEquals(1, plan.eligibleLoopCount)
        assertEquals(1, plan.loweredLoopCount)
        assertEquals(1, plan.generatedClassCount)
        assertTrue(plan.generatedBytecodeBytes > 0)
        assertTrue(plan.bodyInstructionCount in 1..128)
        val input = mapOf("rows" to listOf(
            mapOf("id" to 1, "amount" to 2, "parts" to listOf(8)),
            mapOf("id" to 3, "amount" to 5, "parts" to listOf(9)),
        ))
        val vm = VM()
        assertEquals(mapOf("rows" to listOf(linkedMapOf("id" to 3, "calc" to 13, "first" to 9))),
            vm.executeLoopBodies(plan, mutableMapOf("input" to input)))
        assertTrue(vm.loopBodyExecuted)
        assertTrue(vm.loopBodyEntries > 0)
        assertNull(vm.loopBodyFallbackReason)
    }

    @Test
    fun an_empty_loop_does_not_claim_a_generated_entry() {
        val plan = JvmLoopCompiler.prepare(compile(projection))
        val vm = VM()
        assertEquals(mapOf("rows" to emptyList<Any?>()),
            vm.executeLoopBodies(plan, mutableMapOf("input" to mapOf("rows" to emptyList<Any?>()))))
        assertFalse(vm.loopBodyExecuted)
        assertEquals(0, vm.loopBodyEntries)
        assertNull(vm.loopBodyFallbackReason)
    }

    @Test
    fun nested_loops_closures_and_body_calls_have_explicit_ordinary_fallback() {
        var calls = 0
        val hosts: Map<String, (List<Any?>) -> Any?> = mapOf("record" to { args -> calls++; args.single() })
        val bodies = listOf(
            "OUTPUT {rows: [[y FOR EACH y IN x] FOR EACH x IN input.rows]}",
            "LET f = (x) -> x + 1; OUTPUT {rows: [f(x) FOR EACH x IN input.rows]}",
            "OUTPUT {rows: [record(x) FOR EACH x IN input.rows]}",
        )
        val inputs = listOf(listOf(listOf(1, 2)), listOf(1, 2), listOf(1, 2))
        for ((index, body) in bodies.withIndex()) {
            val code = compile(body, hosts)
            val plan = JvmLoopCompiler.prepare(code)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, plan.generatedClassCount)
            val baselineEnv = mutableMapOf<String, Any?>("input" to mapOf("rows" to inputs[index]))
            val expected = VM(hosts).execute(code, baselineEnv)
            calls = 0
            val vm = VM(hosts)
            assertEquals(expected, vm.executeLoopBodies(plan,
                mutableMapOf("input" to mapOf("rows" to inputs[index]))))
            assertFalse(vm.loopBodyExecuted)
            assertEquals(0, vm.loopBodyEntries)
            assertNotNull(vm.loopBodyFallbackReason)
            assertEquals(if (index == 2) 2 else 0, calls)
        }
    }

    @Test
    fun unsupported_loop_edges_and_malformed_operands_never_generate_a_kernel() {
        val source = compile(projection)
        val nextPc = source.opcodes.indexOf(Opcode.NEXT_FOREACH.ordinal)
        val branchPc = source.opcodes.indexOf(Opcode.JUMP_IF_FALSE.ordinal)
        fun withTarget(pc: Int, position: Int, target: Int): Bytecode {
            val operands = source.intOperands.copyOf()
            operands[source.intOperandOffsets[pc] + position] = target
            return source.copy(intOperands = operands)
        }
        val malformed = listOf(
            withTarget(nextPc, 0, nextPc),
            withTarget(nextPc, 1, source.size() + 1),
            withTarget(branchPc, 0, 0),
            source.copy(opcodes = source.opcodes.copyOf().also { it[0] = Int.MAX_VALUE }),
            source.copy(intOperandOffsets = IntArray(0)),
            source.copy(stringOperandOffsets = IntArray(0)),
        )
        for (code in malformed) {
            val plan = JvmLoopCompiler.prepare(code)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, plan.generatedClassCount)
            assertEquals(0, plan.loweredLoopCount)
        }
    }

    @Test
    fun source_array_mutation_after_preparation_cannot_change_the_owned_program() {
        val code = compile(projection)
        val plan = JvmLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        code.opcodes.fill(Opcode.POP.ordinal)
        code.intOperands.fill(Int.MAX_VALUE)
        code.stringOperands.fill("changed")
        code.constOperands.fill(Int.MAX_VALUE)
        val vm = VM()
        val input = mapOf("rows" to listOf(mapOf("id" to 2, "amount" to 6, "parts" to listOf(4))))
        assertEquals(mapOf("rows" to listOf(linkedMapOf("id" to 2, "calc" to 14, "first" to 4))),
            vm.executeLoopBodies(plan, mutableMapOf("input" to input)))
        assertTrue(vm.loopBodyExecuted)
        assertNull(vm.loopBodyFallbackReason)
    }

    @Test
    fun oversized_bodies_are_bounded_before_class_generation() {
        val fields = (0 until 50).joinToString(",") { "v$it: row.id" }
        val plan = JvmLoopCompiler.prepare(compile("OUTPUT {rows: [{$fields} FOR EACH row IN input.rows]}"))
        assertNotNull(plan.fallbackReason)
        assertEquals(0, plan.generatedClassCount)
        assertEquals(0, plan.loweredLoopCount)
    }

    @Test
    fun fallback_tracing_uses_owned_path_write_segment_and_display_collections() {
        for (display in listOf(false, true)) {
            val source = compile("LET seed = {before: 1}; OUTPUT {rows: [row.id FOR EACH row IN input.rows]}")
            val storePc = source.opcodes.indices.first {
                source.getOpcode(it) == Opcode.STORE_VAR.ordinal && source.getStringOperand(it) == "seed"
            }
            val segments = mutableListOf<PathSegmentMeta>(PathSegmentMeta.Static(ObjKey.Name("before")))
            val displayPath = mutableListOf<Any>("visible")
            val code = source.copy(traceMetadata = source.traceMetadata.copy(pathWrites = mapOf(
                storePc to PathWriteMeta(PathWriteOp.SET, "seed", segments,
                    displayPath = if (display) displayPath else null, directValues = display),
            )))
            val plan = JvmLoopCompiler.prepare(code)
            assertNull(plan.fallbackReason)
            segments[0] = PathSegmentMeta.Static(ObjKey.Name("changed"))
            displayPath[0] = "changed"
            val trace = CollectingTracer(TraceOptions(step = true))
            val vm = VM(tracer = trace)
            val env = mutableMapOf<String, Any?>("seed" to mapOf("before" to 0),
                "input" to mapOf("rows" to listOf(mapOf("id" to 7))))
            assertEquals(mapOf("rows" to listOf(7)), vm.executeLoopBodies(plan, env))
            val write = trace.events.map { it.event }.filterIsInstance<TraceEvent.PathWrite>().single { it.root == "seed" }
            assertEquals(if (display) listOf("visible") else listOf("before"), write.path)
            assertFalse(vm.loopBodyExecuted)
        }
    }
}
