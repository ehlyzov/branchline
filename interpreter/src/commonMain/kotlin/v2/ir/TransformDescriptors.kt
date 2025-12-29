package v2.ir

import v2.TransformDecl
import v2.TypeDecl
import v2.contract.TransformContractBuilder
import v2.sema.TypeResolver

public fun buildTransformDescriptors(
    transforms: List<TransformDecl>,
    typeDecls: List<TypeDecl> = emptyList(),
    hostFns: Set<String> = emptySet(),
): Map<String, TransformDescriptor> {
    val typeResolver = TypeResolver(typeDecls)
    val contractBuilder = TransformContractBuilder(typeResolver, hostFns)
    return transforms.mapNotNull { decl ->
        val name = decl.name ?: return@mapNotNull null
        name to TransformDescriptor(decl, contractBuilder.build(decl))
    }.toMap()
}
