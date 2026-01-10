package io.github.ehlyzov.branchline.debug

import io.github.ehlyzov.branchline.testutils.compileAndRun
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.collections.buildMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExplainProvenanceOneLoopTest {

    /** Helpers to build precise numeric host fns */
    private fun bd(x: Any?): BigDecimal = when (x) {
        is BigDecimal -> x
        is Number -> BigDecimal.valueOf(x.toDouble())
        else -> error("Expected number, got ${x?.let { it::class.simpleName } ?: "null"}")
    }

    private fun hostFns(): Map<String, (List<Any?>) -> Any?> = mapOf(
        // APPLY_COUPON(amount, couponCode, couponsMap)
        "APPLY_COUPON" to { args ->
            val amount = bd(args[0])
            val code = args[1] as? String

            @Suppress("UNCHECKED_CAST")
            val coupons = args[2] as Map<String, Any?>
            val rate = (code?.let { coupons[it] } as? Number)?.toDouble() ?: 0.0
            amount.multiply(BigDecimal.valueOf(1.0 - rate))
        },
        // FX(amount, fromCur, toCur, fxMap) — expects key "USD_EUR" etc; from==to -> passthrough
        "FX" to { args ->
            val amount = bd(args[0])
            val from = args[1] as String
            val to = args[2] as String

            @Suppress("UNCHECKED_CAST")
            val fx = args[3] as Map<String, Any?>
            if (from == to) {
                amount
            } else {
                val key = "${from}_$to"
                val rate = (fx[key] as? Number)?.toDouble()
                    ?: error("FX rate '$key' not found")
                amount.multiply(BigDecimal.valueOf(rate))
            }
        },
        // TAX_RATE(region, taxMap) -> BigDecimal rate (e.g., 0.19)
        "TAX_RATE" to { args ->
            val region = args[0] as String

            @Suppress("UNCHECKED_CAST")
            val tax = args[1] as Map<String, Any?>
            BigDecimal.valueOf((tax[region] as? Number)?.toDouble() ?: 0.0)
        },
        // ROUND(value, places)
        "ROUND" to { args ->
            val v = bd(args[0])
            val places = (args[1] as Number).toInt()
            v.setScale(places, RoundingMode.HALF_UP)
        }
    )

    /**
     * Run a stream transform with a CollectingTracer (like compileStream, but
     * with tracer + extra host fns).
     */
    private fun runWithTracer(body: String, row: Map<String, Any?>): Pair<Any?, CollectingTracer> {
        val tracer = CollectingTracer(
            TraceOptions(
                step = true, // we need Enter/Exit around write nodes
                includeEval = false, // keep noise low
                includeCalls = true
            )
        )
        Debug.tracer = tracer
        return compileAndRun(body, row, extraFns = hostFns()) to tracer
    }

    @Test
    fun `simple provenance`() {
        val row = mapOf(
            "order_id" to "A-1024",
            "unit_price" to 35,
            "quantity" to 3,
            "discount" to 5
        )
        val program = """
            LET unused = 5;
            LET subtotal = row.unit_price * row.quantity;
            LET discount = row.discount ?? 0;
            LET result = subtotal - discount;
            OUTPUT {
                order_id: row.order_id,
                result: result,
                explain: EXPLAIN("result"),
                explain_order: EXPLAIN("order_id")
            }
        """.trimIndent()

        val (out, tracer) = runWithTracer(program, row)
        @Suppress("UNCHECKED_CAST")
        val obj = out as Map<String, Any?>
        assertEquals(100, obj["result"])

        @Suppress("UNCHECKED_CAST")
        val explain = obj["explain"] as Map<String, Any?>
        assertEquals("result", explain["var"])
        @Suppress("UNCHECKED_CAST")
        val steps = explain["steps"] as List<Map<String, Any?>>
        assertTrue(steps.isNotEmpty(), "Expected provenance steps for result")
        val firstStep = steps.first()
        assertEquals("LET", firstStep["op"])
        @Suppress("UNCHECKED_CAST")
        val inputs = firstStep["inputs"] as List<Map<String, Any?>>
        val names = inputs.map { it["name"] as String }
        assertTrue(names.containsAll(listOf("row.unit_price", "row.quantity", "row.discount")))

        val report = TraceReport.from(tracer, sample = 20, maxExplains = 8)
        val human = report.explanations.joinToString("\n").also(::println)
        assertTrue(human.contains("result"))
        assertTrue(human.contains("order_id"))
        Debug.tracer = null
    }

    @Test
    fun `explainOutput aggregates provenance`() {
        val row = mapOf(
            "order_id" to "A-1024",
            "unit_price" to 35,
            "quantity" to 3,
            "discount" to 5
        )
        val program = """
            LET subtotal = row.unit_price * row.quantity;
            LET discount = row.discount ?? 0;
            LET result = subtotal - discount;

            OUTPUT {
                result: result,
                explain: EXPLAIN("result")
            }
        """.trimIndent()

        val (out, _) = runWithTracer(program, row)
        val obj = out.requireStringMap("explain result")
        val explanations = Debug.explainOutput(obj)
        assertNotNull(explanations)
        val resultExplain = explanations!!["result"].requireStringMap("explain[result]")
        val steps = resultExplain["steps"].requireMapList("explain[result].steps")
        assertTrue(steps.isNotEmpty(), "Expected result provenance from explainOutput")
        val firstStep = steps.first()
        @Suppress("UNCHECKED_CAST")
        val inputNames = (firstStep["inputs"] as List<Map<String, Any?>>).map { it["name"] as String }
        assertTrue(inputNames.containsAll(listOf("row.unit_price", "row.quantity", "row.discount")))
        Debug.tracer = null
    }

    @Test
    fun `one loop + external functions - provenance`() {
        val row = mapOf(
            "orders" to listOf(
                mapOf(
                    "id" to 101,
                    "price" to 100,
                    "qty" to 2,
                    "currency" to "USD",
                    "region" to "DE",
                    "coupon" to "WELCOME10"
                ),
                mapOf(
                    "id" to 102,
                    "price" to 120,
                    "qty" to 1,
                    "currency" to "USD",
                    "region" to "DE",
                    "coupon" to null
                ),
                mapOf(
                    "id" to 103,
                    "price" to 50,
                    "qty" to 3,
                    "currency" to "USD",
                    "region" to "FR",
                    "coupon" to "VIP5"
                ),
            ),
            "fx" to mapOf("USD_EUR" to 0.9),
            "tax" to mapOf("DE" to 0.19, "FR" to 0.20),
            "coupons" to mapOf("WELCOME10" to 0.10, "VIP5" to 0.05)
        )

        val program = """
            LET result = { total: 0 };
            FOR o IN row.orders {
              APPEND TO result.items {
                id: o.id,
                final: ROUND(FX(APPLY_COUPON(o.price * o.qty, o.coupon, row.coupons), o.currency, "EUR", row.fx)
                             * (1 + TAX_RATE(o.region, row.tax)), 2)
              } INIT [];
              
              SET result.total = (result.total ?? 0)
                + ROUND(FX(APPLY_COUPON(o.price * o.qty, o.coupon, row.coupons), o.currency, "EUR", row.fx)
                        * (1 + TAX_RATE(o.region, row.tax)), 2);
            }
            OUTPUT { result: result, explain: EXPLAIN("result") };
        """.trimIndent()

        val (out, tracer) = runWithTracer(program, row)

        val obj = out.requireStringMap("result wrapper")
        val result = obj["result"].requireStringMap("result")
        val items = result["items"].requireMapList("result.items")

        // --- Check numeric results (ids/finals & total) ---
        val finals = items.map { (it["final"] as BigDecimal).setScale(2) }.map { it.toPlainString() }
        assertEquals(listOf("192.78", "128.52", "153.90"), finals)

        val total = (result["total"] as BigDecimal).setScale(2).toPlainString()
        assertEquals("475.20", total)

        // --- Structured EXPLAIN("result") ---
        @Suppress("UNCHECKED_CAST")
        val ex = obj["explain"] as Map<String, Any?>
        assertEquals("result", ex["var"])
        @Suppress("UNCHECKED_CAST")
        val steps = ex["steps"] as List<Map<String, Any?>>
        assertTrue(steps.isNotEmpty())

        // 1) items: expect 3 APPEND steps, inputs must contain o, coupons, fx, tax
        val itemsSteps = steps.filter { (it["path"] as List<*>).firstOrNull() == "items" }
        assertEquals(3, itemsSteps.size)
        assertTrue(itemsSteps.all { it["op"] == "APPEND" })
        // ensure inputs captured
        itemsSteps.forEach { st ->
            @Suppress("UNCHECKED_CAST")
            val inputs = st["inputs"] as List<Map<String, Any?>>
            val names = inputs.map { it["name"] as String }

            // Должна быть переменная цикла (хотя бы одно 'o.*')
            assertTrue(names.any { it.startsWith("o.") }, "expect some o.* inputs")

            // Эти — ключевые внешние зависимости (после синтетических Read — уже dotted):
            assertTrue("row.coupons" in names, "expect row.coupons in inputs")
            assertTrue("row.fx" in names, "expect row.fx in inputs")
            assertTrue("row.tax" in names, "expect row.tax in inputs")

            // Для APPEND по items дополнительно проверим, что читали id заказа:
            if (st["op"] == "APPEND") {
                assertTrue("o.id" in names, "expect o.id in inputs for appended item")
            }
        }

        // id sequence in APPEND deltas: 101,102,103
        val deltaIds = itemsSteps.mapNotNull {
            @Suppress("UNCHECKED_CAST")
            val d = it["delta"] as? Map<String, Any?>
            (d?.get("id") as? Number)?.toInt()
        }
        assertEquals(listOf(101, 102, 103), deltaIds)

        // 2) total: expect 3 SET steps with cumulative 'new' values 192.78, 321.30, 475.20
        val totalSteps = steps.filter { (it["path"] as List<*>).firstOrNull() == "total" }
        assertEquals(3, totalSteps.size)
        assertTrue(totalSteps.all { it["op"] == "SET" })

        // Human-readable report should include calc steps for LET-bound variable
        val report = TraceReport.from(tracer, sample = 20, maxExplains = 8)
        val human = report.explanations.joinToString("\n")
        assertTrue(human.contains("APPLY_COUPON"))
        assertTrue(human.contains("FX("))
        assertTrue(human.contains("TAX_RATE"))
        assertTrue(human.contains("ROUND("))
        val totalNewSeq = totalSteps.map {
            val n = it["new"]
            (n as BigDecimal).setScale(2).toPlainString()
        }
        assertEquals(listOf("192.78", "321.30", "475.20"), totalNewSeq)

        // --- Human summary should clearly mention both branches and inputs ---
        assertTrue(human.contains("result.items"), "human summary should mention result.items")
        assertTrue(human.contains("result.total"), "human summary should mention result.total")
        assertTrue(human.contains("APPEND"), "human summary should include APPEND steps")
        assertTrue(human.contains("SET"), "human summary should include SET steps")
        assertTrue(human.contains("fx"), "human summary should include fx input")
        assertTrue(human.contains("tax"), "human summary should include tax input")
        assertTrue(human.contains("coupons"), "human summary should include coupons input")

        println("--- EXPLAIN(result) human ---\n$human")
    }

    @Test
    fun `one loop + external functions with LET binding - provenance`() {
        val row = mapOf(
            "orders" to listOf(
                mapOf(
                    "id" to 101,
                    "price" to 100,
                    "qty" to 2,
                    "currency" to "USD",
                    "region" to "DE",
                    "coupon" to "WELCOME10"
                ),
                mapOf(
                    "id" to 102,
                    "price" to 120,
                    "qty" to 1,
                    "currency" to "USD",
                    "region" to "DE",
                    "coupon" to null
                ),
                mapOf(
                    "id" to 103,
                    "price" to 50,
                    "qty" to 3,
                    "currency" to "USD",
                    "region" to "FR",
                    "coupon" to "VIP5"
                ),
            ),
            "fx" to mapOf("USD_EUR" to 0.9),
            "tax" to mapOf("DE" to 0.19, "FR" to 0.20),
            "coupons" to mapOf("WELCOME10" to 0.10, "VIP5" to 0.05)
        )

        val program = """
            LET result = { total: 0 };
            FOR o IN row.orders {
              LET effectivePrice = ROUND(FX(APPLY_COUPON(o.price * o.qty, o.coupon, row.coupons), o.currency, "EUR", row.fx)
                             * (1 + TAX_RATE(o.region, row.tax)), 2);
              APPEND TO result.items {
                id: o.id,
                final: effectivePrice
              } INIT [];
              
              SET result.total = (result.total ?? 0) + effectivePrice;
            }
            OUTPUT { result: result, explain: EXPLAIN("result") };
        """.trimIndent()

        val (out, tracer) = runWithTracer(program, row)

        @Suppress("UNCHECKED_CAST")
        val obj = out as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val result = obj["result"] as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val items = result["items"] as List<Map<String, Any?>>
        val finals = items.map { (it["final"] as BigDecimal).setScale(2) }.map { it.toPlainString() }
        assertEquals(listOf("192.78", "128.52", "153.90"), finals)
        val total = (result["total"] as BigDecimal).setScale(2).toPlainString()
        assertEquals("475.20", total)

        val ex = obj["explain"].requireStringMap("result.explain")
        val steps = ex["steps"].requireMapList("result.explain.steps")
        val itemsSteps = steps.filter { (it["path"] as List<*>).firstOrNull() == "items" }
        assertEquals(3, itemsSteps.size)
        assertTrue(itemsSteps.all { it["op"] == "APPEND" })
        itemsSteps.forEach { st ->
            val inputs = st["inputs"].requireMapList("result.explain.steps.inputs")
            val names = inputs.map { it["name"] as String }
            assertTrue(names.any { it.startsWith("o.") }, "expect some o.* inputs")
            assertTrue("row.coupons" in names, "expect row.coupons in inputs")
            assertTrue("row.fx" in names, "expect row.fx in inputs")
            assertTrue("row.tax" in names, "expect row.tax in inputs")
            assertTrue("o.id" in names, "expect o.id in inputs for appended item")
        }

        val totalSteps = steps.filter { (it["path"] as List<*>).firstOrNull() == "total" }
        assertEquals(3, totalSteps.size)
        assertTrue(totalSteps.all { it["op"] == "SET" })

        // Human-readable report should include calc steps for LET-bound variable
        val report = TraceReport.from(tracer, sample = 20, maxExplains = 8)
        val human = report.explanations.joinToString("\n")
        assertTrue(human.contains("APPLY_COUPON"))
        assertTrue(human.contains("FX("))
        assertTrue(human.contains("TAX_RATE"))
        assertTrue(human.contains("ROUND("))
        println("--- EXPLAIN(result) human ---\n$human")
    }
}

private fun Any?.requireStringMap(context: String): Map<String, Any?> {
    val map = this as? Map<*, *> ?: error("$context expected object")
    return buildMap(map.size) {
        for ((k, v) in map) {
            require(k is String) { "$context keys must be strings" }
            put(k, v)
        }
    }
}

private fun Any?.requireMapList(context: String): List<Map<String, Any?>> {
    val list = this as? List<*> ?: error("$context expected list")
    return list.mapIndexed { idx, value -> value.requireStringMap("$context[$idx]") }
}
