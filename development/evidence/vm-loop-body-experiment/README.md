# Whole-loop-body JVM compilation experiment

2026-10-17. User-authorized goal mode, GPT-6.1 Sol High implementation agents,
parent-owned profile, independent acceptance and serialized measurements.

## Implemented mechanism

JFR profiling preceded implementation. On large unchanged projection, ordinary
VM leaf samples attributed 28.0% to executeOpcode and 17.1% to run. Previous
prepared execution still attributed 24.1% to its runner and material work to deque
operations. These are sampled method locations including inlined work, not a
removable dispatch-only percentage. See [PROFILE.md](PROFILE.md) and raw JFR.

`JvmLoopCompiler.prepare(bytecode)` recognizes one compiler-shaped comprehension
with at most 128 body instructions and generates one JVM class through pinned
ASM 9.9 (`jvmMain` only). `VM.executeLoopBodies(plan, env)` executes the linear
prefix through INIT once with ordinary dispatch, then the generated loop, then
ordinary suffix. The generated projection method contains native JVM filter and
backedge branches, fixed operation-specific context calls, and no opcode switch,
instruction-array traversal or interpreter delegation. Its Code attribute is
**646 bytes**; the classfile is **1,546 bytes**. Inspectable bytes, javap output
and classfile size reader are retained alongside the source/jar manifests.

This is a JVM code-generation backend for a bounded loop shape. It still uses
canonical operand stack, map bindings, boxed numeric/site helpers, field lookups,
object creation, per-operation source-PC marks and post-observer tracer checks.
It is not SSA/register lowering, unboxed arithmetic or a compiler for every
Branchline program. Asymptotic work stays O(rows × body operations); the experiment
targets constant factors. The ordinary VM run loop and Compiler.kt are unchanged.

Native branches follow the emitted bytecode order, including reverse evaluation
of object fields. At callbacks/failures the canonical stack/env/locals/loop state
are already present; there is no deferred state to reconstruct. A callback that
enables tracing completes once, emits the current Exit with canonical pre-increment
PC, then continues ordinary dispatch at the precise next instruction. No effects
are replayed. Initial tracing/custom root environment/unsupported bytecode has
explicit whole-plan fallback. Empty loops do not claim generated entry.

Nested loops, calls/closures, TRY/suspension/mutations and unsupported control
flow are outside the first slice. Fixed projection must actually enter generated
code. Paths and reduction intentionally fall back; successful controls are not
compiled-loop coverage. Scalar status fields identify actual latest-call entry
and fallback; entry counts are not per-row execution counters.

Plans own bytecode and metadata collection structure but retain constant value/IR
node identities; concurrent mutation is unsupported. One eligible plan owns one
loader/class/kernel/classfile byte array, with **no global class cache**. A cached
context is reused per VM and clears its plan reference in cleanup. VM use is
sequential/nonreentrant, including fallback and tracer.close. Canonical VM state
continues retaining last env/results/error-loop state for snapshot compatibility.
Class unloading is JVM-dependent. See [independent-review.md](independent-review.md).

## Independent correctness acceptance

`gate1.log`: **862 passed, one existing skip, zero failures**, across VM/conformance/
CLI JVM and Node plus JMH jar build. New tests are JVM-only because kernel generation
is JVM-only; common VM changes are covered by existing Node suites. VM counts:
208 JVM /118 Node. Sixteen new JVM tests include 14 independently authored plan/
observation cases and two runtime-owner cases. Exact counts: `test-counts.json`.

Parent `LoopBodyAcceptance.java` passed **299 comparisons**: 80 seeded input sets
with three non-fixture program forms, independent expected outputs and interpreter
comparison where semantic domains agree, ordinary-VM exact errors/env/key order/
snapshots, numeric overflow/null/string cases, outer-loop shadowing, and failure
followed by reuse. Thirty-two fixed fixture configurations each passed three
executions with input immutability and actual-mode checks. Old jar baseline linked
and validated through the new harness. Source hashes and jar were frozen afterward.

Observation tests additionally cover nested custom map/list callbacks, exact
snapshot inside callbacks, JVM BigInteger numeric/truth subclass callbacks,
dynamic trace event parity, branch Exit-callback snapshots, NEXT failure after
completed rows, malformed CFG/operands, source/metadata collection ownership,
unsupported fallback without replay, and reentrancy through tracer cleanup.

## Fair measurement protocol

Four engines: interpreter, direct ordinary VM (`baseline`), prior
`preparedOptimized`, and new `loopBody`. The prior prepared engine is an architectural
reference, not a same-runner optimization-off control. The new slice only replaces
body dispatch/operand decoding with generated calls and JVM control flow; JIT
inlining and code layout can also change. Do not attribute a measured gain to one
machine-level cause without additional evidence.

