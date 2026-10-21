package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.ExecutionEngine
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.compileStream
import io.github.ehlyzov.branchline.runtime.isNumericValue
import io.github.ehlyzov.branchline.runtime.numericCompare
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.toDouble
import io.github.ehlyzov.branchline.std.SharedResourceHandle
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.VMExecutionPath
import io.github.ehlyzov.branchline.vm.VMExecutionReport
import io.github.ehlyzov.branchline.vm.VMFallbackPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal expect fun readStdlibSupportInventory(): String

class StdlibSupportContractTest {
    private data class Row(val fields: List<String>) {
        val name get() = fields[0]
        val arity get() = fields[2]
        val candidate get() = fields[8] == "candidate"
    }

    private fun inventory(markdown: String, registryNames: Set<String> = StdLib.names): Map<String, Row> {
        val block = markdown.substringAfter("<!-- stdlib-inventory:start -->", "")
            .substringBefore("<!-- stdlib-inventory:end -->", "")
        val rows = block.lineSequence().filter { it.startsWith("| ") }.drop(2).map { line ->
            Row(line.removePrefix("| ").removeSuffix(" |").split(" | "))
        }.toList()
        assertTrue(rows.isNotEmpty(), "Missing inventory markers/table")
        assertTrue(rows.all { it.fields.size == 10 }, "Malformed contract row")
        assertEquals(rows.size, rows.map { it.name }.toSet().size, "Duplicate registered name")
        assertEquals(registryNames, rows.map { it.name }.toSet(), "Default registry drift")
        assertEquals(expectedRows.trim(), rows.joinToString("\n") { "| ${it.fields.joinToString(" | ")} |" },
            "A contract, alias, arity, platform, classification or evidence changed; review both inventory and gate")
        return rows.associateBy { it.name }
    }

    @Test
    fun every_default_registry_name_and_contract_is_classified() {
        val rows = inventory(readStdlibSupportInventory())
        assertEquals("LENGTH", rows.getValue("COUNT").fields[1])
        assertEquals(setOf("KEYS", "AWAIT_SHARED", "SHARED_WRITE"), StdLib.meta.keys)
        assertEquals(false, StdLib.meta.getValue("KEYS").requiresSharedAccess(listOf(emptyMap<String, Any?>())))
        assertEquals(true, StdLib.meta.getValue("KEYS").requiresSharedAccess(listOf(SharedResourceHandle("probe"))))
        for (name in listOf("AWAIT_SHARED", "SHARED_WRITE")) {
            assertEquals(true, StdLib.meta.getValue(name).requiresSharedAccess(emptyList()))
        }
        assertTrue("NOT" !in StdLib.names && "EXISTS" !in StdLib.names)
    }

    @Test
    fun inventory_negative_controls_reject_registry_and_contract_drift() {
        val doc = readStdlibSupportInventory()
        assertFailsWith<AssertionError> { inventory(doc, StdLib.names + "UNCLASSIFIED") }
        assertFailsWith<AssertionError> { inventory(doc, StdLib.names - "COUNT") }
        assertFailsWith<AssertionError> { inventory(doc.replace("| COUNT | LENGTH |", "| COUNT | — |")) }
        assertFailsWith<AssertionError> {
            inventory(doc.replace("| APPEND | — | 2 |", "| APPEND | — | 3 |"))
        }
        assertFailsWith<AssertionError> {
            inventory(doc.replace("| candidate | core |", "| experimental: changed | core |"))
        }
    }

    private data class Oracle(val expression: String, val expected: Any?)

