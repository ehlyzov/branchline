@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.ObjKey
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.IRLet
import io.github.ehlyzov.branchline.ir.ToIR
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoopBodyObservationTest {
    private fun compile(): Bytecode {
        val source = """
            TRANSFORM Independent {
                OUTPUT {rows: [{id: row.id, calc: row.amount * 2 + row.id, first: row.parts[0].value}
                    FOR EACH row IN input.rows WHERE row.amount > 3]}
            }
        """.trimIndent()
        val transform = Parser(Lexer(source).lex()).parse().decls.filterIsInstance<TransformDecl>().single()
        return Compiler().compile(ToIR(emptyMap(), emptyMap()).compile(transform.body.statements))
    }

    private fun row(id: Any? = 1, amount: Any? = 5, parts: Any? = listOf(mapOf("value" to 7))) =
        linkedMapOf("id" to id, "amount" to amount, "parts" to parts)

    @Test
    fun tracer_exit_callback_on_generated_filter_sees_canonical_branch_pc_before_increment() {
        val source = "TRANSFORM Filter { OUTPUT {rows: [row.id FOR EACH row IN input.rows WHERE row.flag]} }"
        val transform = Parser(Lexer(source).lex()).parse().decls.filterIsInstance<TransformDecl>().single()
        val original = Compiler().compile(ToIR(emptyMap(), emptyMap()).compile(transform.body.statements))
        val branchPc = original.opcodes.indexOf(Opcode.JUMP_IF_FALSE.ordinal)
        val node = IRLet("filter", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        val code = original.copy(traceMetadata = original.traceMetadata.copy(
            exitEvents = original.traceMetadata.exitEvents + (branchPc to listOf(node)),
        ))
        val plan = JvmLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        val previous = Debug.tracer
        try {
            for (accepted in listOf(false, true)) {
                var vm = VM()
                var snapshots = mutableListOf<JsonElement>()
                val tracer = object : Tracer {
                    override val opts = TraceOptions(step = true)
                    override fun on(event: TraceEvent) {
                        if (event is TraceEvent.Exit && event.node == node) snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                    }
                }
                val flag = object : BigInteger(if (accepted) "1" else "0") {
                    override fun toShort(): Short = toInt().toShort()
                    override fun toByte(): Byte = toInt().toByte()
                    override fun signum(): Int { Debug.tracer = tracer; return super.signum() }
                }
                val input = mapOf("rows" to listOf(mapOf("id" to 7, "flag" to flag)))
                fun execute(generated: Boolean): Pair<Any?, List<JsonElement>> {
                    Debug.tracer = null
                    snapshots = mutableListOf()
                    vm = VM()
                    val env = mutableMapOf<String, Any?>("input" to input)
                    val result = if (generated) vm.executeLoopBodies(plan, env) else vm.execute(code, env)
                    assertEquals(1, snapshots.size)
                    return result to snapshots.toList()
                }
                assertEquals(execute(false), execute(true))
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun next_foreach_list_failure_keeps_completed_row_and_original_advanced_loop_state() {
        val code = compile()
        val plan = JvmLoopCompiler.prepare(code)
        val first = row()
        val second = row(id = 2)
        val failure = IllegalStateException("second item failed")
        var failNext = true
        var failedReads = 0
        val rows = object : AbstractList<Any?>() {
            override val size: Int get() = 2
            override fun get(index: Int): Any? {
                if (index == 1 && failNext) {
                    failNext = false
                    failedReads++
                    throw failure
                }
                return if (index == 0) first else second
            }
        }
        fun execute(generated: Boolean): JsonElement {
            failNext = true
            failedReads = 0
            val vm = VM()
            val env = mutableMapOf<String, Any?>("input" to mapOf("rows" to rows))
            assertSame(failure, assertFailsWith<IllegalStateException> {
                if (generated) vm.executeLoopBodies(plan, env) else vm.execute(code, env)
            })
            assertEquals(1, failedReads)
            assertSame(first, env["row"])
            // Snapshot serialization may read the custom list; only its first get(1) fails.
            return Json.parseToJsonElement(vm.snapshotJson())
        }
        val expected = execute(false)
        assertEquals(expected, execute(true))
    }

    @Test
    fun errors_after_a_completed_row_preserve_partial_builder_loop_binding_pc_stack_and_reuse() {
        val code = compile()
        val plan = JvmLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        val failures = listOf(
            row(id = 2, amount = "bad", parts = emptyList<Any?>()),
            row(id = 2, amount = null),
            linkedMapOf("amount" to 6, "parts" to listOf(mapOf("value" to 7))),
            row(id = 3, parts = listOf(null)),
            row(id = 4, parts = emptyList<Any?>()),
            linkedMapOf("id" to 5, "amount" to 5),
        )
        for (bad in failures) {
            val input = mapOf("rows" to listOf(row(), bad))
            val baselineEnv = linkedMapOf<String, Any?>("input" to input, "row" to "outer")
            val baseline = VM()
            val expected = assertFails { baseline.execute(code, baselineEnv) }
            val expectedSnapshot = Json.parseToJsonElement(baseline.snapshotJson())
            assertSame(bad, baselineEnv["row"])
            val env = linkedMapOf<String, Any?>("input" to input, "row" to "outer")
            val vm = VM()
            val actual = assertFails { vm.executeLoopBodies(plan, env) }
            assertEquals(expected::class, actual::class)
            assertEquals(expected.message, actual.message)
            assertSame(bad, env["row"])
            assertEquals(baselineEnv.keys.toList(), env.keys.toList())
            assertEquals(expectedSnapshot, Json.parseToJsonElement(vm.snapshotJson()))
            val nextEnv = linkedMapOf<String, Any?>("input" to mapOf("rows" to listOf(row())), "row" to null)
            assertEquals(mapOf("rows" to listOf(linkedMapOf("id" to 1, "calc" to 11, "first" to 7))),
                vm.executeLoopBodies(plan, nextEnv))
            assertTrue(nextEnv.containsKey("row"))
            assertNull(nextEnv["row"])
            assertTrue(vm.loopBodyExecuted)
            assertNull(vm.loopBodyFallbackReason)
        }
    }

    @Test
    fun generic_numeric_null_terminal_and_shadowing_semantics_have_independent_expected_values() {
        val code = compile()
        val plan = JvmLoopCompiler.prepare(code)
        val inputRows = listOf(
            row(id = 0, amount = 3, parts = emptyList<Any?>()),
            row(id = "z", amount = 5, parts = listOf(emptyMap<String, Any?>())),
            row(id = 2, amount = Long.MAX_VALUE),
        )
        val exact = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)).add(BigInteger.valueOf(2))
        val expected = mapOf("rows" to listOf(
            linkedMapOf("id" to "z", "calc" to "10z", "first" to null),
            linkedMapOf("id" to 2, "calc" to exact, "first" to 7),
        ))
        for (outerPresent in listOf(false, true)) {
            val env = linkedMapOf<String, Any?>("input" to mapOf("rows" to inputRows))
            if (outerPresent) env["row"] = 91
            val vm = VM()
            assertEquals(expected, vm.executeLoopBodies(plan, env))
            assertEquals(outerPresent, env.containsKey("row"))
            assertEquals(if (outerPresent) 91 else null, env["row"])
            assertTrue(vm.loopBodyExecuted)
            @Suppress("UNCHECKED_CAST")
            val output = (vm.executeLoopBodies(plan, env) as Map<String, Any?>)["rows"] as List<Map<String, Any?>>
            assertEquals(listOf("id", "calc", "first"), output.first().keys.toList())
        }
    }

    @Test
    fun custom_row_observes_field_order_and_exact_callback_snapshot_then_activates_trace_once() {
        val original = compile()
        val partsPc = original.opcodes.indices.first {
            original.getOpcode(it) == Opcode.ACCESS_STATIC.ordinal && original.getObjKeyOperand(it) == ObjKey.Name("parts")
        }
        val node = IRLet("parts", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        val code = original.copy(traceMetadata = original.traceMetadata.copy(
            enterEvents = original.traceMetadata.enterEvents + (partsPc to listOf(node)),
            exitEvents = original.traceMetadata.exitEvents + (partsPc to listOf(node)),
        ))
        val plan = JvmLoopCompiler.prepare(code)
        var currentEnv = mutableMapOf<String, Any?>()
        var currentVm = VM()
        var currentTrace = CollectingTracer(TraceOptions(step = true))
        val operations = mutableListOf<String>()
        val snapshots = mutableListOf<JsonElement>()
        val backing = row()
        val observed = object : Map<String, Any?> by backing {
            override fun get(key: String): Any? {
                operations.add(key)
                if (key == "parts") {
                    assertSame(this, currentEnv["row"])
                    snapshots.add(Json.parseToJsonElement(currentVm.snapshotJson()))
                    currentEnv["seen"] = "callback"
                    Debug.tracer = currentTrace
                }
                return backing[key]
            }
        }
        data class Observation(val result: Any?, val operations: List<String>, val snapshots: List<JsonElement>,
            val events: List<TraceEvent>, val counts: Map<String, Long>, val keys: List<String>)
        fun execute(generated: Boolean): Observation {
            operations.clear(); snapshots.clear()
            currentEnv = linkedMapOf("input" to mapOf("rows" to listOf(observed)), "row" to "outer")
            currentVm = VM()
            currentTrace = CollectingTracer(TraceOptions(step = true))
            val result = if (generated) currentVm.executeLoopBodies(plan, currentEnv) else currentVm.execute(code, currentEnv)
            assertEquals("outer", currentEnv["row"])
            assertEquals("callback", currentEnv["seen"])
            return Observation(result, operations.toList(), snapshots.toList(), currentTrace.events.map { it.event },
                currentTrace.instructionCounts.toMap(), currentEnv.keys.toList())
        }
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val expected = execute(false)
            assertEquals(listOf("amount", "parts", "amount", "id", "id"), expected.operations)
            assertEquals(1, expected.snapshots.size)
            Debug.tracer = null
            assertEquals(expected, execute(true))
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun numeric_subclass_callback_retains_source_state_and_trace_without_replay() {
        val code = compile()
        val plan = JvmLoopCompiler.prepare(code)
        var env = mutableMapOf<String, Any?>()
        var vm = VM()
        var trace = CollectingTracer(TraceOptions(step = true))
        val snapshots = mutableListOf<JsonElement>()
        var calls = 0
        val amount = object : BigInteger("5") {
            override fun toShort(): Short = toInt().toShort()
            override fun toByte(): Byte = toInt().toByte()
            override fun multiply(value: BigInteger): BigInteger {
                calls++
                assertTrue(env.containsKey("row"))
                snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                Debug.tracer = trace
                return super.multiply(value)
            }
        }
        val input = mapOf("rows" to listOf(row(amount = amount)))
        data class Observation(val result: Any?, val calls: Int, val snapshots: List<JsonElement>,
            val events: List<TraceEvent>, val counts: Map<String, Long>)
        fun execute(generated: Boolean): Observation {
            calls = 0; snapshots.clear()
            env = mutableMapOf("input" to input)
            vm = VM()
            trace = CollectingTracer(TraceOptions(step = true))
            val result = if (generated) vm.executeLoopBodies(plan, env) else vm.execute(code, env)
            return Observation(result, calls, snapshots.toList(), trace.events.map { it.event }, trace.instructionCounts.toMap())
        }
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val expected = execute(false)
            assertEquals(1, expected.calls)
            Debug.tracer = null
            assertEquals(expected, execute(true))
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun tracer_close_keeps_all_execution_entries_non_reentrant_in_generated_and_initial_fallback_paths() {
        val code = compile()
        val plan = JvmLoopCompiler.prepare(code)
        val previous = Debug.tracer
        try {
            for (activeAtEntry in listOf(false, true)) {
                Debug.tracer = null
                val vm = VM()
                val env = mutableMapOf<String, Any?>()
                var closes = 0
                val tracer = object : Tracer, AutoCloseable {
                    override val opts = TraceOptions(step = true)
                    override fun on(event: TraceEvent) = Unit
                    override fun close() {
                        closes++
                        assertFailsWith<IllegalStateException> { vm.executeLoopBodies(plan, mutableMapOf()) }
                        assertFailsWith<IllegalStateException> { vm.execute(code) }
                        assertFailsWith<IllegalStateException> { vm.begin(code) }
                        assertFailsWith<IllegalStateException> { vm.step() }
                    }
                }
                val backing = row()
                val observed = object : Map<String, Any?> by backing {
                    override fun get(key: String): Any? {
                        if (key == "parts") Debug.tracer = tracer
                        return backing[key]
                    }
                }
                env["input"] = mapOf("rows" to listOf(observed))
                if (activeAtEntry) Debug.tracer = tracer
                assertEquals(mapOf("rows" to listOf(linkedMapOf("id" to 1, "calc" to 11, "first" to 7))),
                    vm.executeLoopBodies(plan, env))
                assertEquals(1, closes)
                if (activeAtEntry) assertFalse(vm.loopBodyExecuted) else assertTrue(vm.loopBodyExecuted)
            }
        } finally {
            Debug.tracer = previous
        }
    }
}
