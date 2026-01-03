package io.github.ehlyzov.branchline.debug

import io.github.ehlyzov.branchline.runtime.bignum.BLBigInt
import kotlin.time.Duration

object TraceReport {
    data class TimelineEntry(
        val at: Duration,
        val kind: String,
        val label: String?,
    )
    data class Hotspot(
        val kind: String,
        val name: String?,
        val calls: Int,
        val total: Duration,
        val mean: Duration,
    )
    data class WatchInfo(val last: Any?, val changes: Int)
    data class Checkpoint(val at: Duration, val label: String?)

    data class TraceReportData(
        val totalEvents: Int,
        val duration: Duration,
        val timelineSample: List<TimelineEntry>,
        val hotspots: List<Hotspot>,
        val watches: Map<String, WatchInfo>,
        val checkpoints: List<Checkpoint>,
        val explanations: List<String>,
        val instructions: Map<String, Long>,
    )

    fun from(t: CollectingTracer, sample: Int = 25, maxExplains: Int = 8): TraceReportData {
        val timed = t.events
        val totalEvents = timed.size
        val duration = if (timed.isEmpty()) Duration.ZERO else timed.last().at

        // ---- hotspots (без изменений) ----
        data class Frame(val kind: String, val name: String?, val start: Duration)
        val stack = ArrayList<Frame>()
        data class Agg(var calls: Int = 0, var total: Duration = Duration.ZERO)
        val agg = LinkedHashMap<Pair<String, String?>, Agg>()

        fun keyOf(kind: String, name: String?) = Pair(kind, name)

        for (ev in timed) when (val e = ev.event) {
            is TraceEvent.Call -> stack.add(Frame(e.kind, e.name, ev.at))
            is TraceEvent.Return -> {
                val idx = stack.indexOfLast { it.kind == e.kind && it.name == e.name }
                if (idx >= 0) {
                    val fr = stack.removeAt(idx)
                    val k = keyOf(fr.kind, fr.name)
                    val a = agg.getOrPut(k) { Agg() }
                    a.calls += 1
                    a.total += (ev.at - fr.start)
                }
            }
            else -> Unit
        }
        val hotspots = agg.map { (k, a) ->
            Hotspot(k.first, k.second, a.calls, a.total, if (a.calls == 0) Duration.ZERO else a.total / a.calls)
        }.sortedByDescending { it.total }

        // ---- watches (без изменений) ----
        val watches = LinkedHashMap<String, WatchInfo>()
        val lastVal = HashMap<String, Any?>()
        val changes = HashMap<String, Int>()
        for (ev in timed) when (val e = ev.event) {
            is TraceEvent.Read -> {
                val name = e.name
                val old = lastVal[name]
                if (name !in lastVal || old != e.value) {
                    lastVal[name] = e.value
                    changes[name] = (changes[name] ?: 0) + 1
                }
            }
            else -> Unit
        }
        for ((n, v) in lastVal) watches[n] = WatchInfo(v, (changes[n] ?: 0).coerceAtLeast(1))

        // ---- checkpoints (как было) ----
        val checkpoints = timed.mapNotNull { te ->
            val e = te.event
            if (e is TraceEvent.Call && e.kind == "CHECKPOINT") Checkpoint(te.at, e.name) else null
        }

        // ---- timeline sample (как было) ----
        val timelineSample = timed.take(sample).map { te ->
            fun renderPath(root: String, path: List<Any>): String {
                if (path.isEmpty()) return root
                val sb = StringBuilder(root)
                for (seg in path) {
                    when (seg) {
                        is Int, is Long, is BLBigInt -> sb.append('[').append(seg).append(']')
                        else -> sb.append('.').append(seg.toString())
                    }
                }
                return sb.toString()
            }
            when (val e = te.event) {
                is TraceEvent.Call -> TimelineEntry(te.at, "call:${e.kind}", e.name)
                is TraceEvent.Return -> TimelineEntry(te.at, "ret:${e.kind}", e.name)
                is TraceEvent.Let -> TimelineEntry(te.at, "let", e.name)
                is TraceEvent.PathWrite -> TimelineEntry(te.at, e.op, renderPath(e.root, e.path))
                is TraceEvent.Output -> TimelineEntry(te.at, "output", null)
                is TraceEvent.Read -> TimelineEntry(te.at, "read", e.name)
                is TraceEvent.Enter -> TimelineEntry(te.at, "enter", e.node::class.simpleName)
                is TraceEvent.Exit -> TimelineEntry(te.at, "exit", e.node::class.simpleName)
                is TraceEvent.EvalEnter -> TimelineEntry(te.at, "eval-enter", e.expr::class.simpleName)
                is TraceEvent.EvalExit -> TimelineEntry(te.at, "eval-exit", e.expr::class.simpleName)
                is TraceEvent.Error -> TimelineEntry(te.at, "error", e.message)
            }
        }

        // ---- NEW: human explanations from provenance ----
        val explainLines: List<String> = run {
            val preferred = t.requestedProvenanceKeys()
            val seed = if (preferred.isNotEmpty()) preferred else t.provenanceKeys()
            if (seed.isEmpty()) {
                emptyList()
            } else {
                val closure = LinkedHashSet<String>()
                val queue = ArrayDeque<String>()
                queue.addAll(seed)
                while (queue.isNotEmpty()) {
                    val name = queue.removeFirst()
                    if (!closure.add(name)) continue
                    val explanation = t.explainProvenance(name)
                    if (explanation != null) {
                        @Suppress("UNCHECKED_CAST")
                        val steps = explanation["steps"] as? List<Map<String, Any?>> ?: emptyList()
                        if (steps.isEmpty()) {
                            val deps = t.directDependenciesOf(name)
                            for (dep in deps) {
                                if (dep !in closure && dep in t.provenanceKeys()) queue.addLast(dep)
                            }
                        } else {
                            val deps = t.directDependenciesOf(name)
                            for (dep in deps) {
                                if (dep !in closure && dep in t.provenanceKeys()) queue.addLast(dep)
                            }
                        }
                    }
                }
                closure.take(maxExplains).map { k -> t.humanize(k) }
            }
        }

        return TraceReportData(
            totalEvents = totalEvents,
            duration = duration,
            timelineSample = timelineSample,
            hotspots = hotspots,
            watches = watches,
            checkpoints = checkpoints,
            explanations = explainLines,
            instructions = t.instructionCounts.toMap(),
        )
    }

    fun instructionsPerRow(data: TraceReportData): Double {
        val rows = data.instructions["OUTPUT"] ?: 0L
        if (rows == 0L) return 0.0
        val total = data.instructions.values.sum()
        return total.toDouble() / rows.toDouble()
    }
}
