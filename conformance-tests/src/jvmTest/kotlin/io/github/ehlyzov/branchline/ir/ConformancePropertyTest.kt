package io.github.ehlyzov.branchline.ir

import kotlin.test.assertEquals
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.testutils.compileAndRun

private fun runBothEngines(body: String, row: Map<String, Any?>): Pair<Any?, Any?> {
    val interp = compileAndRun(body, row = row, engine = ExecutionEngine.INTERPRETER)
    val vm = compileAndRun(body, row = row, engine = ExecutionEngine.VM)
    return interp to vm
}

data class ListIndexInput(
    val items: List<Int>,
    val index: Int,
)

data class MergeInput(
    val base: Map<String, Int>,
    val key: String,
    val value: Int,
)

private fun mergeExpected(base: Map<String, Int>, key: String, value: Int): Map<String, Int> {
    return LinkedHashMap<String, Int>(base.size + 1).apply {
        putAll(base)
        put(key, value)
    }
}

class ConformancePropertyTest {

    @Provide
    fun listAndIndex(): Arbitrary<ListIndexInput> {
        val listArb = Arbitraries.integers().between(-50, 50).list().ofMinSize(1).ofMaxSize(12)
        return listArb.flatMap { list ->
            Arbitraries.integers().between(0, list.lastIndex).map { idx ->
                ListIndexInput(list, idx)
            }
        }
    }

    @Provide
    fun intLists(): Arbitrary<List<Int>> =
        Arbitraries.integers().between(-100, 100).list().ofMinSize(0).ofMaxSize(20)

    @Provide
    fun mergeInputs(): Arbitrary<MergeInput> {
        val mapArb = Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
            Arbitraries.integers().between(-20, 20),
        ).ofMinSize(1).ofMaxSize(8)
        val keyArb = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6)
        val valueArb = Arbitraries.integers().between(-20, 20)
        return Combinators.combine(mapArb, keyArb, valueArb).`as` { base, key, value ->
            MergeInput(base, key, value)
        }
    }

    @Property
    fun path_access_returns_expected_value(@ForAll("listAndIndex") input: ListIndexInput) {
        val items = input.items
        val idx = input.index
        val body = "OUTPUT { v: row.items[row.idx] };"
        val expected = mapOf("v" to items[idx])
        val (interp, vm) = runBothEngines(body, mapOf("items" to items, "idx" to idx))
        assertEquals(expected, interp)
        assertEquals(expected, vm)
    }

    @Property
    fun array_comprehension_filters_positive(@ForAll("intLists") items: List<Int>) {
        val body = "OUTPUT { vals: [x FOR EACH x IN row.items WHERE x > 0] };"
        val expected = mapOf("vals" to items.filter { it > 0 })
        val (interp, vm) = runBothEngines(body, mapOf("items" to items))
        assertEquals(expected, interp)
        assertEquals(expected, vm)
    }

    @Property
    fun object_merge_updates_field(@ForAll("mergeInputs") input: MergeInput) {
        val base = input.base
        val key = input.key
        val value = input.value
        val body = """
            LET obj = input.base;
            MODIFY obj { [input.key] : input.value };
            OUTPUT { obj: obj };
        """.trimIndent()
        val expected = mapOf("obj" to mergeExpected(base, key, value))
        val (interp, vm) = runBothEngines(body, mapOf("base" to base, "key" to key, "value" to value))
        assertEquals(expected, interp)
        assertEquals(expected, vm)
    }
}
