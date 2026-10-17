---
status: Implemented
depends_on: ['planning/vm-hotpath-optimization']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# VM slots and instruction-fusion factorial experiment

User authorized GPT-6.1 Sol agents to implement independent slot and fusion variants, compare baseline/slots/fusion/both against the interpreter, with parent-owned correctness, timing and memory acceptance. Existing dirty files are accepted earlier work in this chat; preserve all unrelated edits and deep-research-report*.md. No default optimization enablement, commits or publication.

1. Preserve source/hash/jar baseline before product edits. Agree bounded experimental API and ownership. Keep default VM behavior and external bytecode format unchanged where feasible. Report effective mode, support/fallback reason and mechanism counts; unsupported programs must not silently count as optimized execution.
2. Independent flags: slots=false/fusion=false is current VM; slots=true/fusion=false, false/true, true/true are the factorial treatments. No numeric specialization, parser rewrite or additional optimization mixed into one treatment. Shared preparation changes must be identifiable and checked against preserved baseline.
3. Correctness before timings: independent expected values and differential outputs, final environment, alias/input immutability, numeric overflow/mixed types, null/missing, loop shadowing/restoration, errors/partial effects, host calls, tracing activation, custom maps, closures, suspension/stepping. Conservative explicit fallback is acceptable for unsupported cases, disclosed separately. Never replay effects after runtime failure.
4. Parent owns sequential CPU-heavy runs. JVM/Node VM and conformance gates plus affected integration tests; inspect actual XML counts. A separate parent acceptance probe checks new modes and negative sensitivity. Agents do not run builds/benchmarks without coordinating ownership.
5. Main paired JMH: same prepared Map/List data, environment reset, result consumption, compilation exclusion, heap/JDK/forks/warmup for interpreter and all four VM variants. Cover unchanged native paths/comprehension/reduction plus independent holdout workload(s), sizes and fallback controls. Measure preparation separately. Use GC allocation B/op separately from fresh-process post-GC heap-used and peak RSS. Do not infer memory advantage from B/op alone.
6. Decision: report per-case treatment effects and interactions, not only winning aggregate. Proposed strong advantage criterion: VM time <=0.8 * interpreter on preregistered supported workloads, repeatable, no supported workload regression >5%, correctness intact. This is an experiment, not a promise of reaching threshold. Keep unsuccessful variants opt-in and report limits. Include old-vs-new baseline check for incidental runtime changes.
7. Record all commands, raw measurements, machine/JVM/source bindings, gate results and contour changes; update INDEX and final report.

## Implemented treatment boundaries

New VM.prepareExperimental / executeExperimental API is opt-in. Public bytecode format and compiler
are unchanged; ordinary VM.execute remains the reference. Prepared bytecode must remain immutable
for a plan's lifetime and VM instances remain thread-confined.

Slots: prepare name-to-index and local-alias tables, defer ordinary environment map writes, use
slots for loop variables, and eliminate eligible DUP/STORE_VAR/STORE_LOCAL mirrors. The treatment
therefore includes removal of redundant mirror instructions as a consequence of unified storage;
it is not a pure map-versus-array microbenchmark. Flush/materialize at host, observer, error and
final boundaries. Preserve absent versus null, insertion order, original local capacity, and stale
local versus live named binding behavior after host mutation. No numeric specialization is added.

Fusion: prepare groups for LOAD_VAR/LOCAL/SCOPE + static accesses and DUP/STORE_VAR/STORE_LOCAL.
Keep canonical source PCs and all incoming jump boundaries. Fusion-only still performs both stores;
combined mode gives slot mirror elimination precedence. Overlap means treatment effects need not
multiply. Guards/helper calls remain; this is a bounded prototype, not an optimal register engine.

Unsupported bytecodes (including closure/user call/TRY/suspension and several mutations) fall back
before execution. Custom root environments/tracing also fall back. Mid-execution observers stop at
the current original instruction after flushing state, then continue baseline VM without replay.
Host barriers materialize locals, flush named values and invalidate cached names after invocation.
Actual effective flags/fallback are reported; diagnostic counters are enabled only in validation.
All timed runs disable counters. Final reporting/frame/guard overhead remains part of treatment cost.

## Correctness gate

