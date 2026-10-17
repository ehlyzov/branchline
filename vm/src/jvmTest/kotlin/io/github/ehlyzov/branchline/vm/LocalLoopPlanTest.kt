@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
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

class LocalLoopPlanTest {
    private fun compile(body: String, hosts: Map<String, (List<Any?>) -> Any?> = emptyMap()): Bytecode {
        val transform = Parser(Lexer("TRANSFORM Independent { $body }").lex()).parse()
            .decls.filterIsInstance<TransformDecl>().single()
        return Compiler(hostFns = hosts).compile(ToIR(emptyMap(), hosts).compile(transform.body.statements))
    }

    private val source = "OUTPUT {rows: [item.n * 3 + item.id FOR EACH item IN input.rows WHERE item.n > 2]}"
    private fun env() = linkedMapOf<String, Any?>("input" to linkedMapOf("rows" to arrayListOf(
        linkedMapOf("n" to 1, "id" to 9), linkedMapOf("n" to 4, "id" to 2),
    )))

    @Test
    fun different_scalar_object_and_filter_shapes_enter_real_local_lowering() {
        val cases = listOf(
            source to mapOf("rows" to listOf(14)),
            "OUTPUT {rows: [-item.n FOR EACH item IN input.rows]}" to mapOf("rows" to listOf(-1, -4)),
            "OUTPUT {rows: [{value: item.n * 2, id: item.id} FOR EACH item IN input.rows]}" to
                mapOf("rows" to listOf(linkedMapOf("value" to 2, "id" to 9), linkedMapOf("value" to 8, "id" to 2))),
        )
        for ((body, expected) in cases) {
            val plan = JvmLocalLoopCompiler.prepare(compile(body))
            assertNull(plan.fallbackReason)
            assertEquals(1, plan.loweredLoopCount)
            assertEquals(1, plan.generatedClassCount)
            assertTrue(plan.localValueCount > 0)
            assertTrue(plan.primitiveSiteCount > 0)
            assertTrue(plan.spillSiteCount > 0)
            assertTrue(plan.guardSiteCount > 0)
            assertTrue(plan.generatedBytecodeBytes > 0)
            val vm = VM()
            assertEquals(expected, vm.executeLocalLoops(plan, env()))
            assertTrue(vm.localLoopExecuted)
            assertTrue(vm.localLoopPrimitiveUsed)
            assertTrue(vm.localLoopEntries > 0)
            assertNull(vm.localLoopFallbackReason)
        }
    }

    @Test
    fun empty_collection_does_not_claim_local_or_primitive_execution() {
        val vm = VM()
        val plan = JvmLocalLoopCompiler.prepare(compile(source))
        assertEquals(mapOf("rows" to emptyList<Any?>()), vm.executeLocalLoops(plan,
            mutableMapOf("input" to linkedMapOf("rows" to arrayListOf<Any?>()))))
        assertFalse(vm.localLoopExecuted)
        assertFalse(vm.localLoopPrimitiveUsed)
        assertEquals(0, vm.localLoopEntries)
        assertNull(vm.localLoopFallbackReason)
    }

