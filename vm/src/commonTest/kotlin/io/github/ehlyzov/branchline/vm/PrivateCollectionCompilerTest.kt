package io.github.ehlyzov.branchline.vm

import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.debug.CollectingTracer
import io.github.ehlyzov.branchline.debug.TraceEvent
import io.github.ehlyzov.branchline.ir.ToIR
import kotlin.test.*

class PrivateCollectionCompilerTest {
    private fun compile(body: String, hosts: Map<String, (List<Any?>) -> Any?> = emptyMap()): Bytecode {
        val program = Parser(Lexer("TRANSFORM T { $body }").lex()).parse()
        val transform = program.decls.filterIsInstance<TransformDecl>().single()
        return Compiler(hostFns = hosts).compile(ToIR(emptyMap(), hosts).compile(transform.body.statements))
    }

    private fun run(bytecode: Bytecode, input: Map<String, Any?> = emptyMap()): Any? =
        VM().execute(bytecode, mutableMapOf("input" to input))

    @Test
    fun fresh_array_nested_loops_and_branches_use_linear_builder() {
        val bytecode = compile("""
            LET acc = []
            FOR EACH row IN input.rows {
                FOR EACH value IN row WHERE value != NULL {
                    IF value > 1 THEN { acc += value } ELSE { acc += 0 }
                }
            }
            OUTPUT { values: acc }
        """.trimIndent())
        assertEquals(2, bytecode.instructions.count { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertFalse(bytecode.instructions.any { it === Instruction.PLUS_ASSIGN })
        assertEquals(mapOf("values" to listOf(0, 2, 3)), run(bytecode,
            mapOf("rows" to listOf(listOf(1, null, 2), listOf(3)))))
        assertEquals(mapOf("values" to emptyList<Any?>()), run(bytecode, mapOf("rows" to emptyList<Any?>())))
    }

    @Test
    fun array_comprehensions_keep_their_existing_private_stack_builder() {
        val bytecode = compile("OUTPUT [value FOR EACH value IN input.values]")
        assertTrue(bytecode.instructions.any { it === Instruction.ARRAY_BUILDER_ADD })
        assertFalse(bytecode.instructions.any { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(listOf(1, 2), run(bytecode, mapOf("values" to listOf(1, 2))))
    }

    @Test
    fun observing_array_ends_private_region_and_preserves_alias() {
        val bytecode = compile("""
            LET acc = []
            FOR EACH value IN input.values { acc += value }
            LET alias = acc
            acc += 9
            OUTPUT { values: acc, alias: alias }
        """.trimIndent())
        assertEquals(1, bytecode.instructions.count { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(1, bytecode.instructions.count { it === Instruction.PLUS_ASSIGN })
        assertEquals(mapOf("values" to listOf(1, 2, 9), "alias" to listOf(1, 2)),
            run(bytecode, mapOf("values" to listOf(1, 2))))
    }

    @Test
    fun earlier_alias_closure_and_loop_observation_disable_mutation() {
        for (body in listOf(
            "LET alias = acc; FOR EACH value IN input.values { acc += value } OUTPUT { alias: alias, values: acc }",
            "LET captured = () -> acc; FOR EACH value IN input.values { acc += value } OUTPUT { alias: captured(), values: acc }",
            "LET alias = []; FOR EACH value IN input.values { SET alias = acc; acc += value } OUTPUT { alias: alias, values: acc }",
        )) {
            val bytecode = compile("LET acc = []; $body")
            assertFalse(bytecode.instructions.any { it === Instruction.PRIVATE_ARRAY_APPEND }, body)
            val expectedAlias = when {
                body.contains("SET alias") -> listOf(1)
                body.contains("captured") -> listOf(1, 2)
                else -> emptyList()
            }
            assertEquals(mapOf("alias" to expectedAlias, "values" to listOf(1, 2)),
                run(bytecode, mapOf("values" to listOf(1, 2))), body)
        }
    }

    @Test
    fun host_effect_observes_previous_values_and_runs_once_in_order() {
        val env = mutableMapOf<String, Any?>("input" to mapOf("values" to listOf(1, 2)))
        val seen = mutableListOf<Any?>()
        val hosts = mapOf<String, (List<Any?>) -> Any?>("observe" to { seen.add(env["acc"]); null })
        val bytecode = compile("""
            LET acc = []
            FOR EACH value IN input.values { observe(); acc += value }
            OUTPUT { values: acc }
        """.trimIndent(), hosts)
        assertFalse(bytecode.instructions.any { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(mapOf("values" to listOf(1, 2)), VM(hosts).execute(bytecode, env))
        assertEquals(listOf<Any?>(emptyList<Any?>(), listOf(1)), seen)
    }

    @Test
    fun shared_input_and_loop_binding_shadow_never_use_builder() {
        val shared = compile("LET acc = input.values; FOR EACH value IN [1, 2] { acc += value } OUTPUT acc")
        assertFalse(shared.instructions.any { it === Instruction.PRIVATE_ARRAY_APPEND })
        val input = mutableListOf<Any?>(8)
        assertEquals(listOf(8, 1, 2), run(shared, mapOf("values" to input)))
        assertEquals(listOf<Any?>(8), input)
        val shadow = compile("LET acc = []; FOR EACH acc IN [1, 2] { acc += 1 } OUTPUT acc")
        assertFalse(shadow.instructions.any { it === Instruction.PRIVATE_ARRAY_APPEND })
        assertEquals(3, run(shadow))
    }

    @Test
    fun fresh_private_root_sets_use_specialized_static_and_dynamic_writes() {
        val bytecode = compile("""
            LET object = {}
            FOR EACH key IN input.keys { SET object[key] = 4 }
            SET object.last = 5
            LET array = [0, 0]
            FOR EACH value IN [1, 2] { SET array[0] = value }
            OUTPUT { object: object, array: array }
        """.trimIndent())
        assertEquals(2, bytecode.instructions.count { it === Instruction.PRIVATE_SET_DYNAMIC })
        assertEquals(1, bytecode.instructions.count { it is Instruction.PRIVATE_SET_STATIC })
        assertEquals(mapOf("object" to mapOf("a" to 4, "b" to 4, "last" to 5), "array" to listOf(2, 0)),
            run(bytecode, mapOf("keys" to listOf("a", "b"))))
    }

    @Test
    fun aliased_and_nested_sets_keep_copy_semantics() {
        val aliased = compile("""
            LET object = {}; LET alias = object
            FOR EACH key IN input.keys { SET object[key] = 4 }
            OUTPUT { object: object, alias: alias }
        """.trimIndent())
        assertFalse(aliased.instructions.any { it === Instruction.PRIVATE_SET_DYNAMIC })
        assertEquals(mapOf("object" to mapOf("a" to 4), "alias" to emptyMap<String, Any?>()),
            run(aliased, mapOf("keys" to listOf("a"))))
        val nested = compile("LET object = { nested: {} }; SET object.nested.value = 1; OUTPUT object")
        assertFalse(nested.instructions.any { it is Instruction.PRIVATE_SET_STATIC })
        assertEquals(mapOf("nested" to mapOf("value" to 1)), run(nested))
    }

    @Test
    fun traces_keep_original_array_and_map_write_values() {
        val bytecode = compile("""
            LET acc = []; FOR EACH value IN [1, 2] { acc += value }
            LET object = {}; FOR EACH key IN ["a", "b"] { SET object[key] = 1 }
            OUTPUT { values: acc, object: object }
        """.trimIndent())
        val tracer = CollectingTracer()
        VM(tracer = tracer).execute(bytecode, mutableMapOf())
        val writes = tracer.events.map { it.event }.filterIsInstance<TraceEvent.PathWrite>()
        val arrays = writes.filter { it.root == "acc" }
        assertEquals(listOf(emptyList<Any?>(), listOf(1)), arrays.map { it.old })
        assertEquals(listOf(listOf(1), listOf(1, 2)), arrays.map { it.new })
        val objects = writes.filter { it.root == "object" }
        assertEquals(listOf(null, null), objects.map { it.old })
        assertEquals(listOf(1, 1), objects.map { it.new })
    }

    private class ObservingEnv(
        private val root: String,
        private val backing: MutableMap<String, Any?> = mutableMapOf(),
    ) : MutableMap<String, Any?> by backing {
        val writes = mutableListOf<Any?>()
        override fun put(key: String, value: Any?): Any? {
            if (key == root) writes.add(value)
            return backing.put(key, value)
        }
    }

    @Test
    fun custom_environment_write_observers_retain_previous_root_values() {
        for ((root, body, expected) in listOf(
            Triple("acc", "LET acc = []; FOR EACH value IN [1, 2] { acc += value } OUTPUT acc",
                listOf<Any?>(emptyList<Any?>(), listOf(1), listOf(1, 2))),
            Triple("obj", "LET obj = {}; FOR EACH key IN [\"a\", \"b\"] { SET obj[key] = 1 } OUTPUT obj",
                listOf<Any?>(emptyMap<String, Any?>(), mapOf("a" to 1), mapOf("a" to 1, "b" to 1))),
        )) {
            val env = ObservingEnv(root)
            VM().execute(compile(body), env)
            assertEquals(expected, env.writes, root)
        }
    }

    @Test
    fun private_static_numeric_key_survives_bytecode_roundtrip() {
        val bytecode = Bytecode.fromInstructions(listOf(
            Instruction.PUSH(0), Instruction.MAKE_ARRAY(1), Instruction.PUSH(3),
            Instruction.PRIVATE_SET_STATIC(I32(0)),
        ))
        val roundtrip = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(bytecode))
        assertEquals(Instruction.PRIVATE_SET_STATIC(I32(0)), roundtrip.instructions.last())
        assertEquals(listOf(3L), VM().execute(roundtrip))
    }

    @Test
    fun incremental_execution_preserves_values_retained_between_steps() {
        for ((name, body, empty) in listOf(
            Triple("acc", "LET acc = []; FOR EACH value IN [1, 2] { acc += value } OUTPUT acc", emptyList<Any?>()),
            Triple("obj", "LET obj = {}; FOR EACH key IN [\"a\", \"b\"] { SET obj[key] = 1 } OUTPUT obj", emptyMap<String, Any?>()),
        )) {
            val bytecode = compile(body)
            val env = mutableMapOf<String, Any?>()
            val vm = VM()
            vm.begin(bytecode, env)
            while (name !in env) assertFalse(vm.step(1))
            val retained = env[name]
            assertTrue(vm.step())
            assertEquals(empty, retained, name)
        }
    }

    @Test
    fun private_opcodes_roundtrip_and_snapshots_resume_at_each_instruction_boundary() {
        val bytecode = compile("""
            LET acc = []; FOR EACH value IN [1, 2] { acc += value }
            LET object = {}; FOR EACH key IN ["a", "b"] { SET object[key] = 4 }
            LET array = [0]; SET array[0] = 3
            OUTPUT { values: acc, object: object, array: array }
        """.trimIndent())
        val expected = mapOf("values" to listOf(1L, 2L), "object" to mapOf("a" to 4L, "b" to 4L), "array" to listOf(3L))
        val roundtrip = BytecodeIO.decodeFromString(BytecodeIO.encodeToString(bytecode))
        assertEquals(expected, run(roundtrip))
        // Covers pauses before/after builder ADD and STORE as well as list/map
        // private SET. Snapshot serialization separates stack/env/local aliases.
        var unfinished = 0
        for (steps in 1..120) {
            val vm = VM()
            vm.begin(roundtrip)
            if (vm.step(steps)) break
            unfinished++
            val restored = VM.restoreFromSnapshot(vm.snapshotJson())
            assertTrue(restored.step(), "steps=$steps")
            assertEquals(expected, restored.resultOrNull(), "steps=$steps")
        }
        assertTrue(unfinished > roundtrip.size())
    }
}