Unchanged VMFactorialFixture source/input: paths, reduction, holdoutProjection;
small 10 orders ×5 items, large 500 ×25. Projection uses the first item per row;
paths reads only a few fields. Every execution includes identical fresh environment
construction and raw output consumption, with reusable prepared executors. Parsing,
preparation and serialization are excluded. This is not a JSON-to-JSON pipeline
or cold-first-call benchmark.

Main matrix 24 states plus reverse engine-order repeat of 12 large states: JMH 1.36,
one thread, two forks, 3×1s warmup, 5×1s measurements, G1, Xms256m/Xmx768m,
Temurin 21.0.11, Apple M4 Pro/48 GiB. All own heavy processes run serially on a shared
workstation. Reverse ordering does not eliminate all thermal/background/JIT effects.
Preserved previous jar precedes new harness jar for three large ordinary baseline
checks. Exact commands, outputs, hashes and JVM binding are retained.

Preparation and closure fallback use separate shorter 2×500ms warmup/4×500ms
measurement protocols, two forks. Preparation includes parsing, compilation and
new class definition on every eligible call, without a cache. Its allocation and
metaspace/class-loader churn are part of the cost. Profiled timing is excluded
from main comparisons. After-change JFR uses the same profiling protocol as before.

Memory uses projection, 24 fresh JVMs, three repeats per engine/lifetime, alternating
engine order, Xms32m/Xmx768m/G1, external /usr/bin/time -l RSS. Reuse prepares one
executor and runs it 1,000 times; many prepares 1,000 independent executors and runs
each once. All share one input. Five requested-GC stages separate primed fixture,
prepared-but-unexecuted plans, executed engines/results, executor release, and final
output/weak-reference release. Prepared-only heap is distinct from executed-state
heap: VMs may retain a result tree per executor, beyond the single explicit last
output root. Non-heap used/committed pools and process class counts are captured
separately. Wrapper collection is not a proof of generated-class unloading; process
unload counts do not identify particular loaders. Classfile byte lengths are neither
loaded metaspace nor total heap. See [harness.md](harness.md).

## Results and decision

**The generated body improves large projection versus ordinary VM, but does not
establish an advantage over interpreter. Keep opt-in.** The bounded goal is
complete. The predeclared >=15% improvement in both orders was not met, and
preparation/plan memory costs increased. Full raw-derived tables and JMH intervals:
[TABLES.md](TABLES.md); ratios: `effects.csv`.

Large execution time in microseconds/op. Cells are forward / reverse means,
not ranges of individual samples or pooled estimates:

| Workload | Interpreter | Ordinary VM | Prior prepared | Generated loop |
| --- | ---: | ---: | ---: | ---: |
| paths | 0.283 / 0.277 | 0.417 / 0.404 | 0.472 / 0.545 | 0.421 / 0.425 |
| reduction | 25.67 / 28.07 | 39.53 / 38.49 | 29.95 / 28.91 | 38.96 / 38.91 |
| projection | 106.49 / 110.82 | 124.17 / 123.49 | 123.26 / 128.66 | 104.22 / 109.65 |

Projection time falls **16.1% / 11.2% versus ordinary VM** and 15.4% / 14.8%
versus prior prepared execution. Individual JMH 99.9% intervals for generated
versus ordinary VM do not overlap in either series, supporting a repeatable
benefit on this workstation/workload. However, the required >=15% benefit did not
repeat. Conservative endpoint ratios (new upper / baseline lower) are 0.849 /
0.958; these are sensitivity checks, not formal ratio confidence intervals.

Against interpreter the generated means are just **2.1% / 1.1% lower**, with
overlapping intervals in both series. That is not a demonstrated speed advantage
or a formal equivalence result. Small projection is 1.316 us versus VM 1.341 and
interpreter 1.119: approximately ties VM and is slower than interpreter. There is
no demonstrated general small-input benefit.

Fallback paths time is 1.1% / 5.4% above VM on large inputs; reduction is 1.4%
lower /1.1% higher. Paths intervals overlap; >5% regression is not repeatable
across orders, but the measurements also do not certify a strict <=5% upper
bound. Small controls approximately tie direct VM. These are ordinary-VM fallback
results, not a generated reduction implementation. Closure fallback under the
shorter protocol is 0.228 us/824 B versus direct VM 0.219 us/824 B, with overlapping
intervals. The previous prepared mode remains faster on large reduction.

Execution allocation equals ordinary VM on every primary case to profiler
precision: large projection **128,280 B/op**, versus interpreter **193,248 B/op**.
That 33.6% allocation advantage already exists in ordinary VM; code generation
adds no allocation saving. Prior prepared projection uses 128,248 B/op. Fractional
sub-byte differences are normalization noise, not additional object allocations.

## Preparation and memory cost

Projection source-to-executor preparation, including a fresh class per call:

| Engine | Preparation us | Allocated B/preparation |
| --- | ---: | ---: |
| Interpreter | 1.714 | 13,456 |
| Ordinary VM | 3.251 | 20,732 |
| Prior prepared | 5.844 | 29,624 |
| Generated loop | 50.20 | 64,670 |

Generated preparation is about 15.4x ordinary VM and allocates about 42.9 KiB more.
A simple warm-cost calculation puts the extra 46.95 us against 13.83–19.95 us saved
per large execution (roughly 3–4 warm executions); this is **not a measured cold
break-even point**. First-call/class initialization and per-generated-method JIT
warmup are not included in that calculation. Execution allocation is unchanged
versus VM, so there is no per-call allocation saving to repay preparation bytes.

For 1,000 independently prepared executors, medians of 3 fresh JVMs:

| Memory observation | Interpreter | Ordinary VM | Prior prepared | Generated loop |
| --- | ---: | ---: | ---: | ---: |
| Prepared-only incremental heap, MiB | 3.209 | 6.069 | 9.145 | 9.468 |
| After execution incremental heap, MiB | 3.686 | 117.11 | 120.48 | 120.52 |
| Prepared incremental Metaspace used, MiB | 0.127 | 0.267 | 0.328 | 3.505 |
| Peak process RSS, MiB | 190.77 | 261.47 | 305.69 | 304.27 |

Generated prepared-only heap is about 56% larger than ordinary VM, plus substantially
more Metaspace. Its prepared Metaspace committed delta is 6.44 MiB; classfile byte
sum is 1,546,000 bytes and is not a substitute for either memory measure.
Those retained inspection byte-array payloads alone account for about 1.47 MiB
in the generated plans; this is measured prototype overhead, not an unavoidable
lower bound for a future production compiler. Removing retention would need a
new version and measurement. The raw
non-heap pool reports are separate observations, not quantities to blindly add.

The large executed-heap difference versus interpreter reflects **VM retention of
per-executor result/state trees for snapshots**. It is not the size of generated
code or a generic per-record working-set claim. With one reused executor, executed
heap deltas are about 0.115–0.122 MiB across engines; peak RSS medians vary materially
with engine/JIT/GC and must not be confused with live heap. Ranges and all stages
are retained in memory-raw.csv/logs.

All expected wrapper weak references were collected after release in all 24 probes.
For generated plans, process unload counts increased by 1 / 1000 for reuse/many in
each repeat, consistent with generated-class unloading. Process counters do not
identify exact loaders and do not prove every lifetime is leak-free. Class/cache
sharing, thousands of distinct source programs and long-running production churn
were not measured. There is no global generated-class cache in this prototype.

## Interpretation and next architectural boundary

After-profile JFR has 498 worker samples: 17.5% at LoopContext.gt, 26.1% across
ArrayDeque methods, 7.4% at narrowInteger and 4.6% at the PC setter. The ordinary
body dispatcher no longer dominates this recording. Sampling attribution includes
inlined work and is not a removable-cost budget. This supports the narrower
conclusion: **whole-body code generation helps, but keeping canonical per-operation
stack/helper/state work limits the gain**. The remaining numeric/type behavior and
result construction also cost time.

The next separately authorized hypothesis would be lowering expressions into JVM
locals with typed guarded arithmetic and compiler-derived spill/state maps at
observer/error boundaries. That would remove stack traffic and many PC writes;
it requires new correctness proof and is not implemented or known faster here.
Reusing a generated immutable plan could reduce preparation/metaspace churn in a
stable-transform deployment, but caching/invalidation/concurrent use is a distinct
experiment. Do not expand into these changes automatically or enable this mode
for general workloads on the evidence above.

## Baseline and artifact integrity

Preserved old ordinary VM large paths/reduction/projection measured 0.460 / 39.485 /
126.165 us. Current ordinary VM results are mixed or lower, with no stable slowdown
shown; this is an incidental-change check, not proof of identical machine code.
Only existing VM.kt and vm/build.gradle changed in this slice; 7 new runtime/test/
harness files were added. Original Compiler.kt and workload sources are unchanged.

All 30 serialized measurement commands succeeded: 51 JMH states across old-baseline,
main/reverse/preparation/fallback protocols, 24 memory processes and one after-JFR
state. The initial 3 before-profiles are recorded separately. All 72 primary fork
fixture checks report the required actual modes. Final verification matched 77
source hashes and the benchmark jar (`freeze-verification.json`). No product
changes were made after the freeze. No default enablement, commit or publication.

## Reproduction

[Plan](../../planning/vm-loop-body-experiment.md), [harness](harness.md), and
`development/service/VERIFY.md` contain commands and acceptance boundaries.
`measure.py` serializes measurements; `summarize.py` and `report_tables.py` derive
tables; `profile_summary.py` derives worker-sample profiles. Do not run builds or
other CPU-heavy probes alongside timing. No result establishes JS execution speed,
multicore scalability, arbitrary-program coverage or full-pipeline advantage.
