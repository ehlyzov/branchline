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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class PreparedBlockBigNumberObservationTest {
    private fun mirror() = listOf(
        Instruction.PUSH(7), Instruction.DUP, Instruction.STORE_VAR("x"), Instruction.STORE_LOCAL(0),
    )

    @Test
    fun subclass_truth_callbacks_observe_flushed_values_and_preserve_trace_at_the_source_instruction() {
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
            listOf(Instruction.JUMP_IF_TRUE(7), Instruction.NOP) to 5,
            listOf(Instruction.JUMP_IF_FALSE(7), Instruction.NOP) to 5,
        )
        data class Observation(val result: Any?, val seen: List<Any?>, val events: List<Any>, val counts: Map<String, Long>)
        val previous = Debug.tracer
        try {
            for (factory in factories) for ((body, operationPc) in bodies) {
                val node = IRLet("truth", NumberLiteral(I32(1), Token(TokenType.NUMBER, "1", 1, 1)))
                val code = Bytecode.fromInstructions(mirror() + Instruction.LOAD_VAR("input") + body + listOf(
                    Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(2),
                ), traceMetadata = TraceMetadata(enterEvents = mapOf(operationPc to listOf(node)), exitEvents = mapOf(operationPc to listOf(node))))
                fun execute(optimize: Boolean?): Observation {
                    val env = mutableMapOf<String, Any?>()
                    val seen = mutableListOf<Any?>()
                    val trace = CollectingTracer(TraceOptions(step = true))
                    env["input"] = factory {
                        seen.add(env["x"])
                        env["x"] = 11
                        Debug.tracer = trace
                    }
                    val vm = VM()
                    val result = if (optimize == null) vm.execute(code, env)
                        else vm.executeBlocks(vm.prepareBlocks(code, optimize), env)
                    return Observation(result, seen, trace.events.map { it.event }, trace.instructionCounts.toMap())
                }
                Debug.tracer = null
                val expected = execute(null)
                assertEquals(listOf(7, 11), expected.result)
                assertEquals(listOf(7), expected.seen)
                for (optimize in listOf(false, true)) {
                    Debug.tracer = null
                    assertEquals(expected, execute(optimize))
                }
            }
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun overridden_big_integer_arithmetic_observes_named_writes_before_its_callback() {
        val code = Bytecode.fromInstructions(mirror() + listOf(
            Instruction.LOAD_VAR("input"), Instruction.PUSH(1), Instruction.ADD, Instruction.POP,
            Instruction.LOAD_LOCAL(0), Instruction.LOAD_VAR("x"), Instruction.MAKE_ARRAY(2),
        ))
        for (optimize in listOf(false, true)) {
            val env = mutableMapOf<String, Any?>()
            var calls = 0
            env["input"] = object : BigInteger("1") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun add(value: BigInteger): BigInteger {
                    calls++
                    assertEquals(7, env["x"])
                    env["x"] = 11
                    return super.add(value)
                }
            }
            val vm = VM()
            assertEquals(listOf(7, 11), vm.executeBlocks(vm.prepareBlocks(code, optimize), env))
            assertEquals(1, calls)
        }
    }

    @Test
    fun static_big_integer_subclass_keys_fall_back_before_any_callback() {
        for (optimize in listOf(false, true)) {
            var calls = 0
            val key = IBig(object : BigInteger("0") {
                override fun toShort(): Short = toInt().toShort()
                override fun toByte(): Byte = toInt().toByte()
                override fun signum(): Int { calls++; return super.signum() }
            })
            val code = Bytecode.fromInstructions(listOf(Instruction.PUSH(listOf(7)), Instruction.ACCESS_STATIC(key)))
            val vm = VM()
            val plan = vm.prepareBlocks(code, optimize)
            assertNotNull(plan.fallbackReason)
            assertEquals(0, calls)
            assertEquals(7, vm.executeBlocks(plan))
            assertEquals(1, calls)
            assertFalse(vm.preparedBlockExecuted)
        }
    }
}
