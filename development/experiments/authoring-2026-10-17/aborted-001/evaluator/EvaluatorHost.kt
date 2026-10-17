import com.dashjoin.jsonata.Jsonata
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ehlyzov.branchline.*
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.sema.SemanticAnalyzer
import io.github.ehlyzov.branchline.std.StdLib
import java.io.File
import java.math.BigDecimal
import java.lang.reflect.InvocationTargetException

private val mapper = ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

private fun diagnostic(failure: Throwable): String {
    val cause = if (failure is InvocationTargetException) failure.targetException else failure
    return "${cause.javaClass.simpleName}: ${cause.message ?: "No diagnostic message"}"
}

private fun compile(language: String, source: String): (Any?) -> Any? = when (language) {
    "branchline" -> {
        val program = Parser(Lexer(source).lex(), source).parse()
        SemanticAnalyzer(StdLib.fns.keys).analyze(program)
        val funcs = program.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val transforms = program.decls.filterIsInstance<TransformDecl>()
        require(transforms.size == 1 && transforms.single().name == "Main") {
            "Declare exactly one TRANSFORM named Main"
        }
        val transform = transforms.single()
        require(transform.options.mode == Mode.BUFFER) { "Only BUFFER transforms are supported" }
        val ir = ToIR(funcs, StdLib.fns).compile(transform.body.statements)
        // Fresh executor and fresh JSON input for every case.
        val run: (Any?) -> Any? = { input ->
            val env = linkedMapOf<String, Any?>(DEFAULT_INPUT_ALIAS to input)
            COMPAT_INPUT_ALIASES.forEach { env[it] = input }
            if (input is Map<*, *>) input.forEach { (key, value) ->
                if (key is String && key != DEFAULT_INPUT_ALIAS && key !in COMPAT_INPUT_ALIASES) env[key] = value
            }
            Exec(ir = ir, hostFns = StdLib.fns, hostFnMeta = StdLib.meta, funcs = funcs,
                source = source, runtimeContextEnabled = true).run(env, stringifyKeys = true)
        }
        run
    }
    "jsonata" -> {
        val compiled = Jsonata.jsonata(source)
        // Keep explicit JSON null distinguishable from JSONata's undefined result.
        compiled.isOutputConvertNulls = false
        val run: (Any?) -> Any? = { input ->
            val value = compiled.evaluate(input ?: Jsonata.NULL_VALUE)
            require(value != null) { "JSONata returned undefined (no JSON value)" }
            value
        }
        run
    }
    "kotlin" -> {
        val method = Class.forName("SolutionKt").getMethod("transform", Any::class.java)
        val run: (Any?) -> Any? = { input -> method.invoke(null, input) }
        run
    }
    else -> error("Unsupported language: $language")
}

// This only normalizes JVM representations of JSON; it does not alter business values.
private fun jsonValue(value: Any?): Any? = when {
    value === Jsonata.NULL_VALUE -> null
    value == null || value is String || value is Boolean -> value
    value is Number -> {
        require(value !is Double || value.isFinite()) { "Non-finite JSON number" }
        require(value !is Float || value.isFinite()) { "Non-finite JSON number" }
        value
    }
    value is Map<*, *> -> value.entries.associate { (key, item) ->
        require(key is String) { "JSON object keys must be strings" }
        key to jsonValue(item)
    }
    value is List<*> -> value.map(::jsonValue)
    value is Array<*> -> value.map(::jsonValue)
    else -> error("Result is not a JSON value: ${value.javaClass.name}")
}

private fun same(actual: Any?, expected: Any?): Boolean = when {
    actual is Number && expected is Number ->
        BigDecimal(actual.toString()).compareTo(BigDecimal(expected.toString())) == 0
    actual is Map<*, *> && expected is Map<*, *> ->
        actual.keys == expected.keys && actual.keys.all { same(actual[it], expected[it]) }
    actual is List<*> && expected is List<*> ->
        actual.size == expected.size && actual.indices.all { same(actual[it], expected[it]) }
    else -> actual == expected
}

fun main(args: Array<String>) {
    // Invocation and malformed fixture failures remain infrastructure failures (nonzero process).
    require(args.size == 5) { "language source cases output extraCompileSeconds" }
    val extraCompile = args[4].toDouble()
    val source = File(args[1]).readText()
    val fixtures = mapper.readTree(File(args[2]))
    require(fixtures.isArray) { "Cases must be a JSON array" }
    fixtures.forEach { require(it.has("input") && it.has("expected")) { "Case needs input and expected" } }
    // Remove expected values from the candidate's readable filesystem before class initialization.
    check(File(args[2]).delete()) { "Could not remove copied fixture file before candidate loading" }
    val result = linkedMapOf<String, Any?>("compile_ok" to false, "cases" to emptyList<Any>(),
        "compile_seconds" to 0.0, "execute_seconds" to 0.0)
    val compileStart = System.nanoTime()
    val run = try {
        compile(args[0], source)
    } catch (failure: Throwable) {
        if (failure is LinkageError) throw failure
        result["compile_seconds"] = extraCompile + (System.nanoTime() - compileStart) / 1e9
        result["error"] = diagnostic(failure)
        File(args[3]).writeText(mapper.writeValueAsString(result))
        return
    }
    result["compile_ok"] = true
    result["compile_seconds"] = extraCompile + (System.nanoTime() - compileStart) / 1e9
    val executeStart = System.nanoTime()
    result["cases"] = fixtures.map { fixture ->
        try {
            val input = mapper.readValue(mapper.writeValueAsString(fixture["input"]), Any::class.java)
            val expected = mapper.readValue(mapper.writeValueAsString(fixture["expected"]), Any::class.java)
            val actual = jsonValue(run(input))
            val ok = same(actual, expected)
            linkedMapOf("ok" to ok, "actual" to actual,
                "error" to if (ok) null else "Output does not match expected JSON")
        } catch (failure: Throwable) {
            if (failure is LinkageError) throw failure
            linkedMapOf("ok" to false, "actual" to null, "error" to diagnostic(failure))
        }
    }
    result["execute_seconds"] = (System.nanoTime() - executeStart) / 1e9
    File(args[3]).writeText(mapper.writeValueAsString(result))
}
