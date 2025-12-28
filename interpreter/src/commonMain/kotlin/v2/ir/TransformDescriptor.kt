package v2.ir

import v2.TransformDecl
import v2.contract.TransformContract

public data class TransformDescriptor(
    val decl: TransformDecl,
    val contract: TransformContract,
)
