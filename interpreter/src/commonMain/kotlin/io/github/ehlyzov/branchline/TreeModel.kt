package io.github.ehlyzov.branchline

import io.github.ehlyzov.branchline.runtime.bignum.BLBigDec
import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import io.github.ehlyzov.branchline.runtime.bignum.blBigIntOfLong
import kotlin.jvm.JvmInline

sealed interface Jv {
    data class Obj(val fields: ObjMap) : Jv
    data class Arr(val items: List<Jv>) : Jv
    data class Str(val v: String) : Jv
    data class Bool(val v: Boolean) : Jv
    data object Null : Jv
}

// Numbers modeled as distinct wrappers
sealed interface NumValue : Jv

@JvmInline
value class I32(val v: Int) : NumValue, ObjKey.Index

@JvmInline
value class I64(val v: Long) : NumValue, ObjKey.Index

@JvmInline
value class IBig(val v: BLBigInt) : NumValue, ObjKey.Index

@JvmInline
value class Dec(val v: BLBigDec) : NumValue

sealed interface ObjKey {
    @JvmInline
    value class Name(val v: String) : ObjKey
    sealed interface Index : ObjKey
}

/**
 * Ordered map of object fields keyed by either names or non-negative integer indices.
 * Uses separate maps for names vs indices for predictable ordering and type safety.
 */
class ObjMap(
    private val byName: LinkedHashMap<String, Jv> = LinkedHashMap(),
    private val byIndex: LinkedHashMap<BLBigInt, Jv> = LinkedHashMap()
) {
    operator fun get(k: ObjKey): Jv? = when (k) {
        is ObjKey.Name -> byName[k.v]
        is I32 -> byIndex[blBigIntOfLong(k.v.toLong())]
        is I64 -> byIndex[blBigIntOfLong(k.v)]
        is IBig -> byIndex[k.v]
    }

    fun keys(): List<ObjKey> = buildList {
        byName.keys.forEach { add(ObjKey.Name(it)) }
        byIndex.keys.forEach { add(IBig(it)) }
    }

    fun put(k: ObjKey, v: Jv): ObjMap = ObjMap(
        LinkedHashMap(byName).apply { if (k is ObjKey.Name) put(k.v, v) },
        LinkedHashMap(byIndex).apply { if (k is ObjKey.Index) put(k.toBigInt(), v) }
    )

    fun delete(k: ObjKey): ObjMap = ObjMap(
        LinkedHashMap(byName).apply { if (k is ObjKey.Name) remove(k.v) },
        LinkedHashMap(byIndex).apply { if (k is ObjKey.Index) remove(k.toBigInt()) }
    )

    private fun ObjKey.Index.toBigInt(): BLBigInt = when (this) {
        is I32 -> blBigIntOfLong(v.toLong())
        is I64 -> blBigIntOfLong(v)
        is IBig -> v
    }
}

