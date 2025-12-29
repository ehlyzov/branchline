package io.branchline.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import v2.ArrayTypeRef
import v2.EnumTypeRef
import v2.Lexer
import v2.NamedTypeRef
import v2.Parser
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.RecordTypeRef
import v2.TypeDecl
import v2.TypeRef
import v2.UnionTypeRef
import v2.schema.JsonSchemaCodec
import v2.schema.JsonSchemaOptions
import v2.sema.TypeResolver

internal data class LoadedTypeDefinition(
    val label: String,
    val typeRef: TypeRef,
)

internal data class ContractDiffSummary(
    val breakingChanges: List<String>,
    val nonBreakingChanges: List<String>,
)

internal fun renderContractDiff(
    oldDefinition: LoadedTypeDefinition,
    newDefinition: LoadedTypeDefinition,
): String {
    val summary = diffTypeRefs(oldDefinition.typeRef, newDefinition.typeRef)
    val lines = mutableListOf<String>()
    lines += "Contract diff: ${oldDefinition.label} -> ${newDefinition.label}"
    if (summary.breakingChanges.isEmpty() && summary.nonBreakingChanges.isEmpty()) {
        lines += "No changes detected."
        return lines.joinToString("\n")
    }
    if (summary.breakingChanges.isNotEmpty()) {
        lines += "Breaking changes:"
        summary.breakingChanges.forEach { change -> lines += "  - $change" }
    }
    if (summary.nonBreakingChanges.isNotEmpty()) {
        lines += "Non-breaking changes:"
        summary.nonBreakingChanges.forEach { change -> lines += "  - $change" }
    }
    return lines.joinToString("\n")
}

internal fun loadTypeDefinition(
    path: String,
    typeName: String?,
): LoadedTypeDefinition {
    val raw = readTextFile(path)
    return if (looksLikeJsonSchema(path, raw)) {
        val typeRef = decodeSchemaTypeRef(raw)
        LoadedTypeDefinition("schema:$path", typeRef)
    } else {
        val program = Parser(Lexer(raw).lex(), raw).parse()
        val typeDecls = program.decls.filterIsInstance<TypeDecl>()
        if (typeDecls.isEmpty()) {
            throw CliException("No TYPE declarations found in $path")
        }
        val selected = selectTypeDecl(typeDecls, typeName)
        val resolver = TypeResolver(typeDecls)
        val resolved = resolver.resolve(NamedTypeRef(selected.name, selected.token))
        LoadedTypeDefinition("type:${selected.name} ($path)", resolved)
    }
}

private fun looksLikeJsonSchema(path: String, raw: String): Boolean {
    if (path.lowercase().endsWith(".json")) return true
    val trimmed = raw.trimStart()
    if (!trimmed.startsWith("{")) return false
    return raw.contains("\"\\$schema\"") ||
        raw.contains("\"\\$defs\"") ||
        raw.contains("\"definitions\"") ||
        raw.contains("\"properties\"") ||
        raw.contains("\"anyOf\"")
}

private fun decodeSchemaTypeRef(rawSchema: String): TypeRef {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement(rawSchema)
    val schema = element as? JsonObject ?: throw CliException("Schema must be a JSON object")
    val defs = resolveSchemaDefinitions(schema)
    val root = resolveSchemaRoot(schema, defs)
    return JsonSchemaCodec.decodeTypeRef(root, JsonSchemaOptions())
}

private fun resolveSchemaDefinitions(schema: JsonObject): Map<String, JsonElement> {
    val defs = schema["\$defs"] as? JsonObject
    if (defs != null) return defs
    val legacy = schema["definitions"] as? JsonObject
    return legacy ?: emptyMap()
}

private fun resolveSchemaRoot(
    schema: JsonObject,
    defs: Map<String, JsonElement>,
): JsonElement {
    val ref = schema["\$ref"] ?: return schema
    val refName = extractRefName(ref)
    return defs[refName] ?: schema
}

private fun extractRefName(ref: JsonElement): String {
    val raw = (ref as? JsonPrimitive)?.content ?: throw CliException("Schema \$ref must be a string")
    val trimmed = raw.removePrefix("#/")
    return trimmed.split('/').last()
}

private fun selectTypeDecl(typeDecls: List<TypeDecl>, typeName: String?): TypeDecl {
    if (typeName != null) {
        return typeDecls.firstOrNull { it.name == typeName }
            ?: throw CliException("TYPE '$typeName' not found in the script")
    }
    if (typeDecls.size == 1) return typeDecls.single()
    val names = typeDecls.joinToString(", ") { decl -> decl.name }
    throw CliException("Multiple TYPE declarations found (${names}); use --type to choose one")
}

