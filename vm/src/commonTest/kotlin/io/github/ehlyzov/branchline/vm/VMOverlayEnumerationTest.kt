package io.github.ehlyzov.branchline.vm

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VMOverlayEnumerationTest {
    private class CountingEnvironment(
        private val bindings: MutableMap<String, Any?>,
    ) : MutableMap<String, Any?> by bindings {
        var enumerations = 0
        override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
            get() {
                enumerations++
                return bindings.entries
            }
    }

    private fun code(vararg instructions: Instruction) = Bytecode.fromInstructions(instructions.toList())

    @Test
    fun deep_closure_snapshot_enumerates_root_linearly_and_restores() {
        val depth = 12
        var program = code(
            Instruction.SUSPEND, Instruction.LOAD_SCOPE("answer"), Instruction.RETURN_VALUE,
        )
        repeat(depth) {
            program = code(
                Instruction.PUSH(LambdaTemplate(emptyList(), program)),
                Instruction.CALL_LAMBDA(0), Instruction.RETURN_VALUE,
            )
        }
        val root = CountingEnvironment(mutableMapOf("answer" to 7))
        val payload = VM().execute(program, root) as Map<*, *>
        assertTrue(root.enumerations <= 2 * (depth + 1), "Root enumerated ${root.enumerations} times")
        val restored = VM.restoreFromSnapshot(payload["snapshot"] as String)
        assertTrue(restored.resume())
        assertEquals(7L, restored.resultOrNull())
    }

    @Test
    fun nested_lookup_does_not_enumerate_root() {
        var program = code(Instruction.LOAD_SCOPE("answer"), Instruction.RETURN_VALUE)
        repeat(12) {
            program = code(
                Instruction.PUSH(LambdaTemplate(emptyList(), program)),
                Instruction.CALL_LAMBDA(0), Instruction.RETURN_VALUE,
            )
        }
        val root = CountingEnvironment(mutableMapOf("answer" to 7))
        assertEquals(7, VM().execute(program, root))
        assertEquals(0, root.enumerations)
    }

    // Returning a closure exposes its captured scope through the public VM API.
    private fun scope(parent: MutableMap<String, Any?>, bindings: Map<String, Any?>): MutableMap<String, Any?> {
        val body = code(
            Instruction.PUSH(LambdaTemplate(emptyList(), code(Instruction.RETURN))),
            Instruction.RETURN_VALUE,
        )
        val instructions = mutableListOf<Instruction>(Instruction.PUSH(LambdaTemplate(bindings.keys.toList(), body)))
        bindings.values.reversed().forEach { instructions.add(Instruction.PUSH(it)) }
        instructions.add(Instruction.CALL_LAMBDA(bindings.size))
        val closure = VM().execute(Bytecode.fromInstructions(instructions), parent) as LambdaValue
        @Suppress("UNCHECKED_CAST")
        return closure.captured as MutableMap<String, Any?>
    }

    @Test
    fun enumeration_preserves_shadowing_order_live_parents_and_detached_entries() {
        val root = linkedMapOf<String, Any?>("first" to 1, "shadow" to 10, "last" to 3)
        val outer = scope(root, linkedMapOf("shadow" to 20, "outer" to 4))
        val inner = scope(outer, linkedMapOf("shadow" to null, "inner" to 5))
        val before = inner.entries
        assertEquals(listOf("first", "shadow", "last", "outer", "inner"), before.map { it.key })
        assertNull(before.single { it.key == "shadow" }.value)
        assertTrue(inner.containsKey("shadow"))

        root["first"] = 9
        outer["outer"] = 8
        inner["inner"] = 6
        assertEquals(1, before.single { it.key == "first" }.value)
        assertEquals(4, before.single { it.key == "outer" }.value)
        assertEquals(5, before.single { it.key == "inner" }.value)
        assertEquals(9, inner.entries.single { it.key == "first" }.value)
        assertEquals(8, inner.entries.single { it.key == "outer" }.value)
        assertEquals(6, inner.entries.single { it.key == "inner" }.value)

        before.single { it.key == "first" }.setValue(100)
        assertEquals(9, inner["first"])
        assertNull(inner.remove("shadow"))
        assertEquals(20, inner["shadow"])
        assertEquals(20, inner.entries.single { it.key == "shadow" }.value)
        outer.remove("shadow")
        assertEquals(10, inner["shadow"])
    }

    @Test
    fun snapshot_preserves_null_shadow_order_and_current_ancestor_values() {
        val root = linkedMapOf<String, Any?>("first" to 1, "shadow" to 10, "last" to 3)
        val outer = scope(root, linkedMapOf("outer" to 4))
        val inner = scope(outer, linkedMapOf("shadow" to null))
        root["first"] = 9
        outer["outer"] = 8
        val payload = VM().execute(code(
            Instruction.SUSPEND, Instruction.LOAD_SCOPE("shadow"), Instruction.RETURN_VALUE,
        ), inner) as Map<*, *>
        val snapshot = payload["snapshot"] as String
        val saved = Json.decodeFromString(VM.Snapshot.serializer(), snapshot)
        assertEquals(listOf("first", "shadow", "last", "outer"), saved.env.keys.toList())
        assertEquals("9", saved.env.getValue("first").s)
        assertEquals("8", saved.env.getValue("outer").s)
        assertEquals("null", saved.env.getValue("shadow").t)
        val restored = VM.restoreFromSnapshot(snapshot)
        assertTrue(restored.resume())
        assertNull(restored.resultOrNull())
    }
}
