package io.github.ehlyzov.branchline.std

import java.util.ServiceLoader

actual fun loadStdModules(registry: StdRegistry) {
    ServiceLoader.load(StdModule::class.java).forEach { it.register(registry) }
}