    // Literal expected values are independent mathematical/data-transformation oracles.
    private fun oracles(): Map<String, Oracle> = linkedMapOf(
        "KEYS" to Oracle("KEYS({b: 2, a: 1})", listOf("b", "a")),
        "VALUES" to Oracle("VALUES({b: null, a: 1})", listOf(null, 1)),
        "ENTRIES" to Oracle("ENTRIES({x: null})", listOf(mapOf("key" to "x", "value" to null))),
        "PUT" to Oracle("PUT([1, 2], 2, null)", listOf(1, 2, null)),
        "DELETE" to Oracle("DELETE({x: 1, y: 2}, \"x\")", mapOf("y" to 2)),
        "APPEND" to Oracle("APPEND([1], null)", listOf(1, null)),
        "PREPEND" to Oracle("PREPEND([1], null)", listOf(null, 1)),
        "IS_OBJECT" to Oracle("IS_OBJECT({x: 1})", true),
        "LISTIFY" to Oracle("LISTIFY(null)", emptyList<Any?>()),
        "GET" to Oracle("GET({x: null}, \"x\", 9)", null),
        "SIFT" to Oracle("SIFT({x: 1, y: 3}, (v, k, o) -> v > 1)", mapOf("y" to 3)),
        "DISTINCT" to Oracle("DISTINCT([2, null, 2, 1, null])", listOf(2, null, 1)),
        "FLATTEN" to Oracle("FLATTEN([[1], 2, [null, [3]]])", listOf(1, 2, null, listOf(3))),
        "ZIP" to Oracle("ZIP([1, 2], [null])", listOf(listOf(1, null))),
        "REVERSE" to Oracle("REVERSE([1, null, 2])", listOf(2, null, 1)),
        "LENGTH" to Oracle("LENGTH(\"A\\uD83D\\uDE00\")", 3),
        "COUNT" to Oracle("COUNT([1, null])", 2),
        "SUM" to Oracle("SUM([1, -2, 4])", 3),
        "AVG" to Oracle("AVG([1, 2])", 1.5),
        "ABS" to Oracle("ABS(-7)", 7),
        "DEC" to Oracle("DEC(\"1.5\")", 1.5),
        "FLOOR" to Oracle("FLOOR(-1.25)", -2),
        "CEIL" to Oracle("CEIL(-1.25)", -1),
        "ROUND" to Oracle("ROUND(250, -2)", 200),
        "POWER" to Oracle("POWER(-2, 3)", -8),
        "SQRT" to Oracle("SQRT(9)", 3),
        "NUMBER" to Oracle("NUMBER(\"1.25e2\")", 125),
        "BOOLEAN" to Oracle("BOOLEAN([])", true),
        "INT" to Oracle("INT(\"-42\")", -42),
        "PARSE_INT" to Oracle("PARSE_INT(\"-12x3\")", 123),
        "SUBSTRING" to Oracle("SUBSTRING(\"abcdef\", 2, 2)", "cd"),
        "CONTAINS" to Oracle("CONTAINS(\"branchline\", \"line\")", true),
        "SPLIT" to Oracle("SPLIT(\"a,,b,\", \",\")", listOf("a", "", "b", "")),
        "JOIN" to Oracle("JOIN([\"a\", null, \"b\"], \":\")", "a::b"),
        "UPPER" to Oracle("UPPER(\"aZ09\")", "AZ09"),
        "LOWER" to Oracle("LOWER(\"aZ09\")", "az09"),
        "TRIM" to Oracle("TRIM(\"  a b  \")", "a b"),
        "FORMAT" to Oracle("FORMAT(\"{{{x}}}:{missing}\", {x: null})", "{null}:{missing}"),
        "SUBSTRING_BEFORE" to Oracle("SUBSTRING_BEFORE(\"a:b:c\", \":\")", "a"),
        "SUBSTRING_AFTER" to Oracle("SUBSTRING_AFTER(\"a:b:c\", \":\")", "b:c"),
        "BASE64_ENCODE" to Oracle("BASE64_ENCODE(input.bytes)", "AP9h"),
        "BASE64_DECODE" to Oracle("BASE64_DECODE(\"AP9h\")", byteArrayOf(0, -1, 97)),
        "MAP" to Oracle("MAP([10, 20], (v, i, a) -> v + i + LENGTH(a))", listOf(12, 23)),
        "FILTER" to Oracle("FILTER([1, 2, 3], (v, i, a) -> i == 1)", listOf(2)),
        "SOME" to Oracle("SOME([0, 2], (v) -> v)", true),
        "EVERY" to Oracle("EVERY([1, 0], (v) -> v)", false),
        "FIND" to Oracle("FIND([1, 3, 5], (v) -> v > 2)", 3),
        "REDUCE" to Oracle("REDUCE([2, 3], 1, (acc, v, i, a) -> acc + v + i)", 7),
        "APPLY" to Oracle("APPLY((x, y) -> x + y, 2, 3)", 5),
        "IS_FUNCTION" to Oracle("IS_FUNCTION((x) -> x)", true),
    )

