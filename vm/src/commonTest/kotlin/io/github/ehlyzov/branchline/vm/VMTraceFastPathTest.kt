package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.NumberLiteral
import io.github.ehlyzov.branchline.Token
import io.github.ehlyzov.branchline.TokenType
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.Debug
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.debug.TraceOptions
import io.github.ehlyzov.branchline.debug.Tracer
import io.github.ehlyzov.branchline.ir.IRLet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VMTraceFastPathTest {
    private val token = Token(TokenType.NUMBER, "1", 1, 1)
    private val hostNode = IRLet("host", NumberLiteral(I32(1), token))
    private val storeNode = IRLet("value", NumberLiteral(I32(1), token))

    private fun tracer() = CollectingTracer(TraceOptions(step = true))

    private fun hostProgram() = Bytecode.fromInstructions(
        listOf(
            Instruction.CALL_HOST(0, "switchTrace", 0),
            Instruction.POP,
            Instruction.PUSH(1),
            Instruction.STORE_VAR("value"),
            Instruction.LOAD_VAR("value"),
        ),
        traceMetadata = TraceMetadata(
            enterEvents = mapOf(0 to listOf(hostNode), 3 to listOf(storeNode)),
            exitEvents = mapOf(0 to listOf(hostNode), 3 to listOf(storeNode)),
            pathWrites = mapOf(3 to valueWrite()),
        ),
    )

    private fun valueWrite() = PathWriteMeta(
        op = PathWriteOp.SET,
        root = "value",
        displayPath = listOf("value"),
        directValues = true,
    )

    private inline fun withAmbientTrace(block: () -> Unit) {
        val previous = Debug.tracer
        Debug.tracer = null
        try {
            block()
        } finally {
            Debug.tracer = previous
        }
    }

    @Test
    fun host_can_enable_tracing_including_exit_of_current_instruction() = withAmbientTrace {
        val trace = tracer()
        val hosts = mapOf<String, (List<Any?>) -> Any?>("switchTrace" to { Debug.tracer = trace; null })

        assertEquals(1, VM(hosts).execute(hostProgram(), mutableMapOf("value" to 0)))

        val events = trace.events.map { it.event }
        assertEquals(listOf(hostNode, storeNode), events.filterIsInstance<TraceEvent.Exit>().map { it.node })
        assertEquals(listOf(storeNode), events.filterIsInstance<TraceEvent.Enter>().map { it.node })
        assertEquals(TraceEvent.Let("value", 0, 1), events.filterIsInstance<TraceEvent.Let>().single())
        assertEquals(TraceEvent.Read("value", 1), events.filterIsInstance<TraceEvent.Read>().single())
        assertEquals(TraceEvent.PathWrite("SET", "value", listOf("value"), 0, 1),
            events.filterIsInstance<TraceEvent.PathWrite>().single())
        assertEquals(mapOf("POP" to 1L, "PUSH" to 1L, "STORE_VAR" to 1L, "LOAD_VAR" to 1L),
            trace.instructionCounts)
    }

    @Test
    fun host_can_disable_tracing_before_later_instructions() = withAmbientTrace {
        val trace = tracer()
        Debug.tracer = trace
        val hosts = mapOf<String, (List<Any?>) -> Any?>("switchTrace" to { Debug.tracer = null; null })

        assertEquals(1, VM(hosts).execute(hostProgram(), mutableMapOf("value" to 0)))

        val events = trace.events.map { it.event }
        assertEquals(listOf(hostNode), events.filterIsInstance<TraceEvent.Enter>().map { it.node })
        assertTrue(events.filterIsInstance<TraceEvent.Exit>().isEmpty())
        assertTrue(events.filterIsInstance<TraceEvent.Let>().isEmpty())
        assertTrue(events.filterIsInstance<TraceEvent.PathWrite>().isEmpty())
        assertEquals(mapOf("CALL_HOST" to 1L), trace.instructionCounts)
    }

    @Test
    fun explicit_tracer_keeps_precedence_when_host_changes_ambient_tracer() = withAmbientTrace {
        val explicit = tracer()
        val ambient = tracer()
        val hosts = mapOf<String, (List<Any?>) -> Any?>("switchTrace" to { Debug.tracer = ambient; null })

        assertEquals(1, VM(hosts, tracer = explicit).execute(hostProgram(), mutableMapOf("value" to 0)))

        assertTrue(ambient.events.isEmpty())
        assertTrue(ambient.instructionCounts.isEmpty())
        assertEquals(5L, explicit.instructionCounts.values.sum())
        assertEquals(TraceEvent.Let("value", 0, 1),
            explicit.events.map { it.event }.filterIsInstance<TraceEvent.Let>().single())
    }

    private class ObservedEnvironment(
        private val backing: MutableMap<String, Any?>,
        private val onGet: () -> Unit = {},
        private val onPut: () -> Unit = {},
    ) : MutableMap<String, Any?> by backing {
        val operations = mutableListOf<String>()

        override fun get(key: String): Any? {
            operations.add("get:$key")
            onGet()
            return backing[key]
        }

        override fun put(key: String, value: Any?): Any? {
            operations.add("put:$key")
            onPut()
            return backing.put(key, value)
        }
    }

    private fun storeProgram() = Bytecode.fromInstructions(
        listOf(Instruction.PUSH(1), Instruction.STORE_VAR("value")),
        traceMetadata = TraceMetadata(pathWrites = mapOf(1 to valueWrite())),
    )

    @Test
    fun custom_environment_keeps_old_value_read_without_tracing() = withAmbientTrace {
        val env = ObservedEnvironment(mutableMapOf("value" to 0))

        VM().execute(storeProgram(), env)

        assertEquals(listOf("get:value", "put:value"), env.operations)
    }

    @Test
    fun custom_environment_can_enable_tracing_from_get_or_put() = withAmbientTrace {
        for (enableFromGet in listOf(true, false)) {
            Debug.tracer = null
            val trace = tracer()
            val activate = { Debug.tracer = trace }
            val env = ObservedEnvironment(
                mutableMapOf("value" to 0),
                onGet = if (enableFromGet) activate else ({}),
                onPut = if (enableFromGet) ({}) else activate,
            )

            VM().execute(storeProgram(), env)

            assertEquals(listOf("get:value", "put:value"), env.operations)
            val events = trace.events.map { it.event }
            assertEquals(TraceEvent.Let("value", 0, 1), events.filterIsInstance<TraceEvent.Let>().single())
            assertEquals(TraceEvent.PathWrite("SET", "value", listOf("value"), 0, 1),
                events.filterIsInstance<TraceEvent.PathWrite>().single())
            assertFalse(trace.instructionCounts.containsKey("STORE_VAR"))
        }
    }

    @Test
    fun tracer_callback_can_disable_path_write_emission_after_variable_write() = withAmbientTrace {
        val events = mutableListOf<TraceEvent>()
        Debug.tracer = object : Tracer {
            override val opts = TraceOptions(step = true)

            override fun on(event: TraceEvent) {
                events.add(event)
                if (event is TraceEvent.Let) Debug.tracer = null
            }
        }

        VM().execute(storeProgram(), mutableMapOf("value" to 0))

        assertEquals(TraceEvent.Let("value", 0, 1), events.filterIsInstance<TraceEvent.Let>().single())
        assertTrue(events.filterIsInstance<TraceEvent.PathWrite>().isEmpty())
    }
}
