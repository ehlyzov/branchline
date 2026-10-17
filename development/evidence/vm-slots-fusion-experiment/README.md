# Slots and instruction fusion: controlled VM experiment

2026-10-17. Implementation and review used GPT-6.1 Sol High agents. The parent
owned independent acceptance, all timed processes and the final decision.

## Status and scope

The experiment is implemented behind explicit `VM.prepareExperimental` and
`VM.executeExperimental` APIs. Default `VM.execute` remains the baseline. No
compiler or public bytecode-format change was made for this experiment. No
default enablement, CLI flag, commit or publication is included.

The implementation, rather than an idealized slot/register architecture, is
the treatment being measured. Slots use prepared name/local mappings, defer
named-map writes, and eliminate eligible compiler mirror stores. Fusion groups
static access chains and mirrored stores while preserving source PCs. Their
overlap is intentional: this is not a pair of mathematically orthogonal changes.
Experimental execution also incurs its own guards, frame and final-report costs.
The baseline calls ordinary VM execution directly. There is no new-loop-only
control, so the data cannot isolate common experimental-loop overhead from the
benefit of each mechanism.

## Correctness acceptance

- JVM/Node VM, conformance and CLI gate: **797 passed, one existing skip, zero
  failures**, 798 reported tests. See `final-gate3.log` and `test-counts.json`.
- Parent-written `FactorialAcceptance.java`: **1,016 combinations passed**,
  including independent expected results for seeded input variations, four flags
  with diagnostics on/off, output/error/final environment/key order/snapshot
  parity and closure/suspension fallback. See `parent-after2.log`.
- Negative sensitivity: that independent check failed on the preserved initial
  prototype's missing final local state (`parent-before.log`). The final runtime
  materializes initialized local aliases with baseline array capacity.
- Independent review found a skipped DUP overflow check in mirror elimination;
  the runtime agent corrected it before the final gate. Host boundary,
  custom-container, dynamic tracing, exception identity and no-replay cases are
  covered in common tests. See `independent-review.md`.
- Fixture validation passed all **60 engine/workload/size configurations**,
  three executions each. Primary measurements require requested mechanisms to
  be effective with no fallback. Closure fallback is measured separately.

This is bounded differential acceptance, not a proof for every legal program.
Prepared bytecode must remain unmodified and VM instances are thread-confined.
Unsupported operations and observable/custom objects use explicit fallback;
mid-execution fallback resumes the current source PC without replaying effects.
Existing closure snapshot serialization limitations remain unchanged.

## Measurement design

Apple M4 Pro, 48 GiB RAM, macOS arm64, Temurin Java 21.0.11, JMH 1.36. Full
environment, working-tree source hashes and before/final jar hashes are in
`environment.json`, `before-source-manifest.json`, `final-source-manifest.json`.
These are dirty-working-tree measurements, not measurements of HEAD alone.

Five engines: interpreter, current VM (`baseline`), slots, fusion, both. Common
prepared Map/List data: small 10 orders × 5 items, large 500 × 25. The primary
matrix includes unchanged native paths/comprehension/reduction and independent
projection/deep-path holdouts. Fixed path programs access a few fields, so their
large input size is not equivalent to scanning every record.

Execution includes fresh environment construction uniformly and raw result
consumption by JMH. Parsing JSON, compiling/preparing a program and serializing
output are excluded. This experiment measures the execution stage, **not a
full JSON pipeline**. Preparation from source is a separate benchmark.
Diagnostics run in setup; all timed executors disable mechanism counters.
Frame, guards and final-report allocations remain charged to treatments.

All heavy runs were serial under parent control. Primary matrix: 50 states,
one thread, 2 forks, 2 × 500 ms warmup, 4 × 500 ms measurement. Longer confirmation:
all five engines on all three native large workloads, reverse engine order,
2 forks, 3 × 1 s warmup, 5 × 1 s measurement. Heap: -Xms256m -Xmx768m, G1.
The longer run is the primary evidence for those three workloads. Short-run
absolute values and treatment ratios must not be pooled with confirmation:
warmup/order/host state are confounded and some numbers change materially.
Holdouts and small cases retain exploratory status until independently repeated.

The preserved original jar runs first on the classpath with the same new harness
for an incidental-default-change check. Its short-run native results overlap
the current baseline's JMH intervals; this does not certify a tight regression
bound. It also does not justify comparing short old-baseline numbers against
long-confirmation numbers. The exact original jar is local at
`/tmp/branchline-vm-factorial/before.jar`; its hash is recorded, but it is not a
committed reproducibility artifact.

Memory is measured in three fresh JVMs per engine and lifetime (30 processes),
alternating order, -Xms32m -Xmx768m/G1. `reuse` retains one executor for 1,000 runs;
`many` retains 1,000 independently prepared executors. Both share the same large
reduction input and retain the final result. Post-requested-GC process heap is
an approximation; RSS includes compilation, JIT and native peaks. Weak references
show wrapper collection only. Neither proves exact retained object graph size
or absence of every possible leak.

## Results

All per-case times, JMH interval half-widths, allocation bytes and memory ranges
are in [TABLES.md](TABLES.md). Raw JMH JSON retains individual fork/iteration
values. `effects.csv` records exploratory treatment ratios and the interaction
`both × baseline / (slots × fusion)`; it is not a causal decomposition of the
two mechanisms or a confidence interval for their interaction.