    @Test
    fun candidate_contracts_have_independent_oracles() {
        val rows = inventory(readStdlibSupportInventory())
        val cases = oracles()
        assertEquals(rows.values.filter { it.candidate }.map { it.name }.toSet(), cases.keys,
            "Every admitted function needs an independently expected language-level result")
        val source = "TRANSFORM StdlibContract { OUTPUT { " + cases.values.mapIndexed { i, case ->
            "r$i: ${case.expression}"
        }.joinToString(", ") + " } }"
        for (engine in ExecutionEngine.entries) {
            val parsed = Parser(Lexer(source).lex(), source).parse()
            val transform = parsed.decls.filterIsInstance<TransformDecl>().single()
            val reports = mutableListOf<VMExecutionReport>()
            val runner = compileStream(transform, hostFns = StdLib.fns, hostFnMeta = StdLib.meta,
                engine = engine, vmFallbackPolicy = VMFallbackPolicy.NONE, onVMExecution = reports::add)
            val result = runner(mapOf("bytes" to byteArrayOf(0, -1, 97))) as Map<*, *>
            cases.entries.forEachIndexed { i, (name, case) -> assertValue(case.expected, result["r$i"], "$engine $name") }
            if (engine == ExecutionEngine.VM) {
                assertEquals(listOf(VMExecutionReport(VMExecutionPath.VM_BYTECODE, true)), reports)
            } else assertTrue(reports.isEmpty())
        }
    }

    @Test
    fun fractional_floor_and_ceil_have_signed_language_oracles() {
        val expected = linkedMapOf(
            "FLOOR(1.25)" to 1,
            "FLOOR(-1.25)" to -2,
            "FLOOR(2.75)" to 2,
            "FLOOR(-2.75)" to -3,
            "CEIL(1.25)" to 2,
            "CEIL(-1.25)" to -1,
            "CEIL(2.75)" to 3,
            "CEIL(-2.75)" to -2,
        )
        NumericContractHarness.eachEngine { vm ->
            for ((expression, value) in expected) {
                assertValue(value, NumericContractHarness.evaluate(expression, vm), "vm=$vm $expression")
            }
        }
    }

    @Test
    fun round_half_even_signed_ties_cover_every_admitted_precision() {
        val cases = linkedMapOf(
            "ROUND(2.5, 0)" to 2L,
            "ROUND(3.5, 0)" to 4L,
            "ROUND(-2.5, 0)" to -2L,
            "ROUND(-3.5, 0)" to -4L,
        )
        var factor = 1L
        for (places in 1..12) {
            factor *= 10L
            // At 2.5 units, choose the even 2; at 3.5 units, choose the even 4.
            // These integer inputs/intermediates are exact in both admitted profiles.
            val evenTie = 2L * factor + factor / 2L
            val oddTie = 3L * factor + factor / 2L
            cases["ROUND($evenTie, -$places)"] = 2L * factor
            cases["ROUND($oddTie, -$places)"] = 4L * factor
            cases["ROUND(-$evenTie, -$places)"] = -2L * factor
            cases["ROUND(-$oddTie, -$places)"] = -4L * factor
        }
        NumericContractHarness.eachEngine { vm ->
            for ((expression, expected) in cases) {
                assertValue(expected, NumericContractHarness.evaluate(expression, vm), "vm=$vm $expression")
            }
            for (expression in listOf(
                "ROUND(250, -1.5)", "ROUND(250, -0.5)", "ROUND(250, 0.5)",
                "ROUND(250, NUMBER(\"1e309\"))",
            )) NumericContractHarness.assertFailure(expression, vm)
        }
    }

    private fun call(name: String, vararg args: Any?): Any? = StdLib.fns.getValue(name)(args.toList())

