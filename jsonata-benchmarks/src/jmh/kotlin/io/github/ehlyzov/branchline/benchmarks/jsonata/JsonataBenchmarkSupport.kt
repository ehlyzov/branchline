package io.github.ehlyzov.branchline.benchmarks.jsonata

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.ehlyzov.branchline.COMPAT_INPUT_ALIASES
import io.github.ehlyzov.branchline.DEFAULT_INPUT_ALIAS
import io.github.ehlyzov.branchline.FuncDecl
import io.github.ehlyzov.branchline.Lexer
import io.github.ehlyzov.branchline.Mode
import io.github.ehlyzov.branchline.Parser
import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.ir.Exec
import io.github.ehlyzov.branchline.ir.IRNode
import io.github.ehlyzov.branchline.ir.ToIR
import io.github.ehlyzov.branchline.std.StdLib
import io.github.ehlyzov.branchline.vm.VMFactory
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val DEFAULT_TEST_SUITE_SUBDIR: String = "jsonata/test/test-suite"
private const val TEST_SUITE_ROOT_PROPERTY: String = "jsonata.testSuiteRoot"
private const val TEST_SUITE_ROOT_ENV: String = "JSONATA_TEST_SUITE_ROOT"
private const val DASHJOIN_REPO_ENV: String = "DASHJOIN_REPO"
private const val DOTENV_FILENAME: String = ".env"

private val DOTENV_VALUES: Map<String, String> = loadDotenv()

public data class JsonataTestCase(
    public val id: String,
    public val expression: String,
    public val inputJson: String?,
    public val expectedErrorCode: String?,
    public val expectsUndefined: Boolean,
    public val usesBindings: Boolean,
    public val usesTimelimit: Boolean,
    public val usesDepth: Boolean,
    public val expectedResult: JsonElement?,
)

public data class CrossEngineCase(
    public val id: String,
    public val inputJson: String?,
    public val jsonataExpression: String,
    public val branchlineProgram: String?,
    public val kotlinEvalId: String?,
    public val expectedFailures: Map<String, String>,
    public val loadError: String?,
)

public data class CrossEngineInput(
    public val kotlinInput: Any?,
    public val branchlineInput: Any?,
    public val dashjoinInput: Any?,
    public val ibmInput: JsonNode,
)

public object JsonataTestSuite {
    private val json: Json = Json { ignoreUnknownKeys = true }

    public fun resolveRoot(): Path {
        val override = System.getProperty(TEST_SUITE_ROOT_PROPERTY)
            ?: getenvOrDotenv(TEST_SUITE_ROOT_ENV)
        if (override != null) return Paths.get(override)
        val dashjoinRepo = getenvOrDotenv(DASHJOIN_REPO_ENV)
        require(!dashjoinRepo.isNullOrBlank()) {
            "Set -D$TEST_SUITE_ROOT_PROPERTY, $TEST_SUITE_ROOT_ENV, or $DASHJOIN_REPO_ENV to locate the JSONata test suite."
        }
        return Paths.get(dashjoinRepo, DEFAULT_TEST_SUITE_SUBDIR)
    }

    public fun loadCase(root: Path, caseId: String): JsonataTestCase {
        val (group, caseName) = parseCaseId(caseId)
        val casePath = root.resolve("groups").resolve(group).resolve("$caseName.json")
        val caseJson = Files.readString(casePath)
        val parsed = json.parseToJsonElement(caseJson)
        val caseElement = parsed as? JsonObject
            ?: error("Case $caseId is not a JSON object (found ${parsed::class.simpleName}).")
        val expression = caseElement["expr"]?.jsonPrimitive?.content
            ?: error("Missing expr for $caseId")
        val inputJson = resolveInput(caseElement, root)
        val expectedErrorCode = caseElement["code"]?.jsonPrimitive?.content
        val expectsUndefined = caseElement["undefinedResult"]?.jsonPrimitive?.booleanOrNull == true
        val usesTimelimit = caseElement.containsKey("timelimit")
        val usesDepth = caseElement.containsKey("depth")
        val bindings = caseElement["bindings"]?.jsonObject ?: JsonObject(emptyMap())
        val usesBindings = bindings.isNotEmpty()
        val expectedResult = caseElement["result"]
        return JsonataTestCase(
            id = caseId,
            expression = expression,
            inputJson = inputJson,
            expectedErrorCode = expectedErrorCode,
            expectsUndefined = expectsUndefined,
            usesBindings = usesBindings,
            usesTimelimit = usesTimelimit,
            usesDepth = usesDepth,
            expectedResult = expectedResult,
        )
    }

