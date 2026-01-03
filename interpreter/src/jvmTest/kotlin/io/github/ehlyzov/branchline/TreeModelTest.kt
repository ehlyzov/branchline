package io.github.ehlyzov.branchline

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreeModelTest {
    @Test
    fun nameKeyOperations() {
        val map = ObjMap()
        val value = Jv.Str("value")
        val updated = map.put(ObjKey.Name("field"), value)

        // retrieve by name
        assertEquals(value, updated[ObjKey.Name("field")])
        // keys should include the name key
        assertEquals(listOf<ObjKey>(ObjKey.Name("field")), updated.keys())

        val removed = updated.delete(ObjKey.Name("field"))
        assertNull(removed[ObjKey.Name("field")])
        assertTrue(removed.keys().isEmpty())
    }

    @Test
    fun numericIndicesShareEntryAcrossTypes() {
        val initial = ObjMap()
        val first = Jv.Str("first")
        val second = Jv.Str("second")

        val withIndex = initial.put(I32(1), first)
        // get across numeric key types
        assertEquals(first, withIndex[I32(1)])
        assertEquals(first, withIndex[I64(1L)])
        assertEquals(first, withIndex[IBig(BigInteger.ONE)])
        // keys normalised to IBig
        assertEquals(listOf<ObjKey>(IBig(BigInteger.ONE)), withIndex.keys())

        val overwritten = withIndex.put(I64(1L), second)
        assertEquals(second, overwritten[I32(1)])
        assertEquals(second, overwritten[I64(1L)])
        assertEquals(second, overwritten[IBig(BigInteger.ONE)])
        assertEquals(listOf<ObjKey>(IBig(BigInteger.ONE)), overwritten.keys())

        val deleted = overwritten.delete(IBig(BigInteger.ONE))
        assertNull(deleted[I32(1)])
        assertNull(deleted[I64(1L)])
        assertNull(deleted[IBig(BigInteger.ONE)])
        assertTrue(deleted.keys().isEmpty())
    }
}
