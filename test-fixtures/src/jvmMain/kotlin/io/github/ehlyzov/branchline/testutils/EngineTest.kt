package io.github.ehlyzov.branchline.testutils

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Meta-annotation to run a test for all available [io.github.ehlyzov.branchline.ExecutionEngine]s.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ParameterizedTest
@MethodSource("io.github.ehlyzov.branchline.testutils.Engines#all")
annotation class EngineTest