    @Test
    fun unsupported_operations_and_calls_report_fallback_before_any_effect() {
        var calls = 0
        val hosts: Map<String, (List<Any?>) -> Any?> = mapOf("record" to { args -> calls++; args.single() })
        val bodies = listOf(
            "OUTPUT {rows: [item.n / 2 FOR EACH item IN input.rows]}",
            "OUTPUT {rows: [record(item.n) FOR EACH item IN input.rows]}",
            "LET f = (x) -> x + 1; OUTPUT {rows: [f(item.n) FOR EACH item IN input.rows]}",
            "OUTPUT {rows: [[x FOR EACH x IN [1, 2]] FOR EACH item IN input.rows]}",
        )
        for ((index, body) in bodies.withIndex()) {
            val code = compile(body, hosts)
            calls = 0
            val plan = JvmLocalLoopCompiler.prepare(code)
            assertEquals(0, calls)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, plan.generatedClassCount)
            val expected = VM(hosts).execute(code, env())
            calls = 0
            val vm = VM(hosts)
            assertEquals(expected, vm.executeLocalLoops(plan, env()))
            assertEquals(if (index == 1) 2 else 0, calls)
            assertFalse(vm.localLoopExecuted)
            assertFalse(vm.localLoopPrimitiveUsed)
            assertNotNull(vm.localLoopFallbackReason)
        }
    }

    @Test
    fun malformed_control_flow_operands_and_oversized_bodies_have_no_generated_class() {
        val code = compile(source)
        val next = code.opcodes.indexOf(Opcode.NEXT_FOREACH.ordinal)
        val branch = code.opcodes.indexOf(Opcode.JUMP_IF_FALSE.ordinal)
        fun target(pc: Int, position: Int, address: Int) = code.copy(intOperands = code.intOperands.copyOf().also {
            it[code.intOperandOffsets[pc] + position] = address
        })
        val fields = (0 until 50).joinToString(",") { "v$it: item.n" }
        val cases = listOf(
            target(next, 0, next), target(branch, 0, 0), target(next, 1, code.size() + 1),
            code.copy(intOperandOffsets = IntArray(0)), code.copy(stringOperandOffsets = IntArray(0)),
            code.copy(opcodes = code.opcodes.copyOf().also { it[0] = Int.MAX_VALUE }),
            compile("OUTPUT {rows: [{$fields} FOR EACH item IN input.rows]}"),
        )
        for (bad in cases) {
            val plan = JvmLocalLoopCompiler.prepare(bad)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, plan.generatedClassCount)
            assertEquals(0, plan.loweredLoopCount)
        }
    }

    @Test
    fun caller_mutation_of_source_arrays_does_not_rewrite_the_prepared_program() {
        val code = compile(source)
        val plan = JvmLocalLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        code.opcodes.fill(Opcode.POP.ordinal)
        code.intOperands.fill(Int.MAX_VALUE)
        code.stringOperands.fill("changed")
        code.constOperands.fill(Int.MAX_VALUE)
        val vm = VM()
        assertEquals(mapOf("rows" to listOf(14)), vm.executeLocalLoops(plan, env()))
        assertTrue(vm.localLoopPrimitiveUsed)
        assertNull(vm.localLoopFallbackReason)
    }

    @Test
    fun traced_fallback_owns_path_write_metadata_collections() {
        val original = compile("LET seed = {before: 1}; $source")
        val store = original.opcodes.indices.first {
            original.getOpcode(it) == Opcode.STORE_VAR.ordinal && original.getStringOperand(it) == "seed"
        }
        for (display in listOf(false, true)) {
            val segments = mutableListOf<PathSegmentMeta>(PathSegmentMeta.Static(ObjKey.Name("before")))
            val path = mutableListOf<Any>("visible")
            val code = original.copy(traceMetadata = original.traceMetadata.copy(pathWrites = mapOf(
                store to PathWriteMeta(PathWriteOp.SET, "seed", segments, if (display) path else null, display),
            )))
            val plan = JvmLocalLoopCompiler.prepare(code)
            segments[0] = PathSegmentMeta.Static(ObjKey.Name("changed"))
            path[0] = "changed"
            val trace = CollectingTracer(TraceOptions(step = true))
            val vm = VM(tracer = trace)
            val environment = env().also { it["seed"] = mapOf("before" to 0) }
            assertEquals(mapOf("rows" to listOf(14)), vm.executeLocalLoops(plan, environment))
            val write = trace.events.map { it.event }.filterIsInstance<TraceEvent.PathWrite>().single { it.root == "seed" }
            assertEquals(if (display) listOf("visible") else listOf("before"), write.path)
            assertFalse(vm.localLoopExecuted)
        }
    }
}