private fun diffTypeRefs(oldType: TypeRef, newType: TypeRef): ContractDiffSummary {
    val breaking = mutableListOf<String>()
    val nonBreaking = mutableListOf<String>()
    when {
        oldType is RecordTypeRef && newType is RecordTypeRef -> {
            diffRecordTypes(oldType, newType, breaking, nonBreaking)
        }
        oldType is EnumTypeRef && newType is EnumTypeRef -> {
            diffEnumTypes(oldType, newType, breaking, nonBreaking)
        }
        oldType is UnionTypeRef && newType is UnionTypeRef -> {
            diffUnionTypes(oldType, newType, breaking, nonBreaking)
        }
        else -> {
            val relation = compareTypeRelation(oldType, newType)
            when (relation) {
                TypeRelation.SAME -> Unit
                TypeRelation.WIDENING -> nonBreaking +=
                    "Type widened from ${renderTypeRef(oldType)} to ${renderTypeRef(newType)}"
                TypeRelation.NARROWING -> breaking +=
                    "Type narrowed from ${renderTypeRef(oldType)} to ${renderTypeRef(newType)}"
                TypeRelation.INCOMPATIBLE -> breaking +=
                    "Type changed from ${renderTypeRef(oldType)} to ${renderTypeRef(newType)}"
            }
        }
    }
    return ContractDiffSummary(
        breakingChanges = breaking,
        nonBreakingChanges = nonBreaking,
    )
}

private fun diffRecordTypes(
    oldType: RecordTypeRef,
    newType: RecordTypeRef,
    breaking: MutableList<String>,
    nonBreaking: MutableList<String>,
) {
    val oldFields = oldType.fields.associateBy { it.name }
    val newFields = newType.fields.associateBy { it.name }
    val fieldNames = (oldFields.keys + newFields.keys).toSortedSet()
    for (fieldName in fieldNames) {
        val oldField = oldFields[fieldName]
        val newField = newFields[fieldName]
        when {
            oldField == null && newField != null -> {
                if (newField.optional) {
                    nonBreaking += "Added optional field '$fieldName': ${renderTypeRef(newField.type)}"
                } else {
                    breaking += "Added required field '$fieldName': ${renderTypeRef(newField.type)}"
                }
            }
            oldField != null && newField == null -> {
                breaking += "Removed field '$fieldName'"
            }
            oldField != null && newField != null -> {
                if (oldField.optional != newField.optional) {
                    val change = if (newField.optional) {
                        "Field '$fieldName' became optional"
                    } else {
                        "Field '$fieldName' became required"
                    }
                    if (newField.optional) {
                        nonBreaking += change
                    } else {
                        breaking += change
                    }
                }
                val relation = compareTypeRelation(oldField.type, newField.type)
                when (relation) {
                    TypeRelation.SAME -> Unit
                    TypeRelation.WIDENING -> nonBreaking +=
                        "Field '$fieldName' type widened from ${renderTypeRef(oldField.type)} to ${renderTypeRef(newField.type)}"
                    TypeRelation.NARROWING -> breaking +=
                        "Field '$fieldName' type narrowed from ${renderTypeRef(oldField.type)} to ${renderTypeRef(newField.type)}"
                    TypeRelation.INCOMPATIBLE -> breaking +=
                        "Field '$fieldName' type changed from ${renderTypeRef(oldField.type)} to ${renderTypeRef(newField.type)}"
                }
            }
        }
    }
}

private fun diffEnumTypes(
    oldType: EnumTypeRef,
    newType: EnumTypeRef,
    breaking: MutableList<String>,
    nonBreaking: MutableList<String>,
) {
    val oldValues = oldType.values.toSet()
    val newValues = newType.values.toSet()
    val added = newValues - oldValues
    val removed = oldValues - newValues
    if (added.isNotEmpty()) {
        nonBreaking += "Added enum values: ${added.sorted().joinToString(", ")}"
    }
    if (removed.isNotEmpty()) {
        breaking += "Removed enum values: ${removed.sorted().joinToString(", ")}"
    }
}

private fun diffUnionTypes(
    oldType: UnionTypeRef,
    newType: UnionTypeRef,
    breaking: MutableList<String>,
    nonBreaking: MutableList<String>,
) {
    val oldMembers = flattenUnionMembers(oldType).associateBy { typeSignature(it) }
    val newMembers = flattenUnionMembers(newType).associateBy { typeSignature(it) }
    val added = newMembers.keys - oldMembers.keys
    val removed = oldMembers.keys - newMembers.keys
    if (added.isNotEmpty()) {
        val rendered = added.sorted().joinToString(", ")
        nonBreaking += "Added union members: $rendered"
    }
    if (removed.isNotEmpty()) {
        val rendered = removed.sorted().joinToString(", ")
        breaking += "Removed union members: $rendered"
    }
}