    private fun parseCaseId(caseId: String): Pair<String, String> {
        val parts = caseId.split("/")
        require(parts.size == 2) { "Expected caseId like group/case### but got $caseId" }
        return parts[0] to parts[1]
    }

    private fun resolveInput(caseElement: JsonObject, root: Path): String? {
        if (caseElement.containsKey("data")) {
            val dataElement = caseElement["data"]
            return if (dataElement == null || dataElement is JsonNull) {
                null
            } else {
                dataElement.toString()
            }
        }
        val datasetElement = caseElement["dataset"]
        if (datasetElement == null || datasetElement is JsonNull) return null
        val datasetName = datasetElement.jsonPrimitive.content
        val datasetPath = root.resolve("datasets").resolve("$datasetName.json")
        return Files.readString(datasetPath)
    }

    public fun listCaseIds(root: Path): List<String> {
        val groupsRoot = root.resolve("groups")
        val caseIds = ArrayList<String>()
        Files.newDirectoryStream(groupsRoot).use { groupStream ->
            for (groupPath in groupStream) {
                if (!Files.isDirectory(groupPath)) continue
                val groupName = groupPath.fileName.toString()
                Files.newDirectoryStream(groupPath, "*.json").use { caseStream ->
                    for (casePath in caseStream) {
                        if (!Files.isRegularFile(casePath)) continue
                        val caseName = casePath.fileName.toString().removeSuffix(".json")
                        caseIds.add("$groupName/$caseName")
                    }
                }
            }
        }
        caseIds.sort()
        return caseIds
    }
}

public object JsonataInputs {
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val objectMapper: ObjectMapper = ObjectMapper()

    public fun parseKotlinValue(rawJson: String?): Any? {
        if (rawJson == null) return null
        val element = json.parseToJsonElement(rawJson)
        return fromJsonElement(element)
    }

    public fun parseKotlinObject(rawJson: String?): Map<String, Any?>? {
        val parsed = parseKotlinValue(rawJson) ?: return null
        if (parsed !is Map<*, *>) return null
        val out = LinkedHashMap<String, Any?>(parsed.size)
        for ((key, value) in parsed) {
            require(key is String) { "Input JSON keys must be strings." }
            out[key] = value
        }
        return out
    }

    public fun parseJsonNode(rawJson: String?): JsonNode {
        val text = rawJson ?: "null"
        return objectMapper.readTree(text)
    }
}

public interface JsonataEngine {
    public val id: String
    public fun isAvailable(): Boolean
    public fun compile(expression: String): Any
    public fun prepareInput(rawJson: String?): Any?
    public fun evaluate(compiled: Any, input: Any?): Any?
}

public object JsonataEngines {
    private val engines: List<JsonataEngine> = listOf(
        DashjoinJsonataEngine(),
        IbmJsonataEngine(),
    )

    public fun require(id: String): JsonataEngine {
        val engine = engines.firstOrNull { it.id == id }
            ?: error("Unknown JSONata engine: $id")
        check(engine.isAvailable()) { "JSONata engine $id is not available on the classpath." }
        return engine
    }
}

private class DashjoinJsonataEngine : JsonataEngine {
    override val id: String = "dashjoin"

    private val jsonataClass: Class<*>? = loadClass("com.dashjoin.jsonata.Jsonata")
    private val compileMethod: Method? = jsonataClass?.getMethod("jsonata", String::class.java)
    private val evalMethod: Method? = jsonataClass?.getMethod("evaluate", Any::class.java)

