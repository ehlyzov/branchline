package io.github.ehlyzov.branchline.contract

public data class ContractSatisfiabilityResultV3(
    val checked: Boolean,
    val satisfiable: Boolean,
    val diagnostics: List<String>,
)

public object ContractSatisfiabilityV3 {
    private val validator = ContractValidatorV3()

    public fun check(contract: TransformContractV3): ContractSatisfiabilityResultV3 {
        val witness = ContractWitnessGeneratorV3.generate(contract)
        val inputViolations = validator.validateInput(
            requirement = contract.input,
            value = witness.input,
            confidenceThreshold = 0.0,
            includeHeuristic = true,
        ).violations
        val outputViolations = validator.validateOutput(
            guarantee = contract.output,
            value = witness.output,
            confidenceThreshold = 0.0,
            includeHeuristic = true,
        ).violations
        val violations = inputViolations + outputViolations
        if (violations.isEmpty()) {
            return ContractSatisfiabilityResultV3(
                checked = true,
                satisfiable = true,
                diagnostics = emptyList(),
            )
        }
        return ContractSatisfiabilityResultV3(
            checked = true,
            satisfiable = false,
            diagnostics = violations.map(::formatContractViolationV2),
        )
    }
}
