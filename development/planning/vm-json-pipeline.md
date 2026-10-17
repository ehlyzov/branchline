---
status: Implemented
depends_on: ['planning/vm-memory-model-validation']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# VM jobs isolation and selective JSON prototype

User authorized GPT-6.1 Sol agents to first reproduce and fix VM isolation under --jobs, then implement a bounded selective JSON materialization prototype; parent independently verifies correctness and measures full-pipeline time, allocation and memory.

1. Establish deterministic overlapping compiled-CLI/VM execution reproduction and serial reference. Fix ownership of mutable runtime state per worker/task; preserve order, errors, contracts and shared-store context. Cover jobs=1 and jobs>1; no assumption that making a VM thread-local alone solves all shared state. Parent independently runs CLI/JVM/Node gates and checks negative sensitivity before accepting.
2. After isolation accepted, implement opt-in experimental selective materialization with complete JSON validation, detached values, conservative supported access plans and explicit eager fallback. Keep existing parser behavior as reference and do not silently change default CLI semantics. Validate duplicates/malformed unused input, numbers/Unicode, null/missing, aliases/whole-object/dynamic accesses and contracts.
3. Compare full JSON-text -> parse/materialize -> environment/contracts -> strict VM execution -> JSON-output pipeline with identical output, input lifetime, JVM/heap and warming. Include sparse and dense controls plus fallback. Measure timing and allocated bytes separately from retained/live heap and process RSS; keep compilation/preparation excluded and labelled for reusable transforms. Report raw evidence, failure cases and limits.
4. Use 1/16/64 MiB input sizes and 1/10/100% access where practical, preserving bounded workload/runtime. Predeclared candidate gate: sparse 16/64 MiB >=25% allocation/live-heap reduction and >=10% full-pipeline speedup; dense control <=5% regression or keep experimental sparse-only mode. Parent owns timing, memory probes and final acceptance; no simultaneous benchmark/build workloads.
5. Update service contour, gap registry, index and evidence. Current unrelated dirty work is accepted prior work from this chat; never touch deep-research-report*.md. No commits/push or broad architecture rewrite.

## Phase 1 selected implementation

Isolation must include per-worker BranchlineProgram contract inference caches and VMExec, prepared serially before entering workers. Existing compiled fan-out also exposes an ordinary shared regex cache; move only that bounded cache behind platform implementations (JVM synchronized access, unchanged JS serial behavior). CLI-owned tracing cannot merge concurrently safely: retain deterministic traced execution through explicit serial dispatch. Regression uses actual --jobs overlap coordinated at an input read and compares independently expected per-job values; run it against original code before fixing.

## Phase 2 selected prototype boundary (implemented after phase 1 acceptance)

Use an opt-in programmatic experimental JSON pipeline with plan metadata and explicit eager fallback; no new default CLI parsing semantics or combined fan-out integration. Automatically select whole top-level values from a conservative AST whitelist (static root paths, sequential LET/scalar expressions, object/array output, narrowly recognized LENGTH). Preserve raw top-level input alias collisions. Dynamic/whole-root/unknown calls or source/context features that may observe omitted fields use eager parsing; contracts ON use eager fallback. Support detached ordinary values and validate ALL JSON before any execution, including skipped duplicates/malformed escapes/numeric errors. Existing eager API remains unchanged; avoid constructing unused container/string values while reusing parser grammar/number validation. Benchmark factory prepares source, planner and strict VM once; measured execute method includes text parse, env/contracts, VM and output serialization.

## Independent correctness acceptance

Phase 1 race was reproduced against the original implementation using actual compiled CLI jobs,
coordinated by a thread-safe tracer: the first input received the second input's key/value.
Worker-local full runtime/VM contexts resolve that reproduction. Error/interruption joining,
shared context lifetime, deterministic result/summary order, strict contracts, collecting traces,
and bounded synchronized regex caching are covered. Serial jobs reuse their original runtime.

Phase 2 static review found an ambient-tracing observation bug. It was fixed before measurement:
every materialization checks the current ambient tracer and falls back eagerly, including a tracer
installed after preparation. Report metadata is an immutable snapshot of current context.
The independent reviewer accepted the final source; review and source hashes are in
`../evidence/vm-json-pipeline/independent-review.md`.

Parent command (exit 0; BUILD SUCCESSFUL in 12s):

```sh
./gradlew :cli:jvmTest :cli:jsNodeTest :interpreter:jvmTest :interpreter:jsNodeTest \
  :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest \
  :vm-benchmarks:jmhJar --console=plain
```

XML reports contain 1,038 tests: 1,037 passed, zero errors/failures, one existing disabled
TreeReconstructionLoadTest throughput test. Node suites ran unfiltered; a successful task with
zero selected tests is not counted as coverage. Counts and logs are retained under evidence.
Parent-owned `PipelineAcceptance.java` passed 63 additional checks of exact output/error parity,
independent expected values, aliases, fallback modes, malformed unused values, numeric modes,
report immutability, detached input lifetime and reuse. It is compiled/run against the measured jar;
commands and output are in `parent-acceptance.log`.

