package v2.playground

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
import v2.FuncDecl
import v2.Lexer
import v2.Parser
import v2.TransformDecl
import v2.ir.ToIR
import v2.ir.buildRunnerFromIRMP
import v2.sema.SemanticAnalyzer
import v2.std.StdLib
import kotlin.test.Test
import kotlin.test.assertTrue

class PlaygroundExamplesJsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val fs = js("require('fs')")
    private val path = js("require('path')")
    private val deterministicNow: (List<Any?>) -> Any? = { args ->
        require(args.isEmpty()) { "NOW()" }
        "2000-01-01T00:00:00Z"
    }

    @Test
    fun allExamplesExecute() {
        val examplesDir = findExamplesDir()

        val files = (fs.readdirSync(examplesDir) as Array<String>).asList()
        val failures = mutableListOf<String>()
        val hostFns = StdLib.fns + mapOf("NOW" to deterministicNow)

        for (name in files) {
            val fullPath = path.join(examplesDir, name)
            val stat = fs.statSync(fullPath)
            if (!(stat.isFile() as Boolean)) continue
            if (!name.endsWith(".json")) continue
            try {
                val text = fs.readFileSync(fullPath, "utf8") as String
                val example = json.parseToJsonElement(text).jsonObject
                val programLines = example["program"] ?: error("Missing program in $fullPath")
                val body = programLines.jsonArray.joinToString("\n") { it.jsonPrimitive.content }
                val hasTransform = Regex("\\bTRANSFORM\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
                val sharedDecls = renderSharedDecls(example)
                val sharedPrefix = if (sharedDecls.isBlank()) "" else "$sharedDecls\n\n"
                val program = if (hasTransform) {
                    body
                } else {
                    val namePart = name.removeSuffix(".json").replace('-', '_')
                    sharedPrefix + "TRANSFORM $namePart {\n$body\n}"
                }
                val inputElement = example["input"] ?: JsonObject(emptyMap())

                val tokens = Lexer(program).lex()
                val parsed = Parser(tokens, program).parse()
                SemanticAnalyzer(hostFns.keys).analyze(parsed)
                val funcs = parsed.decls.filterIsInstance<FuncDecl>().associateBy { it.name }
                val transforms = parsed.decls.filterIsInstance<TransformDecl>()
                require(transforms.isNotEmpty()) { "No TRANSFORM found in $fullPath" }
                val transform = transforms.first()
                val ir = ToIR(funcs, hostFns).compile(transform.body.statements)
                val runner = buildRunnerFromIRMP(ir, hostFns = hostFns, funcs = funcs)

                val input = toKotlin(inputElement) as? Map<String, Any?> ?: emptyMap()
                val sharedNames = sharedResourceNames(example)
                val seededInput = linkedMapOf<String, Any?>().apply {
                    putAll(input)
                    for (name in sharedNames) {
                        if (!containsKey(name)) {
                            this[name] = emptyMap<String, Any?>()
                        }
                    }
                }
                val output = runner(seededInput)
                assertTrue(output != null, "Example $fullPath produced null output")
            } catch (ex: Throwable) {
                failures += "$fullPath -> ${ex::class.simpleName}: ${ex.message}"
            }
        }

        if (failures.isNotEmpty()) {
            error("Playground examples failed (JS):\n${failures.joinToString("\n")}")
        }
    }

    private fun findExamplesDir(): String {
        var current = js("process.cwd()") as String
        repeat(6) {
            val candidate = path.resolve(current, "playground", "examples")
            if (fs.existsSync(candidate) as Boolean) return candidate
            val parent = path.resolve(current, "..")
            if (parent == current) return candidate
            current = parent
        }
        error("Examples directory not found from cwd ${js("process.cwd()")}")
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
        is JsonObject -> linkedMapOf<String, Any?>().apply {
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