    override fun isAvailable(): Boolean = jsonataClass != null

    override fun compile(expression: String): Any {
        val method = requireNotNull(compileMethod) { "Dashjoin JSONata engine not available." }
        return requireNotNull(method.invoke(null, expression)) { "Dashjoin JSONata returned null." }
    }

    override fun prepareInput(rawJson: String?): Any? = JsonataInputs.parseKotlinValue(rawJson)

    override fun evaluate(compiled: Any, input: Any?): Any? {
        val method = requireNotNull(evalMethod) { "Dashjoin JSONata engine not available." }
        return method.invoke(compiled, input)
    }
}

private class IbmJsonataEngine : JsonataEngine {
    override val id: String = "ibm"

    private val expressionClass: Class<*>? = loadClass("com.api.jsonata4java.Expression")
    private val jsonNodeClass: Class<*>? = loadClass("com.fasterxml.jackson.databind.JsonNode")
    private val compileMethod: Method? = expressionClass?.getMethod("jsonata", String::class.java)
    private val evalMethod: Method? = expressionClass?.getMethod("evaluate", jsonNodeClass)

    override fun isAvailable(): Boolean = expressionClass != null && jsonNodeClass != null

    override fun compile(expression: String): Any {
        val method = requireNotNull(compileMethod) { "IBM JSONata engine not available." }
        return requireNotNull(method.invoke(null, expression)) { "IBM JSONata returned null." }
    }

    override fun prepareInput(rawJson: String?): Any? = JsonataInputs.parseJsonNode(rawJson)

    override fun evaluate(compiled: Any, input: Any?): Any? {
        val method = requireNotNull(evalMethod) { "IBM JSONata engine not available." }
        return method.invoke(compiled, input)
    }
}

public object BranchlineCompiler {
    private val hostFns: Map<String, (List<Any?>) -> Any?> = StdLib.fns

    public fun compileInterpreter(program: String): (Any?) -> Any? {
        val compiled = compileProgram(program)
        val exec = Exec(
            ir = compiled.ir,
            hostFns = compiled.hostFns,
            hostFnMeta = compiled.hostFnMeta,
            funcs = compiled.funcs,
        )
        return { input ->
            exec.run(buildEnv(input))
        }
    }

    public fun compileVm(program: String): (Any?) -> Any? {
        val compiled = compileProgram(program)
        val vmExec = VMFactory.createExecutor(
            compiled.ir,
            null,
            compiled.hostFns,
            compiled.hostFnMeta,
            compiled.funcs,
        )
        return { input ->
            vmExec.run(buildEnv(input))
        }
    }

    private fun compileProgram(program: String): BranchlineCompiled {
        val tokens = Lexer(program).lex()
        val parsed = Parser(tokens, program).parse()
        val funcs = parsed.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
        val transforms = parsed.decls.filterIsInstance<TransformDecl>()
        require(transforms.size == 1) { "Program must contain exactly one TRANSFORM." }
        val transform = transforms.single()
        require(transform.mode == Mode.BUFFER) { "Only buffer mode transforms are supported." }
        val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
        return BranchlineCompiled(
            ir = ir,
            hostFns = hostFns,
            hostFnMeta = StdLib.meta,
            funcs = funcs,
        )
    }

    private fun buildEnv(input: Any?): MutableMap<String, Any?> {
        val env = LinkedHashMap<String, Any?>()
        env[DEFAULT_INPUT_ALIAS] = input
        for (alias in COMPAT_INPUT_ALIASES) {
            env[alias] = input
        }
        val inputMap = input as? Map<*, *> ?: return env
        for ((key, value) in inputMap) {
            if (key is String) {
                env[key] = value
            }
        }
        return env
    }
}

private data class BranchlineCompiled(
    val ir: List<IRNode>,
    val hostFns: Map<String, (List<Any?>) -> Any?>,
    val hostFnMeta: Map<String, io.github.ehlyzov.branchline.std.HostFnMetadata>,
    val funcs: Map<String, FuncDecl>,
)

