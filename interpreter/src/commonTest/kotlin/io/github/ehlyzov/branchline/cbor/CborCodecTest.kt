package io.github.ehlyzov.branchline.cbor

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CborCodecTest {
    @Test
    fun roundTripPreservesExtendedValueTypes() {
        val bigInt = blBigIntParse("9007199254740993")
        val bigDec = blBigDecParse("1234.500")
        val encoded = encodeCborValue(
            linkedMapOf(
                "null" to null,
                "bool" to true,
                "text" to "hello",
                "bytes" to byteArrayOf(0, 1, 2, (-1).toByte()),
                "int" to 42L,
                "float" to 1.25,
                "bigInt" to bigInt,
                "bigDec" to bigDec,
                "array" to listOf(1L, "x", false),
                "set" to linkedSetOf("a", 2L),
                5 to "five",
            ),
        )

        val decoded = decodeCborValue(encoded) as Map<*, *>
        assertEquals("five", decoded[5])
        assertEquals(42L, decoded["int"])
        assertEquals(1.25, decoded["float"])
        assertContentEquals(byteArrayOf(0, 1, 2, (-1).toByte()), decoded["bytes"] as ByteArray)

        val decodedBigInt = decoded["bigInt"] as BLBigInt
        assertEquals(bigInt.toString(), decodedBigInt.toString())

        val decodedBigDec = decoded["bigDec"] as BLBigDec
        assertEquals(0, decodedBigDec.compareTo(bigDec))

        val decodedSet = decoded["set"] as Set<*>
        assertTrue(decodedSet.contains("a"))
        assertTrue(decodedSet.contains(2L))
    }

    @Test
    fun bigNumberTypesUseDedicatedTags() {
        val positive = blBigIntParse("123456789012345678")
        val negative = blBigIntParse("-7")
        val decimal = blBigDecParse("1.5")

        val positiveBytes = encodeCborValue(positive)
        val negativeBytes = encodeCborValue(negative)
        val decimalBytes = encodeCborValue(decimal)

        assertEquals(0xC2.toByte(), positiveBytes[0])
        assertEquals(0xC3.toByte(), negativeBytes[0])
        assertEquals(0xC4.toByte(), decimalBytes[0])

        assertEquals(positive.toString(), (decodeCborValue(positiveBytes) as BLBigInt).toString())
        assertEquals(negative.toString(), (decodeCborValue(negativeBytes) as BLBigInt).toString())
        assertEquals("1.5", (decodeCborValue(decimalBytes) as BLBigDec).toPlainString())
    }

    @Test
    fun setUsesBranchlineTag() {
        val encoded = encodeCborValue(linkedSetOf(3L, 1L))
        assertEquals(0xD9.toByte(), encoded[0])
        assertEquals(0x01.toByte(), encoded[1])
        assertEquals(0x0B.toByte(), encoded[2])

        val decoded = decodeCborValue(encoded) as Set<*>
        assertEquals(setOf(3L, 1L), decoded)
    }

    @Test
    fun rejectsUnsupportedMapKeysOnEncode() {
        assertFailsWith<CborCodecException> {
            encodeCborValue(mapOf(true to 1))
        }
    }

    @Test
    fun rejectsUnsupportedMapKeysOnDecode() {
        val invalid = byteArrayOf(0xA1.toByte(), 0xF5.toByte(), 0x01)
        assertFailsWith<CborCodecException> {
            decodeCborValue(invalid)
        }
    }

    @Test
    fun deterministicEncodingStabilizesMapOrdering() {
        val first = linkedMapOf<Any, Any?>(
            "b" to 2L,
            "a" to 1L,
            10 to "ten",
            2 to "two",
        )
        val second = linkedMapOf<Any, Any?>(
            2 to "two",
            10 to "ten",
            "a" to 1L,
            "b" to 2L,
        )

        val firstFast = encodeCborValue(first)
        val secondFast = encodeCborValue(second)
        assertNotEquals(firstFast.toList(), secondFast.toList())

        val deterministic = CborEncodeOptions(deterministic = true)
        val firstDeterministic = encodeCborValue(first, deterministic)
        val secondDeterministic = encodeCborValue(second, deterministic)
        assertContentEquals(firstDeterministic, secondDeterministic)
    }

    @Test
    fun deterministicEncodingStabilizesSetOrdering() {
        val first = linkedSetOf<Any?>(
            "z",
            2L,
            listOf(1L, "a"),
            mapOf("k" to 1L),
        )
        val second = linkedSetOf<Any?>(
            mapOf("k" to 1L),
            listOf(1L, "a"),
            2L,
            "z",
        )

        val deterministic = CborEncodeOptions(deterministic = true)
        val firstDeterministic = encodeCborValue(first, deterministic)
        val secondDeterministic = encodeCborValue(second, deterministic)
        assertContentEquals(firstDeterministic, secondDeterministic)
    }
}
