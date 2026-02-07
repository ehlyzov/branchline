package io.github.ehlyzov.branchline.cbor

import io.github.ehlyzov.branchline.Dec
import io.github.ehlyzov.branchline.I32
import io.github.ehlyzov.branchline.I64
import io.github.ehlyzov.branchline.IBig
import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigDecParse
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntParse
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.div
import io.github.ehlyzov.branchline.runtime.bignum.minus
import io.github.ehlyzov.branchline.runtime.bignum.plus
import io.github.ehlyzov.branchline.runtime.bignum.rem
import io.github.ehlyzov.branchline.runtime.bignum.signum
import io.github.ehlyzov.branchline.runtime.bignum.times
import io.github.ehlyzov.branchline.runtime.bignum.toLong
import io.github.ehlyzov.branchline.runtime.bignum.toPlainString
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus
import kotlin.math.pow

public class CborCodecException(message: String) : IllegalArgumentException(message)

public data class CborEncodeOptions(
    val deterministic: Boolean = false,
)

public fun encodeCborValue(
    value: Any?,
    options: CborEncodeOptions = CborEncodeOptions(),
): ByteArray = CborEncoder(options).encode(value)

public fun decodeCborValue(bytes: ByteArray): Any? = CborDecoder(bytes).decode()

private const val MAJOR_UNSIGNED: Int = 0
private const val MAJOR_NEGATIVE: Int = 1
private const val MAJOR_BYTES: Int = 2
private const val MAJOR_TEXT: Int = 3
private const val MAJOR_ARRAY: Int = 4
private const val MAJOR_MAP: Int = 5
private const val MAJOR_TAG: Int = 6
private const val MAJOR_SIMPLE: Int = 7

private const val AI_FALSE: Int = 20
private const val AI_TRUE: Int = 21
private const val AI_NULL: Int = 22
private const val AI_FLOAT16: Int = 25
private const val AI_FLOAT32: Int = 26
private const val AI_FLOAT64: Int = 27
private const val AI_BREAK: Int = 31

private const val TAG_POS_BIGNUM: ULong = 2u
private const val TAG_NEG_BIGNUM: ULong = 3u
private const val TAG_DECIMAL_FRACTION: ULong = 4u
private const val TAG_BRANCHLINE_SET: ULong = 267u

private val BIG_INT_ZERO: BLBigInt = blBigIntOfLong(0)
private val BIG_INT_ONE: BLBigInt = blBigIntOfLong(1)
private val BIG_INT_256: BLBigInt = blBigIntOfLong(256)
private val BIG_INT_LONG_MIN: BLBigInt = blBigIntOfLong(Long.MIN_VALUE)
private val BIG_INT_LONG_MAX: BLBigInt = blBigIntOfLong(Long.MAX_VALUE)
private val LONG_MAX_UNSIGNED: ULong = Long.MAX_VALUE.toULong()
private const val TWO_POW_8: Double = 256.0
private const val TWO_POW_16: Double = 65536.0
private const val TWO_POW_24: Double = 16777216.0
private const val TWO_POW_32: Double = 4294967296.0
private const val TWO_POW_40: Double = 1099511627776.0
private const val TWO_POW_48: Double = 281474976710656.0
private const val TWO_POW_52: Double = 4503599627370496.0
private const val TWO_POW_NEG_1022: Double = 2.2250738585072014e-308
private val DETERMINISTIC_CBOR_OPTIONS: CborEncodeOptions = CborEncodeOptions(deterministic = true)

