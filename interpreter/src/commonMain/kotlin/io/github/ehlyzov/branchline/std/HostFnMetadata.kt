package io.github.ehlyzov.branchline.std

public data class HostFnMetadata(
    private val sharedAccess: (List<Any?>) -> Boolean,
) {
    public fun requiresSharedAccess(args: List<Any?>): Boolean = sharedAccess(args)

    public companion object {
        public val SHARED_ALWAYS: HostFnMetadata = HostFnMetadata { true }
    }
}