public const val ENGINE_DASHJOIN: String = "dashjoin"
public const val ENGINE_IBM: String = "ibm"
public const val ENGINE_BRANCHLINE_INTERPRETER: String = "branchline-interpreter"
public const val ENGINE_BRANCHLINE_VM: String = "branchline-vm"
public const val ENGINE_KOTLIN: String = "kotlin"

public object KotlinEvaluators {
    private val evaluators: Map<String, (Any?) -> Any?> = mapOf(
        "sum_prices" to ::evalSumPrices,
        "filter_names" to ::evalFilterNames,
        "map_discount" to ::evalMapDiscount,
        "summary_stats" to ::evalSummaryStats,
        "function-sift/case004" to ::evalFunctionSiftCase004,
        "hof-map/case000" to ::evalHofMapCase000,
        "function-zip/case002" to ::evalFunctionZipCase002,
        "hof-zip-map/case000" to ::evalHofZipMapCase000,
        "partial-application/case002" to ::evalPartialApplicationCase002,
        "function-sum/case000" to ::evalFunctionSumCase000,
        "function-count/case000" to ::evalFunctionCountCase000,
        "simple-array-selectors/case000" to ::evalSimpleArraySelectorsCase000,
        "fields/case000" to ::evalFieldsCase000,
        "numeric-operators/case000" to ::evalNumericOperatorsCase000,
        "string-concat/case000" to ::evalStringConcatCase000,
        "range-operator/case000" to ::evalRangeOperatorCase000,
        "performance/case001" to ::evalPerformanceCase001,
    )

    public fun resolve(id: String): (Any?) -> Any? {
        return evaluators[id] ?: error("Unknown kotlin eval id: $id")
    }
}

public object CrossEngineCases {
    public fun loadAll(): List<CrossEngineCase> {
        val root = JsonataTestSuite.resolveRoot()
        val entries = JsonataCaseMatrix.load()
        val cases = ArrayList<CrossEngineCase>(entries.size)
        for (entry in entries) {
            val usesInlineJsonata = entry.jsonataExpression != null || entry.inputJson != null
            val testSuiteCase = if (usesInlineJsonata) {
                null
            } else {
                try {
                    JsonataTestSuite.loadCase(root, entry.id)
                } catch (ex: Throwable) {
                    BenchmarkErrorReporter.record(ENGINE_DASHJOIN, entry.id, "load-case", ex)
                    BenchmarkErrorReporter.record(ENGINE_IBM, entry.id, "load-case", ex)
                    BenchmarkErrorReporter.record(ENGINE_BRANCHLINE_INTERPRETER, entry.id, "load-case", ex)
                    BenchmarkErrorReporter.record(ENGINE_BRANCHLINE_VM, entry.id, "load-case", ex)
                    BenchmarkErrorReporter.record(ENGINE_KOTLIN, entry.id, "load-case", ex)
                    null
                }
            }
            if (usesInlineJsonata && entry.jsonataExpression == null) {
                error("Case ${entry.id} must define jsonataExpression when using inline JSONata.")
            }
            val jsonataExpression = entry.jsonataExpression
                ?: testSuiteCase?.expression
                ?: ""
            val inputJson = if (usesInlineJsonata) entry.inputJson else testSuiteCase?.inputJson
            val expectedFailures = LinkedHashMap<String, String>()
            mergeExpectedFailures(expectedFailures, entry.expectedFailures)
            val loadError = if (testSuiteCase == null && !usesInlineJsonata && entry.jsonataExpression == null) {
                "Failed to load JSONata test suite case."
            } else {
                null
            }
            if (testSuiteCase != null) {
                val reasons = testSuiteFailureReasons(testSuiteCase)
                for (reason in reasons) {
                    addExpectedFailureForAllEngines(expectedFailures, reason)
                }
            }
            if (loadError != null) {
                addExpectedFailureForAllEngines(expectedFailures, loadError)
            }
            if (entry.branchlineProgram == null) {
                addExpectedFailure(expectedFailures, ENGINE_BRANCHLINE_INTERPRETER, "missing Branchline analog")
                addExpectedFailure(expectedFailures, ENGINE_BRANCHLINE_VM, "missing Branchline analog")
            }
            if (entry.kotlinEvalId == null) {
                addExpectedFailure(expectedFailures, ENGINE_KOTLIN, "missing Kotlin analog")
            }
            cases.add(
                CrossEngineCase(
                    id = entry.id,
                    inputJson = inputJson,
                    jsonataExpression = jsonataExpression,
                    branchlineProgram = entry.branchlineProgram,
                    kotlinEvalId = entry.kotlinEvalId,
                    expectedFailures = expectedFailures,
                    loadError = loadError,
                ),
            )
        }
        return cases
    }
}

