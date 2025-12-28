package v2.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import v2.ArrayTypeRef
import v2.EnumTypeRef
import v2.NamedTypeRef
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.RecordFieldType
import v2.RecordTypeRef
import v2.Token
import v2.TokenType
import v2.TypeDecl
import v2.TypeKind
import v2.TypeRef
import v2.UnionTypeRef

public enum class NullabilityStyle {
    TYPE_UNION,
    NULLABLE_KEYWORD,
}

public data class JsonSchemaOptions(
    val nullabilityStyle: NullabilityStyle = NullabilityStyle.TYPE_UNION,
)

public class JsonSchemaException(message: String) : RuntimeException(message)

public object JsonSchemaCodec {
    public fun encodeTypeRef(
        typeRef: TypeRef,
        options: JsonSchemaOptions = JsonSchemaOptions(),
        typeDecls: Map<String, TypeDecl> = emptyMap(),
    ): JsonObject = encodeTypeRefInternal(typeRef, options, typeDecls)

    public fun encodeTypeDecl(
        typeDecl: TypeDecl,
        options: JsonSchemaOptions = JsonSchemaOptions(),
        typeDecls: Map<String, TypeDecl> = emptyMap(),
    ): JsonObject = when (typeDecl.kind) {
        TypeKind.ENUM -> JsonObject(
            mapOf(
                "type" to JsonPrimitive("string"),
                "enum" to JsonArray(typeDecl.defs.map { JsonPrimitive(it) }),
            ),
        )
        TypeKind.UNION -> {
            val members = typeDecl.defs.map { encodeTypeName(it, typeDecls) }
            JsonObject(mapOf("anyOf" to JsonArray(members)))
        }
    }

    public fun decodeTypeRef(
        schema: JsonElement,
        options: JsonSchemaOptions = JsonSchemaOptions(),
        token: Token = schemaToken(),
    ): TypeRef = decodeTypeRefInternal(schema, options, token)

    public fun decodeTypeDecl(
        name: String,
        schema: JsonElement,
        options: JsonSchemaOptions = JsonSchemaOptions(),
        token: Token = schemaToken(),
    ): TypeDecl {
        val typeRef = decodeTypeRefInternal(schema, options, token)
        return when (typeRef) {
            is EnumTypeRef -> TypeDecl(name, TypeKind.ENUM, typeRef.values, token)
            is UnionTypeRef -> TypeDecl(name, TypeKind.UNION, unionMembersToDefs(typeRef), token)
            is PrimitiveTypeRef -> TypeDecl(name, TypeKind.UNION, listOf(primitiveTypeName(typeRef.kind)), token)
            is NamedTypeRef -> TypeDecl(name, TypeKind.UNION, listOf(typeRef.name), token)
            else -> throw JsonSchemaException(
                "Schema cannot be represented as a TYPE declaration (only enums or unions are supported).",
            )
        }
    }
}

private fun encodeTypeRefInternal(
    typeRef: TypeRef,
    options: JsonSchemaOptions,
    typeDecls: Map<String, TypeDecl>,
): JsonObject = when (typeRef) {
    is PrimitiveTypeRef -> encodePrimitive(typeRef, options)
    is EnumTypeRef -> JsonObject(
        mapOf(
            "type" to JsonPrimitive("string"),
            "enum" to JsonArray(typeRef.values.map { JsonPrimitive(it) }),
        ),
    )
    is ArrayTypeRef -> JsonObject(
        mapOf(
            "type" to JsonPrimitive("array"),
            "items" to encodeTypeRefInternal(typeRef.elementType, options, typeDecls),
        ),
    )
    is RecordTypeRef -> {
        val properties = LinkedHashMap<String, JsonElement>(typeRef.fields.size)
        val required = mutableListOf<JsonPrimitive>()
        for (field in typeRef.fields) {
            val baseSchema = encodeTypeRefInternal(field.type, options, typeDecls)
            val fieldSchema = if (field.optional) {
                encodeNullableSchema(baseSchema, options)
            } else {
                baseSchema
            }
            properties[field.name] = fieldSchema
            if (!field.optional) {
                required += JsonPrimitive(field.name)
            }
        }
        val content = LinkedHashMap<String, JsonElement>(3).apply {
            put("type", JsonPrimitive("object"))
            put("properties", JsonObject(properties))
            if (required.isNotEmpty()) {
                put("required", JsonArray(required))
            }
        }
        JsonObject(content)
    }
    is UnionTypeRef -> encodeUnion(typeRef.members, options, typeDecls)
    is NamedTypeRef -> JsonObject(
        mapOf("\$ref" to JsonPrimitive("#/\$defs/${typeRef.name}")),
    )
}

