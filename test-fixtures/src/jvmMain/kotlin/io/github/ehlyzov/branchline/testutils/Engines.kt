package io.github.ehlyzov.branchline.testutils

import io.github.ehlyzov.branchline.ExecutionEngine
import java.util.stream.Stream

/**
 * Utility method sources for parameterizing tests over available execution engines.
 */
object Engines {
    @JvmStatic
    fun all(): Stream<ExecutionEngine> = Stream.of(*ExecutionEngine.values())
}
