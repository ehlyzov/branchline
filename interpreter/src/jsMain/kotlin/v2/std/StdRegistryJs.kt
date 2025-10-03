package v2.std

actual fun loadStdModules(registry: StdRegistry) {
    listOf(
        StdCoreModule(),
        StdArraysModule(),
        StdAggModule(),
        StdStringsModule(),
        StdTimeModule(),
        StdHofModule(),
        StdDebugModule(),
        StdSharedModule(),
    ).forEach { it.register(registry) }
}
