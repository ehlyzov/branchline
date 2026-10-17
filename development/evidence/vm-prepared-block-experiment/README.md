# Compiler-prepared VM experiment

2026-10-17. Goal-mode bounded implementation by GPT-6.1 Sol High agents; parent
owns independent acceptance, serial measurements and the final decision.

## Implemented scope

New opt-in API: `VM.prepareBlocks(bytecode, optimize)` and
`VM.executeBlocks(plan, env)`. The ordinary VM remains the default. One prepared
dispatch serves both control and optimized plans. Effective execution/fallback
is exposed through scalar fields, without per-call diagnostic report allocation.

Preparation is a lowering pass **after the existing compiler**, whose source is
unchanged. It copies the bytecode structure, resolves operands, computes basic
blocks/control-flow entry points and classifies possible observers. Optimized
plans additionally resolve named slots/local aliases, eliminate eligible mirror
stores and emit direct static-path operations inside blocks. Runtime no longer
recognizes those source patterns. Paths keep intermediate values outside the
operand stack and reconstruct the canonical stack only at deoptimization points.

The optimized frame is reused per VM/last-plan identity. Linked insertion-order
indices replace per-flush filter/sort allocations. Frame references are cleared
in finally, including failures. The canonical VM still intentionally retains its
last environment/locals/stack for compatibility; this is not a claim that all
input references are released after a call.

This is **not** a complete register VM, SSA optimizer, general liveness/escape
analysis or type-specializing compiler. Most arithmetic still uses the canonical
operand stack and numeric helpers. Basic blocks constrain lowering; runtime
dispatches prepared operations indexed by original PCs, rather than generated
machine code or one compiled function per block. Opcode effects and a bounded
write census establish the supported transformations; actual nested containers
and observable scalars still require guards. See `independent-review.md`.

Plans own opcode/operand/offset arrays and metadata collection structure;
constant values and trace-node objects retain identity. Concurrent mutation of
those objects is unsupported. VM use is sequential and nonreentrant, including
fallback and tracer cleanup. Unsupported operations/tracing/custom root maps
fall back before execution; dynamically encountered observers resume ordinary
dispatch at the exact source PC without replay. Host boundaries preserve final
environment order, local/name divergence and snapshots. Public bytecode format,
parser, numeric representation, default execution and CLI are unchanged.

## Correctness acceptance

Final gate `gate5.log`: **846 passed, one existing skip, zero failures** across
VM/conformance/CLI JVM and Node. VM specifically has 192 JVM and 118 Node tests.
Counts are saved in `test-counts-final.json`. New coverage includes 23 common tests
and three JVM-only bignum-subclass tests.

Parent `PreparedBlockAcceptance.java`: **538 comparisons passed**, with 80 seeded
input sets and independently calculated expected results against interpreter and
ordinary VM, both prepared modes, exact errors/final environments/key order/
snapshots, input immutability, error→success reuse, host-time snapshots/mutations
and host failures. `parent-acceptance-final.log` records the result. The independent
probe is distinct from implementation-agent fixture expectations.

All **32 fixed fixture configurations** passed three executions each, including
closure fallback. Primary cases must execute the requested new mode without
fallback; optimized mode must report actual optimized work and positive static
prepared sites. Static sites are not dynamic instruction counters.

Issues found before freeze: custom-list tracing activation on NEXT_FOREACH;
JVM BigInteger/BigDecimal subclass callbacks; nonreentrancy through fallback and
tracer.close; and JVM/JS differences in malformed operand reads. Corrected in
the new runner/planner and covered by regressions. Intermediate build failures
also included missing Kotlin conversion overrides in JVM test subclasses and a
stale expected observation-PC list. They are retained in gate1–gate3 logs; the
gate4 passed, and gate5 passed again after the bounded structural correction
below. Existing snapshot value-domain restrictions remain.

## JIT eligibility correction

The first frozen candidate exposed a concrete implementation problem during
initial timing: Kotlin expanded the invocation's finally across early returns,
making executeBlocks 8,935 bytes of JVM code. Prepared-control small reduction
took about 13.2 us. Same-jar diagnostics changing only DontCompileHugeMethods
produced 12.917 us with the default restriction versus 1.481 us without it.

The parent stopped and archived that incomplete matrix, then authorized a
bounded extraction of the unchanged hot loop and cleanup into private methods.
The hot method is now 3,120 bytes. No additional guards, patterns or optimizations
were introduced. All correctness gates/probes were repeated, sources refrozen,
and the **entire final matrix restarted with ordinary JVM flags**. Initial data
is separate in [initial-candidate](initial-candidate/README.md); it is diagnostic
evidence and is not pooled with final results. This correction is documented
explicitly because it followed early measurement rather than the initial freeze.

## Fair comparison and boundaries

