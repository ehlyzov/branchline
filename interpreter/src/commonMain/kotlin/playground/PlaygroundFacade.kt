package playground

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import v2.runtime.bignum.toDouble
import v2.debug.Debug
import v2.debug.CollectingTracer
import v2.debug.TraceOptions
import v2.debug.TraceReport
import v2.Dec
import v2.FuncDecl
import v2.IBig
import v2.I32
import v2.I64
import v2.Lexer
import v2.ParseException
import v2.Parser
import v2.TransformDecl
import v2.DEFAULT_INPUT_ALIAS
import v2.ir.Exec
import v2.ir.ToIR
import v2.ir.TransformRegistry
import v2.ir.buildTransformDescriptors
import v2.ir.makeEval
import v2.runtime.bignum.BLBigDec
import v2.runtime.bignum.BLBigInt
import v2.sema.SemanticAnalyzer
import v2.std.StdLib
import v2.std.SharedResourceKind
import v2.std.SharedStoreProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.collections.buildMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@OptIn(ExperimentalJsExport::class)
@JsExport
object PlaygroundFacade {
    private const val INPUT_VAR = DEFAULT_INPUT_ALIAS
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json
    private val sharedJson = Json { ignoreUnknownKeys = true }

    private val debugHostFns: Map<String, (List<Any?>) -> Any?> = mapOf(
        "EXPLAIN" to { args ->
            require(args.size == 1 && args[0] is String) { "EXPLAIN(varName)" }
            val name = args[0] as String
            Debug.explain(name) ?: mapOf(
                "variable" to name,
                "info" to "Tracing disabled or no provenance recorded"
            )
        }
    )

    private val playgroundHostFns: Map<String, (List<Any?>) -> Any?> by lazy {
        StdLib.fns + debugHostFns
    }

    fun run(program: String, inputJson: String, enableTracing: Boolean = false): PlaygroundResult {
        return runWithShared(program, inputJson, enableTracing, null)
    }

    fun runWithShared(
        program: String,
        inputJson: String,
        enableTracing: Boolean = false,
        sharedJsonConfig: String? = null,
    ): PlaygroundResult {
        val tracer = if (enableTracing) {
            CollectingTracer(
                TraceOptions(
                    step = true,
                    includeEval = false,
                    includeCalls = true
                )
            )
        } else {
            null
        }
        val priorTracer = Debug.tracer
        return try {
            Debug.tracer = tracer
            val sharedSpecs = parseSharedSpecs(sharedJsonConfig)
            if (sharedSpecs.isNotEmpty()) {
                val store = SharedStoreProvider.store ?: return PlaygroundResult(
                    success = false,
                    outputJson = null,
                    errorMessage = "SharedStore is not configured.",
                    line = null,
                    column = null,
                    explainJson = null,
                    explainHuman = null
                )
                for (spec in sharedSpecs) {
                    if (!store.hasResource(spec.name)) {
                        store.addResource(spec.name, spec.kind)
                    }
                }
            }
            val effectiveProgram = wrapProgramIfNeeded(program, sharedSpecs)
            val tokens = Lexer(effectiveProgram).lex()
            val parsed = Parser(tokens, effectiveProgram).parse()

            val transforms = parsed.decls.filterIsInstance<TransformDecl>()
            val transform = transforms.find { it.name.equals("PLAYGROUND", ignoreCase = true) }
                ?: transforms.firstOrNull()
                ?: return PlaygroundResult(
                    success = false,
                    outputJson = null,
                    errorMessage = "Program must declare at least one TRANSFORM block.",
                    line = null,
                    column = null,
                    explainJson = null,
                    explainHuman = null
                )

            val hostFns = playgroundHostFns
            val funcs: Map<String, FuncDecl> = parsed.decls
                .filterIsInstance<FuncDecl>()
                .associateBy { it.name }

            SemanticAnalyzer(hostFns.keys).analyze(parsed)

            val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
            val typeDecls = parsed.decls.filterIsInstance<v2.TypeDecl>()
            val descriptors = buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
            val registry = TransformRegistry(
                funcs,
                hostFns,
                descriptors,
            )
            val eval = makeEval(hostFns, funcs, registry, tracer)
            val exec = Exec(ir, eval, tracer)

            val msg = parseInput(inputJson)
            val env = HashMap<String, Any?>().apply {
                this[INPUT_VAR] = msg
                putAll(msg)
            }
            val sharedSnapshot = SharedStoreProvider.store?.snapshot().orEmpty()
            if (sharedSpecs.isNotEmpty()) {
                for (spec in sharedSpecs) {
                    val seed = sharedSnapshot[spec.name] ?: emptyMap()
                    if (!env.containsKey(spec.name)) {
                        env[spec.name] = LinkedHashMap(seed)
                    }
                }
            }
            val result = exec.run(env, stringifyKeys = true)
            if (sharedSpecs.isNotEmpty()) {
                val store = SharedStoreProvider.store
                if (store != null) {
                    val delta = buildSharedDelta(sharedSpecs, sharedSnapshot, env)
                    if (delta.isNotEmpty()) {
                        GlobalScope.launch { store.commit(sharedSnapshot, delta) }
                    }
                }
            }
            val jsonElement = toJsonElement(result)
            val output = prettyJson.encodeToString(jsonElement)
            val explanationMap = tracer?.let { Debug.explainOutput(result) }
            val explainJson = explanationMap?.let { prettyJson.encodeToString(toJsonElement(it)) }
            val explainHuman = tracer?.let { TraceReport.from(it) }?.let(::renderTraceSummary)

            PlaygroundResult(
                success = true,
                outputJson = output,
                errorMessage = null,
                line = null,
                column = null,
                explainJson = explainJson,
                explainHuman = explainHuman
            )
        } catch (ex: ParseException) {
            PlaygroundResult(
                success = false,
                outputJson = null,
                errorMessage = ex.message ?: "Parser error",
                line = ex.token.line,
                column = ex.token.column,
                explainJson = null,
                explainHuman = null
            )
        } catch (ex: v2.sema.SemanticException) {
            PlaygroundResult(
                success = false,
                outputJson = null,
                errorMessage = ex.message ?: "Semantic error",
                line = ex.token.line,
                column = ex.token.column,
                explainJson = null,
                explainHuman = null
            )
        } catch (ex: Throwable) {
            PlaygroundResult(
                success = false,
                outputJson = null,
                errorMessage = ex.message ?: ex.toString(),
                line = null,
                column = null,
                explainJson = null,
                explainHuman = null
            )
        } finally {
            Debug.tracer = priorTracer
        }
    }

