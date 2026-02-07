package io.github.ehlyzov.branchline.conformance

import io.github.ehlyzov.branchline.cbor.CborCodecException
import io.github.ehlyzov.branchline.cbor.CborEncodeOptions
import io.github.ehlyzov.branchline.cbor.decodeCborValue
import io.github.ehlyzov.branchline.cbor.encodeCborValue
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ConformCborInternalRepresentationTest {
    @Test
    fun cborEncodingMatchesKnownExtendedSample() {
        val payload = linkedMapOf<Any, Any?>(
            "a" to 1L,
            "b" to blBigIntParse("2"),
            "c" to linkedSetOf(3L, 4L),
            "d" to blBigDecParse("1.5"),
        )

        val encoded = encodeCborValue(payload)
        assertEquals(
            "a46161016162c241026163d9010b8203046164c482200f",
            encoded.toHexString(),
        )
    }

    @Test
    fun cborRoundTripPreservesTypeFamilies() {
        val bigInt = blBigIntParse("9007199254740993")
        val bigDec = blBigDecParse("12.340")
        val payload = linkedMapOf(
            "bigInt" to bigInt,
            "bigDec" to bigDec,
            "set" to linkedSetOf("x", 9L),
        )

        val decoded = decodeCborValue(encodeCborValue(payload)) as Map<*, *>
        assertEquals(bigInt.toString(), (decoded["bigInt"] as BLBigInt).toString())
        assertEquals(0, (decoded["bigDec"] as BLBigDec).compareTo(bigDec))
        assertTrue(decoded["set"] is Set<*>)
    }

    @Test
    fun cborRejectsNonTextOrIntegerMapKeys() {
        assertFailsWith<CborCodecException> {
            encodeCborValue(mapOf(false to "x"))
        }
    }

    @Test
    fun deterministicCborOutputIsByteStable() {
        val first = linkedMapOf<Any, Any?>(
            "z" to 1L,
            "a" to 2L,
            3 to "three",
        )
        val second = linkedMapOf<Any, Any?>(
            3 to "three",
            "a" to 2L,
            "z" to 1L,
        )

        assertNotEquals(
            encodeCborValue(first).toHexString(),
            encodeCborValue(second).toHexString(),
        )

        val deterministic = CborEncodeOptions(deterministic = true)
        assertEquals(
            encodeCborValue(first, deterministic).toHexString(),
            encodeCborValue(second, deterministic).toHexString(),
        )
    }
}

private fun ByteArray.toHexString(): String {
    val chars = CharArray(this.size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xFF
        chars[index * 2] = HEX_DIGITS[value ushr 4]
        chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return chars.concatToString()
}

private val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()
