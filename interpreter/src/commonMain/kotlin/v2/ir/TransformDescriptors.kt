package v2.ir

import v2.TransformDecl
import v2.sema.TransformShapeSynthesizer

public fun buildTransformDescriptors(
    transforms: List<TransformDecl>,
    hostFns: Set<String> = emptySet(),
): Map<String, TransformDescriptor> {
    val synthesizer = TransformShapeSynthesizer(hostFns)
    return transforms.mapNotNull { decl ->
        val name = decl.name ?: return@mapNotNull null
        name to TransformDescriptor(decl, synthesizer.synthesize(decl))
    }.toMap()
}
