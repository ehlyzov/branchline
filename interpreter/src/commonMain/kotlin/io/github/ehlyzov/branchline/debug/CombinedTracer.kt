package io.github.ehlyzov.branchline.debug

class CombinedTracer(private vararg val tracers: Tracer) : Tracer, AutoCloseable {
    override val opts: TraceOptions = tracers.map { it.opts }.reduce(::mergeOptions)
    override fun on(event: TraceEvent) { for (t in tracers) t.on(event) }
    override fun shouldBreak(): Boolean = tracers.any { it.shouldBreak() }
    override fun close() { /* no-op in common */ }
}

private fun mergeOptions(a: TraceOptions, b: TraceOptions): TraceOptions = TraceOptions(
    step = a.step || b.step,
    watch = a.watch + b.watch,
    captureValues = a.captureValues || b.captureValues,
    maxEvents = maxOf(a.maxEvents, b.maxEvents),
    includeEval = a.includeEval || b.includeEval,
    includeCalls = a.includeCalls || b.includeCalls,
)