## Measurement boundaries

Compare force-eager and selective modes of the same prepared strict-VM pipeline. The timed boundary
is raw JSON text -> full validation/materialization -> environment/contracts-OFF wrapper -> VM ->
compact JSON string. Program compilation/preparation, fixture generation and file I/O are excluded.
This is reusable-transform processing, not cold CLI latency. Both modes return identical independently
calculated totals. Primary fixtures have 100 uniformly sized string fields; records controls use
arrays of small objects. The transform sums LENGTH of selected fields and emits one small object.
The alias-shadow guard also retains a field named input if present; these fixtures have none.

JMH uses one thread, two forks, three 500 ms warmup iterations and five 500 ms measurements per fork,
Java 21.0.11, fixed -Xms512m -Xmx2g and SerialGC on Apple M4 Pro. CPU-heavy commands run sequentially.
There are 18 strings states, four records states and two dynamic-access eager-fallback states.
All setup methods assert plan and expected output. Commands, environment, jar hash and raw JMH
confidence intervals are retained. Eager/selective states run in JMH parameter order; no claim of
randomized scheduling or thermal isolation is made.

Separate fresh JVM memory probes use identical files/options, three repetitions with alternating
engine order. They report post-full-GC used heap for raw input plus prepared VM, plus retained map,
then plus the VM's last environment; peak process RSS is independently read from /usr/bin/time -l.
Allocated B/op, live heap and RSS are distinct measures. RSS includes startup/file loading/compiler
transients, fixed metadata and native/runtime overhead. Weak references verify collection after
raw input/pipeline owners are released. Fixed probe/runtime metadata remain in the final heap stage.

## Limits

This remains an opt-in programmatic prototype, not a default CLI parser or a --jobs integration.
It selects complete top-level subtrees, scans all bytes, decodes all keys, tracks duplicates, and
converts even skipped numbers. It does not stream input or provide nested projection. Dynamic paths,
whole-root observation, unsupported syntax/calls, user functions, shared resources, contracts,
numeric-key mode and ambient tracing use eager materialization. Instances are sequential/stateful.
Multicore throughput and JS performance are not measured here. No new absolute-address or object-
insertion representation is introduced. Exact concurrent VMFactory diagnostic counters and
simultaneous independent CLI invocations using process-global state remain outside the isolation fix.

## Additional preparation-time guard and final acceptance

Parent review subsequently identified SUSPEND as a unary expression whose snapshot observes the
whole environment. `SuspendAcceptance.java` reproduced differing full snapshots against the measured
pre-guard jar (nonzero exit, `suspend-before.log`). The planner now whitelists MINUS/BANG and sends
SUSPEND/AWAIT, including LET-bound forms, to eager parsing. Common regression tests and parent exact
snapshot checks pass. The affected CLI/interpreter JVM and unfiltered Node suites plus JMH jar were
rebuilt successfully in 10s (`suspend-final-gate.log`). Final combined reports: 1,040 tests,
1,039 passed and the same one existing skip. Parent acceptance totals 65 checks on final source.

The primary timing/memory matrix used the jar recorded in `benchmark-jar.sha256`, before this final
preparation-only guard. None of its programs contains unary expressions; the guard changes neither
its selected fields nor timed execution/parser/serializer code. The longer warmup confirmation uses
the final jar (`final-jar.sha256`), reverses requested engine order, and repeats 1 MiB sparse/dense
cases. Both artifacts and their measurement scopes are distinguished explicitly.

## Primary full-pipeline results

Means from two JVM forks, five measurements per fork. Full error/confidence data remain in raw JMH
JSON; the table is descriptive. MiB/op means newly allocated bytes divided by 1,048,576.

| Shape | Input MiB | Fields read | Eager ms/op | Selective ms/op | Speed ratio | Eager MiB/op | Selective MiB/op |
|---|---:|---:|---:|---:|---:|---:|---:|
| strings | 1 | 1% | 2.286 | 0.547 | 4.18× | 4.559 | 0.060 |
| strings | 1 | 10% | 2.294 | 0.927 | 2.47× | 4.560 | 0.471 |
| strings | 1 | 100% | 2.289 | 3.294 | 0.69× | 4.571 | 4.581 |
| strings | 16 | 1% | 36.256 | 9.147 | 3.96× | 72.300 | 0.738 |
| strings | 16 | 10% | 36.410 | 11.583 | 3.14× | 72.302 | 7.246 |
| strings | 16 | 100% | 36.098 | 36.606 | 0.99× | 72.314 | 72.324 |
| strings | 64 | 1% | 146.496 | 35.032 | 4.18× | 289.053 | 2.905 |
| strings | 64 | 10% | 145.856 | 44.883 | 3.25× | 289.054 | 28.921 |
| strings | 64 | 100% | 145.140 | 146.732 | 0.99× | 289.073 | 289.083 |
| records | 16 | 1% | 129.024 | 26.049 | 4.95× | 144.563 | 75.103 |
| records | 16 | 100% | 128.642 | 131.976 | 0.97× | 144.584 | 144.591 |
| strings / dynamic fallback | 16 | 1% | 38.218 | 37.026 | 1.03× | 72.300 | 72.300 |