    private fun wrapProgramIfNeeded(body: String, sharedSpecs: List<SharedStorageSpec>): String {
        val hasTransformKeyword = Regex("\\bTRANSFORM\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val sharedDecls = renderSharedDecls(sharedSpecs)
        val sharedPrefix = if (sharedDecls.isBlank()) "" else "$sharedDecls\n\n"
        if (hasTransformKeyword) return body

        val trimmed = body.trim().lines()
        val indented = trimmed.joinToString("\n") { line ->
            if (line.isBlank()) line else "    $line"
        }
        val wrapped = """
            TRANSFORM Playground {
$indented
            }
        """.trimIndent()
        return sharedPrefix + wrapped
    }

    private fun parseSharedSpecs(sharedJsonConfig: String?): List<SharedStorageSpec> {
        if (sharedJsonConfig.isNullOrBlank()) return emptyList()
        return sharedJson.decodeFromString(
            ListSerializer(SharedStorageSpec.serializer()),
            sharedJsonConfig
        )
    }

    private fun renderSharedDecls(sharedSpecs: List<SharedStorageSpec>): String =
        sharedSpecs.joinToString("\n") { spec ->
            "SHARED ${spec.name} ${spec.kind.name};"
        }

    private fun buildSharedDelta(
        sharedSpecs: List<SharedStorageSpec>,
        snapshot: Map<String, Map<String, Any?>>,
        env: Map<String, Any?>,
    ): Map<String, Map<String, Any?>> {
        val delta = LinkedHashMap<String, Map<String, Any?>>()
        for (spec in sharedSpecs) {
            val before = snapshot[spec.name] ?: emptyMap()
            val afterRaw = env[spec.name] as? Map<*, *> ?: continue
            val after = LinkedHashMap<String, Any?>().apply {
                for ((k, v) in afterRaw) {
                    this[k?.toString() ?: "null"] = v
                }
            }
            val keys = LinkedHashSet<String>()
            keys.addAll(before.keys)
            keys.addAll(after.keys)
            val resourceDelta = LinkedHashMap<String, Any?>()
            for (key in keys) {
                val beforeValue = before[key]
                val afterValue = after[key]
                if (beforeValue != afterValue) {
                    resourceDelta[key] = afterValue
                }
            }
            if (resourceDelta.isNotEmpty()) {
                delta[spec.name] = resourceDelta
            }
        }
        return delta
    }

    private fun parseInput(inputJson: String): Map<String, Any?> {
        if (inputJson.isBlank()) return emptyMap()
        val element = compactJson.parseToJsonElement(inputJson)
        val parsed = fromJsonElement(element)
        if (parsed !is Map<*, *>) {
            error("Input JSON must be an object at the top level.")
        }
        val out = LinkedHashMap<String, Any?>(parsed.size)
        for ((k, v) in parsed) {
            require(k is String) { "Input JSON keys must be strings." }
            out[k] = v
        }
        return out
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> fromPrimitive(element)
        is JsonObject -> LinkedHashMap<String, Any?>().apply {
            for ((k, v) in element) {
                this[k] = fromJsonElement(v)
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

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Byte -> JsonPrimitive(value)
        is Short -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is BLBigInt -> JsonPrimitive(value.toString())
        is BLBigDec -> JsonPrimitive(value.toDouble())
        is I32 -> JsonPrimitive(value.v)
        is I64 -> JsonPrimitive(value.v)
        is IBig -> JsonPrimitive(value.v.toString())
        is Dec -> JsonPrimitive(value.v.toDouble())
        is Map<*, *> -> JsonObject(buildMap {
            value.entries.forEach { (k, v) ->
                val key = k?.toString() ?: "null"
                put(key, toJsonElement(v))
            }
        })
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        is Sequence<*> -> JsonArray(value.map { toJsonElement(it) }.toList())
        else -> JsonPrimitive(value.toString())
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
data class PlaygroundResult(
    val success: Boolean,
    val outputJson: String?,
    val errorMessage: String?,
    val line: Int?,
    val column: Int?,
    val explainJson: String?,
    val explainHuman: String?
)

@Serializable
data class SharedStorageSpec(
    val name: String,
    val kind: SharedResourceKind = SharedResourceKind.MANY,
)

private fun renderTraceSummary(report: TraceReport.TraceReportData): String? {
    val sections = mutableListOf<String>()

    if (report.checkpoints.isNotEmpty()) {
        val checkpointLines = report.checkpoints.map { checkpoint ->
            val duration = checkpoint.at.toString()
            val labelSuffix = checkpoint.label?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            "  - @$duration$labelSuffix"
        }
        sections += buildString {
            append("Checkpoints:")
            checkpointLines.forEach { line ->
                append('\n')
                append(line)
            }
        }
    }

    if (report.explanations.isNotEmpty()) {
        sections += report.explanations.joinToString("\n\n")
    }

    val summary = sections.joinToString("\n\n").trim()
    return summary.ifEmpty { null }
}
