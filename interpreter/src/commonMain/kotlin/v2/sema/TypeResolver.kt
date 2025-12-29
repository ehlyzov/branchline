package v2.sema

import v2.ArrayTypeRef
import v2.EnumTypeRef
import v2.NamedTypeRef
import v2.PrimitiveType
import v2.PrimitiveTypeRef
import v2.RecordFieldType
import v2.RecordTypeRef
import v2.Token
import v2.TypeDecl
import v2.TypeKind
import v2.TypeRef
import v2.UnionTypeRef

public class TypeResolver(
    typeDecls: List<TypeDecl>,
) {
    private val typeDeclsByName = typeDecls.associateBy { it.name }
    private val resolvedTypeCache = mutableMapOf<String, TypeRef>()
    private val resolvingTypeNames = mutableSetOf<String>()

    public fun resolve(typeRef: TypeRef): TypeRef = resolveTypeRef(typeRef, resolvingTypeNames)

    private fun resolveTypeRef(typeRef: TypeRef, resolving: MutableSet<String>): TypeRef = when (typeRef) {
        is PrimitiveTypeRef -> typeRef
        is EnumTypeRef -> typeRef
        is ArrayTypeRef -> ArrayTypeRef(
            elementType = resolveTypeRef(typeRef.elementType, resolving),
            token = typeRef.token,
        )
        is RecordTypeRef -> {
            val resolvedFields = typeRef.fields.map { field ->
                RecordFieldType(
                    name = field.name,
                    type = resolveTypeRef(field.type, resolving),
                    optional = field.optional,
                    token = field.token,
                )
            }
            RecordTypeRef(
                fields = resolvedFields,
                token = typeRef.token,
            )
        }
        is UnionTypeRef -> UnionTypeRef(
            members = typeRef.members.map { member -> resolveTypeRef(member, resolving) },
            token = typeRef.token,
        )
        is NamedTypeRef -> resolveNamedType(typeRef, resolving)
    }

    private fun resolveNamedType(typeRef: NamedTypeRef, resolving: MutableSet<String>): TypeRef {
        resolvedTypeCache[typeRef.name]?.let { return it }
        val decl = typeDeclsByName[typeRef.name]
            ?: throw SemanticException("Unknown type '${typeRef.name}'", typeRef.token)
        if (!resolving.add(typeRef.name)) {
            throw SemanticException("Cyclic type reference '${typeRef.name}'", typeRef.token)
        }
        val resolved = resolveTypeDecl(decl, resolving)
        resolving.remove(typeRef.name)
        resolvedTypeCache[typeRef.name] = resolved
        return resolved
    }

    private fun resolveTypeDecl(typeDecl: TypeDecl, resolving: MutableSet<String>): TypeRef = when (typeDecl.kind) {
        TypeKind.ENUM -> EnumTypeRef(typeDecl.defs, typeDecl.token)
        TypeKind.UNION -> {
            val members = typeDecl.defs.map { name ->
                resolveTypeRef(typeRefFromName(name, typeDecl.token), resolving)
            }
            UnionTypeRef(
                members = members,
                token = typeDecl.token,
            )
        }
    }

    private fun typeRefFromName(name: String, token: Token): TypeRef {
        val normalized = name.lowercase()
        return when (normalized) {
            "string", "text" -> PrimitiveTypeRef(PrimitiveType.TEXT, token)
            "number" -> PrimitiveTypeRef(PrimitiveType.NUMBER, token)
            "boolean" -> PrimitiveTypeRef(PrimitiveType.BOOLEAN, token)
            "null" -> PrimitiveTypeRef(PrimitiveType.NULL, token)
            "any" -> PrimitiveTypeRef(PrimitiveType.ANY, token)
            else -> NamedTypeRef(name, token)
        }
    }
}
