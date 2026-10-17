# VM trace bookkeeping diagnosis and bounded change

The preserved native JMH baseline is recorded in
`development/evidence/vm-interpreter-comparison/README.md`. The interpreter is
1.92–2.47 times faster on its nine native pairs, although the VM allocates less
on some workloads. These results alone do not identify a mechanism.

Source inspection identifies unconditional trace work in the VM execution
path. Before this slice, `emitEnterEventsAt` and `emitExitEventsAt` looked up
their integer-keyed metadata maps before checking for a tracer. Both helpers
run for every successful opcode. `emitPathWriteIfNeeded` similarly looked up
metadata before checking the tracer, and `storeVar` always read the old
environment value even when no tracing would consume it.

For the native `typicalTransform`, the compiler emits approximately eight
opcodes per input order: `LOAD_LOCAL`, `LOAD_VAR`, `ACCESS_STATIC`, `ADD`,
`DUP`, `STORE_VAR`, `STORE_LOCAL`, `NEXT_FOREACH`. That creates sixteen
Enter/Exit metadata map probes and one PathWrite probe per order before this
change, plus the store's old-value read. The interpreter executes this simple
loop body through `execObjectNoReturn`, without the VM operand stack or the
equivalent sequence of dispatches. This is a constant-factor difference;
both paths still scale linearly with the number of orders.

Read-only `javap` inspection of the preserved compiled VM found the last JVM
bytecode offsets of `executeOpcode` and `run` at 2570 and 577 respectively.
The dispatcher is large enough that ordinary hot inlining is unlikely, but
this is not proof of a JIT decision. No new opcode, dispatch redesign, or
compiler change is part of this first slice.

The change checks tracer presence and step options before metadata access.
For stores without tracing it skips the old-value read only for exact
`HashMap` and `LinkedHashMap` environments. A cached environment classification
is refreshed on every environment replacement, including function entry and
unwinding. Custom map subclasses and delegated maps retain their original
get/put order: either callback may change `Debug.tracer`, so emission still
resolves the current tracer after the write. Ambient tracing is not cached
across instructions or host calls; a host enabling tracing can still produce
the current instruction's Exit event.

`VMTraceFastPathTest` covers host activation/deactivation, explicit-tracer
precedence, instruction counts, Enter/Exit/Read/Let/PathWrite values, custom
environment old-value reads without tracing, and activation during custom
get/put callbacks, and disabling tracing from a Let event callback before the
subsequent PathWrite event. The implementation agent ran `git diff --check`; build,
runtime tests, profiling attribution, and before/after JMH verification belong
to the parent acceptance gate and are not claimed here.

## Dispatch experiments and final source

Two bounded dispatch experiments were measured separately by the parent and
rejected. Inlining the entire dispatcher increased exploratory large
`typicalTransform` from about 39.473 us for trace-only to 48.515 us. Moving ten
unchanged common opcode cases to a smaller inline switch also regressed the
exploratory large workloads: paths 0.595 us versus 0.366 us, typicalTransform
47.279 us versus 39.473 us, and comprehensions 41.806 us versus 38.356 us.
These short exploratory runs select the implementation; they are not the
matched final benchmark gate or proof of a specific JIT mechanism.

The final implementation restores the exact trace-only source; neither
dispatch experiment remains in product code. `VM.kt` SHA-256 is
`4fea7d8220392e87b408406405f04992711c2884548c5c4f3a66c11d392a2d49`.
All six focused common tests remain. The parent owns full correctness gates
and matched longer before/after measurements.

The stack VM still dispatches about eight instructions per order for the
native reduction, including local/environment mirroring. Removing avoidable
trace metadata work does not remove that execution-model overhead. Further
instruction fusion would change a wider compiler/bytecode surface and is
outside this accepted slice; no claim of interpreter parity follows from
the source optimization or these exploratory timings.
