# VM tracing hot-path optimization — 2026-10-17

## Outcome

The accepted change removes unused trace bookkeeping from ordinary execution. One native large reduction previously performed 8,529 trace-metadata map gets with no tracer (4,014 Enter, 4,014 Exit, 501 PathWrite); the same independently checked operation now performs zero. Instructions, transformation programs, outputs and bytecode format are unchanged. Remaining interpreter advantage is reported below; this change does not establish a general VM speed advantage.

## Diagnosis and plan execution

GPT-6.1 Sol High agents inspected dispatch and compiler paths, implemented the bounded fix and six common regression tests, and reviewed the source and experimental method independently. The parent inspected the isolated patch, executed builds/tests, checked outputs/input immutability and class hashes, and ran all workload timings serially.

- **Accepted:** tracer/step checks precede metadata lookups. For exact plain HashMap/LinkedHashMap environments with no tracer, STORE_VAR omits an old-value read used only by tracing. Cache the exact environment class on replacement, including call entry/return/unwind. Custom maps preserve get-before-put order and can enable tracing from either callback. Ambient tracer is re-resolved at the original observation boundaries; host activation can produce the current instruction's Exit event.
- **Diagnosed residual:** the reduction uses eight stack instructions per order, including a three-instruction local/environment mirrored store. The interpreter has a direct straight-line loop-body path. Both are O(n); this is constant-factor execution work. Numeric-site preparation was already cached by PC.
- **Rejected experiments:** full dispatcher inlining and compact hot/cold dispatcher splitting made exploratory native workloads slower than trace-only. Both were reverted byte-for-byte to the accepted trace-only source. Their patches/raw measurements are retained and explicitly not shipped. Adding fused opcodes is deferred because it changes bytecode, stepping and serialization contracts; no unmeasured dispatch redesign is presented as an improvement.

Stack sampling on the preserved baseline showed dispatch and HashMap operations as prominent frames. It is a coarse Java sampler, not an exact attribution of every map access; the separate counting probe establishes the metadata mechanism directly. The final comparison, rather than the samples, establishes speed.

## Correctness and scope

Command: `./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :vm-benchmarks:jmhJar :interpreter-benchmarks:jmhJar --console=plain` — success. Collected suite results: VM 149 JVM + 78 Node tests, all pass; conformance 359 JVM (one existing skip) + 109 Node, no failures/errors: 694 passed, one skipped overall. Gradle may reuse unchanged conformance tasks; the changed VM suites executed. Six new common tests cover host tracer on/off, explicit precedence, custom map reads/get-put activation, and tracer callback changes.

Existing VM tests also cover stepping/snapshot resume, private ownership, numeric overflow, functions/callbacks and exception unwinding. Native acceptance checks all nine expected results and compares each input against a separately constructed pristine input. Final VM/Compiler/Exec class bytes match across both benchmark jars. Native instruction histograms/counts match the preserved baseline. Product change scope: VM.kt and the new VMTraceFastPathTest.kt only; pre-existing unrelated working-tree changes are preserved.

Contour trigger: tracing/environment optimization and its verification invariant changed; SERVICE_MAP.md and VERIFY.md were updated. No language syntax, grammar, public bytecode or native benchmark source changes were required. No JS performance, retained heap, RSS, startup/compilation, parallel throughput or production latency claim is made.

## Measurement contract

Apple M4 Pro, 48 GiB RAM, macOS 27.0.1, Temurin 21.0.11, JMH 1.36. Unchanged native VMTransformBenchmark and InterpreterTransformBenchmark; interpreter collectionType=hash. Same input/programs; VM executes bytecode directly, no interpreter fallback. Parsing, compilation, I/O and environment reset are outside timed execution. The GC profiler includes invocation-setup allocation, so B/op is allocation pressure per transform, not resident/live memory.

All own builds, JVM profiles and JMH jobs ran serially. Read-only agent work continued; a few tiny validation/javap probes ran during full runs, so this is a local workstation experiment, not an isolated publication environment. No per-invocation extra tracing is enabled. Two independent JVM forks, one worker thread, 256–768 MiB heap. Raw JMH JSON preserves 99.9% intervals and individual iterations. Confirmed wins below have non-overlapping intervals; fixed ordering still leaves order/thermal uncertainty.

## All nine native pairs (short repeated comparison)

Three 300 ms warmups, four 300 ms measurements per fork, AverageTime in microseconds. Lower time/allocation is better; a time ratio above 1 means VM is slower. Historical VM times in CSV are context from the preceding comparison, not the stronger matched long before/after experiment.

