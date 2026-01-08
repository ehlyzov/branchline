package io.github.ehlyzov.branchline.ir

public data class ExecutionCaps(
    public val outputAllowed: Boolean,
    public val sharedAllowed: Boolean,
    public val envWriteAllowed: Boolean,
) {
    public companion object {
        public val DEFAULT: ExecutionCaps = ExecutionCaps(
            outputAllowed = true,
            sharedAllowed = true,
            envWriteAllowed = true,
        )
    }
}