private fun encodeUnion(
    members: List<TypeRef>,
    options: JsonSchemaOptions,
    typeDecls: Map<String, TypeDecl>,
): JsonObject {
    val encoded = members.map { encodeTypeRefInternal(it, options, typeDecls) }
    val primitiveTypes = members.mapNotNull { primitiveTypeNameForSchema(it) }
    if (primitiveTypes.size == members.size && options.nullabilityStyle == NullabilityStyle.TYPE_UNION) {
        return JsonObject(mapOf("type" to JsonArray(primitiveTypes.map { JsonPrimitive(it) })))
    }
    return JsonObject(mapOf("anyOf" to JsonArray(encoded)))
}

private fun encodePrimitive(typeRef: PrimitiveTypeRef, options: JsonSchemaOptions): JsonObject = when (typeRef.kind) {
    PrimitiveType.TEXT -> JsonObject(mapOf("type" to JsonPrimitive("string")))
    PrimitiveType.NUMBER -> JsonObject(mapOf("type" to JsonPrimitive("number")))
    PrimitiveType.BOOLEAN -> JsonObject(mapOf("type" to JsonPrimitive("boolean")))
    PrimitiveType.NULL -> JsonObject(mapOf("type" to JsonPrimitive("null")))
    PrimitiveType.ANY -> JsonObject(emptyMap())
    PrimitiveType.ANY_NULLABLE -> when (options.nullabilityStyle) {
        NullabilityStyle.NULLABLE_KEYWORD -> JsonObject(mapOf("nullable" to JsonPrimitive(true)))
        NullabilityStyle.TYPE_UNION -> JsonObject(
            mapOf(
                "anyOf" to JsonArray(
                    listOf(
                        JsonObject(emptyMap()),
                        JsonObject(mapOf("type" to JsonPrimitive("null"))),
                    ),
                ),
            ),
        )
    }
}

private fun encodeNullableSchema(schema: JsonObject, options: JsonSchemaOptions): JsonObject = when (options.nullabilityStyle) {
    NullabilityStyle.NULLABLE_KEYWORD -> {
        val content = LinkedHashMap<String, JsonElement>(schema.size + 1).apply {
            putAll(schema)
            put("nullable", JsonPrimitive(true))
        }
        JsonObject(content)
    }
    NullabilityStyle.TYPE_UNION -> {
        val typeElement = schema["type"]
        val canUseTypeArray = typeElement is JsonPrimitive &&
            !schema.containsKey("enum") &&
            !schema.containsKey("anyOf") &&
            !schema.containsKey("\$ref")
        if (canUseTypeArray) {
            val content = LinkedHashMap<String, JsonElement>(schema.size).apply {
                putAll(schema)
                put(
                    "type",
                    JsonArray(listOf(JsonPrimitive("null"), typeElement as JsonPrimitive)),
                )
            }
            JsonObject(content)
        } else {
            JsonObject(
                mapOf(
                    "anyOf" to JsonArray(
                        listOf(
                            schema,
                            JsonObject(mapOf("type" to JsonPrimitive("null"))),
                        ),
                    ),
                ),
            )
        }
    }
}

private fun encodeTypeName(name: String, typeDecls: Map<String, TypeDecl>): JsonObject {
    val primitive = primitiveTypeName(name)
    if (primitive != null) {
        return JsonObject(mapOf("type" to JsonPrimitive(primitive)))
    }
    return if (typeDecls.containsKey(name)) {
        JsonObject(mapOf("\$ref" to JsonPrimitive("#/\$defs/$name")))
    } else {
        JsonObject(mapOf("\$ref" to JsonPrimitive(name)))
    }
}

