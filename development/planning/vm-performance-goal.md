---
status: Implemented
depends_on: ['architecture/vm-interpreter-split']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# VM performance goal

Explicitly authorized: implement the VM performance audit and continue until a final bounded audit finds no new actionable P0/P1 speed issues. GPT-6.1 Sol/high implements; parent independently integrates and accepts. Preserve all prior local work and user-owned deep-research reports. No commits, pushes or deployment are required.

## Scope and stop condition

Audit execution dispatch, numeric specialization, call/environment management, collection construction and updates, repeated expression evaluation, cache lifetime, and snapshot overhead. P0 means a pervasive avoidable asymptotic cost or catastrophic resource growth on ordinary supported use; P1 means a reproducible material cost on a supported representative workload with a semantics-preserving remedy. Constant-factor speculation without evidence is not a new P1. Necessary output/copy costs and specialized snapshot workloads must be identified explicitly, not silently waived.

Stop only after all confirmed P0/P1 are resolved, appropriate JVM/JS correctness gates pass, before/after scaling and allocation evidence is recorded, and an independent final audit of the changed and adjacent scope finds no new P0/P1. This establishes bounded coverage, not a proof of universal optimality. If a new confirmed issue appears, implement and repeat affected checks. Do not broaden scope solely because a benchmark could be improved.

## Initial work packages

1. Runtime: replace structural Bytecode hashing in arithmetic cache; prepare host bindings once per program; remove avoidable caller-environment copies while preserving closure, host effect and resume behavior; bound runtime cache lifetime.
2. Compiler: linearize provably private collection accumulation without altering observable aliasing, evaluation order, tracing or snapshots; use existing builder opcodes where safe. Inspect repeated SET costs and handle actionable cases or document necessary semantics with evidence.
3. Adapter: prepare repeated VMEval expressions safely with explicit bounded caching/invalidation; do not retain every historical program; retain fallback and error semantics.
4. Parent: independent scaling/allocation probes, native JVM/JS tests, benchmark evidence, final adjacent audit and documentation/contour updates.

## Baseline evidence

Read-only audit and native bytecode probes live initially in /tmp/branchline-vm-complexity-audit. Current JMH jar is built with ./gradlew :vm-benchmarks:jmhJar. A cached ADD still hashes program constants each time. A 4000-element += accumulator allocates about64.24MB versus50.32KB with existing builder; a trivial call with4096caller bindings allocates328112bytes;1000fresh equivalent arithmetic programs leave1000numeric-site cache entries. These are diagnostic single-process measurements, not publication-quality speedup claims.

## Acceptance gates

- Arithmetic lookup does not traverse program structure after preparation; fixed executed work is insensitive to dead bytecode size except one-time preparation.
- Repeated function/lambda call overhead does not scale with caller program length or unrelated environment bindings after preparation.
- Selected private accumulation patterns scale linearly; shared values retain previous observable behavior.
- Repeated evaluation reuses prepared state and has a demonstrated bound on retained programs; a changed expression/functions authority cannot reuse stale compilation.
- Tests cover numeric type changes/overflow, closure capture/shadowing, host exception behavior, nested calls, alias preservation, try/retry, pause/resume and VM-only execution.
- Run relevant VM/interpreter/conformance/CLI JVM and Node checks and focused JMH measurements. Compare outputs before interpreting speed ratios.

## Ledger

- Goal and scope recorded before implementation. Existing dirty product changes belong to earlier work in this chat and are the accepted baseline.

### Accepted implementation

- Numeric dispatch: PC-indexed specialization; no structural Bytecode hash in the execution loop. Exact JVM overflow and cross-platform type changes remain tested.
- Calls: caller environment references and prepared caller state replace copies/re-preparation. `CALL_HOST` performs a live name lookup; no host-instruction scan or stale binding table remains.
- Collections: proof-limited fresh literal array/root-map updates use dedicated private opcodes. Tracing, custom environments, incremental/resumed execution and aliases preserve their observable copies; comprehensions keep the existing builder.
- Expression evaluation: explicit detached preparation compiles once; bounded implicit caching handles small authorities. Disabled execution bypasses cache validation. Large live authority opts out of implicit caching rather than scanning unrelated ASTs.
- Lifetime: at most 64 prepared programs, active root pinned, distinct root clears old working set. Explicit prepared handles retain only the authority owned by their caller-held lifetime.

### Acceptance findings closed during iteration

1. Full function-authority equality introduced O(unrelated function AST) cost, including VM-disabled evaluation. Removed; 1,024 unused function bodies no longer cause growth in constant-expression evaluation.
2. Prepared host tables retained a replaced binding after nested calls. Removed tables in favor of live O(1) standard-map name lookup; nested and repeated-call tests preserve intended live bindings.
3. FIFO preparation cache evicted its own current root. Pinned root; 1,000 transient callees now cause one preparation instead of 16 and no allocation growth with dead root length.
4. Test/domain correction: exact Long overflow is asserted on JVM; JS common tests exercise its supported numeric domain. This does not extend JS numeric precision.

### Final evidence and stop decision

- Native combined VM/interpreter/conformance/CLI JVM/Node tasks and benchmark jar build pass. XML aggregate: 980 tests, 979 passed, one existing skip, zero failures/errors. Unchanged interpreter tasks are Gradle UP-TO-DATE; VM/conformance/CLI run after the final product delta.
- Frozen before/after JMH, source/jar hashes, runnable harness, regression probes and exact command logs are under `development/evidence/vm-performance/`. Eligible 4,000 element arrays improve 2.705 ms→0.336 ms and 64.24 MB→65.5 KB allocation; maps 79.804 ms→0.607 ms and 405.29 MB→226.1 KB. These are targeted JVM measurements, not comparative-language product claims.
- Independent final source/probe audit: `development/evidence/vm-performance/independent-audit.md`. No open confirmed actionable P0/P1 in dispatch, call/environment, collection ownership, repeated evaluation, cache lifetime and tested snapshot boundaries.
- Remaining costs/limits: observable/shared-value copying, conservative nested paths, one-time authority capture, bounded callee-cache misses, supported snapshot value domain, immutable low-level bytecode/function declarations, existing JS precision limits. No claim of universal computational optimality or arbitrary closure persistence.
- Stop condition met for this bounded performance goal; further work needs a newly measured issue rather than speculative tuning.

- Packaging gate: `./gradlew docsBuild` and `TZ=Europe/Moscow bin/refresh_contour.sh` pass; contour audit has zero failures/warnings. Measured source/jar hashes still match the accepted artifacts. Changes remain local, with no commit or push.
