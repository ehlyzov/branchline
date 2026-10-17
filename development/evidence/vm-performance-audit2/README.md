# Second VM speed/memory audit — 2026-10-17

The requested second cycle was started after the status check established that the interrupted request had no active goal or agents. The baseline is the accepted first-cycle VM, verified against its after-manifest, not clean HEAD. Two newly reproduced P1 defects were repaired. The final independent review found no new confirmed open P0/P1 within the declared scope.

## Findings, plan and repair

The plan was recorded before each product edit in `development/planning/vm-performance-audit2.md`. GPT-6.1 Sol/high agents implemented disjoint changes; the parent ran final integration and paired probes. An independent agent reviewed source and compile equivalence.

| Confirmed P1 | Before | Repair and resulting complexity |
| --- | --- | --- |
| Nested overlay enumeration | Repeated parent.size/entries materialization, exponential in overlay depth on JVM | Iterative walk to the base, then outer-to-inner local merge; O(depth + total layer bindings) per enumeration |
| Compiler ownership scopes | Full live-candidate map copy/restore for every child block; O(N²) for N eligible roots and N shallow branches | Checkpoint and undo log of eligibility changes; O(actual changes), with no unrelated-candidate copy |

Null shadowing, key order, live ancestor updates, detached entries and local-only remove remain covered by tests. Compiler scope restoration preserves whole-control-flow invalidation and conservative alias/effect handling. No grammar, snapshot schema or language syntax changed. An empty candidate set also short-circuits unnecessary invalidation traversals.

## Final paired measurements

Both final probes use the frozen first-cycle jar and the rebuilt accepted jar, with the same Java source, Temurin 21.0.11, heap 256–768MiB and no concurrent Gradle build. Baseline runs precede final runs. This is a diagnostic single-process allocation/count benchmark, not randomized multi-fork JMH; nanosecond/millisecond timings in raw records are not treated as robust product throughput ratios. Program/input construction is excluded from the measured execution or compile region. JVM allocation is obtained from ThreadMXBean.

| Scenario | Before | After |
| --- | ---: | ---: |
| Depth 12 suspension: root enumerations | 531,441 | 25 |
| Depth 12 suspension: allocated bytes | 122,111,688 | 951,848 |
| Depth 12 suspension: snapshot characters | 30,960 | 30,960 |
| Compile 1024 conditional array accumulators: allocated bytes | 96,971,640 | 4,648,661 |
| Same compile: emitted instructions | 14,338 | 14,338 |
| No-candidate depth 128 / 4096 statements: body-list reads | 536,639 | 4,223 |

The suspension probe restores and returns 7 before/after. The compiler probe executes the compiled result and checks all 1024 arrays equal ["ok"]. Compiler allocation for 128/512/1024 accumulators is 2.005/25.407/96.972MB before and 0.566/2.313/4.649MB after. This removes quadratic scope-bookkeeping growth. Plain nested lookup remains at zero root enumeration and4800 allocated bytes at depth 12; only enumeration was changed.

Final measurement records: `overlay-{before,after}.csv`, `compile-scopes-{before,after}.csv`, `compile-scans-{before,after}.csv`; matching logs preserve complete probe output including warmup. Manifests identify source and jar hashes. `repair.patch` contains only this cycle's two production-file deltas against the frozen baseline.

## Reproduction

Build the current jar with `./gradlew :vm-benchmarks:jmhJar`. The exact before jar is retained locally at `/tmp/branchline-vm-audit2/baseline/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar`; its hash is recorded in `baseline-manifest.json`. It is not bundled in Git. The after jar is `vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar`.

For each jar, compile `OverlayProbe.java`, `CompileScopeCostAudit.java` and `CompileScanAudit.java` into a temporary directory using `javac -proc:none -cp <jar> -d <classes> <sources>`, then run each class using `java -Xms256m -Xmx768m -cp <classes>:<jar> <class>`. Use Java 21 or newer. OverlayProbe prints warmup rows before its `mode,depth,...` header; only rows after that header belong to the final CSV. Source files and complete logs are included here.

## Verification and stop decision

- Combined `./gradlew :vm:jvmTest :vm:jsTest :conformance-tests:jvmTest :conformance-tests:jsTest :interpreter:jvmTest :interpreter:jsTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar` passes after the final product delta. Reports contain 994 tests: 993 passed, one existing conformance skip, no failures/errors. VM coverage is 143 JVM + 72 Node tests, including seven new common tests. Unchanged interpreter tasks may be Gradle UP-TO-DATE; see `final-gates.log` and `test-summary.json`.
- Independent compilation of 500 deterministic generated IR cases yields identical instruction counts and serialized-bytecode SHA256 hashes, including nested/sibling control flow, aliases, rebinding, host effects and Compiler reuse. `ownership-baseline.csv` equals `ownership-final.csv`. This compile-equivalence check complements runtime tests; it is not itself runtime correctness proof.
- [Independent bounded acceptance](independent-audit.md) found no new confirmed actionable P0/P1. Final source/jar hashes are in `after-manifest.json`.

Remaining limits: snapshots still serialize every saved frame and bytecode payload; arbitrary executable closure payload persistence is an existing unsupported domain. Overlay lookup remains depth-dependent; this cycle fixes enumeration. Very deep ASTs with still-live private candidates can still trigger repeated ownership observation; no additional representative P1 was established for that residual lead. No universal optimality or superiority over another language is claimed. Changes stay local without commit/push.

Packaging gates passed: `./gradlew docsBuild`, contour refresh/audit (zero failures/warnings), and `git diff --check`. Final measured product hashes remain unchanged. The bounded goal is complete.
