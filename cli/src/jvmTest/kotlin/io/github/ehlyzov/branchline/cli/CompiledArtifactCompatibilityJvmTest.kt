package io.github.ehlyzov.branchline.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class CompiledArtifactCompatibilityJvmTest {
    @Test
    fun commonFixturePreservesExactOldWriterBytes() {
        val original = requireNotNull(javaClass.getResource("/fixtures/compatibility/legacy-hello.blc"))
            .readText(Charsets.UTF_8)
        assertEquals(original, LegacyArtifactFixture.JSON)
    }
}
