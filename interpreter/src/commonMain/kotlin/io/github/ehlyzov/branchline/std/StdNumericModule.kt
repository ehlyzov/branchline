package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_DEC_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.BL_BIG_INT_ZERO
import io.github.ehlyzov.branchline.runtime.bignum.ceil
import io.github.ehlyzov.branchline.runtime.bignum.compareTo
import io.github.ehlyzov.branchline.runtime.bignum.floor
import io.github.ehlyzov.branchline.runtime.bignum.unaryMinus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class StdNumericModule : StdModule {
    override fun register(r: StdRegistry) {
        r.fn("ABS", ::fnABS)
        r.fn("FLOOR", ::fnFLOOR)
        r.fn("CEIL", ::fnCEIL)
        r.fn("ROUND", ::fnROUND)
        r.fn("POWER", ::fnPOWER)
        r.fn("SQRT", ::fnSQRT)
        r.fn("RANDOM", ::fnRANDOM)
    }
}

/**
 * ABS(number) – returns the absolute value of a number.
 */
private fun fnABS(args: List<Any?>): Any? {
    require(args.size == 1) { "ABS(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> abs(v)
        is Long -> abs(v)
        is Float -> abs(v)
        is Double -> abs(v)
        is Short -> abs(v.toInt())
        is Byte -> abs(v.toInt())
        is BLBigInt -> if (v >= BL_BIG_INT_ZERO) v else -v
        is BLBigDec -> if (v >= BL_BIG_DEC_ZERO) v else -v
        else -> error("ABS: arg must be numeric")
    }
}

/** FLOOR(number) – returns the largest integer ≤ number. */
private fun fnFLOOR(args: List<Any?>): Any? {
    require(args.size == 1) { "FLOOR(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> v
        is Long -> v
        is Float -> floor(v)
        is Double -> floor(v)
        is Short -> v
        is Byte -> v
        is BLBigInt -> v
        is BLBigDec -> v.floor()
        else -> error("FLOOR: arg must be numeric")
    }
}

/** CEIL(number) – returns the smallest integer ≥ number. */
private fun fnCEIL(args: List<Any?>): Any? {
    require(args.size == 1) { "CEIL(number)" }
    val v = args[0] ?: return null
    return when (v) {
        is Int -> v
        is Long -> v
        is Float -> ceil(v)
        is Double -> ceil(v)
        is Short -> v
        is Byte -> v
        is BLBigInt -> v
        is BLBigDec -> v.ceil()
        else -> error("CEIL: arg must be numeric")
    }
}

/** ROUND(number[, precision]) – rounds using “round half to even”. */
private fun fnROUND(args: List<Any?>): Any? {
    require(args.size in 1..2) { "ROUND(number[, precision])" }
    val v = args[0] ?: return null
    val precision = if (args.size == 2) parsePrecision(args[1]) else 0
    TODO()
}

/** Helper to parse the precision argument as an integer. */
private fun parsePrecision(arg: Any?): Int {
    TODO()
}

/** POWER(base, exponent) – raises base to exponent. */
private fun fnPOWER(args: List<Any?>): Any? {
    require(args.size == 2) { "POWER(base, exponent)" }
    val b = args[0] ?: return null
    val e = args[1] ?: return null

    TODO()
}

/** SQRT(number) – returns the square root of a non‑negative number. */
private fun fnSQRT(args: List<Any?>): Any? {
    require(args.size == 1) { "SQRT(number)" }
    TODO()
}

/** RANDOM() – returns a pseudo‑random number in [0, 1). */
private fun fnRANDOM(args: List<Any?>): Any {
    require(args.isEmpty()) { "RANDOM()" }
    return kotlin.random.Random.nextDouble()
}