# VM performance acceptance — 2026-10-17

## Scope and experimental controls

This compares the local VM before and after the explicitly authorized performance goal. The baseline already contains the earlier numeric/fallback hardening; it is not clean HEAD. Base commit: `ce58599df24a16cb7ffd1f5d4165bdbf84d90ad0`. `baseline-manifest.json` hashes the frozen source files and executable jar. `baseline-source-vs-head.patch` records those four VM files relative to HEAD; it is not a full dependency/workspace snapshot. The exact baseline jar remains at `/tmp/branchline-vm-performance-goal/baseline/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar` on this workstation. Reproducing the baseline requires that artifact or an equivalently reconstructed build; it is not bundled in Git.

Both measured jars use the same external `VMPerformanceBench.java`, JMH 1.36, Temurin 21.0.11 on macOS 27.0.1, two fresh JVM forks per scenario, two 200 ms warmups and three 200 ms measurements per fork, 256–768MiB heap, average time in microseconds, and GC allocation profiling. No Gradle build runs concurrently with these JMH measurements. Order is baseline then changed implementation; it is not randomized or an independently blinded product benchmark. Short runs are sufficient to expose these large scaling/allocation defects but not to publish a universal throughput ratio or small nanosecond improvements.

All scenarios check expected output in trial setup. Array/map scenarios compile actual Branchline source through Parser, ToIR and Compiler, and allocate fresh local roots on every execution. Compilation is outside timing. Arithmetic/call scenarios execute identical work with skipped NOP padding; environment scenarios differ only in unrelated caller bindings. Results include runtime allocation, not input/program construction.

The artifact is about VM implementation costs. It does not establish superiority over JSONata, Kotlin, native code or the interpreter, and JVM timings do not establish JS speed.

## Recorded results

Times below are mean microseconds per operation; allocation is bytes per operation. Ratios are descriptive point estimates from these short controlled runs.

| Scenario | Before µs | After µs | Before bytes | After bytes |
| --- | ---: | ---: | ---: | ---: |
| add:0 | 0.046 | 0.034 | 40 | 16 |
| add:8192 | 3.261 | 0.040 | 136 | 112 |
| call:0 | 0.079 | 0.056 | 384 | 168 |
| call:8192 | 1.896 | 0.060 | 448 | 232 |
| env:0 | 0.081 | 0.062 | 384 | 168 |
| env:4096 | 55.908 | 0.058 | 328,048 | 168 |
| append:1000 | 203.220 | 82.024 | 4,060,304 | 13,444 |
| append:4000 | 2704.982 | 335.527 | 64,240,309 | 65,505 |
| set:1000 | 5147.092 | 148.674 | 25,429,674 | 56,930 |
| set:4000 | 79804.236 | 606.669 | 405,287,624 | 226,121 |

For 1,000→4,000 elements, private array accumulation time grows 4.09× and map SET time 4.08× after the change; baseline allocation grows about 16× for both. Final array allocation grows about 4.87× because capacity growth is discrete, and final map allocation about 3.97×. This is consistent with amortized linear construction. At 4,000 elements, array allocation falls from 64.24 MB to 65.5 KB and map allocation from 405.29 MB to 226.1 KB (decimal units).

The same executed arithmetic/call work no longer scales with 8,192 skipped instructions. A call with 4,096 unrelated caller bindings now allocates 168 bytes, the same as the zero-unrelated-binding case, versus 328,048 bytes before. Do not interpret the small differences among nanosecond-scale final cases as robust rankings. Root-eviction and implicit-authority regressions introduced during development were separately reproduced and closed; they are not hidden in these final numbers.

## Reproduction

Build the changed runtime with `./gradlew :vm-benchmarks:jmhJar`. Existing Gradle caches must contain the JMH 1.36 bytecode/reflection/ASM generators (the repository benchmark build normally resolves them). Run:

```bash
python3 development/evidence/vm-performance/run_jmh.py \
  --java-home /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  --jar vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar \
  --output /tmp/branchline-vm-performance-recheck.json
```

Use the frozen baseline jar for the baseline run. The runner writes raw JSON and a sibling log, aborts on a failed fork and requires all 10 scenarios. `baseline-jmh.json` and `after-jmh.json` are the recorded runs; `comparison.csv` is derived directly from their primary scores and normalized allocated bytes, with score intervals retained in the raw JSON.

## Independent regression probes

- `EvalAuthorityAudit.java`: constant expression with 0/128/1024 unused functions of 256 AST elements. `eval-authority-current.csv` records the first implementation's newly introduced full-authority scan. `eval-authority-fixed.csv` confirms its removal. The baseline/default/fixed measurements are diagnostic, not JMH; function-size scaling and lookup-only regression tests support the conclusion. The explicit prepared path is measured after preparation, so its capture cost is deliberately excluded.
- `HostAuthorityAudit.java`: nested and repeated-execution host binding replacement. Final direct lookup returns live bindings. This fixes a new nested-call regression and the baseline's stale same-root binding behavior; explicit prepared handles still freeze their own binding maps.
- `RootRetentionAudit.java` and `RootRetentionCostAudit.java`: 1000 transient callees under the same mostly skipped root. The first bounded FIFO implementation repeatedly evicted its own root; the final implementation pins it. Allocation probes preconstruct all inputs/callees and measure thread allocations, not setup. They are diagnostic scaling checks, not timing claims.

Compile each Java probe against the current benchmark jar with `javac -proc:none -cp <jar> -d <temp> <probe.java>`, then run `java -cp <temp>:<jar> <ClassName>` (`EvalAuthorityAudit` accepts a trailing argument to also measure the new prepared API). The baseline does not implement `prepare`; compile that probe against the new API but run its default-only path against the baseline.

## Final acceptance

Independent final acceptance found no open confirmed actionable P0/P1 in the bounded scope; see [independent-audit.md](independent-audit.md). The goal stops here. `./gradlew docsBuild` also passes; source/bytecode syntax is unchanged, and no new language grammar or playground syntax example is required. Documentation and service contour were updated for the new preparation/ownership boundary.

## Correctness gates and limits

Final gate: PASS. XML reports contain 980 tests: 979 passed, one existing skipped conformance test, zero failures/errors. VM-specific coverage is 136 JVM + 65 Node tests. See `test-summary.json` and `final-gates.log`. Interpreter JVM/Node tasks were UP-TO-DATE; VM/conformance/CLI executed after the final product delta. The combined command is:

```bash
./gradlew :vm:jvmTest :vm:jsTest :conformance-tests:jvmTest :conformance-tests:jsTest \
  :interpreter:jvmTest :interpreter:jsTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar
```

Common tests cover numeric dispatch type changes, private collection aliases/observers, custom environments, stepping, serializable snapshots, live closures, host callbacks, retries, mutable adapter inputs and fallback effects. Exact Long overflow remains a JVM-only assertion because JS arbitrary precision is explicitly unsupported. JVM tests also verify bounded prepared state and root retention. Unchanged interpreter tasks may be accepted as Gradle UP-TO-DATE; this is not a forced rerun of every historical test.

Remaining copying for observed/shared values, nested writes, tracing and stepping is conservative and necessary under the current proof/representation. General immutable data structures, local slots, dispatch tuning and deeper escape analysis remain potential measured follow-ups, not open confirmed P0/P1 from this audit. Snapshot executable-closure serialization and old-schema migration are outside the supported evidence domain and recorded as an existing knowledge gap. Direct bytecode/function declarations must remain immutable for a VM lifetime. Explicit prepared handles retain their captured authority while the caller holds them.
