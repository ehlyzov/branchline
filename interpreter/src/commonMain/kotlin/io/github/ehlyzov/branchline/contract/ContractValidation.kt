package io.github.ehlyzov.branchline.contract

public enum class ContractValidationMode {
    OFF,
    WARN,
    STRICT,
    ;

    public companion object {
        public fun parse(value: String?): ContractValidationMode = when (value?.trim()?.lowercase()) {
            null, "", "off" -> OFF
            "warn", "warning" -> WARN
            "strict", "error" -> STRICT
            else -> throw IllegalArgumentException("Unknown contract validation mode '$value'")
        }
    }
}
