@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.ir.IRLet
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalLoopObservationTest {
    private val projection = """
        OUTPUT {rows: [{late: r.more.value, calc: r.amount * 3 + r.id, first: r.parts[0].value}
            FOR EACH r IN input.rows WHERE r.amount > 3]}
    """.trimIndent()

    private fun compile(body: String = projection): Bytecode {
        val transform = Parser(Lexer("TRANSFORM Independent { $body }").lex()).parse()
            .decls.filterIsInstance<TransformDecl>().single()
        return Compiler().compile(ToIR(emptyMap(), emptyMap()).compile(transform.body.statements))
    }

    private fun row(amount: Any? = 5, id: Any? = 1,
        parts: Any? = arrayListOf(linkedMapOf("value" to 7)), more: Any? = linkedMapOf("value" to 9)) =
        linkedMapOf("amount" to amount, "id" to id, "parts" to parts, "more" to more)

    private fun env(rows: List<Any?>) = linkedMapOf<String, Any?>("input" to linkedMapOf("rows" to rows))

    @Suppress("UNCHECKED_CAST")
    private fun rows(result: Any?): List<Any?> = (result as Map<String, Any?>)["rows"] as List<Any?>

    @Test
    fun integer_boundaries_preserve_raw_int_long_and_big_integer_wrappers() {
        data class Case(val expression: String, val a: Any, val b: Any?, val expected: Any)
        val cases = listOf(
            Case("r.a + r.b", 1, 2, 3),
            Case("r.a + r.b", 1L, 2, 3L),
            Case("r.a + r.b", 1, 2L, 3L),
            Case("r.a + r.b", Int.MAX_VALUE, 0, Int.MAX_VALUE),
            Case("r.a + r.b", Int.MAX_VALUE, 1, 2147483648L),
            Case("r.a + r.b", Long.MAX_VALUE, 1, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
            Case("r.a - r.b", Int.MIN_VALUE, 1, -2147483649L),
            Case("r.a - r.b", 4L, 1, 3L),
            Case("r.a - r.b", Long.MIN_VALUE, 1, BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)),
            Case("r.a * r.b", Int.MAX_VALUE, 0, 0),
            Case("r.a * r.b", Long.MAX_VALUE, 0, 0L),
            Case("r.a * r.b", Int.MIN_VALUE, -1, 2147483648L),
            Case("r.a * r.b", Long.MIN_VALUE, -1, BigInteger.ONE.shiftLeft(63)),
            Case("-r.a", 2, null, -2),
            Case("-r.a", 2L, null, -2L),
            Case("-r.a", Int.MIN_VALUE, null, 2147483648L),
            Case("-r.a", Long.MIN_VALUE, null, BigInteger.ONE.shiftLeft(63)),
        )
        for ((expression, group) in cases.groupBy { it.expression }) {
            val code = compile("OUTPUT {rows: [$expression FOR EACH r IN input.rows]}")
            val plan = JvmLocalLoopCompiler.prepare(code)
            assertNull(plan.fallbackReason)
            for (case in group) {
                val input = arrayListOf<Any?>(linkedMapOf("a" to case.a, "b" to case.b))
                val expected = rows(VM().execute(code, env(input))).single()!!
                assertEquals(case.expected, expected)
                assertEquals(case.expected::class, expected::class)
                val actual = rows(VM().executeLocalLoops(plan, env(input))).single()!!
                assertEquals(case.expected, actual)
                assertEquals(case.expected::class, actual::class, "$expression ${case.a::class}/${case.b?.let { it::class }}")
            }
        }
    }

    @Test
    fun nested_callback_after_primitive_arithmetic_sees_exact_spilled_fields_and_no_replay() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        var vm = VM()
        var environment = mutableMapOf<String, Any?>()
        var trace = CollectingTracer(TraceOptions(step = true))
        var calls = 0
        val snapshots = mutableListOf<JsonElement>()
        val late = object : Map<String, Any?> by linkedMapOf("value" to 9) {
            override fun get(key: String): Any? {
                calls++
                snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                environment["side"] = "once"
                Debug.tracer = trace
                return 9
            }
        }
        val input = arrayListOf<Any?>(row(more = late))
        data class Observation(val result: Any?, val snapshots: List<JsonElement>, val events: List<TraceEvent>,
            val counts: Map<String, Long>, val keys: List<String>, val calls: Int)
        fun execute(local: Boolean): Observation {
            calls = 0; snapshots.clear()
            environment = env(input).also { it["r"] = null }
            vm = VM(); trace = CollectingTracer(TraceOptions(step = true))
            val result = if (local) vm.executeLocalLoops(plan, environment) else vm.execute(code, environment)
            assertTrue(environment.containsKey("r"))
            assertNull(environment["r"])
            assertEquals("once", environment["side"])
            if (local) {
                assertTrue(vm.localLoopExecuted)
                assertTrue(vm.localLoopPrimitiveUsed)
                assertNotNull(vm.localLoopFallbackReason)
            }
            return Observation(result, snapshots.toList(), trace.events.map { it.event }, trace.instructionCounts.toMap(),
                environment.keys.toList(), calls)
        }
        val previous = Debug.tracer
        try {
            Debug.tracer = null
            val expected = execute(false)
            assertEquals(1, expected.calls)
            Debug.tracer = null
            val actual = execute(true)
            assertEquals(expected, actual)
            @Suppress("UNCHECKED_CAST")
            val projected = rows(actual.result).single() as Map<String, Any?>
            assertEquals(Int::class, projected["calc"]!!::class)
        } finally { Debug.tracer = previous }
    }

    @Test
    fun errors_with_live_fields_preserve_snapshot_partial_rows_current_binding_and_reuse() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        val failures = listOf(row(more = null), row(more = arrayListOf<Any?>()), row(amount = null),
            row(id = null), row(parts = arrayListOf<Any?>()))
        for (bad in failures) {
            val input = arrayListOf<Any?>(row(), bad)
            val baseline = VM()
            val expectedEnv = env(input).also { it["r"] = "outer" }
            val expectedError = assertFails { baseline.execute(code, expectedEnv) }
            val expectedSnapshot = Json.parseToJsonElement(baseline.snapshotJson())
            val vm = VM()
            val actualEnv = env(input).also { it["r"] = "outer" }
            val actualError = assertFails { vm.executeLocalLoops(plan, actualEnv) }
            assertEquals(expectedError::class, actualError::class)
            assertEquals(expectedError.message, actualError.message)
            assertSame(bad, actualEnv["r"])
            assertEquals(expectedSnapshot, Json.parseToJsonElement(vm.snapshotJson()))
            val next = env(arrayListOf(row()))
            assertEquals(mapOf("rows" to listOf(linkedMapOf("late" to 9, "calc" to 16, "first" to 7))),
                vm.executeLocalLoops(plan, next))
            assertEquals(false, next.containsKey("r"))
            assertNull(vm.localLoopFallbackReason)
        }
    }

    @Test
    fun unsafe_primitive_magnitude_after_a_completed_row_resumes_current_arithmetic_once() {
        val code = compile(projection.replace("* 3", "* 5000000"))
        val input = arrayListOf<Any?>(row(), row(amount = Int.MAX_VALUE))
        val vm = VM()
        val result = vm.executeLocalLoops(JvmLocalLoopCompiler.prepare(code), env(input))
        val expected = mapOf("rows" to listOf(
            linkedMapOf<String, Any?>("late" to 9, "calc" to 25000001, "first" to 7),
            linkedMapOf<String, Any?>("late" to 9, "calc" to 10737418235000001L, "first" to 7),
        ))
        assertEquals(expected, VM().execute(code, env(input)))
        assertEquals(expected, result)
        assertTrue(vm.localLoopPrimitiveUsed)
        assertNotNull(vm.localLoopFallbackReason)
        @Suppress("UNCHECKED_CAST")
        val values = rows(result) as List<Map<String, Any?>>
        assertEquals(Int::class, values[0]["calc"]!!::class)
        assertEquals(Long::class, values[1]["calc"]!!::class)
    }

    @Test
    fun custom_number_conversion_and_big_integer_methods_observe_canonical_spilled_state() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        var vm = VM()
        var trace = CollectingTracer(TraceOptions(step = true))
        val snapshots = mutableListOf<JsonElement>()
        var calls = 0
        fun callback() { calls++; snapshots.add(Json.parseToJsonElement(vm.snapshotJson())); Debug.tracer = trace }
        val customNumber = object : Number() {
            override fun toDouble(): Double { callback(); return 4.5 }
            override fun toFloat(): Float = error("unexpected narrowing")
            override fun toLong(): Long = error("unexpected narrowing")
            override fun toInt(): Int = error("unexpected narrowing")
            override fun toShort(): Short = error("unexpected narrowing")
            override fun toByte(): Byte = error("unexpected narrowing")
            override fun toString(): String = "custom-number"
        }
        val customBig = object : BigInteger("5") {
            override fun toShort(): Short = toInt().toShort()
            override fun toByte(): Byte = toInt().toByte()
            override fun multiply(value: BigInteger): BigInteger { callback(); return super.multiply(value) }
        }
        data class Observation(val result: Any?, val snapshots: List<JsonElement>, val events: List<TraceEvent>, val counts: Map<String, Long>, val calls: Int)
        val previous = Debug.tracer
        try {
            for (input in listOf(arrayListOf<Any?>(row(id = customNumber)), arrayListOf<Any?>(row(amount = customBig)))) {
                fun execute(local: Boolean): Observation {
                    calls = 0; snapshots.clear(); vm = VM(); trace = CollectingTracer(TraceOptions(step = true))
                    val result = if (local) vm.executeLocalLoops(plan, env(input)) else vm.execute(code, env(input))
                    return Observation(result, snapshots.toList(), trace.events.map { it.event }, trace.instructionCounts.toMap(), calls)
                }
                Debug.tracer = null
                val expected = execute(false)
                assertEquals(1, expected.calls)
                Debug.tracer = null
                assertEquals(expected, execute(true))
            }
        } finally { Debug.tracer = previous }
    }

    @Test
    fun access_order_linked_maps_keep_every_emitted_lookup() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        fun execute(local: Boolean): Pair<Any?, List<String>> {
            val inputRow = java.util.LinkedHashMap<String, Any?>(16, 0.75f, true)
            inputRow.putAll(row())
            val vm = VM()
            val result = if (local) vm.executeLocalLoops(plan, env(arrayListOf(inputRow)))
                else vm.execute(code, env(arrayListOf(inputRow)))
            return result to inputRow.keys.toList()
        }
        val expected = execute(false)
        assertEquals(listOf("parts", "amount", "id", "more"), expected.second)
        assertEquals(expected, execute(true))
    }

    @Test
    fun exact_string_lookup_in_a_collision_tree_never_calls_existing_custom_keys() {
        var armed = false
        class CollisionKey(private val id: Int) : Comparable<CollisionKey> {
            override fun hashCode(): Int { check(!armed); return "amount".hashCode() }
            override fun equals(other: Any?): Boolean { check(!armed); return other is CollisionKey && id == other.id }
            override fun compareTo(other: CollisionKey): Int { check(!armed); return id.compareTo(other.id) }
        }
        val inputRow = java.util.HashMap<Any, Any?>(64)
        repeat(12) { inputRow[CollisionKey(it)] = it }
        inputRow.putAll(row())
        armed = true
        val code = compile()
        val expected = VM().execute(code, env(arrayListOf(inputRow)))
        val vm = VM()
        assertEquals(expected, vm.executeLocalLoops(JvmLocalLoopCompiler.prepare(code), env(arrayListOf(inputRow))))
        assertTrue(vm.localLoopPrimitiveUsed)
        assertNull(vm.localLoopFallbackReason)
    }

    @Test
    fun next_list_failure_restores_advanced_index_without_duplicate_append() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        val first = row()
        val second = row(id = 2)
        var fail = true
        val failure = IllegalStateException("next item failed")
        val input = object : AbstractList<Any?>() {
            override val size: Int get() = 2
            override fun get(index: Int): Any? {
                if (index == 1 && fail) { fail = false; throw failure }
                return if (index == 0) first else second
            }
        }
        fun execute(local: Boolean): JsonElement {
            fail = true
            val vm = VM()
            val environment = env(input)
            assertSame(failure, assertFailsWith<IllegalStateException> {
                if (local) vm.executeLocalLoops(plan, environment) else vm.execute(code, environment)
            })
            assertSame(first, environment["r"])
            return Json.parseToJsonElement(vm.snapshotJson())
        }
        assertEquals(execute(false), execute(true))
    }

    @Test
    fun non_integer_comparison_fallback_keeps_vm_nan_and_null_ordering() {
        val code = compile("OUTPUT {rows: [r.value FOR EACH r IN input.rows WHERE r.amount > 3]}")
        val input = arrayListOf<Any?>(
            linkedMapOf("amount" to 5, "value" to 1),
            linkedMapOf("amount" to Double.NaN, "value" to 2),
            linkedMapOf("amount" to null, "value" to 3),
            linkedMapOf("amount" to Double.NEGATIVE_INFINITY, "value" to 4),
        )
        val expected = VM().execute(code, env(input))
        assertEquals(mapOf("rows" to listOf(1, 2, 3)), expected)
        val vm = VM()
        assertEquals(expected, vm.executeLocalLoops(JvmLocalLoopCompiler.prepare(code), env(input)))
        assertTrue(vm.localLoopPrimitiveUsed)
        assertNotNull(vm.localLoopFallbackReason)
    }

    @Test
    fun predicate_guard_deoptimization_preserves_branch_exit_callback_pc_and_stack() {
        val original = compile("OUTPUT {rows: [r.id FOR EACH r IN input.rows WHERE r.flag]}")
        val branch = original.opcodes.indexOf(Opcode.JUMP_IF_FALSE.ordinal)
        val node = IRLet("filter", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
        val code = original.copy(traceMetadata = original.traceMetadata.copy(
            exitEvents = original.traceMetadata.exitEvents + (branch to listOf(node)),
        ))
        val plan = JvmLocalLoopCompiler.prepare(code)
        assertNull(plan.fallbackReason)
        val previous = Debug.tracer
        try {
            for (accepted in listOf(false, true)) {
                var vm = VM()
                var snapshots = mutableListOf<JsonElement>()
                val trace = object : Tracer {
                    override val opts = TraceOptions(step = true)
                    override fun on(event: TraceEvent) {
                        if (event is TraceEvent.Exit && event.node == node) snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                    }
                }
                val flag = object : BigInteger(if (accepted) "1" else "0") {
                    override fun toShort(): Short = toInt().toShort()
                    override fun toByte(): Byte = toInt().toByte()
                    override fun signum(): Int { Debug.tracer = trace; return super.signum() }
                }
                val input = arrayListOf<Any?>(linkedMapOf("id" to 7, "flag" to flag))
                fun execute(local: Boolean): Pair<Any?, List<JsonElement>> {
                    Debug.tracer = null; vm = VM(); snapshots = mutableListOf()
                    val result = if (local) vm.executeLocalLoops(plan, env(input)) else vm.execute(code, env(input))
                    if (local) assertTrue(vm.localLoopExecuted)
                    assertEquals(1, snapshots.size)
                    return result to snapshots.toList()
                }
                assertEquals(execute(false), execute(true))
            }
        } finally { Debug.tracer = previous }
    }

    @Test
    fun tracer_close_stays_guarded_after_local_spill_and_in_initial_fallback() {
        val code = compile()
        val plan = JvmLocalLoopCompiler.prepare(code)
        val previous = Debug.tracer
        try {
            for (activeAtEntry in listOf(false, true)) {
                Debug.tracer = null
                val vm = VM()
                var closes = 0
                val trace = object : Tracer, AutoCloseable {
                    override val opts = TraceOptions(step = true)
                    override fun on(event: TraceEvent) = Unit
                    override fun close() {
                        closes++
                        assertFailsWith<IllegalStateException> { vm.executeLocalLoops(plan, mutableMapOf()) }
                        assertFailsWith<IllegalStateException> { vm.execute(code) }
                        assertFailsWith<IllegalStateException> { vm.begin(code) }
                        assertFailsWith<IllegalStateException> { vm.step() }
                    }
                }
                val late = object : Map<String, Any?> by linkedMapOf("value" to 9) {
                    override fun get(key: String): Any? { Debug.tracer = trace; return 9 }
                }
                if (activeAtEntry) Debug.tracer = trace
                assertEquals(mapOf("rows" to listOf(linkedMapOf("late" to 9, "calc" to 16, "first" to 7))),
                    vm.executeLocalLoops(plan, env(arrayListOf(row(more = late)))))
                assertEquals(1, closes)
                assertEquals(!activeAtEntry, vm.localLoopExecuted)
            }
        } finally { Debug.tracer = previous }
    }
}
