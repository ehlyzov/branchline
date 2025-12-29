package v2.playground

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import v2.ExecutionEngine
import v2.FuncDecl
import v2.Lexer
import v2.Parser
import v2.TransformDecl
import v2.TypeDecl
import v2.ir.buildTransformDescriptors
import v2.ir.compileStream
import v2.sema.SemanticAnalyzer
import v2.std.StdLib

class PlaygroundExamplesJvmTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val deterministicNow: (List<Any?>) -> Any? = { args ->
        require(args.isEmpty()) { "NOW()" }
        "2000-01-01T00:00:00Z"
    }

    @Test
    fun `all playground examples execute without errors`() {
        val examplesDir = Path.of("..", "playground", "examples").toAbsolutePath().normalize()
        require(Files.exists(examplesDir)) { "Examples directory not found: $examplesDir" }

        val exampleFiles = Files.list(examplesDir).use { stream ->
            stream.filter(Path::isRegularFile)
                .filter { it.toString().endsWith(".json") }
                .toList()
        }

        val failures = mutableListOf<String>()
        val hostFns = StdLib.fns + mapOf("NOW" to deterministicNow)

        for (examplePath in exampleFiles) {
            try {
                val example = json.parseToJsonElement(Files.readString(examplePath)).jsonObject
                val inputFormat = example["inputFormat"]?.jsonPrimitive?.content
                if (inputFormat != null && !inputFormat.equals("json", ignoreCase = true)) {
                    continue
                }
                val programLines = example["program"] ?: error("Missing program in $examplePath")
                val programBody = programLines.jsonArray.joinToString("\n") { it.jsonPrimitive.content }
                val hasTransform = Regex("\\bTRANSFORM\\b", RegexOption.IGNORE_CASE).containsMatchIn(programBody)
                val sharedDecls = renderSharedDecls(example)
                val sharedPrefix = if (sharedDecls.isBlank()) "" else "$sharedDecls\n\n"
                val program = if (hasTransform) {
                    programBody
                } else {
                    val name = examplePath.fileName.toString().removeSuffix(".json")
                    sharedPrefix + "TRANSFORM ${name.replace('-', '_')} {\n$programBody\n}"
                }
                val inputElement = example["input"] ?: JsonObject(emptyMap())

                val tokens = Lexer(program).lex()
                val parsed = Parser(tokens, program).parse()
                SemanticAnalyzer(hostFns.keys).analyze(parsed)
                val funcs = parsed.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
                val transforms = parsed.decls.filterIsInstance<TransformDecl>()
                val typeDecls = parsed.decls.filterIsInstance<TypeDecl>()
                require(transforms.isNotEmpty()) { "No TRANSFORM found in $examplePath" }
                val transform = transforms.first()
                val descriptors = buildTransformDescriptors(transforms, typeDecls, hostFns.keys)
                val runnerInterp = compileStream(
                    t = transform,
                    funcs = funcs,
                    hostFns = hostFns,
                    transforms = descriptors,
                    engine = ExecutionEngine.INTERPRETER,
                )
                val runnerVm = compileStream(
                    t = transform,
                    funcs = funcs,
                    hostFns = hostFns,
                    transforms = descriptors,
                    engine = ExecutionEngine.VM,
                )

                val input = toKotlin(inputElement) as? Map<String, Any?> ?: emptyMap()
                val sharedNames = sharedResourceNames(example)
                val seededInput = LinkedHashMap<String, Any?>(input).apply {
                    for (name in sharedNames) {
                        putIfAbsent(name, emptyMap<String, Any?>())
                    }
                }
                val interpOutput = runnerInterp(seededInput)
                val vmOutput = runnerVm(seededInput)
                assertTrue(interpOutput != null, "Example $examplePath produced null output in interpreter")
                assertTrue(vmOutput != null, "Example $examplePath produced null output in VM")
                assertTrue(
                    interpOutput == vmOutput,
                    "Example $examplePath interpreter/VM mismatch. interp=$interpOutput vm=$vmOutput",
                )
            } catch (ex: Throwable) {
                failures += "$examplePath -> ${ex::class.simpleName}: ${ex.message}"
            }
        }

        if (failures.isNotEmpty()) {
            error("Playground examples failed:\n${failures.joinToString("\n")}")
        }
    }

    private fun toKotlin(elem: JsonElement): Any? = when (elem) {
        JsonNull -> null
        is JsonPrimitive -> when {
            elem.isString -> elem.content
            elem.booleanOrNull != null -> elem.booleanOrNull
            elem.longOrNull != null -> elem.longOrNull
            elem.doubleOrNull != null -> elem.doubleOrNull
            else -> elem.content
        }

        is kotlinx.serialization.json.JsonArray -> elem.map { toKotlin(it) }
        is JsonObject -> LinkedHashMap<String, Any?>().apply {
            elem.forEach { (k, v) -> this[k] = toKotlin(v) }
        }
    }

    private fun renderSharedDecls(example: JsonObject): String {
        val shared = example["shared"]?.jsonArray ?: return ""
        return shared.joinToString("\n") { entry ->
            val obj = entry.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@joinToString ""
            val kind = obj["kind"]?.jsonPrimitive?.content ?: "MANY"
            "SHARED $name $kind;"
        }.trim()
    }

    private fun sharedResourceNames(example: JsonObject): List<String> {
        val shared = example["shared"]?.jsonArray ?: return emptyList()
        return shared.mapNotNull { entry ->
            entry.jsonObject["name"]?.jsonPrimitive?.content
        }
    }
}
