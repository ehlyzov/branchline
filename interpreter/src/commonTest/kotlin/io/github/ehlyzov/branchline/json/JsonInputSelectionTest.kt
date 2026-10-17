package io.github.ehlyzov.branchline.json

import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import kotlin.test.*

class JsonInputSelectionTest {
    @Test
    fun selectedValuesAreCompleteOrderedAndDetached() {
        val text = """{"unused":{"deep":[1,{"x":"skip"}]},"b":null,"a":{"x":[1,true,"text"]}}"""
        val selected = parseJsonObjectInputSelected(text, setOf("a", "b", "missing"))
        val eager = parseJsonObjectInput(text)
        assertEquals(listOf("b", "a"), selected.keys.toList())
        assertTrue(selected.containsKey("b"))
        assertFalse(selected.containsKey("missing"))
        assertEquals(eager["a"], selected["a"])
        @Suppress("UNCHECKED_CAST")
        ((selected["a"] as Map<String, Any?>)["x"] as MutableList<Any?>).add("changed")
        assertEquals(3, ((parseJsonObjectInputSelected(text, setOf("a"))["a"] as Map<*, *>)["x"] as List<*>).size)
    }

    @Test
    fun skippedInputPreservesAllValidationAndErrorPositions() {
        val badValues = listOf(
            "{\"x\":1,\"x\":2}", "{\"x\":1,\"\\u0078\":2}",
            "{\"x\":1,\"x\": [}", "[1,]", "{\"x\":1,}", "[true false]",
            "{x:1}", "{\"x\" 1}", "\"bad\\q\"", "\"bad\ncontrol\"",
            "\"\\uD800\"", "\"\\uD800\\u0041\"", "\"\\uZZZZ\"", "\"\\u12\"",
            "\"unfinished", "\"escape\\", "tru", "nul", "-", "01", "1.", "1e+", "NaN",
        )
        for (value in badValues) {
            val text = """{"keep":1,"unused":$value}"""
            val eager = runCatching { parseJsonObjectInput(text) }.exceptionOrNull()
            val selected = runCatching { parseJsonObjectInputSelected(text, setOf("keep")) }.exceptionOrNull()
            assertNotNull(eager, text)
            assertTrue(selected is JsonInputException, text)
            assertEquals(eager.message, selected.message, text)
        }
        for (text in listOf("null", "[]", "[1,]", "{} trailing", "{\"keep\":1,\"keep\":[}", "{")) {
            assertEquals(
                runCatching { parseJsonObjectInput(text) }.exceptionOrNull()?.message,
                runCatching { parseJsonObjectInputSelected(text, emptySet()) }.exceptionOrNull()?.message,
                text,
            )
        }
    }

    @Test
    fun everyTruncatedPrefixUsesTheSameFailure() {
        val texts = listOf(
            """{"keep":{"nested":[null,true,3]},"skip":[{"escaped":"😀\uD83D\uDE00\n","a":[]},false]}""",
            """{"skip":{"empty":{},"list":[[],[{}]],"decimal":-12.5e-2},"keep":"done"}""",
        )
        for (text in texts) for (end in 1 until text.length) {
            val prefix = text.substring(0, end)
            val eager = runCatching { parseJsonObjectInput(prefix) }.exceptionOrNull()
            val selected = runCatching { parseJsonObjectInputSelected(prefix, setOf("keep")) }.exceptionOrNull()
            assertNotNull(eager, prefix)
            assertEquals(eager.message, selected?.message, prefix)
        }
    }

    @Test
    fun unicodeNumbersBlankAndNumericKeysMatchEager() {
        val values = listOf(
            "null", "false", "[]", "{}", "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"",
            "\"\\uD83D\\uDE00\"", "\"\\uDC00\"", "\"😀\"", "\"" + '\uD800' + "\"",
            "0", "-0", "1.25", "1e2", "9007199254740993", "9223372036854775808",
            "0.1234567890123456", "1e309", "{\"0\":1,\"01\":2,\"2147483648\":3}",
        )
        for (mode in JsonNumberMode.entries) for (keyMode in JsonKeyMode.entries) for (value in values) {
            val options = JsonParseOptions(mode, keyMode)
            val text = """{"skip":$value,"keep":$value}"""
            val eager = runCatching { parseJsonObjectInput(text, options) }
            val selected = runCatching { parseJsonObjectInputSelected(text, setOf("keep"), options) }
            assertEquals(eager.exceptionOrNull()?.message, selected.exceptionOrNull()?.message, "$mode $keyMode $text")
            // JS big-number wrappers do not provide structural Kotlin equality.
            if (eager.isSuccess) assertEquals(
                formatJsonValue(eager.getOrThrow()["keep"], pretty = false),
                formatJsonValue(selected.getOrThrow()["keep"], pretty = false),
                text,
            )
        }
        for (text in listOf("", " \n\t")) {
            assertEquals(emptyMap(), parseJsonObjectInputSelected(text, emptySet()))
        }
    }

    @Test
    fun accessPlannerCopiesKeysAndRejectsUnsupportedSyntax() {
        fun plan(body: String): JsonInputSelectionPlan {
            val source = "TRANSFORM Test { $body }"
            val program = Parser(Lexer(source).lex(), source).parse()
            return planJsonInputSelection(program, program.decls.filterIsInstance<TransformDecl>().single())
        }
        val plan = plan("LET x = input.price.amount; OUTPUT { x: x + 1, n: LENGTH(row.tags) };")
        assertTrue(plan.supported)
        assertEquals(setOf("input", "price", "tags"), plan.selectedRootKeys)
        (plan.selectedRootKeys as MutableSet<String>).clear()
        assertEquals(setOf("input", "price", "tags"), plan.selectedRootKeys)
        for (body in listOf(
            "OUTPUT input;", "LET x = row; OUTPUT x;", "OUTPUT input[\"a\"];",
            "OUTPUT { x: NUMBER(input.a) };", "LET LENGTH = 1; OUTPUT LENGTH;",
            "LET x = input.a; SET x = 2; OUTPUT x;", "OUTPUT { x: unknown };",
            "OUTPUT SUSPEND 0;", "LET x = SUSPEND 0; OUTPUT x;", "OUTPUT AWAIT 0;",
        )) assertFalse(plan(body).supported, body)
        assertTrue(plan("OUTPUT { negative: -input.a, inverted: !input.b };").supported)
    }
}
