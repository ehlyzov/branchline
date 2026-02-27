package io.github.ehlyzov.branchline.contract

public data class ContractSatisfiabilityResult(
    val checked: Boolean,
    val satisfiable: Boolean,
    val diagnostics: List<String>,
)

public object ContractSatisfiability {
    private val validator = ContractValidator()

    public fun check(contract: TransformContract): ContractSatisfiabilityResult {
        val witness = ContractWitnessGenerator.generate(contract)
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
            return ContractSatisfiabilityResult(
                checked = true,
                satisfiable = true,
                diagnostics = emptyList(),
            )
        }
        return ContractSatisfiabilityResult(
            checked = true,
            satisfiable = false,
            diagnostics = violations.map(::formatContractViolation),
        )
    }
}
