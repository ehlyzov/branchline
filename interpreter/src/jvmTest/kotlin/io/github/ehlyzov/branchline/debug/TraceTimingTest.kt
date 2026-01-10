package io.github.ehlyzov.branchline.debug

import io.github.ehlyzov.branchline.testutils.compileAndRun
import kotlin.collections.buildMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

private fun pretty(rep: TraceReport.TraceReportData): String {
    fun d(x: Duration) = x.toString()
    return buildString {
        appendLine("Summary: events=${rep.totalEvents}, duration=${d(rep.duration)}")
        if (rep.hotspots.isNotEmpty()) {
            appendLine("Hotspots:")
            rep.hotspots.take(10).forEach { h ->
                val name = h.name?.let { ":$it" } ?: ""
                appendLine("  - ${h.kind}$name calls=${h.calls} total=${d(h.total)} mean=${d(h.mean)}")
            }
        }
        if (rep.checkpoints.isNotEmpty()) {
            appendLine("Checkpoints:")
            rep.checkpoints.forEach { c ->
                val label = c.label ?: ""
                appendLine("  - @${d(c.at)} $label")
            }
        }
        if (rep.timelineSample.isNotEmpty()) {
            appendLine("Timeline sample:")
            rep.timelineSample.forEach { t ->
                val lbl = t.label?.let { " $it" } ?: ""
                appendLine("  - @${d(t.at)} ${t.kind}$lbl")
            }
        }
    }
}

private fun tracerForPerf(maxEvents: Int = 2_000_000, sample: Int = 200) = CollectingTracer(
    TraceOptions(
        includeCalls = true,
        includeEval = false,
        watch = emptySet(),
        step = false,
        maxEvents = maxEvents,
    )
)

private fun Any?.requireStringMap(context: String): Map<String, Any?> {
    val map = this as? Map<*, *> ?: error("$context expected object")
    return buildMap(map.size) {
        for ((k, v) in map) {
            require(k is String) { "$context keys must be strings" }
            put(k, v)
        }
    }
}

private fun generateOrders(
    nOrders: Int = 2_500,
    maxLinesPerOrder: Int = 25,
    seed: Long = 42L
): Map<String, Any?> {
    val rnd = Random(seed)
    val cats = listOf("books", "toys", "electronics", "fashion", "groceries", "health")
    val tagsPool = listOf("promo", "clearance", "vip", "fragile", "gift", "bulk", "eco")

    fun randomTags(): List<String> {
        val k = rnd.nextInt(0, 4)
        return (0 until k).map { tagsPool[rnd.nextInt(tagsPool.size)] }.distinct()
    }

    fun line(idx: Int): Map<String, Any?> {
        val price = rnd.nextDouble() * 200.0 + 1.0
        val qty = rnd.nextInt(1, 6)
        val cat = cats[rnd.nextInt(cats.size)]
        val attrs = mapOf(
            "weight" to rnd.nextDouble() * 5.0,
            "giftWrap" to (rnd.nextInt(10) == 0),
            "meta" to mapOf(
                "warehouse" to "W-${rnd.nextInt(1, 5)}",
                "priority" to rnd.nextInt(1, 4)
            )
        )
        return mapOf(
            "sku" to "SKU-$idx-${rnd.nextInt(1000)}",
            "category" to cat,
            "price" to price,
            "qty" to qty,
            "tags" to randomTags(),
            "attrs" to attrs
        )
    }

    val orders = ArrayList<Map<String, Any?>>(nOrders)
    for (i in 0 until nOrders) {
        val nLines = rnd.nextInt(5, maxLinesPerOrder + 1)
        val lines = (0 until nLines).map { line(it) }
        val cust = mapOf(
            "id" to "C-${rnd.nextInt(100_000)}",
            "vip" to (rnd.nextInt(20) == 0),
            "age" to rnd.nextInt(18, 80),
            "region" to listOf("NA", "EU", "APAC", "LATAM")[rnd.nextInt(4)]
        )
        orders += mapOf(
            "id" to "O-$i",
            "lines" to lines,
            "customer" to cust,
            "createdAt" to "2024-${1 + rnd.nextInt(12)}-${1 + rnd.nextInt(28)}",
            "tags" to randomTags()
        )
    }
    return mapOf("orders" to orders)
}

