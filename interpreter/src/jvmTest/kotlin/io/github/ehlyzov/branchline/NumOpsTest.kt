package io.github.ehlyzov.branchline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumOpsTest {
    @Test
    fun testIsIntegral() {
        assertTrue(NumOps.isIntegral(I32(1)))
        assertTrue(NumOps.isIntegral(I64(2L)))
        assertTrue(NumOps.isIntegral(IBig(java.math.BigInteger.valueOf(3))))
        assertFalse(NumOps.isIntegral(F64(4.5)))
    }
}
