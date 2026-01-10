package io.github.ehlyzov.branchline.ir

import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.contract.TransformContract

public data class TransformDescriptor(
    val decl: TransformDecl,
    val contract: TransformContract,
)