Parent final command: ./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest
:conformance-tests:jsNodeTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar --console=plain.
Success in 8s: 798 reported tests, 797 passed, one existing conformance load-test skip, no failures.
The initial broader attempts found a common-test type inference issue and a test fixture using
unsupported NOP snapshot serialization; only tests were corrected, no serialization expansion.

Parent FactorialAcceptance.java passed 1,016 combinations: 40 seeded input sets, independent expected
results checked against interpreter/current VM, all four flags with diagnostics on/off, exact
output/error/final environment/key order/snapshot comparisons, overflow/null/missing/shadowing and
fallback controls. Closure environments are cyclic: the parent comparator checks capture identity
and functional results rather than recursively comparing LambdaValue.equals. Closure snapshots
remain outside the existing serializer's supported domain.

Negative sensitivity: the same parent check failed on the preserved initial prototype because
slot-only final snapshots omitted local state. Runtime now materializes the original locals and
preserves their geometric capacity. See parent-before.log versus parent-after2.log.
Independent agent tests additionally cover custom Map/List callbacks, dynamic tracing, exception
identity/no duplicate effects, host-time snapshots, null/presence/order and both host barriers.
The fixture probe passed all 60 engine/workload/size combinations (three executions each), including
explicit closure fallback; primary cases require both requested mechanisms effective and no fallback.

## Measurement contract

Prepared Map/List input is common to all engines. Small=10 orders x 5 items; large=500 x 25.
Five primary programs: unchanged native paths/comprehension/reduction plus preregistered arithmetic
projection and deeper path holdouts. The separate closure case is a fallback control, not evidence
of optimized execution. Fixture expected outputs are computed independently from construction rules.

Timed execution includes fresh environment creation uniformly and returns raw output consumed by
JMH. It excludes input parsing, program preparation and serialization. Do not compare absolute
times directly to older native runs whose environment reset was outside timing. Preparation is a
separate source-to-executor benchmark. Baseline calls VM.execute directly; no experimental report
or eligibility work is charged to baseline. An old-jar-first-classpath run checks incidental change
against the exact preserved original VM using the same new harness.

Primary matrix: JMH1.36, one thread, 2 forks, 2x500ms warmup and 4x500ms measurement, Java21.0.11,
-Xms256m -Xmx768m -XX:+UseG1GC, Apple M4 Pro. CPU-heavy commands run serially; raw CI/iterations and
commands retained. Fixed engine order is not randomized; use longer reverse-order confirmation for
representative conclusions. No JS speed or multicore-throughput inference follows from JVM results.

Memory: three fresh JVMs per engine/lifetime, alternating engine order, -Xms32m -Xmx768m/G1GC.
Reuse keeps one prepared executor for 1,000 executions; many keeps 1,000 independent prepared
executors, each run once. Shared resident input and last output are matched. Post-requested-GC heap
used is an approximate process observation, not exact object graph retained size. Peak RSS is from
/usr/bin/time -l and includes cumulative compilation/JIT/native peaks. Allocation B/op, heap-used
and RSS are separate outputs; weak references establish only executor-wrapper collection.

## Final decision

Experiment complete; correctness accepted within the documented supported domain.
Performance promotion rejected. All 50 exploratory execution states, 10 preparation
states, five fallback states, 30 fresh-process memory probes and 15 longer reverse-order
confirmation states completed successfully. Runtime/harness source hashes and jar
hash remained frozen throughout measurements.

Longer large-workload confirmation (paths/comprehension/reduction, microseconds/op):
interpreter 0.269/21.25/26.62; current VM 0.386/36.61/36.44; slots 0.647/45.11/36.60;
fusion 0.473/48.29/43.36; both 0.509/43.48/36.68. The exploratory 8–9% reduction
gain did not repeat; no speed win is accepted. Combined mode is 1.38–2.05x the
interpreter's time, with paths/comprehension regressions versus current VM.
All treatments also increase allocation and prepared-executor memory versus
current VM in the measured cases. The strong-advantage gate is not met.

Keep flags opt-in. A potential next experiment is a single-dispatch prepared
basic-block runner with resolved indices and an unoptimized-new-runner control;
the present data does not prove slot/fusion architecture can never win.
See [final report](../evidence/vm-slots-fusion-experiment/README.md) and
[all measured tables](../evidence/vm-slots-fusion-experiment/TABLES.md).
Service-map/verification changes and regenerated contour cover the new API,
fallback contract and reproducible acceptance workflow. No default enablement,
commit, publication, JS-speed claim or multicore claim is included.