    @Test
    fun candidate_arity_and_wrong_domain_failures_are_executable() {
        val candidates = inventory(readStdlibSupportInventory()).values.filter { it.candidate }
        for (row in candidates) {
            if (row.arity.endsWith("+")) {
                val minimum = row.arity.removeSuffix("+").toInt()
                if (minimum > 0) assertFails(row.name) { StdLib.fns.getValue(row.name)(List(minimum - 1) { null }) }
            } else {
                val bounds = row.arity.split("..")
                val min = bounds.first().toInt()
                val max = bounds.last().toInt()
                assertFails("${row.name} too few") { StdLib.fns.getValue(row.name)(List(min - 1) { null }) }
                assertFails("${row.name} too many") { StdLib.fns.getValue(row.name)(List(max + 1) { null }) }
            }
        }
        val invalid = linkedMapOf<String, List<Any?>>(
            "KEYS" to listOf(null), "VALUES" to listOf(null), "ENTRIES" to listOf(null),
            "PUT" to listOf(listOf(1), 3, 2), "DELETE" to listOf(listOf(1), 1),
            "APPEND" to listOf(null, 1), "PREPEND" to listOf(null, 1), "LISTIFY" to listOf(3),
            "GET" to listOf(emptyMap<String, Any?>(), -1), "SIFT" to listOf(emptyMap<String, Any?>(), null),
            "DISTINCT" to listOf(null), "FLATTEN" to listOf(null), "REVERSE" to listOf(null),
            "ZIP" to listOf(emptyList<Any?>(), null), "LENGTH" to listOf(1), "COUNT" to listOf(1),
            "SUM" to listOf(listOf(1, null)), "AVG" to listOf(listOf(1, "2")),
            "ABS" to listOf("1"), "DEC" to listOf("bad"), "FLOOR" to listOf("1"), "CEIL" to listOf("1"),
            "ROUND" to listOf(1, "bad"), "POWER" to listOf(-2, 0.5), "SQRT" to listOf(-1),
            "NUMBER" to listOf("bad"), "INT" to listOf(1.5), "PARSE_INT" to listOf(1.5),
            "SUBSTRING" to listOf("abc", 4), "CONTAINS" to listOf(null, "a"),
            "SPLIT" to listOf(null, ","), "JOIN" to listOf(null), "UPPER" to listOf(null),
            "LOWER" to listOf(null), "TRIM" to listOf(null), "FORMAT" to listOf("{x}", null),
            "SUBSTRING_BEFORE" to listOf("a", null), "SUBSTRING_AFTER" to listOf("a", null),
            "BASE64_ENCODE" to listOf("a"), "BASE64_DECODE" to listOf(null),
            "MAP" to listOf(emptyList<Any?>(), null), "FILTER" to listOf(emptyList<Any?>(), null),
            "SOME" to listOf(emptyList<Any?>(), null), "EVERY" to listOf(emptyList<Any?>(), null),
            "FIND" to listOf(emptyList<Any?>(), null), "REDUCE" to listOf(emptyList<Any?>(), 0, null),
            "APPLY" to listOf(null),
        )
        assertEquals(candidates.map { it.name }.toSet() - setOf("BOOLEAN", "IS_OBJECT", "IS_FUNCTION"), invalid.keys,
            "Predicates accepting any value only have arity errors; every other candidate needs a domain rejection")
        for ((name, args) in invalid) assertFails("$name domain") { StdLib.fns.getValue(name)(args) }
        for (name in listOf("MAP", "FILTER", "SOME", "EVERY", "FIND", "SIFT")) {
            val failure: (List<Any?>) -> Any? = { error("callback failure sentinel") }
            val collection: Any = if (name == "SIFT") mapOf("x" to 1) else listOf(1)
            val error = assertFails(name) { call(name, collection, failure) }
            assertEquals("callback failure sentinel", error.message)
        }
    }