| Case | Size | VM µs | Interpreter µs | VM / interpreter time | VM B/op | Interpreter B/op |
|---|---|---:|---:|---:|---:|---:|
| arrayComprehensions | small | 0.925 | 0.496 | 1.87 | 800 | 1,376 |
| arrayComprehensions | medium | 5.732 | 3.453 | 1.66 | 800 | 5,112 |
| arrayComprehensions | large | 37.867 | 22.743 | 1.66 | 5,656 | 32,768 |
| pathExpressions | small | 0.364 | 0.238 | 1.53 | 704 | 784 |
| pathExpressions | medium | 0.365 | 0.278 | 1.31 | 704 | 784 |
| pathExpressions | large | 0.368 | 0.263 | 1.40 | 704 | 784 |
| typicalTransform | small | 0.877 | 0.683 | 1.28 | 824 | 800 |
| typicalTransform | medium | 7.559 | 5.066 | 1.49 | 2,264 | 2,280 |
| typicalTransform | large | 38.551 | 25.921 | 1.49 | 8,680 | 8,724 |

## Longer matched confirmation

The preserved original VM jar and accepted final VM jar use the same two forks, three 1 s warmups and five 1 s measurements per fork for all three large cases. Interpreter uses identical settings. No warmed data is pooled across program types. See `before-long.json`, `after-long.json`, `interpreter-long.json` and `confirmation.csv`.

| Large workload | VM before µs | VM after µs | Interpreter µs | VM time reduction | VM / interpreter time | VM B/op | Interpreter B/op |
|---|---:|---:|---:|---:|---:|---:|---:|
| pathExpressions | 0.496 | 0.369 | 0.262 | 25.7% | 1.41 | 704 | 784 |
| arrayComprehensions | 44.847 | 38.383 | 23.035 | 14.4% | 1.67 | 5,656 | 32,768 |
| typicalTransform | 53.470 | 39.282 | 26.859 | 26.5% | 1.46 | 8,680 | 8,736 |

The accepted patch reduces VM time by 14–27% on these larger confirmed native workloads. Allocation is effectively unchanged from the VM baseline. The interpreter is still faster in all repeated native cases; on the confirmed large cases it is roughly 1.41–1.67 times faster. VM keeps its comprehension allocation advantage (~5.79x fewer bytes per operation), path allocation advantage (~10%), and near-equal reduction allocation. The avoidable trace bookkeeping is removed; stack dispatch and local/environment mirroring remain architectural overhead, not a solved performance claim.

## Reproduce and inspect

- Build and gates: command above; output `final-gates.log`, counts `test-summary.json`.
- All-size pairs: `python3 development/evidence/vm-interpreter-comparison/run_native.py --java-home /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home --output-dir /tmp/branchline-native-recheck`.
- Long confirmation: `python3 development/evidence/vm-hotpath-optimization/run_confirmation.py --java-home /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home --jar vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar --engine vm --output /tmp/branchline-confirmation.json`; use interpreter jar and `--engine interpreter` for the comparison. Run variants sequentially.
- `NativePairValidation.java`: compile against both native jars and run with VM jar first on classpath; all 9 cases match expected outputs and unchanged pristine input (`final-native-validation.txt`).
- `TraceLookupProbe.java`: documented compile/run command; use final VM jar for zero-lookup result. This probe counts accesses and does not supply timed results.
- Final source/jar SHA-256: `after-manifest.json`; preserved baseline: `before-manifest.json`. The original jars remain in `/tmp/branchline-vm-hotpath/before/` for this local session; the accepted isolated patch and source hashes allow checking the exact baseline delta. Native benchmark sources were not changed.
- `independent-method-review.md` contains agent review and caveats. `trace-analysis.md` documents the mechanism and rejected experiments. Primary evidence is raw JSON, with CSV summaries, not quick exploratory scores.

## Separate memory-model proposal

The user's four ideas are assessed separately in `development/planning/vm-memory-model-validation.md`. Selective materialization needs a raw-JSON end-to-end benchmark; existing prepared Map/List JMH does not measure that boundary. Ownership-aware insertion is partly implemented, control-flow/local indices already exist, and multicore batch work first needs worker-state isolation. Source inspection found shared VMExec use in compiled CLI fan-out; it is recorded in the gap registry as a concurrency risk pending reproduction, not a reproduced failure or a change made here.

Final contour audit: pass, no warnings/failures. `git diff --check`: pass. Separate after-change stack profile is retained as `profile-after.log`; timing claims use the GC-profiled matched JMH confirmation rather than stack-sampler runs.