Sparse string controls at 16/64 MiB reduce time by 74.8/76.1% and allocations by 99.0% when reading
1%; reading 10% reduces time by 68.2/69.2% and allocations by 90.0%. Nested-record sparse control
reduces time by 79.8% but allocations only 48.0%: skipped keys/duplicate sets and number conversion
still allocate. No general 99% allocation claim applies to arbitrary JSON structure.

Dense 16/64 MiB strings have 1.4/1.1% slower point estimates with similar allocation. Dense records
are 2.6% slower with broad time uncertainty. The 1 MiB dense primary control is 43.9% slower, so the
universal dense <=5% criterion is not established. Dynamic fallback gives the same ~72.300 MiB/op
and overlapping timing intervals (38.22±5.43 vs 37.03±5.05 ms); there is no demonstrated fallback
speed advantage. Controls must not be interpreted as proof of zero overhead.

## Separate memory observations

Medians of three fresh processes. All columns are MiB. Heap column is post-requested-full-GC
process heap used while the raw string, reusable VM/last input environment and tiny output remain
strongly rooted. RSS is process peak and includes startup, parsing transients and native overhead.
These numbers are not allocated B/op and are not isolated object-graph sizes.

| Shape / input / selected | Eager heap used | Selective heap used | Eager peak RSS | Selective peak RSS |
|---|---:|---:|---:|---:|
| records / 16 MiB / 1% | 102.54 | 23.34 | 383.69 | 215.92 |
| strings / 16 MiB / 1% / fallback | 38.60 | 38.60 | 202.50 | 202.39 |
| strings / 16 MiB / 1% | 38.54 | 22.80 | 202.08 | 129.59 |
| strings / 16 MiB / 100% | 38.70 | 38.69 | 215.86 | 207.36 |
| strings / 64 MiB / 1% | 134.58 | 71.08 | 449.50 | 282.88 |

Sparse strings16/64 reduce the observed rooted heap by about 41%/47% and process peak RSS by
36%/37%. Sparse records16 reduce rooted heap by 77% and RSS by 44%. Raw input text remains fully
resident throughout processing, so total memory falls much less than string allocation B/op.

Caveat established by independent review: some post-release heap observations remain flat even
though weak references to raw String and pipeline are cleared. Those references prove collection
eligibility of these two wrappers; they do not prove collection of every backing array/tree or
that the final heap equals detached-output graph size. Report materialized-minus-baseline and
VM-minus-baseline only as comparative proxies, not exact retained sizes. Absolute values, releases,
weak-reference checks and repeated process RSS are all retained in raw logs/CSV for inspection.

## Confirmation and readiness decision

Final-jar confirmation (two forks; four 1s warmups, five 1s measured iterations; requested order
selective then eager) preserved the sparse gain: 1 MiB/1% is 2.447±0.060 -> 0.553±0.008 ms, 4.43×.
Dense 1 MiB/100% remains slower: 2.256±0.059 -> 3.516±0.120 ms, **55.9% regression**. Longer warming
and reversing requested engine order did not eliminate the regression. Its root cause has not been
isolated; do not attribute it to JIT, thermal drift or branch prediction without a separate profile.
The initial 43.9% and confirmation 55.9% regressions are both retained, not averaged or cherry-picked.

Decision: accept worker isolation and the opt-in sparse-use prototype. Sparse 16/64 MiB string
workloads exceed the time/allocation thresholds and show substantial reductions in post-GC rooted
heap and peak RSS; the records control also benefits. Exact retained object-graph sizing remains
unmeasured. Reject automatic/default enablement because the dense criterion fails on 1 MiB and
larger dense confidence intervals do not establish a universal <=5% bound. The implementation does
not automatically detect workload density; explicit opt-in remains the caller's choice.

The next bounded performance investigation should profile the confirmed dense regression, then
validate an eager-versus-selective selection rule on independent real transforms and output sizes.
Nested projection and reduced skip-path key/numeric allocations are separate candidate slices.
No benchmark-driven heuristic or unmeasured integration with --jobs is included in this change.

All raw measurement logs/JSON, fixture hashes, commands, source review hashes, summary CSV and
reproduction sources are under `development/evidence/vm-json-pipeline/`. This work is uncommitted.