Four engines: interpreter; current ordinary VM; the new prepared runner without
transformations (`preparedControl`); the same runner with them
(`preparedOptimized`). Differences between prepared modes include the optimized
frame and its guards/materialization; control does not pay for an unused frame.
No numeric specialization, JIT, parser rewrite, selective JSON or concurrency is
mixed into the treatment. Baseline directly calls ordinary VM, not a disabled
wrapper. Original source programs/inputs come from unchanged VMFactorialFixture.

Primary workloads are paths, reduction and holdoutProjection. Small input has
10 orders × 5 items; large has 500 × 25. Paths access only a few fields even on
large input. Each operation includes identical fresh environment construction
and raw output consumption; JSON parsing/serialization and preparation are
excluded. Preparation is measured separately. This is an engine-stage comparison,
not a complete JSON-to-JSON pipeline benchmark.

Primary forward matrix: 24 states. Reverse engine-order confirmation: 12 large
states. Both use JMH 1.36, one thread, 2 forks, 3 × 1 s warmup, 5 × 1 s measurement,
G1, -Xms256m -Xmx768m, Temurin Java 21.0.11 on Apple M4 Pro/48 GiB. Exact arguments
and exits are in `commands.json`; machine/JVM/jar bindings in `environment.json`.
All own CPU-heavy work is serial. This is a shared workstation, not an isolated
or CPU-pinned laboratory. Order reversal helps detect variability but does not
remove all host-state confounding.

Preserved original VM jar precedes the new harness jar on the classpath for an
incidental-default-change check. `before-environment.json` records its hash and
local location; it is not committed. Only VM.kt changed among previous sources;
eight source/test/harness files were added (`source-delta.json`). Neither prior
experimental runtime/harness nor Compiler.kt was rewritten. Full before/final
source manifests bind measurements to the dirty worktree, not HEAD alone.

Preparation (8 states) and closure fallback (4 states) use a separate shorter
2 × 500 ms warmup/4 × 500 ms measurement protocol, two forks, same heap. Do not pool
their absolute timing with the primary long runs or claim optimized execution
from a successful fallback result.

Memory: 24 fresh JVMs, three repeats per engine/lifetime, alternating order,
-Xms32m -Xmx768m/G1. Reuse retains one executor for 1,000 executions; many retains
1,000 independently prepared executors, each executed once. All share the same
large reduction input and retain the last output. This does not measure 1,000
workers sharing one plan. Post-requested-GC heap is approximate process state;
peak RSS includes preparation/JIT/native peaks. Weak references show wrapper
collection only. Allocation B/op, heap deltas and RSS are separate measurements.

## Results and decision

**Do not promote the new runner to default.** The bounded experiment is complete:
correctness passed, a repeatable large-reduction benefit exists, but the joint
performance gate failed. All four engines remain available for reproduction;
ordinary execution remains the default. Full values, JMH uncertainty, allocations
and memory ranges are in [TABLES.md](TABLES.md); ratios are in `effects.csv`.

Large execution time, microseconds/op. Each cell shows forward / reverse means,
not confidence limits and not a pooled estimate:

| Workload | Interpreter | Ordinary VM | Prepared control | Prepared optimized |
| --- | ---: | ---: | ---: | ---: |
| paths | 0.277 / 0.286 | 0.399 / 0.440 | 0.549 / 0.551 | 0.468 / 0.445 |
| reduction | 26.45 / 26.45 | 39.29 / 39.75 | 34.91 / 41.85 | 29.03 / 29.19 |
| holdoutProjection | 104.36 / 103.44 | 120.37 / 129.53 | 129.76 / 132.67 | 128.93 / 121.54 |

- Reduction: optimized means are **26.1% / 26.6% lower than ordinary VM**, but
  **9.8% / 10.4% higher than interpreter**. The transformations reduce time
  against the same prepared control by 16.8% / 30.3%. This is evidence for useful
  work moved to preparation on this workload, not a general VM victory.
- Projection: optimized is 7.1% slower / 6.2% faster than ordinary VM. Neither
  series reaches the required 15% benefit; direction changes with run order.
- Paths: optimized is 17.3% / 1.0% slower than ordinary VM. The <=5% regression
  criterion is not repeatably met. Small paths is 35.7% slower, small projection
  18.2% slower, and small reduction approximately ties ordinary VM.
- The new runner without transformations is itself not a stable improvement:
  large reduction changes from 11.1% faster to 5.3% slower than ordinary VM;
  paths and projection are slower in both runs. Do not attribute all differences
  solely to resolved operands or a particular compiler pass without profiling.

The three-part speed gate therefore fails without needing to choose a favorable
run or make a borderline confidence decision. Reduction's repeatable mean gain
is useful but not a guaranteed >=15% lower bound: the conservative upper ratio
formed from individual JMH interval endpoints is 0.772 forward / 0.860 reverse.
Those endpoint ratios are sensitivity checks, not formal ratio confidence
intervals. The workstation and between-series effects remain limitations.