public fun testSuiteFailureReasons(testCase: JsonataTestCase): List<String> {
    val reasons = ArrayList<String>()
    val expectedErrorCode = testCase.expectedErrorCode
    if (expectedErrorCode != null) {
        reasons.add("expected error $expectedErrorCode")
    }
    if (testCase.expectsUndefined) {
        reasons.add("expects undefined result")
    }
    if (testCase.usesTimelimit) {
        reasons.add("timelimit case")
    }
    if (testCase.usesDepth) {
        reasons.add("depth case")
    }
    if (testCase.usesBindings) {
        reasons.add("bindings not supported")
    }
    return reasons
}

private fun getenvOrDotenv(name: String): String? {
    val env = System.getenv(name)
    if (!env.isNullOrBlank()) {
        return env
    }
    val dotenvValue = DOTENV_VALUES[name]
    return dotenvValue?.takeIf { it.isNotBlank() }
}

private fun loadDotenv(): Map<String, String> {
    val path = findDotenvPath() ?: return emptyMap()
    val values = LinkedHashMap<String, String>()
    val lines = Files.readAllLines(path)
    for (line in lines) {
        val parsed = parseDotenvLine(line) ?: continue
        values[parsed.first] = parsed.second
    }
    return values
}

private fun findDotenvPath(): Path? {
    var current = Paths.get("").toAbsolutePath()
    while (true) {
        val candidate = current.resolve(DOTENV_FILENAME)
        if (Files.isRegularFile(candidate)) {
            return candidate
        }
        val parent = current.parent ?: return null
        if (parent == current) return null
        current = parent
    }
}

private fun parseDotenvLine(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
    val separator = trimmed.indexOf('=')
    if (separator <= 0) return null
    val key = trimmed.substring(0, separator).trim()
    if (key.isEmpty()) return null
    var value = trimmed.substring(separator + 1).trim()
    value = stripDotenvQuotes(value)
    return key to value
}

private fun stripDotenvQuotes(value: String): String {
    if (value.length < 2) return value
    val first = value.first()
    val last = value.last()
    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length - 1)
    }
    return value
}

private fun mergeExpectedFailures(
    target: MutableMap<String, String>,
    incoming: Map<String, String>,
) {
    for ((engineId, reason) in incoming) {
        addExpectedFailure(target, engineId, reason)
    }
}

private fun addExpectedFailureForAllEngines(
    target: MutableMap<String, String>,
    reason: String,
) {
    addExpectedFailure(target, ENGINE_DASHJOIN, reason)
    addExpectedFailure(target, ENGINE_IBM, reason)
    addExpectedFailure(target, ENGINE_BRANCHLINE_INTERPRETER, reason)
    addExpectedFailure(target, ENGINE_BRANCHLINE_VM, reason)
    addExpectedFailure(target, ENGINE_KOTLIN, reason)
}

private fun addExpectedFailure(
    target: MutableMap<String, String>,
    engineId: String,
    reason: String,
) {
    val trimmed = reason.trim()
    if (trimmed.isEmpty()) return
    val existing = target[engineId]
    target[engineId] = if (existing.isNullOrBlank()) trimmed else "$existing; $trimmed"
}

private fun evalSumPrices(input: Any?): Any? {
    val items = readItems(input)
    var sum = 0.0
    for (item in items) {
        val price = readNumber(item, "price")
        if (price != null) {
            sum += price
        }
    }
    return linkedMapOf("value" to sum)
}