private class CborEncoder(
    private val options: CborEncodeOptions = CborEncodeOptions(),
) {
    private val out: ArrayList<Byte> = ArrayList()

    fun encode(value: Any?): ByteArray {
        writeValue(value)
        return out.toByteArray()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun writeValue(value: Any?) {
        when (value) {
            null -> writeSimple(AI_NULL)
            is Boolean -> writeSimple(if (value) AI_TRUE else AI_FALSE)
            is String -> writeText(value)
            is ByteArray -> writeByteString(value)
            is BLBigInt -> writeBigInt(value)
            is BLBigDec -> writeBigDec(value)
            is IBig -> writeBigInt(value.v)
            is Dec -> writeBigDec(value.v)
            is Float -> writeFallbackNumber(value)
            is Double -> writeFallbackNumber(value)
            is I32 -> writeSigned(value.v.toLong())
            is I64 -> writeSigned(value.v)
            is Byte -> writeSigned(value.toLong())
            is Short -> writeSigned(value.toLong())
            is Int -> writeSigned(value.toLong())
            is Long -> writeSigned(value)
            is UInt -> writeUnsigned(value.toLong().toULong())
            is UByte -> writeUnsigned(value.toLong().toULong())
            is UShort -> writeUnsigned(value.toLong().toULong())
            is ULong -> {
                if (value <= LONG_MAX_UNSIGNED) {
                    writeUnsigned(value)
                } else {
                    writeBigInt(blBigIntParse(value.toString()))
                }
            }
            is Number -> writeFallbackNumber(value)
            is Set<*> -> writeSet(value)
            is Map<*, *> -> writeMap(value)
            is Iterable<*> -> writeArray(value.toList())
            is Array<*> -> writeArray(value.toList())
            is Sequence<*> -> writeArray(value.toList())
            else -> throw CborCodecException("Unsupported CBOR value type: ${value::class.simpleName}")
        }
    }

    private fun writeFallbackNumber(value: Number) {
        val asDouble = value.toDouble()
        if (!asDouble.isFinite()) {
            throw CborCodecException("CBOR cannot encode NaN or Infinity")
        }
        val asLong = asDouble.toLong()
        if (asDouble == asLong.toDouble()) {
            writeSigned(asLong)
        } else {
            writeFloat64(asDouble)
        }
    }

    private fun writeByteString(value: ByteArray) {
        writeTypeAndArgument(MAJOR_BYTES, value.size.toULong())
        writeBytes(value)
    }

    private fun writeText(value: String) {
        val bytes = value.encodeToByteArray()
        writeTypeAndArgument(MAJOR_TEXT, bytes.size.toULong())
        writeBytes(bytes)
    }

    private fun writeArray(items: List<*>) {
        writeTypeAndArgument(MAJOR_ARRAY, items.size.toULong())
        for (item in items) {
            writeValue(item)
        }
    }

    private fun writeSet(values: Set<*>) {
        writeTag(TAG_BRANCHLINE_SET)
        val items = if (options.deterministic) {
            values.map { element ->
                DeterministicValue(element, deterministicBytesForValue(element))
            }.sortedWith { left, right ->
                compareDeterministicBytes(left.bytes, right.bytes)
            }.map { it.value }
        } else {
            values.toList()
        }

        writeTypeAndArgument(MAJOR_ARRAY, items.size.toULong())
        for (value in items) {
            writeValue(value)
        }
    }

    private fun writeMap(values: Map<*, *>) {
        val entries = values.entries.map { entry ->
            val normalizedKey = normalizeMapKey(entry.key)
            if (options.deterministic) {
                DeterministicMapEntry(
                    key = normalizedKey,
                    value = entry.value,
                    keyBytes = deterministicBytesForValue(normalizedKey),
                )
            } else {
                DeterministicMapEntry(
                    key = normalizedKey,
                    value = entry.value,
                    keyBytes = null,
                )
            }
        }

        val orderedEntries = if (options.deterministic) {
            entries.sortedWith { left, right ->
                compareDeterministicBytes(left.keyBytes!!, right.keyBytes!!)
            }
        } else {
            entries
        }

        writeTypeAndArgument(MAJOR_MAP, orderedEntries.size.toULong())
        for (entry in orderedEntries) {
            writeNormalizedMapKey(entry.key)
            writeValue(entry.value)
        }
    }

    private fun normalizeMapKey(value: Any?): Any = when (value) {
        is String -> value
        is I32 -> value.v.toLong()
        is I64 -> value.v
        is IBig -> value.v
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        is UByte -> value.toLong()
        is UShort -> value.toLong()
        is UInt -> value.toLong()
        is ULong -> {
            if (value <= LONG_MAX_UNSIGNED) {
                value.toLong()
            } else {
                blBigIntParse(value.toString())
            }
        }
        is BLBigInt -> value
        else -> throw CborCodecException(
            "CBOR map key must be text or integer, got ${value?.let { it::class.simpleName } ?: "null"}",
        )
    }

    private fun writeNormalizedMapKey(value: Any) {
        when (value) {
            is String -> writeText(value)
            is Long -> writeSigned(value)
            is BLBigInt -> writeBigInt(value)
            else -> throw CborCodecException("CBOR map key normalization produced unsupported type")
        }
    }

    private fun deterministicBytesForValue(value: Any?): ByteArray =
        CborEncoder(DETERMINISTIC_CBOR_OPTIONS).encode(value)

    private fun writeBigInt(value: BLBigInt) {
        if (value.signum() >= 0) {
            writeTag(TAG_POS_BIGNUM)
            writeByteString(bigIntToUnsignedBytes(value))
            return
        }
        writeTag(TAG_NEG_BIGNUM)
        val magnitude = -(value + BIG_INT_ONE)
        writeByteString(bigIntToUnsignedBytes(magnitude))
    }

    private fun writeBigDec(value: BLBigDec) {
        val (exponent, mantissa) = decimalToExponentAndMantissa(value.toPlainString())
        writeTag(TAG_DECIMAL_FRACTION)
        writeTypeAndArgument(MAJOR_ARRAY, 2u)
        writeSigned(exponent)
        writeIntegerValue(mantissa)
    }

    private fun writeIntegerValue(value: BLBigInt) {
        if (value >= BIG_INT_LONG_MIN && value <= BIG_INT_LONG_MAX) {
            writeSigned(value.toLong())
        } else {
            writeBigInt(value)
        }
    }

    private fun writeSigned(value: Long) {
        if (value >= 0) {
            writeUnsigned(value.toULong())
            return
        }
        val encoded = (-1L - value).toULong()
        writeTypeAndArgument(MAJOR_NEGATIVE, encoded)
    }

    private fun writeUnsigned(value: ULong) {
        writeTypeAndArgument(MAJOR_UNSIGNED, value)
    }

    private fun writeFloat64(value: Double) {
        if (!value.isFinite()) {
            throw CborCodecException("CBOR cannot encode NaN or Infinity")
        }
        writeInitial(MAJOR_SIMPLE, AI_FLOAT64)
        writeInt64(value.toBits())
    }

    private fun writeTag(tag: ULong) {
        writeTypeAndArgument(MAJOR_TAG, tag)
    }

    private fun writeSimple(ai: Int) {
        writeInitial(MAJOR_SIMPLE, ai)
    }

    private fun writeTypeAndArgument(major: Int, argument: ULong) {
        when {
            argument <= 23u -> writeInitial(major, argument.toInt())
            argument <= UByte.MAX_VALUE.toULong() -> {
                writeInitial(major, 24)
                writeByte(argument.toInt())
            }
            argument <= UShort.MAX_VALUE.toULong() -> {
                writeInitial(major, 25)
                writeUInt16(argument.toInt())
            }
            argument <= UInt.MAX_VALUE.toULong() -> {
                writeInitial(major, 26)
                writeUInt32(argument.toUInt())
            }
            else -> {
                writeInitial(major, 27)
                writeUInt64(argument)
            }
        }
    }

    private fun writeInitial(major: Int, ai: Int) {
        writeByte((major shl 5) or (ai and 0x1F))
    }

    private fun writeUInt16(value: Int) {
        writeByte((value shr 8) and 0xFF)
        writeByte(value and 0xFF)
    }

    private fun writeUInt32(value: UInt) {
        writeByte(((value shr 24) and 0xFFu).toInt())
        writeByte(((value shr 16) and 0xFFu).toInt())
        writeByte(((value shr 8) and 0xFFu).toInt())
        writeByte((value and 0xFFu).toInt())
    }

    private fun writeUInt64(value: ULong) {
        writeByte(((value shr 56) and 0xFFu).toInt())
        writeByte(((value shr 48) and 0xFFu).toInt())
        writeByte(((value shr 40) and 0xFFu).toInt())
        writeByte(((value shr 32) and 0xFFu).toInt())
        writeByte(((value shr 24) and 0xFFu).toInt())
        writeByte(((value shr 16) and 0xFFu).toInt())
        writeByte(((value shr 8) and 0xFFu).toInt())
        writeByte((value and 0xFFu).toInt())
    }

    private fun writeInt64(value: Long) {
        writeByte(((value ushr 56) and 0xFF).toInt())
        writeByte(((value ushr 48) and 0xFF).toInt())
        writeByte(((value ushr 40) and 0xFF).toInt())
        writeByte(((value ushr 32) and 0xFF).toInt())
        writeByte(((value ushr 24) and 0xFF).toInt())
        writeByte(((value ushr 16) and 0xFF).toInt())
        writeByte(((value ushr 8) and 0xFF).toInt())
        writeByte((value and 0xFF).toInt())
    }

    private fun writeBytes(bytes: ByteArray) {
        for (byte in bytes) {
            out.add(byte)
        }
    }

    private fun writeByte(value: Int) {
        out.add((value and 0xFF).toByte())
    }
}

private data class DeterministicValue(
    val value: Any?,
    val bytes: ByteArray,
)

private data class DeterministicMapEntry(
    val key: Any,
    val value: Any?,
    val keyBytes: ByteArray?,
)

private fun compareDeterministicBytes(left: ByteArray, right: ByteArray): Int {
    val lengthComparison = left.size.compareTo(right.size)
    if (lengthComparison != 0) return lengthComparison

    for (index in left.indices) {
        val a = left[index].toInt() and 0xFF
        val b = right[index].toInt() and 0xFF
        if (a != b) return a.compareTo(b)
    }
    return 0
}

private class CborDecoder(
    private val bytes: ByteArray,
) {
    private var index: Int = 0

    fun decode(): Any? {
        val value = readValue()
        if (index != bytes.size) {
            throw CborCodecException("Trailing bytes after top-level CBOR value")
        }
        return value
    }

    @Suppress("CyclomaticComplexMethod")
    private fun readValue(): Any? {
        val initial = readUnsignedByte()
        val major = initial ushr 5
        val ai = initial and 0x1F
        return when (major) {
            MAJOR_UNSIGNED -> decodeUnsigned(ai)
            MAJOR_NEGATIVE -> decodeNegative(ai)
            MAJOR_BYTES -> readByteString(ai)
            MAJOR_TEXT -> readText(ai)
            MAJOR_ARRAY -> readArray(ai)
            MAJOR_MAP -> readMap(ai)
            MAJOR_TAG -> readTag(ai)
            MAJOR_SIMPLE -> readSimple(ai)
            else -> throw CborCodecException("Unsupported CBOR major type: $major")
        }
    }

    private fun decodeUnsigned(ai: Int): Any {
        val value = readArgument(ai)
        if (value > LONG_MAX_UNSIGNED) {
            throw CborCodecException("Unsigned integer exceeds supported range")
        }
        return value.toLong()
    }

    private fun decodeNegative(ai: Int): Any {
        val encoded = readArgument(ai)
        if (encoded > LONG_MAX_UNSIGNED) {
            throw CborCodecException("Negative integer exceeds supported range")
        }
        return -1L - encoded.toLong()
    }

    private fun readByteString(ai: Int): ByteArray {
        val len = readLength(ai)
        return readBytes(len)
    }

    private fun readText(ai: Int): String {
        val len = readLength(ai)
        val data = readBytes(len)
        return data.decodeToString()
    }

    private fun readArray(ai: Int): List<Any?> {
        val len = readLength(ai)
        val out = ArrayList<Any?>(len)
        repeat(len) {
            out.add(readValue())
        }
        return out
    }

    private fun readMap(ai: Int): Map<Any, Any?> {
        val len = readLength(ai)
        val out = LinkedHashMap<Any, Any?>(len)
        repeat(len) {
            val rawKey = readValue()
            val key = normalizeDecodedMapKey(rawKey)
            if (out.containsKey(key)) {
                throw CborCodecException("Duplicate CBOR map key: $key")
            }
            out[key] = readValue()
        }
        return out
    }

    private fun readTag(ai: Int): Any {
        val tag = readArgument(ai)
        val taggedValue = readValue()
        return when (tag) {
            TAG_POS_BIGNUM -> decodeTaggedBigInt(taggedValue, negative = false)
            TAG_NEG_BIGNUM -> decodeTaggedBigInt(taggedValue, negative = true)
            TAG_DECIMAL_FRACTION -> decodeTaggedBigDec(taggedValue)
            TAG_BRANCHLINE_SET -> decodeTaggedSet(taggedValue)
            else -> throw CborCodecException("Unsupported CBOR tag: $tag")
        }
    }

    private fun readSimple(ai: Int): Any? = when (ai) {
        AI_FALSE -> false
        AI_TRUE -> true
        AI_NULL -> null
        AI_FLOAT16 -> decodeFloat16(readUInt16().toInt())
        AI_FLOAT32 -> Float.fromBits(readUInt32().toInt()).toDouble()
        AI_FLOAT64 -> readFloat64()
        in 0..23 -> ai
        24 -> readUnsignedByte()
        AI_BREAK -> throw CborCodecException("Unexpected CBOR break code")
        else -> throw CborCodecException("Unsupported CBOR simple value: $ai")
    }

    private fun decodeTaggedBigInt(taggedValue: Any?, negative: Boolean): BLBigInt {
        val magnitudeBytes = taggedValue as? ByteArray
            ?: throw CborCodecException("BigInt tag payload must be a byte string")
        val magnitude = unsignedBytesToBigInt(magnitudeBytes)
        if (!negative) return magnitude
        return -(magnitude + BIG_INT_ONE)
    }

    private fun decodeTaggedBigDec(taggedValue: Any?): BLBigDec {
        val pair = taggedValue as? List<*>
            ?: throw CborCodecException("BigDecimal tag payload must be [exponent, mantissa]")
        if (pair.size != 2) {
            throw CborCodecException("BigDecimal tag payload must contain exactly 2 items")
        }
        val exponent = integerToLong(pair[0], "BigDecimal exponent")
        val mantissa = integerToBigInt(pair[1], "BigDecimal mantissa")
        val decimalText = decimalStringFromExponentAndMantissa(exponent, mantissa)
        return blBigDecParse(decimalText)
    }

    private fun decodeTaggedSet(taggedValue: Any?): Set<Any?> {
        val items = taggedValue as? List<*>
            ?: throw CborCodecException("Set tag payload must be an array")
        val out = LinkedHashSet<Any?>()
        for (item in items) {
            if (!out.add(item)) {
                throw CborCodecException("Set tag payload contains duplicate element: $item")
            }
        }
        return out
    }

    private fun normalizeDecodedMapKey(value: Any?): Any = when (value) {
        is String -> value
        is Long -> {
            if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else value
        }
        is BLBigInt -> normalizeBigIntKey(value)
        is Int -> value
        else -> throw CborCodecException(
            "CBOR map key must decode to text or integer, got ${value?.let { it::class.simpleName } ?: "null"}",
        )
    }

    private fun normalizeBigIntKey(value: BLBigInt): Any {
        if (value >= blBigIntOfLong(Int.MIN_VALUE.toLong()) && value <= blBigIntOfLong(Int.MAX_VALUE.toLong())) {
            return value.toLong().toInt()
        }
        if (value >= BIG_INT_LONG_MIN && value <= BIG_INT_LONG_MAX) {
            return value.toLong()
        }
        return value
    }

    private fun readArgument(ai: Int): ULong = when (ai) {
        in 0..23 -> ai.toULong()
        24 -> readUnsignedByte().toULong()
        25 -> readUInt16()
        26 -> readUInt32().toULong()
        27 -> readUInt64()
        else -> throw CborCodecException("Unsupported CBOR additional info: $ai")
    }

    private fun readLength(ai: Int): Int {
        if (ai == AI_BREAK) {
            throw CborCodecException("Indefinite-length CBOR items are not supported")
        }
        val raw = readArgument(ai)
        if (raw > Int.MAX_VALUE.toULong()) {
            throw CborCodecException("CBOR item is too large")
        }
        return raw.toInt()
    }

    private fun readBytes(length: Int): ByteArray {
        if (length < 0 || index + length > bytes.size) {
            throw CborCodecException("Unexpected end of CBOR input")
        }
        val out = bytes.copyOfRange(index, index + length)
        index += length
        return out
    }

    private fun readUInt16(): ULong {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return ((b0 shl 8) or b1).toULong()
    }

    private fun readUInt32(): UInt {
        val b0 = readUnsignedByte().toUInt()
        val b1 = readUnsignedByte().toUInt()
        val b2 = readUnsignedByte().toUInt()
        val b3 = readUnsignedByte().toUInt()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readUInt64(): ULong {
        val b0 = readUnsignedByte().toULong()
        val b1 = readUnsignedByte().toULong()
        val b2 = readUnsignedByte().toULong()
        val b3 = readUnsignedByte().toULong()
        val b4 = readUnsignedByte().toULong()
        val b5 = readUnsignedByte().toULong()
        val b6 = readUnsignedByte().toULong()
        val b7 = readUnsignedByte().toULong()
        return (b0 shl 56) or
            (b1 shl 48) or
            (b2 shl 40) or
            (b3 shl 32) or
            (b4 shl 24) or
            (b5 shl 16) or
            (b6 shl 8) or
            b7
    }

    private fun readFloat64(): Double {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        val b4 = readUnsignedByte()
        val b5 = readUnsignedByte()
        val b6 = readUnsignedByte()
        val b7 = readUnsignedByte()

        val sign = if ((b0 and 0x80) == 0) 1.0 else -1.0
        val exponent = ((b0 and 0x7F) shl 4) or (b1 shr 4)
        val mantissa =
            ((b1 and 0x0F) * TWO_POW_48) +
                (b2 * TWO_POW_40) +
                (b3 * TWO_POW_32) +
                (b4 * TWO_POW_24) +
                (b5 * TWO_POW_16) +
                (b6 * TWO_POW_8) +
                b7.toDouble()

        return when (exponent) {
            0 -> {
                if (mantissa == 0.0) {
                    sign * 0.0
                } else {
                    sign * (mantissa / TWO_POW_52) * TWO_POW_NEG_1022
                }
            }
            0x7FF -> if (mantissa == 0.0) sign * Double.POSITIVE_INFINITY else Double.NaN
            else -> sign * (1.0 + mantissa / TWO_POW_52) * 2.0.pow((exponent - 1023).toDouble())
        }
    }

    private fun readUnsignedByte(): Int {
        if (index >= bytes.size) {
            throw CborCodecException("Unexpected end of CBOR input")
        }
        return bytes[index++].toInt() and 0xFF
    }
}

private fun integerToLong(value: Any?, label: String): Long = when (value) {
    is Int -> value.toLong()
    is Long -> value
    is BLBigInt -> {
        if (value < BIG_INT_LONG_MIN || value > BIG_INT_LONG_MAX) {
            throw CborCodecException("$label is outside supported Long range")
        }
        value.toLong()
    }
    else -> throw CborCodecException("$label must be an integer, got ${value?.let { it::class.simpleName } ?: "null"}")
}

private fun integerToBigInt(value: Any?, label: String): BLBigInt = when (value) {
    is Int -> blBigIntOfLong(value.toLong())
    is Long -> blBigIntOfLong(value)
    is BLBigInt -> value
    else -> throw CborCodecException("$label must be an integer, got ${value?.let { it::class.simpleName } ?: "null"}")
}

private fun bigIntToUnsignedBytes(value: BLBigInt): ByteArray {
    if (value.signum() < 0) {
        throw CborCodecException("BigInt magnitude must be non-negative")
    }
    if (value.signum() == 0) {
        return byteArrayOf()
    }
    var remaining = value
    val reversed = ArrayList<Byte>()
    while (remaining.signum() != 0) {
        val quotient = remaining / BIG_INT_256
        val remainder = remaining % BIG_INT_256
        reversed.add((remainder.toLong() and 0xFF).toByte())
        remaining = quotient
    }
    reversed.reverse()
    return reversed.toByteArray()
}

private fun unsignedBytesToBigInt(bytes: ByteArray): BLBigInt {
    var result = BIG_INT_ZERO
    for (byte in bytes) {
        val value = blBigIntOfLong((byte.toInt() and 0xFF).toLong())
        result = (result * BIG_INT_256) + value
    }
    return result
}

private fun decimalToExponentAndMantissa(decimalText: String): Pair<Long, BLBigInt> {
    val text = decimalText.trim()
    if (text.isEmpty()) {
        throw CborCodecException("Cannot encode empty decimal text")
    }
    val negative = text.startsWith('-')
    val unsigned = if (text.startsWith('-') || text.startsWith('+')) text.substring(1) else text
    if (unsigned.isEmpty()) {
        throw CborCodecException("Invalid decimal text '$decimalText'")
    }

    val dotIndex = unsigned.indexOf('.')
    var exponent = 0L
    val digitsWithZeros = if (dotIndex >= 0) {
        exponent = -(unsigned.length - dotIndex - 1).toLong()
        unsigned.removeRange(dotIndex, dotIndex + 1)
    } else {
        unsigned
    }

    var digits = digitsWithZeros.trimStart('0')
    if (digits.isEmpty()) {
        return 0L to BIG_INT_ZERO
    }

    var strippedTrailing = 0
    while (digits.endsWith('0')) {
        digits = digits.dropLast(1)
        strippedTrailing += 1
    }
    exponent += strippedTrailing.toLong()

    val signedDigits = if (negative) "-$digits" else digits
    return exponent to blBigIntParse(signedDigits)
}

private fun decimalStringFromExponentAndMantissa(exponent: Long, mantissa: BLBigInt): String {
    if (mantissa.signum() == 0) return "0"

    val negative = mantissa.signum() < 0
    val unsignedDigits = if (negative) (-mantissa).toString() else mantissa.toString()
    if (exponent < Int.MIN_VALUE.toLong() || exponent > Int.MAX_VALUE.toLong()) {
        throw CborCodecException("BigDecimal exponent is outside supported range")
    }
    val exp = exponent.toInt()

    val rendered = when {
        exp >= 0 -> unsignedDigits + "0".repeat(exp)
        else -> {
            val pointPos = unsignedDigits.length + exp
            if (pointPos > 0) {
                unsignedDigits.substring(0, pointPos) + "." + unsignedDigits.substring(pointPos)
            } else {
                "0." + "0".repeat(-pointPos) + unsignedDigits
            }
        }
    }
    return if (negative) "-$rendered" else rendered
}

private fun decodeFloat16(bits: Int): Double {
    val sign = if ((bits and 0x8000) != 0) -1.0 else 1.0
    val exponent = (bits ushr 10) and 0x1F
    val fraction = bits and 0x03FF
    return when (exponent) {
        0 -> sign * (fraction.toDouble() / 1024.0) * 2.0.pow(-14.0)
        31 -> {
            if (fraction == 0) sign * Double.POSITIVE_INFINITY else Double.NaN
        }
        else -> sign * (1.0 + fraction.toDouble() / 1024.0) * 2.0.pow((exponent - 15).toDouble())
    }
}
