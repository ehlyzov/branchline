package io.github.ehlyzov.branchline.debug

/**
 * Simple, portable humanizer for CollectingTracer.
 * Produces a compact textual summary using TraceReport data so tests can print it.
 */
fun CollectingTracer.humanize(title: String? = null): String {
    val rep = TraceReport.from(this, sample = 32, maxExplains = 8)
    val sb = StringBuilder()
    if (title != null) sb.append(title).append(':').append(' ')
    sb.append("events=").append(rep.totalEvents)
    sb.append(", duration=").append(rep.duration)
    if (rep.checkpoints.isNotEmpty()) {
        sb.append("\ncheckpoints:")
        for (c in rep.checkpoints) {
            sb.append("\n  - @").append(c.at).append(' ').append(c.label ?: "")
        }
    }
    if (rep.hotspots.isNotEmpty()) {
        sb.append("\nhotspots:")
        val top = rep.hotspots.take(5)
        for (h in top) {
            val name = h.name?.let { ":$it" } ?: ""
            sb.append("\n  - ").append(h.kind).append(name)
                .append(" calls=").append(h.calls)
                .append(" total=").append(h.total)
                .append(" mean=").append(h.mean)
        }
    }
    if (rep.explanations.isNotEmpty()) {
        sb.append("\nexplains:")
        for (e in rep.explanations) sb.append("\n  - ").append(e)
    }
    return sb.toString()
}