    @Test
    fun candidate_null_empty_fallback_and_copy_contracts() {
        for (name in listOf("ABS", "DEC", "FLOOR", "CEIL", "ROUND", "SQRT", "NUMBER", "INT")) {
            assertEquals(null, call(name, null), name)
        }
        assertEquals(null, call("POWER", null, 2))
        assertEquals(null, call("POWER", 2, null))
        for (name in listOf("LENGTH", "COUNT")) assertValue(0, call(name, null), name)
        for (value in listOf(null, false, 0, "")) assertEquals(false, call("BOOLEAN", value))
        assertEquals(false, call("IS_OBJECT"))
        assertEquals(false, call("IS_OBJECT", null, mapOf("x" to 1)))
        assertEquals(false, call("IS_FUNCTION", null))
        assertEquals(false, call("CONTAINS", "x", null))
        assertValue(9, call("GET", emptyMap<String, Any?>(), "x", 9), "GET fallback")
        assertEquals(null, call("GET", emptyMap<String, Any?>(), "x"))
        assertValue(7, call("PARSE_INT", null, 7), "PARSE_INT null fallback")
        assertValue(0, call("PARSE_INT", "no digits"), "PARSE_INT empty fallback")
        assertEquals(null, call("PARSE_INT", "no digits", null))
        assertValue(0, call("SUM", emptyList<Any?>()), "SUM empty")
        assertEquals(null, call("AVG", emptyList<Any?>()))
        assertEquals("", call("JOIN", emptyList<Any?>()))
        assertEquals("ab", call("JOIN", listOf("a", null, "b")))
        for (name in listOf("SUBSTRING_BEFORE", "SUBSTRING_AFTER")) assertEquals("abc", call(name, "abc", ":"))
        assertEquals("bc", call("SUBSTRING", "abc", 1))
        assertEquals("A:null:{4}", call("FORMAT", "{0}:{2}:{4}", listOf("A", "unused", null)))
        val identity: (List<Any?>) -> Any? = { it[0] }
        val empty = emptyList<Any?>()
        assertValue(empty, call("MAP", empty, identity), "MAP empty")
        assertValue(empty, call("FILTER", empty, identity), "FILTER empty")
        assertEquals(false, call("SOME", empty, identity))
        assertEquals(true, call("EVERY", empty, identity))
        assertEquals(null, call("FIND", empty, identity))
        assertEquals(null, call("REDUCE", empty, null, identity))
        val zeroArgument: (List<Any?>) -> Any? = { args -> assertTrue(args.isEmpty()); 42 }
        assertValue(42, call("APPLY", zeroArgument), "APPLY zero positional values")
        assertValue(emptyMap<String, Any?>(), call("SIFT", emptyMap<String, Any?>(), identity), "SIFT empty")
        val visited = mutableListOf<Int>()
        val onSecond: (List<Any?>) -> Any? = { args ->
            val index = args[1] as Int
            visited.add(index)
            index == 1
        }
        assertEquals(true, call("SOME", listOf(0, 0, 0), onSecond))
        assertEquals(listOf(0, 1), visited, "SOME short circuits in index order")
        visited.clear()
        assertEquals(0, call("FIND", listOf(0, 0, 0), onSecond))
        assertEquals(listOf(0, 1), visited, "FIND short circuits in index order")
        visited.clear()
        assertEquals(false, call("EVERY", listOf(0, 0, 0), onSecond))
        assertEquals(listOf(0), visited, "EVERY stops at first false predicate")
        val source = mutableListOf<Any?>(1, null)
        assertValue(listOf(1, null, 3), call("PUT", source, 2, 3), "PUT append")
        assertValue(listOf(1, 4), call("PUT", source, 1, 4), "PUT replace")
        call("APPEND", source, 3); call("PREPEND", source, 3); call("DELETE", source, 0)
        assertValue(listOf(1, null), source, "collection calls must not mutate source")
        val obj = linkedMapOf<String, Any?>("x" to null)
        assertValue(mapOf("x" to null, "y" to 2), call("PUT", obj, "y", 2), "PUT object")
        assertValue(obj, call("DELETE", obj, "missing"), "DELETE absent")
        assertValue(mapOf("x" to null), obj, "object source unchanged")
        assertValue(listOf(0, 1), call("KEYS", source), "KEYS list indices")
        assertValue(listOf(obj), call("LISTIFY", obj), "LISTIFY object")
        assertValue(source, call("LISTIFY", source), "LISTIFY list")
        assertValue(byteArrayOf(), call("BASE64_DECODE", ""), "BASE64 empty")
        assertEquals("", call("BASE64_ENCODE", byteArrayOf()))
    }