private enum class TypeRelation { SAME, WIDENING, NARROWING, INCOMPATIBLE }

private fun compareTypeRelation(oldType: TypeRef, newType: TypeRef): TypeRelation {
    val oldToNew = isSubtype(oldType, newType)
    val newToOld = isSubtype(newType, oldType)
    return when {
        oldToNew && newToOld -> TypeRelation.SAME
        oldToNew -> TypeRelation.WIDENING
        newToOld -> TypeRelation.NARROWING
        else -> TypeRelation.INCOMPATIBLE
    }
}

private fun isSubtype(subType: TypeRef, superType: TypeRef): Boolean {
    if (superType is UnionTypeRef) {
        val superMembers = superType.members
        return when (subType) {
            is UnionTypeRef -> subType.members.all { member ->
                superMembers.any { superMember -> isSubtype(member, superMember) }
            }
            else -> superMembers.any { superMember -> isSubtype(subType, superMember) }
        }
    }
    if (subType is UnionTypeRef) {
        return subType.members.all { member -> isSubtype(member, superType) }
    }
    if (superType is PrimitiveTypeRef) {
        return when (superType.kind) {
            PrimitiveType.ANY_NULLABLE -> true
            PrimitiveType.ANY -> !containsNull(subType)
            else -> subType is PrimitiveTypeRef && subType.kind == superType.kind
        }
    }
    if (superType is EnumTypeRef) {
        return subType is EnumTypeRef && superType.values.toSet().containsAll(subType.values.toSet())
    }
    return when (superType) {
        is ArrayTypeRef -> subType is ArrayTypeRef && isSubtype(subType.elementType, superType.elementType)
        is RecordTypeRef -> subType is RecordTypeRef && isRecordSubtype(subType, superType)
        else -> false
    }
}

private fun isRecordSubtype(subType: RecordTypeRef, superType: RecordTypeRef): Boolean {
    val subFields = subType.fields.associateBy { it.name }
    for (superField in superType.fields) {
        val subField = subFields[superField.name]
        if (subField == null) {
            if (superField.optional) {
                continue
            }
            return false
        }
        if (!superField.optional && subField.optional) return false
        if (!isSubtype(subField.type, superField.type)) return false
    }
    return true
}

private fun containsNull(typeRef: TypeRef): Boolean = when (typeRef) {
    is PrimitiveTypeRef -> typeRef.kind == PrimitiveType.NULL || typeRef.kind == PrimitiveType.ANY_NULLABLE
    is UnionTypeRef -> typeRef.members.any { member -> containsNull(member) }
    else -> false
}

private fun flattenUnionMembers(typeRef: TypeRef): List<TypeRef> = when (typeRef) {
    is UnionTypeRef -> typeRef.members.flatMap { member -> flattenUnionMembers(member) }
    else -> listOf(typeRef)
}

private fun typeSignature(typeRef: TypeRef): String = when (typeRef) {
    is PrimitiveTypeRef -> renderPrimitive(typeRef.kind)
    is EnumTypeRef -> "enum{${typeRef.values.sorted().joinToString(",")}}"
    is ArrayTypeRef -> "array<${typeSignature(typeRef.elementType)}>"
    is RecordTypeRef -> {
        val fields = typeRef.fields.sortedBy { it.name }.joinToString(",") { field ->
            val optional = if (field.optional) "?" else ""
            "${field.name}$optional:${typeSignature(field.type)}"
        }
        "{$fields}"
    }
    is UnionTypeRef -> typeRef.members.sortedBy { member -> typeSignature(member) }
        .joinToString("|") { member -> typeSignature(member) }
    is NamedTypeRef -> typeRef.name
}

private fun renderTypeRef(typeRef: TypeRef): String = when (typeRef) {
    is PrimitiveTypeRef -> renderPrimitive(typeRef.kind)
    is EnumTypeRef -> "enum{${typeRef.values.joinToString(", ")}}"
    is ArrayTypeRef -> "array<${renderTypeRef(typeRef.elementType)}>"
    is RecordTypeRef -> {
        val fields = typeRef.fields.joinToString(", ") { field ->
            val optional = if (field.optional) "?" else ""
            "${field.name}$optional: ${renderTypeRef(field.type)}"
        }
        "{$fields}"
    }
    is UnionTypeRef -> typeRef.members.joinToString(" | ") { member -> renderTypeRef(member) }
    is NamedTypeRef -> typeRef.name
}

private fun renderPrimitive(kind: PrimitiveType): String = when (kind) {
    PrimitiveType.TEXT -> "text"
    PrimitiveType.NUMBER -> "number"
    PrimitiveType.BOOLEAN -> "boolean"
    PrimitiveType.NULL -> "null"
    PrimitiveType.ANY -> "any"
    PrimitiveType.ANY_NULLABLE -> "any?"
}
