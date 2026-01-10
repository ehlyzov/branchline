package io.github.ehlyzov.branchline.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.TypeKind
import io.github.ehlyzov.branchline.schema.JsonSchemaCodec
import io.github.ehlyzov.branchline.schema.JsonSchemaException
import io.github.ehlyzov.branchline.schema.JsonSchemaOptions
import kotlin.collections.iterator

private const val DEFAULT_SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema"

public object JsonSchemaCliInterop {
    private val json = Json { prettyPrint = true }

    fun exportSchema(
        typeDecls: List<TypeDecl>,
        typeName: String?,
        options: SchemaOptions,
    ): String {
        if (typeDecls.isEmpty()) {
            throw CliException("No TYPE declarations found in the script", kind = CliErrorKind.INPUT)
        }
        val declsByName = typeDecls.associateBy { it.name }
        if (!options.allTypes && typeName != null && !declsByName.containsKey(typeName)) {
            throw CliException("TYPE '$typeName' not found in the script", kind = CliErrorKind.INPUT)
        }
        val schemaOptions = JsonSchemaOptions(nullabilityStyle = options.nullabilityStyle)
        val defs = LinkedHashMap<String, JsonElement>(declsByName.size)
        for ((name, decl) in declsByName) {
            defs[name] = JsonSchemaCodec.encodeTypeDecl(decl, schemaOptions, declsByName)
        }
        val content = LinkedHashMap<String, JsonElement>(3).apply {
            put("\$schema", JsonPrimitive(DEFAULT_SCHEMA_URI))
            put("\$defs", JsonObject(defs))
            if (!options.allTypes && typeName != null) {
                put("\$ref", JsonPrimitive("#/\$defs/$typeName"))
            }
        }
        return json.encodeToString(JsonObject(content))
    }

    fun importSchema(
        rawSchema: String,
        importName: String,
        options: SchemaOptions,
    ): List<TypeDecl> {
        val element = json.parseToJsonElement(rawSchema)
        val schema = element as? JsonObject
            ?: throw CliException("Schema must be a JSON object", kind = CliErrorKind.INPUT)
        val schemaOptions = JsonSchemaOptions(nullabilityStyle = options.nullabilityStyle)
        val defs = resolveDefinitions(schema)
        val typeDecls = LinkedHashMap<String, TypeDecl>()
        for ((name, defSchema) in defs) {
            if (name == importName) continue
            typeDecls[name] = decodeSchemaDecl(name, defSchema, schemaOptions)
        }
        val rootSchema = resolveRootSchema(schema, defs)
        typeDecls[importName] = decodeSchemaDecl(importName, rootSchema, schemaOptions)
        return typeDecls.values.toList()
    }
}

public fun renderTypeDecl(typeDecl: TypeDecl): String = when (typeDecl.kind) {
    TypeKind.ENUM -> {
        val values = typeDecl.defs.joinToString(", ")
        "TYPE ${typeDecl.name} = enum { $values };"
    }
    TypeKind.UNION -> {
        val members = typeDecl.defs.joinToString(" | ")
        "TYPE ${typeDecl.name} = union $members;"
    }
}

private fun resolveDefinitions(schema: JsonObject): Map<String, JsonElement> {
    val defs = schema["\$defs"] as? JsonObject
    if (defs != null) return defs
    val legacy = schema["definitions"] as? JsonObject
    return legacy ?: emptyMap()
}

private fun resolveRootSchema(
    schema: JsonObject,
    defs: Map<String, JsonElement>,
): JsonElement {
    val ref = schema["\$ref"] ?: return schema
    val refName = extractRefName(ref)
    return defs[refName] ?: schema
}

private fun decodeSchemaDecl(
    name: String,
    schema: JsonElement,
    options: JsonSchemaOptions,
): TypeDecl = try {
    JsonSchemaCodec.decodeTypeDecl(name, schema, options)
} catch (ex: JsonSchemaException) {
    throw CliException(ex.message ?: "Schema import failed", kind = CliErrorKind.INPUT)
}

private fun extractRefName(ref: JsonElement): String {
    val raw = (ref as? JsonPrimitive)?.content
        ?: throw CliException("Schema \$ref must be a string", kind = CliErrorKind.INPUT)
    val trimmed = raw.removePrefix("#/")
    return trimmed.split('/').last()
}