class TraceTimingTest {

    @Test
    fun hotspots_and_checkpoint_present_with_large_input_and_print_report() {
        val tracer = tracerForPerf()

        val rowWarm = generateOrders(nOrders = 200, maxLinesPerOrder = 10, seed = 999)
        val script = """
            LET orders = row.orders ;

            LET perLineTotals = MAP(orders, (o) ->
                MAP(o.lines, (ln) -> ln.price * ln.qty)
            ) ;

            LET perOrder = MAP(perLineTotals, (ls) ->
                REDUCE(ls, 0, (a, v) -> a + v)
            ) ;

            LET heavy = FILTER(perOrder, (t) -> t > 500.0) ;

            CHECKPOINT("after map/filter") ;

            LET grand = REDUCE(heavy, 0, (a, v) -> a + v) ;

            OUTPUT { grand: grand } ;
        """.trimIndent()

        Debug.tracer = null
        compileAndRun(script, rowWarm)

        val row = generateOrders(nOrders = 1200, maxLinesPerOrder = 30, seed = 123L)
        Debug.tracer = tracer
        val out = compileAndRun(script, row).requireStringMap("TraceTimingTest output")
        assertTrue(out.containsKey("grand"))

        val rep = TraceReport.from(tracer)
        println(
            "\n--- TraceReport (TraceTimingTest.hotspots_and_checkpoint_present_with_large_input_and_print_report) ---\n" + pretty(rep)
        )

        assertTrue(rep.checkpoints.any { it.label == "after map/filter" })
        fun hasHotspot(name: String) =
            rep.hotspots.any { it.kind == "HOST" && it.name == name && it.calls > 0 && it.total > Duration.ZERO }
        assertTrue(hasHotspot("MAP"))
        assertTrue(hasHotspot("FILTER"))
        assertTrue(hasHotspot("REDUCE"))
        assertTrue(rep.totalEvents > 0)
        assertTrue(rep.duration > Duration.ZERO)
        assertTrue(rep.timelineSample.isNotEmpty())
    }

    @Test
    fun compare_hotspots_scale_with_input_size_and_print() {
        val tracerSmall = tracerForPerf()
        val tracerBig = tracerForPerf()

        val script = """
            LET orders = row.orders ;
            LET totals = MAP(orders, (o) -> REDUCE(MAP(o.lines, (ln) -> ln.price * ln.qty), 0, (a,v) -> a + v)) ;
            LET heavy  = FILTER(totals, (t) -> t > 500.0) ;
            LET grand  = REDUCE(heavy, 0, (a,v) -> a + v) ;
            OUTPUT { grand: grand } ;
        """.trimIndent()

        val smallRow = generateOrders(nOrders = 200, maxLinesPerOrder = 20, seed = 1L)
        val bigRow = generateOrders(nOrders = 1100, maxLinesPerOrder = 30, seed = 1L)

        Debug.tracer = null
        compileAndRun(script, smallRow)

        Debug.tracer = tracerSmall
        compileAndRun(script, smallRow)
        val repSmall = TraceReport.from(tracerSmall)
        println("\n--- TraceReport (small input) ---\n" + pretty(repSmall))

        Debug.tracer = tracerBig
        compileAndRun(script, bigRow)
        val repBig = TraceReport.from(tracerBig)
        println("\n--- TraceReport (big input) ---\n" + pretty(repBig))

        fun totalHostTime(rep: TraceReport.TraceReportData) =
            rep.hotspots.filter { it.kind == "HOST" }.fold(Duration.ZERO) { acc, h -> acc + h.total }

        assertTrue(totalHostTime(repBig) > totalHostTime(repSmall))
    }
}
