package io.github.ehlyzov.branchline.vm

/**
 * Unstable VM research preview shipped in the alpha distribution.
 *
 * Plans, generated-code interfaces, diagnostics and execution entry points may
 * change or disappear without source or binary compatibility. They are for
 * explicit experiments; ordinary [VM.execute] remains the supported default.
 * JVM loop compilers use the pinned ASM runtime dependency declared by `:vm`.
 * Kotlin callers must use `@OptIn(ExperimentalVMApi::class)`; Java callers must
 * explicitly accept this contract when using these APIs.
 */
@RequiresOptIn(
    message = "Unstable VM alpha preview: no source or binary compatibility guarantee. Opt in explicitly to run experiments.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
@MustBeDocumented
annotation class ExperimentalVMApi
