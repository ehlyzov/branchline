# Independent bounded VM performance acceptance, cycle 2

Date: 2026-10-17. Role: independent source review and executable differential acceptance. No product code was edited by this reviewer and no duplicate Gradle gate was started. The parent authorized this evidence artifact and owns the allocation/timing probes, combined JVM/Node gate, documentation and contour synchronization.

The final bounded source and differential audit found no new confirmed open P0/P1 in the reviewed scope. Both reproduced findings have local repairs whose behavior was reviewed. Performance closure uses the parent's paired reproductions packaged in this evidence directory; this report does not replace those measurements or claim universal optimality.

## Final reviewed state

- Final benchmark jar SHA256: `f59b725b088c83fe84762452ef4dcd0fc86b5ba7a921dfa995b1b4023f3ea04e`.
- `VM.kt` SHA256: `456ee0f9ef50854d7ca3b709e16a3c425c32f920ef332c6ed61a6bc83609c30d`.
- `Compiler.kt` SHA256: `e1fd5dc80eef9bdff446b5f85db4ba3512c3f66cae60c1b7b6a6e35c7c479646`.
- Comparison sources: accepted cycle-2 baseline saved under `/tmp/branchline-vm-audit2/baseline/`; product deltas were limited to overlay enumeration and compiler ownership bookkeeping plus their focused common tests.

## P1-A: nested overlay enumeration

`VM.kt:1151` now walks overlay parent identities iteratively, copies the first non-overlay map, and replays local maps from outermost to innermost. A single enumeration costs expected O(depth + total bindings across the chain), including shadowed bindings. It no longer materializes all prefixes recursively. The non-overlay base can receive a bounded size request from map copying; it is not an overlay prefix and does not recreate the exponential recurrence.

The replay order preserves the previous parent-first insertion order and key position on overwrite. Local null remains a present shadow, captured ancestors stay live, and returned entries belong to the detached combined map. `get`, `containsKey`, `put`, and local-only `remove` are unchanged. No cached flattened map can become stale.

Reviewed `VMOverlayEnumerationTest`: supported twelve-level lambda suspension/restore returns 7 with a deterministic linear root-enumeration bound; ordinary lookup enumerates zero roots; additional tests cover null shadowing, ordering, ancestor updates, detached entry mutation, local removal revealing an ancestor, and snapshot contents/restore. Adjacent review covered the public call-environment creation paths and both snapshot builders.

Full snapshots still serialize each saved environment and caller bytecode separately. That cost includes the represented frame/value/bytecode size and is not eliminated by this repair. Existing serializer limitations for arbitrary lambda payloads remain outside this cycle's accepted snapshot domain.

## P1-B: compiler ownership scope bookkeeping

`Compiler.kt:65-89` centralizes changes in a reversible log containing the prior Boolean kind or absence. `compileNodes` saves an integer checkpoint and restores changes in reverse order in `finally`. All candidate insert/remove/clear sites use these helpers; only compile initialization and restoration mutate the map directly. Unchanged scopes require O(1) bookkeeping, and changed scopes pay O(actual eligibility changes), rather than copying the entire candidate map. Clearing still visits every revoked candidate once.

`false` is a valid object candidate and is distinct from absent/null in the log. Repeated writes and clear/reinsert sequences unwind correctly in reverse order. Nested scopes remove their own records without discarding outer records. Whole IF/FOREACH observation invalidation still happens before emission, and expression/IR observation only removes eligibility, so returning immediately for an empty candidate map is safe. Compiler reuse clears both bookkeeping structures.

Reviewed the 13 existing `PrivateCollectionCompilerTest` cases and three new `PrivateCollectionScopeTest` cases. They cover ordinary/private opcode choice, sibling and nested scopes, branch-local candidate nonescape, host observation, later fresh candidates, aliases, loop shadowing, shared inputs, tracing, observing environments, incremental execution and snapshot boundaries.

Independent executable differential check: `OwnershipDifferentialAudit.java` generates 500 deterministic IR inputs with eight initial array/object candidates, IF/FOREACH nesting up to depth three, local declarations, aliases/reads, rebinding and arbitrary host-call invalidation. Alternating inputs reuse a Compiler instance or construct a fresh instance. Every final instruction count and SHA256 of `BytecodeIO.encodeToString(bytecode)` exactly matched the accepted cycle-2 baseline; `diff -u ownership-baseline.csv ownership-final.csv` exited 0. This is compile-only equivalence evidence; potentially ill-typed generated programs were not used as runtime correctness claims.

## Adjacent review and limits

The review examined overlay map inheritance and liveness, snapshot enumeration/serialization, compiler observer exhaustiveness, ownership mutation sites, scope rollback and arbitrary-call invalidation. No additional confirmed actionable P0/P1 emerged. Deep ASTs can still cause repeated conservative invalidation scans while candidates remain live; that previously recorded bounded lead was not established as a representative P1 and is not silently claimed to be globally linear. Required alias copies, full snapshot output and explicit authority snapshots remain intentional costs.

The parent reported the final combined VM/interpreter/conformance/CLI JVM and Node tests plus `jmhJar` completed successfully with exit 0 after both final fixes. This reviewer did not independently rerun that broad gate. Parent records carry the exact test counts and paired performance results.

Reproduce the differential check from repository root with a JDK and the benchmark fat jars:

```sh
javac -proc:none -cp /tmp/branchline-vm-audit2/baseline/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar /tmp/branchline-vm-audit2/OwnershipDifferentialAudit.java
java -Xmx512m -cp /tmp/branchline-vm-audit2:/tmp/branchline-vm-audit2/baseline/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar OwnershipDifferentialAudit > /tmp/branchline-vm-audit2/ownership-baseline.csv
java -Xmx512m -cp /tmp/branchline-vm-audit2:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar OwnershipDifferentialAudit > /tmp/branchline-vm-audit2/ownership-final.csv
diff -u /tmp/branchline-vm-audit2/ownership-baseline.csv /tmp/branchline-vm-audit2/ownership-final.csv
```