private fun evalFilterNames(input: Any?): Any? {
    val items = readItems(input)
    val names = ArrayList<String>()
    for (item in items) {
        val price = readNumber(item, "price") ?: continue
        if (price > 10.0) {
            val name = readString(item, "name") ?: continue
            names.add(name)
        }
    }
    return linkedMapOf("items" to names)
}

private fun evalMapDiscount(input: Any?): Any? {
    val items = readItems(input)
    val discounted = ArrayList<Double>()
    for (item in items) {
        val price = readNumber(item, "price") ?: continue
        discounted.add(price * 1.1)
    }
    return linkedMapOf("items" to discounted)
}

private fun evalSummaryStats(input: Any?): Any? {
    val items = readItems(input)
    val prices = ArrayList<Double>()
    for (item in items) {
        val price = readNumber(item, "price") ?: continue
        prices.add(price)
    }
    val count = prices.size
    val avg = if (count == 0) null else prices.sum() / count.toDouble()
    return linkedMapOf(
        "count" to count,
        "avg" to avg,
    )
}

private fun evalFunctionSiftCase004(input: Any?): Any? {
    val sift = { data: Map<String, String>, predicate: (k: String, v: String, o: Map<String, String>) -> Boolean ->
        data.filter { entry ->
            predicate(entry.key, entry.value, data)
        }
    }
    return sift(
        mapOf(
            "a" to "hello",
            "b" to "world",
            "hello" to "again",
        ), { _: String, v: String, o: Map<String, String> ->
            o.keys.contains(v)
        })
}

private fun evalHofMapCase000(input: Any?): Any? {
    val data = mapOf("one" to listOf(1, 2, 3, 4, 5), "two" to listOf(5, 4, 3, 2, 1))
    val square = { x: Int -> x * x }
    val result = data["one"]!!.map(square)
    return result
}

private fun evalFunctionZipCase002(input: Any?): Any? {
    val first = listOf(1, 2, 3)
    val second = listOf(4, 5)
    val third = listOf(7, 8, 9)
    val limit = minOf(first.size, second.size, third.size)
    val out = ArrayList<List<Int>>(limit)
    for (i in 0 until limit) {
        out.add(listOf(first[i], second[i], third[i]))
    }
    return out
}

private fun evalHofZipMapCase000(input: Any?): Any? {
    val first = listOf(1, 2, 3, 4, 5)
    val second = listOf(5, 4, 3, 2, 1)
    val limit = minOf(first.size, second.size)
    val out = ArrayList<Int>(limit)
    for (i in 0 until limit) {
        out.add(first[i] + second[i])
    }
    return out
}

private fun evalPartialApplicationCase002(input: Any?): Any? {
    return "Hello World".substring(0, 5)
}

private fun evalFunctionSumCase000(input: Any?): Any? {
    val root = input as? Map<*, *> ?: return null
    val account = readMap(root, "Account") ?: return null
    val orders = readList(account, "Order") ?: return 0.0
    var sum = 0.0
    for (order in orders) {
        val orderMap = order as? Map<*, *> ?: continue
        val products = readList(orderMap, "Product") ?: continue
        for (product in products) {
            val productMap = product as? Map<*, *> ?: continue
            val price = readNumber(productMap, "Price") ?: continue
            val quantity = readNumber(productMap, "Quantity") ?: continue
            sum += price * quantity
        }
    }
    return sum
}

private fun evalFunctionCountCase000(input: Any?): Any? {
    val root = input as? Map<*, *> ?: return null
    val account = readMap(root, "Account") ?: return null
    val orders = readList(account, "Order") ?: return 0
    var count = 0
    for (order in orders) {
        val orderMap = order as? Map<*, *> ?: continue
        val products = readList(orderMap, "Product") ?: continue
        for (product in products) {
            val productMap = product as? Map<*, *> ?: continue
            val price = readNumber(productMap, "Price") ?: continue
            val quantity = readNumber(productMap, "Quantity") ?: continue
            count += 1
        }
    }
    return count
}

