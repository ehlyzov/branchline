package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.TypeDecl
import io.github.ehlyzov.branchline.contract.TransformContractBuilder
import io.github.ehlyzov.branchline.sema.TypeResolver

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
