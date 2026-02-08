package io.github.ehlyzov.branchline.contract

public data class RuntimeContractExampleV2(
    val input: Any?,
    val output: Any?,
    val label: String? = null,
)

public data class RuntimeFitEvidenceV2(
    val ruleId: String,
    val confidenceDelta: Double,
    val notes: String? = null,
)

public data class RuntimeFitResultV2(
    val contract: TransformContractV2,
    val evidence: List<RuntimeFitEvidenceV2> = emptyList(),
)

public fun interface ContractFitterV2 {
    public fun fit(
        staticContract: TransformContractV2,
        examples: List<RuntimeContractExampleV2>,
    ): RuntimeFitResultV2
}

public object NoOpContractFitterV2 : ContractFitterV2 {
    override fun fit(
        staticContract: TransformContractV2,
        examples: List<RuntimeContractExampleV2>,
    ): RuntimeFitResultV2 = RuntimeFitResultV2(staticContract)
}