Allocation is equal on paths (704 B/op) and lower by 32 B/op on reduction and
projection for optimized versus ordinary VM. Sub-millibyte differences are GC
profiler normalization noise, not additional object allocations. On large inputs:

| B/op | Interpreter | Ordinary VM | Prepared control | Prepared optimized |
| --- | ---: | ---: | ---: | ---: |
| reduction | 8,736 | 8,688 | 8,688 | 8,656 |
| projection | 193,248 | 128,280 | 128,280 | 128,248 |

Most of the allocation advantage over interpreter already belongs to ordinary
VM. This experiment adds only the 32-byte saving; it does not establish a new
large memory advantage.

Preparation costs more. Large reduction source-to-executor preparation is
3.117 us / 19,992 B for ordinary VM versus 5.490 us / 27,848 B for optimized;
projection is 3.152 us / 20,724 B versus 5.787 us / 29,604 B. Warm reduction savings
can amortize that time, but this is not a measured cold first-call or JSON pipeline
result. The extra reduction preparation allocation alone requires about 246
executions to offset 32 B/op, and prepared live memory remains larger regardless.

Three fresh-process memory repeats found median incremental heap for 1,000
independently prepared executors of **2.554 / 6.161 / 8.221 / 8.603 MiB** across
interpreter / ordinary / control / optimized. Optimized is about **40% larger**
than ordinary. Corresponding peak RSS medians are 124.70 / 135.98 / 141.05 /
163.45 MiB. For one reused executor the approximate heap deltas are 0.034 /
0.038 / 0.040 / 0.043 MiB; small deltas are not precise object-size estimates.
All expected wrapper weak references were collected after release. These data
show no prepared-executor memory win; they do not prove absence of every leak.

Closure fallback runs ordinary dispatch, approximately 0.223–0.228 us / 824 B/op
for prepared entry versus 0.229 us / 824 B/op direct baseline under the shorter
protocol. It is a compatibility result, not optimized execution.

## Baseline stability and final artifact checks

The preserved old VM measured large paths/reduction/projection at
0.456 / 36.808 / 124.394 us. Current baseline forward/reverse results vary with
workload and order; the first reduction comparison suggested a possible 6–8%
incidental slowdown. One bounded auxiliary check followed the complete matrix,
using the same long protocol, current first then old, without source changes.
Current reduction was 38.758 +/-1.112 us; old observations were
39.044 +/-0.966, 37.859 +/-1.071, 39.355 +/-0.618 and 39.567 +/-0.207 us
(JMH 99.9% half-widths). This does not substantiate a stable baseline regression.
It also does not prove identical machine-code performance on arbitrary programs.

The auxiliary old command repeated existing fixed `-p` parameters; JMH therefore
emitted four identical parameter states instead of one. All four are retained in
`baseline-check-before.json`, with exact commands in `baseline-check-commands.json`.
This unbalanced diagnostic is not pooled into the balanced primary comparison,
used to choose a favorable baseline, or counted as additional treatment evidence.

Final `freeze-verification.json` verifies 68 source hashes and the benchmark jar,
51 primary/auxiliary-protocol JMH states in the original serial matrix, 72 primary
fixture-validated forks, and 24 successful fresh-process memory probes. The
additional baseline diagnostic is listed separately. All main and auxiliary
commands exited successfully. Source files remained unchanged throughout final
measurements. Correctness was accepted before the freeze, not inferred from speed.

## What to take forward

The supported conclusion is narrow: preparation-time slot/mirror/path decisions
help large reduction, but this generic guarded executor does not consistently
beat existing VM and still loses to interpreter. The earlier huge-method issue
was directly diagnosed and repaired; residual attribution among dispatch, stack,
guards, materialization and JIT choices is unmeasured.

A next separately authorized slice should first profile the projection/control
costs. The architectural hypothesis is to lower an entire loop body into a
smaller execution unit, with statically proven effects and observation boundaries,
rather than retain one generic prepared dispatch per original operation. A
specialized JSON-only domain or immutable/versioned host bindings may permit
coarser guards, but must be explicit and tested; current arbitrary-host semantics
cannot simply be dropped. None of those approaches is implemented or claimed
fast by this experiment. Do not add more slot/path patterns by inertia.

## Reproduction

See [harness.md](harness.md), [plan](../../planning/vm-prepared-block-experiment.md)
and `development/service/VERIFY.md`. `measure.py` runs the serial measurement
protocol after gates/source freeze; `summarize.py` produces CSVs and
`report_tables.py` produces the full tables after all measurements complete.
Do not run concurrent builds/probes during timing. No result establishes JS
speed, multicore throughput or performance on arbitrary unsupported programs.