private fun evalSimpleArraySelectorsCase000(input: Any?): Any? {
    val root = input as? List<*> ?: return emptyList<Any?>()
    val out = ArrayList<Any?>()
    for (item in root) {
        val itemMap = item as? Map<*, *> ?: continue
        val nest0 = readList(itemMap, "nest0") ?: continue
        for (nest0Entry in nest0) {
            val nest0Map = nest0Entry as? Map<*, *> ?: continue
            val nest1 = readList(nest0Map, "nest1") ?: continue
            if (nest1.isNotEmpty()) {
                out.add(nest1[0])
            }
        }
    }
    return out
}

private fun evalFieldsCase000(input: Any?): Any? {
    val root = input as? Map<*, *> ?: return null
    val foo = readMap(root, "foo") ?: return null
    return foo["bar"]
}

private fun evalNumericOperatorsCase000(input: Any?): Any? {
    val root = input as? Map<*, *> ?: return null
    val foo = readMap(root, "foo") ?: return null
    val left = readNumber(foo, "bar") ?: return null
    val right = readNumber(root, "bar") ?: return null
    return left + right
}

private fun evalStringConcatCase000(input: Any?): Any? {
    return "foo" + "bar"
}

private fun evalRangeOperatorCase000(input: Any?): Any? {
    val out = ArrayList<Int>(10)
    for (i in 0..9) {
        out.add(i)
    }
    return out
}

private fun evalPerformanceCase001(input: Any?): Any? {
    val items = readItems(input)
    val filtered = items.filter { item ->
        val text = readString(item, "text")
        text != null && text != "" && text != "test"
    }
    val out = ArrayList<Map<String, Any?>>(items.size)
    val limit = minOf(items.size, filtered.size)
    for (i in 0 until limit) {
        val label = readString(items[i], "label") ?: ""
        val text = readString(filtered[i], "text") ?: ""
        out.add(
            linkedMapOf(
                "foo" to "bar",
                "label" to "$label $text".trim(),
            ),
        )
    }
    return linkedMapOf("items" to out)
}

private fun fromJsonElement(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> fromPrimitive(element)
    is JsonObject -> LinkedHashMap<String, Any?>().apply {
        for ((key, value) in element) {
            this[key] = fromJsonElement(value)
        }
    }

    is JsonArray -> ArrayList<Any?>(element.size).apply {
        element.forEach { add(fromJsonElement(it)) }
    }
}

private fun fromPrimitive(primitive: JsonPrimitive): Any? {
    primitive.booleanOrNull?.let { return it }
    primitive.longOrNull?.let { return it }
    primitive.doubleOrNull?.let { return it }
    return primitive.content
}

private fun loadClass(name: String): Class<*>? {
    return try {
        Class.forName(name)
    } catch (_: ClassNotFoundException) {
        null
    }
}

private fun readItems(input: Any?): List<Map<*, *>> {
    val map = input as? Map<*, *> ?: return emptyList()
    val items = map["items"] as? List<*> ?: return emptyList()
    val out = ArrayList<Map<*, *>>(items.size)
    for (item in items) {
        val map = item as? Map<*, *> ?: continue
        out.add(map)
    }
    return out
}

private fun readMap(map: Map<*, *>, key: String): Map<*, *>? {
    val value = map[key] ?: return null
    return value as? Map<*, *>
}

private fun readList(map: Map<*, *>, key: String): List<*>? {
    val value = map[key] ?: return null
    return value as? List<*>
}

private fun readNumber(map: Map<*, *>, key: String): Double? {
    return readNumber(map[key])
}

private fun readNumber(value: Any?): Double? = when (value) {
    is Byte -> value.toDouble()
    is Short -> value.toDouble()
    is Int -> value.toDouble()
    is Long -> value.toDouble()
    is Float -> value.toDouble()
    is Double -> value
    else -> null
}

private fun readString(map: Map<*, *>, key: String): String? {
    val value = map[key] ?: return null
    return value.toString()
}
