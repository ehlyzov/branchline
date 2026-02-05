package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.json.JsonKeyMode
import io.github.ehlyzov.branchline.json.JsonNumberMode
import io.github.ehlyzov.branchline.json.JsonParseOptions
import io.github.ehlyzov.branchline.json.parseJsonObjectInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConformJsonNumericKeysTest {
    @Test
    fun numericKeyModeDefaultsToStrings() {
        val input = parseJsonObjectInput(
            """{"obj":{"1":"a","01":"b","0":"z"}}""",
            JsonParseOptions(numberMode = JsonNumberMode.SAFE),
        )
        val obj = input["obj"] as Map<*, *>
        assertEquals("a", obj["1"])
        assertEquals("b", obj["01"])
        assertEquals("z", obj["0"])
        assertNull(obj[1])
    }

    @Test
    fun numericKeyModeConvertsIntegerKeys() {
        val options = JsonParseOptions(numberMode = JsonNumberMode.SAFE, keyMode = JsonKeyMode.NUMERIC)
        val input = parseJsonObjectInput(
            """{"obj":{"1":"a","01":"b","0":"z","2147483648":"c","9007199254740993":"d"}}""",
            options,
        )
        val obj = input["obj"] as Map<*, *>
        assertEquals("a", obj[1])
        assertNull(obj["1"])
        assertEquals("b", obj["01"])
        assertEquals("z", obj[0])
        assertEquals("c", obj[2147483648L])
        assertEquals("d", obj[9007199254740993L])
    }
}
