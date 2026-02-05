package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.json.JsonInputException
import io.github.ehlyzov.branchline.json.parseJsonObjectInput
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConformJsonDuplicateKeysTest {

    @Test
    fun duplicate_keys_error_by_default() {
        assertFailsWith<JsonInputException> {
            parseJsonObjectInput("""{"a": 1, "a": 2}""")
        }
    }
}