**Decision: correctness accepted within the stated domain; performance promotion
rejected. No repeatable speed or memory advantage from these prototypes was
established. Keep all experimental flags off by default.**

Longer confirmation (microseconds/op; smaller is better):

| Engine | Paths | Comprehension | Reduction |
| --- | ---: | ---: | ---: |
| Interpreter | 0.269 | 21.25 | 26.62 |
| Current VM | 0.386 | 36.61 | 36.44 |
| Slots only | 0.647 | 45.11 | 36.60 |
| Fusion only | 0.473 | 48.29 | 43.36 |
| Slots + fusion | 0.509 | 43.48 | 36.68 |

Compared with the current VM, combined mode takes 32.0% more time on paths and
18.8% more on comprehension; reduction is effectively unchanged within measured
uncertainty (+0.7% point estimate). Slots-only reduction is similarly unchanged
(+0.4%); fusion-only takes 19.0–31.9% more time across these cases. Combined mode
takes 1.38–2.05 times the interpreter's time. The predeclared <=0.8× interpreter
criterion, with no >5% supported regression, is not met.

The exploratory short run appeared to show an 8–9% reduction gain for slots/both.
It disappeared in the longer reverse-order run. It is therefore not an accepted
optimization result. Short-run small cases and both holdouts also show regressions
but are not repeated evidence for a precise regression percentage. All raw values
are retained rather than selecting the favorable reduction result.

Allocation also increases relative to current VM. On confirmed comprehension,
bytes/op are interpreter 32,768; current VM 5,672; slots 6,216; fusion 5,872;
both 6,216. VM's existing allocation advantage over the interpreter survives,
but neither prototype creates that advantage. On reduction the corresponding
values are 8,736; 8,688; 9,136; 8,888; 9,144. Paths likewise allocate more in
all treatments. This prevents interpreting reduced dispatch counts as reduced
memory work.

For 1,000 independently prepared reduction executors, median additional
post-GC process heap is 2.553 MiB (interpreter), 6.156 MiB (current VM),
6.525 MiB (slots), 6.668 MiB (fusion), 7.006 MiB (both). Median peak RSS is
108.12 / 141.62 / 175.14 / 165.64 / 208.17 MiB in that order. A single reused
executor adds only about 35–43 KiB in this process-level probe, including residual
class/runtime costs; differences at that scale should not be treated as exact
executor graph sizes. All wrappers were collected after roots were released.

Source-to-prepared-executor reduction costs in the separate short preparation
run are 2.34 / 4.41 / 8.03 / 7.99 / 8.52 microseconds in the same engine order.
Slots/fusion roughly double current VM preparation time and bytes in this fixture;
projection preparation has the same direction. There is no confirmed execution
gain from which to claim amortization. Closure fallback has overlapping timing
uncertainty and adds 96 allocated bytes/op for experimental reporting; its point
estimates are not evidence of optimized closure execution.

The source and final jar hashes still match the frozen pre-measurement artifacts
(`freeze-verification.json`). No runtime/harness edits were made after seeing
timing results; no extra optimization was mixed into the comparison.

## Architectural interpretation

Code review suggests three plausible costs; these are **hypotheses, not profiler
attribution**:

1. The new execution loop checks observers and eligible groups, then dispatches
   ordinary operations through `executeOpcode`; fewer source dispatches do not
   necessarily mean less machine-level control work.
2. Static-path fusion retains intermediate operand-stack operations and per-key
   observer/container guards. It removes only part of dispatch overhead.
3. Slot frames allocate several per-invocation arrays and preserve presence,
   insertion order, snapshot/local aliases and host observability. Final flush
   and alias materialization add work; ordered commits use filter/sort.

A bounded next experiment would use a prepared basic-block runner with resolved
slot/operand indices and one dispatch, placing observer barriers only at the
operations that need them. Include a new-runner-without-optimization control to
separate runner overhead from the mechanism. Reuse the existing independent
acceptance before timing. This is a proposed investigation, not implemented
work or a promise to beat the interpreter. Do not broaden to JIT, numeric changes
or default enablement in the same comparison.

## Reproduction and evidence

- Build/test/probe commands and fixture definitions: [harness.md](harness.md),
  `development/service/VERIFY.md`, [plan](../../planning/vm-slots-fusion-experiment.md).
- Sequential primary/preparation/fallback/memory orchestration: `measure.py`;
  executed arguments and exits: `commands.json`.
- Longer confirmation: `confirm.py`, `confirmation-command.json`.
- Summaries: `python3 development/evidence/vm-slots-fusion-experiment/summarize.py`;
  tables: `python3 development/evidence/vm-slots-fusion-experiment/report_tables.py`.
- Runtime: `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VM.kt`,
  `ExperimentalExecution.kt`, `ExperimentalFusion.kt`; harness/probes in
  `vm-benchmarks/src/jmh/kotlin/io/github/ehlyzov/branchline/vm/VMFactorial*.kt`.

Run benchmark scripts sequentially, with no concurrent builds/CPU-heavy probes.
The before-jar check requires the preserved jar; other current-source runs can
be rebuilt normally. No JVM result establishes JS speed or multicore throughput.
