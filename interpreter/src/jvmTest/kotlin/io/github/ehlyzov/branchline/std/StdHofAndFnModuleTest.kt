// tests/StdHofAndFnModuleTest.kt
package io.github.ehlyzov.branchline.std

import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.test.Test
import kotlin.test.assertEquals

class LambdaAndCombinatorsTest {

    // -------- Basics --------
    @Test fun lambda_basic_and_apply() {
        val out = compileAndRun(
            """
            LET f = (x) -> x + 1 ;
            LET y = f(41) ;
            LET z = APPLY(f, 41) ;
            OUTPUT { y: y, z: z } ;
            """.trimIndent()
        )
        assertEquals(mapOf("y" to 42, "z" to 42), out)
    }

    @Test fun zero_arg_lambda() {
        val out = compileAndRun(
            """
            LET f = () -> 42 ;
            OUTPUT { y: f() } ;
            """.trimIndent()
        )
        assertEquals(mapOf("y" to 42), out)
    }

    // -------- HOFs --------
    @Test fun map_filter_reduce_some_every_find() {
        val out1 = compileAndRun(
            """ LET ys = MAP([1,2,3], (x) -> x + 1) ; OUTPUT { ys: ys } ; """
                .trimIndent()
        )
        assertEquals(mapOf("ys" to listOf(2, 3, 4)), out1)

        val out2 = compileAndRun(
            """ LET ys = FILTER([1,2,3,4], (x) -> x % 2 == 0) ; OUTPUT { ys: ys } ; """
                .trimIndent()
        )
        assertEquals(mapOf("ys" to listOf(2, 4)), out2)

        val out3 = compileAndRun(
            """ LET s = REDUCE([1,2,3], 0, (acc,v) -> acc + v) ; OUTPUT { s: s } ; """
                .trimIndent()
        )
        assertEquals(mapOf("s" to 6), out3)

        val out4 = compileAndRun(
            """ LET r = SOME([1,2,3], (x) -> x > 2) ; OUTPUT { r: r } ; """
                .trimIndent()
        )
        assertEquals(mapOf("r" to true), out4)

        val out5 = compileAndRun(
            """ LET r = EVERY([2,4,6], (x) -> x % 2 == 0) ; OUTPUT { r: r } ; """
                .trimIndent()
        )
        assertEquals(mapOf("r" to true), out5)

        val out6 = compileAndRun(
            """ LET r = FIND([10,20,30], (x) -> x >= 20) ; OUTPUT { r: r } ; """
                .trimIndent()
        )
        assertEquals(mapOf("r" to 20), out6)
    }

    @Test fun map_index_aware() {
        val out = compileAndRun(
            """
            LET ys = MAP([10,20], (v,i,a) -> v + i) ;
            OUTPUT { ys: ys } ;
            """.trimIndent()
        )
        assertEquals(mapOf("ys" to listOf(10, 21)), out)
    }

    // -------- Combinators --------
    @Test fun currying_and_map() {
        val out = compileAndRun(
            """
            LET add = (a) -> (b) -> a + b ;
            LET plus10 = add(10) ;
            LET ys = MAP([1,2,3], plus10) ;
            OUTPUT { ys: ys } ;
            """.trimIndent()
        )
        assertEquals(mapOf("ys" to listOf(11, 12, 13)), out)
    }

    @Test fun composition() {
        val out = compileAndRun(
            """
            LET inc = (x) -> x + 1 ;
            LET dbl = (x) -> x * 2 ;
            LET compose = (f, g) -> (x) -> g(f(x)) ;
            LET h = compose(inc, dbl) ;
            LET r = h(10) ;
            OUTPUT { r: r } ;
            """.trimIndent()
        )
        assertEquals(mapOf("r" to 22), out)
    }

    @Test fun compose_all_via_reduce() {
        val out = compileAndRun(
            """
            LET inc = (x) -> x + 1 ;
            LET dbl = (x) -> x * 2 ;
            LET COMPOSE_ALL = (fs) -> (x) -> REDUCE(fs, x, (acc, fn) -> APPLY(fn, acc)) ;
            LET h = COMPOSE_ALL([inc, dbl]) ;
            LET out = h(5) ;
            OUTPUT { out: out } ;
            """.trimIndent()
        )
        assertEquals(mapOf("out" to 12), out)
    }

    @Test fun ski_combinators() {
        val out = compileAndRun(
            """
            LET I = (x) -> x ;
            LET K = (x) -> (y) -> x ;
            LET S = (f) -> (g) -> (x) -> APPLY(APPLY(f, x), APPLY(g, x)) ;
    
            LET const5 = K(5) ;
            LET resultK = const5(999) ;
            
            LET add = (a) -> (b) -> a + b ;
            LET sApp = S(add)(I)(3) ;

            LET ID = S(K)(K) ;
            
            OUTPUT { resultK: resultK, sApp: sApp, identity: ID(42) } ;
            """.trimIndent()
        )
        assertEquals(mapOf("resultK" to 5, "sApp" to 6, "identity" to 42), out)
    }

    @Test fun curry_uncurry_flip() {
        val out = compileAndRun(
            """
            LET CURRY = (f) -> (a) -> (b) -> APPLY(f, a, b) ;
            LET UNCURRY = (f) -> (a, b) -> APPLY(APPLY(f, a), b) ;
            LET FLIP = (f) -> (a, b) -> APPLY(f, b, a) ;

            LET minus2 = (a, b) -> a - b ;
            LET curriedMinus = CURRY(minus2) ;
            LET a = curriedMinus(5)(2) ;
            LET b = FLIP(minus2)(5, 2) ;
            LET c = UNCURRY(curriedMinus)(5, 2) ;
            OUTPUT { a: a, b: b, c: c } ;
            """.trimIndent()
        )
        assertEquals(mapOf("a" to 3, "b" to -3, "c" to 3), out)
    }
}
