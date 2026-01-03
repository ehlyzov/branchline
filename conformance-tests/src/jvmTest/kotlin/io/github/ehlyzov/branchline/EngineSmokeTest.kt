package io.github.ehlyzov.branchline

import io.github.ehlyzov.branchline.testutils.EngineTest
import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.assertEquals

class EngineSmokeTest {
    @EngineTest
    fun arithmeticWorks(engine: ExecutionEngine) {
        val result = compileAndRun("OUTPUT { v: 1 + 1 };", engine = engine)
        assertEquals(mapOf("v" to 2), result)
    }
}
