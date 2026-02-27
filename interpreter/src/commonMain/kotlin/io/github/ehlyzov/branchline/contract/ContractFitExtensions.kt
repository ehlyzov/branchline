package io.github.ehlyzov.branchline.contract

public data class RuntimeContractExample(
    val input: Any?,
    val output: Any?,
    val label: String? = null,
)

public data class RuntimeFitEvidence(
    val ruleId: String,
    val confidenceDelta: Double,
    val notes: String? = null,
)

public data class RuntimeFitResult(
    val contract: AnalysisContract,
    val evidence: List<RuntimeFitEvidence> = emptyList(),
)

public fun interface ContractFitter {
    public fun fit(
        staticContract: AnalysisContract,
        examples: List<RuntimeContractExample>,
    ): RuntimeFitResult
}

public object NoOpContractFitter : ContractFitter {
    override fun fit(
        staticContract: AnalysisContract,
        examples: List<RuntimeContractExample>,
    ): RuntimeFitResult = RuntimeFitResult(staticContract)
}
