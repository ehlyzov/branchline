package io.github.ehlyzov.branchline.sema

import io.github.ehlyzov.branchline.TransformDecl
import io.github.ehlyzov.branchline.contract.TransformContract
import io.github.ehlyzov.branchline.contract.TransformContractAdapter

public class TransformShapeSynthesizer(
    hostFns: Set<String> = emptySet(),
    binaryTypeEvalRules: List<BinaryTypeEvalRule> = DefaultBinaryTypeEvalRules.rules,
) {
    private val synthesizer = TransformContractSynthesizer(hostFns, binaryTypeEvalRules)

    public fun synthesize(transform: TransformDecl): TransformContract =
        TransformContractAdapter.fromAnalysis(synthesizer.synthesize(transform))
}
