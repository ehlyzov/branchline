@file:OptIn(ExperimentalVMApi::class)

package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.ir.IRLet
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ExperimentalBigNumberObservationTest {
    private val modes = listOf(
        VMExperimentalOptions(slots = true),
        VMExperimentalOptions(fusion = true),
        VMExperimentalOptions(slots = true, fusion = true),
    )
    private fun mirror() = listOf(
        Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("x"), Instruction.STORE_LOCAL(0),
    )
    private fun result() = listOf(Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(2))

    @Test
    fun subclass_truth_callbacks_match_ordinary_values_source_pc_and_trace_without_replay() {
        val factories: List<(() -> Unit) -> Any> = listOf(
            { callback -> object : BigInteger("1") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun signum(): Int { callback(); return super.signum() }
            } },
            { callback -> object : BigDecimal("1") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun signum(): Int { callback(); return super.signum() }
            } },
        )
        val bodies = listOf(
            listOf(Instruction.NOT, Instruction.POP) to 5,
            listOf(Instruction.PUSH(true), Instruction.AND, Instruction.POP) to 6,
            listOf(Instruction.PUSH(true), Instruction.OR, Instruction.POP) to 6,
            listOf(Instruction.JUMP_IF_TRUE(7), Instruction.JUMP(7)) to 5,
            listOf(Instruction.JUMP_IF_FALSE(7), Instruction.JUMP(7)) to 5,
        )
        data class Observation(
            val result: Any?, val seen: List<Any?>, val callbackSnapshots: List<JsonElement>,
            val finalSnapshot: JsonElement, val events: List<Any>, val counts: Map<String, Long>,
        )
        val previous = Debug.tracer
        try {
            for (factory in factories) for ((body, operationPc) in bodies) {
                val node = IRLet("truth", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
                val code = Bytecode.fromInstructions(mirror() + Instruction.LOAD_VAR("input") + body + result(),
                    traceMetadata = TraceMetadata(enterEvents = mapOf(operationPc to listOf(node)), exitEvents = mapOf(operationPc to listOf(node))))
                fun execute(mode: VMExperimentalOptions?): Observation {
                    Debug.tracer = null
                    val env = mutableMapOf<String, Any?>()
                    val vm = VM()
                    val seen = mutableListOf<Any?>()
                    val snapshots = mutableListOf<JsonElement>()
                    val trace = CollectingTracer(TraceOptions(step = true))
                    env["input"] = factory {
                        seen.add(env["x"])
                        snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                        env["x"] = 11
                        Debug.tracer = trace
                    }
                    val value = if (mode == null) vm.execute(code, env)
                        else vm.executeExperimental(vm.prepareExperimental(code, mode), env)
                    return Observation(value, seen, snapshots, Json.parseToJsonElement(vm.snapshotJson()),
                        trace.events.map { it.event }, trace.instructionCounts.toMap())
                }
                val expected = execute(null)
                assertEquals(listOf(7, 11), expected.result)
                assertEquals(listOf(7), expected.seen)
                assertEquals(operationPc.toString(), expected.callbackSnapshots.single().jsonObject.getValue("pc").jsonPrimitive.content)
                for (mode in modes) assertEquals(expected, execute(mode), "$mode at $operationPc")
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun custom_arithmetic_observes_flushed_aliases_and_exact_failure_state_once() {
        val code = Bytecode.fromInstructions(mirror() + listOf(
            Instruction.LOAD_VAR("input"), Instruction.PUSH(1), Instruction.ADD, Instruction.POP,
        ) + result())
        val factories: List<(() -> Unit) -> Any> = listOf(
            { callback -> object : BigInteger("1") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun add(value: BigInteger): BigInteger { callback(); return super.add(value) }
            } },
            { callback -> object : BigDecimal("1") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun add(value: BigDecimal): BigDecimal { callback(); return super.add(value) }
            } },
        )
        for (factory in factories) for (fail in listOf(false, true)) {
            var expectedSnapshots: List<JsonElement>? = null
            for (mode in listOf<VMExperimentalOptions?>(null) + modes) {
                val env = mutableMapOf<String, Any?>()
                val vm = VM()
                val snapshots = mutableListOf<JsonElement>()
                val failure = IllegalStateException("numeric callback")
                var calls = 0
                env["input"] = factory {
                    calls++
                    assertEquals(7, env["x"])
                    snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                    env["x"] = 11
                    if (fail) throw failure
                }
                fun execute(): Any? = if (mode == null) vm.execute(code, env)
                    else vm.executeExperimental(vm.prepareExperimental(code, mode), env)
                if (fail) assertSame(failure, assertFailsWith<IllegalStateException> { execute() })
                else assertEquals(listOf(7, 11), execute())
                assertEquals(1, calls)
                assertEquals(11, env["x"])
                assertEquals("6", snapshots.single().jsonObject.getValue("pc").jsonPrimitive.content)
                snapshots.add(Json.parseToJsonElement(vm.snapshotJson()))
                if (mode == null) expectedSnapshots = snapshots else assertEquals(expectedSnapshots, snapshots, "$mode failure=$fail")
                if (mode != null) assertNotNull(vm.experimentalReport.fallbackReason)
            }
        }
    }

    @Test
    fun custom_static_numeric_key_falls_back_before_preparation_or_fusion_can_invoke_it() {
        for (mode in modes) {
            var calls = 0
            val key = IBig(object : BigInteger("0") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun signum(): Int { calls++; return super.signum() }
            })
            val code = Bytecode.fromInstructions(listOf(Instruction.LOAD_VAR("items"), Instruction.ACCESS_STATIC(key)))
            val vm = VM()
            val plan = vm.prepareExperimental(code, mode)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, calls)
            assertEquals(7, vm.executeExperimental(plan, mutableMapOf("items" to listOf(7))))
            assertEquals(1, calls)
        }
    }
}