    private fun assertValue(expected: Any?, actual: Any?, label: String) {
        when {
            expected is ByteArray && actual is ByteArray -> assertTrue(expected.contentEquals(actual), label)
            expected is Number && actual is BLBigDec -> assertEquals(expected.toDouble(), actual.toDouble(), label)
            isNumericValue(expected) && isNumericValue(actual) -> assertEquals(0, numericCompare(expected, actual), "$label expected=$expected actual=$actual")
            expected is List<*> && actual is List<*> -> {
                assertEquals(expected.size, actual.size, label)
                expected.indices.forEach { assertValue(expected[it], actual[it], "$label [$it]") }
            }
            expected is Map<*, *> && actual is Map<*, *> -> {
                assertEquals(expected.keys, actual.keys, label)
                expected.keys.forEach { assertValue(expected[it], actual[it], "$label .$it") }
            }
            else -> assertEquals(expected, actual, label)
        }
    }

    private val expectedRows = """
| ABS | — | 1 | finite ordinary numbers with absolute value in common integer profile | absolute numeric value | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| APPEND | — | 2 | list + any admitted value | copy with value last | element preserved; collection reject | wrong collection | JVM+JS | candidate | core |
| APPLY | — | 1+ | synchronous callback followed by positional values | callback result | values passed through; callback reject | bad callback; callback failure propagates | JVM+JS | candidate | hof |
| ASSERT | — | 1+ | condition + optional message; surplus ignored | true when truthy | condition fails; message defaults ASSERT failed | false condition throws supplied/default message | JVM+JS | experimental: diagnostic/error presentation | debug-source |
| AVG | — | 1 | finite primitive numeric list; integer partial sums within common profile | Double division of sum by count; empty null | empty result null; element/collection reject | nonnumeric element or wrong collection | JVM+JS | candidate | aggregate |
| AWAIT_SHARED | — | 2 | resource/key strings + configured store | blocks until shared value available | arguments reject; stored value follows store | missing resource/store; JS cannot block pending read | JVM+JS | experimental: blocking/store lifecycle and platform behavior | shared-source |
| BASE64_DECODE | — | 1 | canonical standard padded Base64 text | ByteArray | reject | wrong type; malformed input outside candidate domain | JVM+JS | candidate | strings |
| BASE64_ENCODE | — | 1 | ByteArray | standard padded Base64 text | reject | wrong type | JVM+JS | candidate | strings |
| BOOLEAN | — | 1 | null/boolean/finite ordinary number/string/list/object | false for null/false/zero/empty string; otherwise true | false | arity; other host values outside candidate domain | JVM+JS | candidate | strings |
| CEIL | — | 1 | finite ordinary numbers with result in common integer profile | smallest integer-valued number >= input | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| CHECKPOINT | — | 0+ | arbitrary values; first becomes label | tracer Call event when configured; returns true | label null | tracer failure propagates | JVM+JS | experimental: global debug/tracer integration | debug-source |
| COLLECT | — | 1 | host Iterable or Sequence | eager list | reject | wrong source; iteration errors propagate | JVM+JS | experimental: arbitrary host iteration effects | core-source |
| CONTAINS | — | 2 | string + string/null | literal case-sensitive containment | second false; first reject | wrong first type | JVM+JS | candidate | strings |
| COUNT | LENGTH | 1 | string/list/object/null | UTF-16 length or collection size; null zero | zero | other scalar rejected | JVM+JS | candidate | aggregate |
| DEC | — | 1 | numeric profile admitted integer/decimal text or finite number | explicit BI/BD; precision follows numeric profile | propagate | malformed text or unsupported type | JVM+JS | candidate | numeric |
| DELETE | — | 2 | object + admitted key; list + index 0..size-1 | copy with removal; absent object key unchanged | reject | wrong collection/key or list bounds | JVM+JS | candidate | core |
| DISTINCT | — | 1 | list | first occurrence order under host equality | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| ENTRIES | — | 1 | object with admitted keys | ordered objects with key and value fields | reject; values preserved | wrong collection | JVM+JS | candidate | core |
| EVERY | — | 2 | list + synchronous callback(value/index/list) | false on first falsy result; empty true | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| EXPLAIN | — | 1 | variable name string | provenance or no-provenance object | reject | wrong arity/type | JVM+JS | experimental: provenance representation | debug-source |
| FILTER | — | 2 | list + synchronous callback(value/index/list) | retain truthy callback results | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| FIND | — | 2 | list + synchronous callback(value/index/list) | first value with truthy callback; absent null | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| FLATTEN | — | 1 | list | flatten exactly one list level; scalars retained | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| FLOOR | — | 1 | finite ordinary numbers with result in common integer profile | largest integer-valued number <= input | propagate | nonnumeric argument | JVM+JS | candidate | numeric |
| FORMAT | — | 2 | string template + list/object of primitive string/boolean/null/common-profile integers | replace {index/key}; escape {{ and }}; missing placeholder retained | value rendered null; collection/template reject | wrong template or args type | JVM+JS | candidate | strings |
| GET | — | 2..3 | object + admitted key + optional default | present value including null; absent default or null | value/default preserved; collection/key reject | wrong collection/key | JVM+JS | candidate | core |
| INT | — | 1 | boolean or integral number/text in common integer profile | integer value; boolean 1/0 | propagate | fractional/malformed/unsupported input | JVM+JS | candidate | strings |
| IS_FUNCTION | — | 1 | any admitted value | whether value is callable Function1 | false | arity | JVM+JS | candidate | hof |
| IS_OBJECT | — | 0+ | any values; only first argument inspected | true exactly when first value is object | false; absent also false | none for admitted values | JVM+JS | candidate | core |
| JOIN | — | 1..2 | list of string/null; optional string/null separator | concatenation; omitted separator empty | element/separator become empty; collection reject | wrong collection | JVM+JS | candidate | strings |
| KEYS | — | 1 | object or list; shared handles excluded | insertion-order object keys or zero-based list indices | reject | wrong collection | JVM+JS | candidate | core |
| LENGTH | — | 1 | string/list/object/null | UTF-16 length or collection size; null zero | zero | other scalar rejected | JVM+JS | candidate | aggregate |
| LISTIFY | — | 1 | list/object/null | list unchanged; object singleton; null empty | empty list | other scalar rejected | JVM+JS | candidate | core |
| LOWER | — | 1 | ASCII string | lowercase | reject | wrong type | JVM+JS | candidate | strings |
| MAP | — | 2 | list + synchronous callback(value/index/list) | map callback results | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| MATCH | — | 2 | string + host regex pattern | all nonoverlapping match texts | pattern/replacement become empty; first reject | wrong first type or invalid regex | JVM+JS | experimental: JVM/JS regex dialect and replacement semantics | strings-source |
| MAX | — | 1 | list | max under numeric/boolean/string or host text comparator | empty null; null elements use text ordering | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | aggregate-source |
| MIN | — | 1 | list | min under numeric/boolean/string or host text comparator | empty null; null elements use text ordering | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | aggregate-source |
| NOW | — | 0 | none | system clock ISO instant text | not applicable | arity | JVM+JS | experimental: ambient wall clock | time-source |
| NUMBER | — | 1 | finite ordinary number/boolean; integer text in common profile; finite decimal/exponent text | number unchanged; boolean 1/0; text Int/Long/Double | propagate | malformed or out-of-Long integer text; unsupported type | JVM+JS | candidate | strings |
| PAD | — | 2..3 | string + numeric width + optional pad text | pad right for positive width; left for negative | pad defaults space; string/width reject | wrong types; empty pad can fail to terminate | JVM+JS | experimental: empty-pad termination and width conversion | strings-source |
| PARSE_INT | — | 1..2 | string whose extracted ASCII digits fit common profile; boolean/integral number; any fallback | extract and concatenate all ASCII digits; no sign; default zero or supplied fallback | fallback | nonintegral nonstring input or unsupported type | JVM+JS | candidate | strings |
| POWER | — | 2 | finite ordinary numbers; nonnegative base or integral exponent; finite result | Double power | either propagates | nonnumeric/nonfinite; negative fractional power; zero negative power | JVM+JS | candidate | numeric |
| PREPEND | — | 2 | list + any admitted value | copy with value first | element preserved; collection reject | wrong collection | JVM+JS | candidate | core |
| PRINT | — | 0+ | arbitrary host values | prints each argument; returns null | prints null | host output errors propagate | JVM+JS | experimental: process output and host rendering | core-source |
| PUT | — | 3 | object + admitted key + value; list + index 0..size + value | copy with replacement or append; source unchanged | value preserved; collection/key reject | wrong collection/key or list bounds | JVM+JS | candidate | core |
| RANDOM | — | 0 | none | pseudo-random Double in [0,1) | not applicable | arity | JVM+JS | experimental: nondeterminism and seed policy | numeric-source |
| RANGE | — | 1..3 | index-like n or start/end/step | inclusive ascending/descending integer list; one argument starts at zero | reject | invalid index; allocation/overflow not bounded | JVM+JS | experimental: zero-count and overflow semantics unresolved | arrays-source |
| REDUCE | — | 3 | list + initial value + synchronous callback(acc/value/index/list) | left fold; empty returns initial value | acc/elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| REPLACE | — | 3 | string + host regex pattern + replacement | regex replacement text | pattern/replacement become empty; first reject | wrong first type or invalid regex | JVM+JS | experimental: JVM/JS regex dialect and replacement semantics | strings-source |
| REVERSE | — | 1 | list | reversed copy | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
| ROUND | — | 1..2 | finite ordinary number; optional integer precision -12..0; result within common profile | half-even rounded integer-valued result | first argument propagates; precision reject | nonnumeric value or invalid precision | JVM+JS | candidate | numeric |
| SHARED_WRITE | — | 3 | resource/key strings + value + configured synchronous store | set once and return value | value allowed; arguments reject | missing store; unsupported sync writes; already set | JVM+JS | experimental: store configuration and write lifecycle | shared-source |
| SIFT | — | 2 | object + synchronous predicate(value/key/object) | ordered object retaining truthy predicate results | values passed through; collection/callback reject | bad callback; callback failure propagates | JVM+JS | candidate | hof |
| SOME | — | 2 | list + synchronous callback(value/index/list) | true on first truthy result; empty false | elements passed through; collection/callback reject | wrong collection/callback; callback failure propagates | JVM+JS | candidate | hof |
| SORT | — | 1 | list | copy sorted by numeric/boolean/string or host text comparator | ordered by fallback text | wrong collection; incompatible numeric domains | JVM+JS | experimental: heterogeneous host-text ordering | arrays-source |
| SPLIT | — | 2 | string + nonempty string separator | literal split preserving empty segments | separator becomes empty; first reject | wrong first type | JVM+JS | candidate | strings |
| SQRT | — | 1 | finite nonnegative ordinary number | Double square root | propagate | nonnumeric/nonfinite or negative | JVM+JS | candidate | numeric |
| STRING | — | 1 | any host value | host toString result | propagate | host rendering can fail | JVM+JS | experimental: host rendering is not a data format | strings-source |
| SUBSTRING | — | 2..3 | string; nonnegative Int start/length with nonoverflowing sum; start <= UTF-16 length | substring using UTF-16 indices; omitted length to end | reject | wrong type; start beyond end | JVM+JS | candidate | strings |
| SUBSTRING_AFTER | — | 2 | two strings | text after first occurrence; unchanged when absent | reject | wrong type | JVM+JS | candidate | strings |
| SUBSTRING_BEFORE | — | 2 | two strings | text before first occurrence; unchanged when absent | reject | wrong type | JVM+JS | candidate | strings |
| SUM | — | 1 | integer list; inputs and all partial sums in common integer profile | integer sum; empty zero | element/collection reject | nonnumeric element or wrong collection | JVM+JS | candidate | aggregate |
| TRIM | — | 1 | ASCII string | remove leading/trailing ASCII whitespace | reject | wrong type | JVM+JS | candidate | strings |
| UPPER | — | 1 | ASCII string | uppercase | reject | wrong type | JVM+JS | candidate | strings |
| VALUES | — | 1 | object with admitted keys | insertion-order values | reject | wrong collection | JVM+JS | candidate | core |
| WALK | — | 1 | acyclic tree with String/Int/Long/BigInt map keys | lazy preorder sequence of path/key/value/depth/isLeaf records | scalar leaf | arity; invalid keys fail during consumption | JVM+JS | experimental: lazy consumption and host tree lifetime | core-source |
| ZIP | — | 2 | two lists | pairs up to shorter length | elements preserved; collection reject | wrong collection | JVM+JS | candidate | arrays |
    """.trimIndent()
}