private fun decodeTypeRefInternal(
    schema: JsonElement,
    options: JsonSchemaOptions,
    token: Token,
): TypeRef {
    if (schema is JsonObject) {
        schema["\$ref"]?.let { ref ->
            return NamedTypeRef(extractRefName(ref), token)
        }
        schema["enum"]?.let { enumElement ->
            val values = enumElement as? JsonArray ?: throw JsonSchemaException("Schema enum must be an array")
            val entries = values.map {
                (it as? JsonPrimitive)?.content ?: throw JsonSchemaException("Schema enum values must be strings")
            }
            return EnumTypeRef(entries, token)
        }
        schema["anyOf"]?.let { anyOf ->
            val members = (anyOf as? JsonArray)?.map { decodeTypeRefInternal(it, options, token) }
                ?: throw JsonSchemaException("Schema anyOf must be an array")
            return applyNullable(UnionTypeRef(members, token), token)
        }
        val typeElement = schema["type"]
        val nullableFlag = schema["nullable"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
        val typeRef = when (typeElement) {
            is JsonArray -> decodeTypeArray(typeElement, options, token)
            is JsonPrimitive -> decodeTypeString(typeElement.content, schema, options, token)
            null -> decodeImplicitType(schema, options, token)
            else -> throw JsonSchemaException("Schema type must be a string or array")
        }
        return if (nullableFlag) {
            applyNullable(typeRef, token)
        } else {
            typeRef
        }
    }
    return decodeTypeRefInternal(JsonObject(emptyMap()), options, token)
}

private fun decodeTypeArray(
    types: JsonArray,
    options: JsonSchemaOptions,
    token: Token,
): TypeRef {
    val members = types.map {
        val entry = (it as? JsonPrimitive)?.content ?: throw JsonSchemaException("Schema type entries must be strings")
        decodeTypeString(entry, JsonObject(emptyMap()), options, token)
    }
    return UnionTypeRef(members, token)
}

private fun decodeTypeString(
    typeName: String,
    schema: JsonObject,
    options: JsonSchemaOptions,
    token: Token,
): TypeRef = when (typeName) {
    "string" -> PrimitiveTypeRef(PrimitiveType.TEXT, token)
    "number" -> PrimitiveTypeRef(PrimitiveType.NUMBER, token)
    "boolean" -> PrimitiveTypeRef(PrimitiveType.BOOLEAN, token)
    "null" -> PrimitiveTypeRef(PrimitiveType.NULL, token)
    "object" -> decodeObject(schema, options, token)
    "array" -> decodeArray(schema, options, token)
    else -> PrimitiveTypeRef(PrimitiveType.ANY, token)
}

private fun decodeImplicitType(schema: JsonObject, options: JsonSchemaOptions, token: Token): TypeRef {
    if (schema.containsKey("properties")) {
        return decodeObject(schema, options, token)
    }
    if (schema.containsKey("items")) {
        return decodeArray(schema, options, token)
    }
    return PrimitiveTypeRef(PrimitiveType.ANY, token)
}

private fun decodeObject(schema: JsonObject, options: JsonSchemaOptions, token: Token): TypeRef {
    val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = schema["required"] as? JsonArray ?: JsonArray(emptyList())
    val requiredNames = required.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
    val fields = properties.map { (name, definition) ->
        RecordFieldType(
            name = name,
            type = decodeTypeRefInternal(definition, options, token),
            optional = !requiredNames.contains(name),
            token = token,
        )
    }
    return RecordTypeRef(fields, token)
}

private fun decodeArray(schema: JsonObject, options: JsonSchemaOptions, token: Token): TypeRef {
    val items = schema["items"] ?: JsonObject(emptyMap())
    return ArrayTypeRef(decodeTypeRefInternal(items, options, token), token)
}

private fun applyNullable(typeRef: TypeRef, token: Token): TypeRef {
    if (typeRef is UnionTypeRef) {
        val members = typeRef.members.toMutableList()
        if (members.none { it is PrimitiveTypeRef && it.kind == PrimitiveType.NULL }) {
            members += PrimitiveTypeRef(PrimitiveType.NULL, token)
        }
        return UnionTypeRef(members, token)
    }
    if (typeRef is PrimitiveTypeRef && typeRef.kind == PrimitiveType.ANY) {
        return PrimitiveTypeRef(PrimitiveType.ANY_NULLABLE, token)
    }
    return UnionTypeRef(listOf(typeRef, PrimitiveTypeRef(PrimitiveType.NULL, token)), token)
}

private fun primitiveTypeNameForSchema(typeRef: TypeRef): String? = when (typeRef) {
    is PrimitiveTypeRef -> primitiveTypeNameForSchema(typeRef.kind)
    else -> null
}

private fun primitiveTypeNameForSchema(kind: PrimitiveType): String? = when (kind) {
    PrimitiveType.TEXT -> "string"
    PrimitiveType.NUMBER -> "number"
    PrimitiveType.BOOLEAN -> "boolean"
    PrimitiveType.NULL -> "null"
    PrimitiveType.ANY -> null
    PrimitiveType.ANY_NULLABLE -> null
}

private fun primitiveTypeName(kind: PrimitiveType): String = when (kind) {
    PrimitiveType.TEXT -> "string"
    PrimitiveType.NUMBER -> "number"
    PrimitiveType.BOOLEAN -> "boolean"
    PrimitiveType.NULL -> "null"
    PrimitiveType.ANY -> "any"
    PrimitiveType.ANY_NULLABLE -> "any"
}

private fun primitiveTypeName(name: String): String? = when (name.lowercase()) {
    "string", "text" -> "string"
    "number" -> "number"
    "boolean" -> "boolean"
    "null" -> "null"
    "any" -> "any"
    else -> null
}

private fun unionMembersToDefs(typeRef: UnionTypeRef): List<String> {
    return typeRef.members.map { member ->
        when (member) {
            is PrimitiveTypeRef -> primitiveTypeName(member.kind)
            is NamedTypeRef -> member.name
            else -> throw JsonSchemaException(
                "Union members must be named or primitive to create a TYPE declaration.",
            )
        }
    }
}

private fun extractRefName(ref: JsonElement): String {
    val raw = (ref as? JsonPrimitive)?.content ?: throw JsonSchemaException("Schema \$ref must be a string")
    val trimmed = raw.removePrefix("#/")
    return trimmed.split('/').last()
}

private fun schemaToken(): Token = Token(
    type = TokenType.EOF,
    lexeme = "<schema>",
    line = 0,
    column = 0,
)
