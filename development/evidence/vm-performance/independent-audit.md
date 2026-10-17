# Independent bounded VM performance acceptance

Date: 2026-10-17. Role: independent source review and executable acceptance probes; no product code was edited by this reviewer. Scope follows `development/planning/vm-performance-goal.md`. Final reviewed product state includes live host lookup and pinned-root eviction.

The final bounded audit found no open confirmed actionable P0/P1 in the reviewed scope. Three findings discovered during acceptance were reproduced, corrected by implementation agents, and independently rechecked. This is bounded acceptance, not proof of universal optimality.

## Closed findings

### P1: implicit expression cache traversed unrelated function authority

Location: `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/Integration.kt:198` (final entry point).

An implicit cache hit structurally compared a detached snapshot of every function body, including functions unrelated to the evaluated expression. VM-disabled evaluation also performed that work. Constant `StringExpr("ok")` evaluation with 1,024 unused functions, each containing 256 AST elements, rose to about 130 microseconds per invocation versus about 1.2 microseconds in the frozen VM baseline and 0.10 microseconds in the disabled baseline. This introduced O(total authority AST) work for fixed evaluated work.

Correction: disabled evaluation uses its live interpreter directly. Implicit reuse is limited to empty function authority and a combined maximum of 32 host/metadata bindings; larger live authorities compile the requested expression afresh without enumeration. Explicit `prepare` detaches authority once and supplies repeated execution without per-invocation validation.

Independent fixed probe: implicit VM with 0/128/1,024 unused functions measured 3,264/6,057/2,319 ns per invocation; disabled evaluation measured 354/108/111 ns. Explicit prepared VM measured 184/192/197 ns. The authority-size-dependent growth was removed. These are diagnostic single-process medians, not publication-quality speed ratios.

Reproducer: `EvalAuthorityAudit.java`; records: `eval-authority-baseline.csv`, `eval-authority-current.csv`, `eval-authority-fixed.csv` in this evidence directory.

### P1: prepared host table preserved stale authority after a nested call

Location: `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VM.kt:1109` (final host call implementation).

A root program called a lambda whose host function replaced another binding in the live host map. The root then called that binding. Frozen baseline returned `new`; the initial optimized implementation returned `old` because restoring the caller restored its cached host table. A simple same-root replacement between executions was already stale in the baseline and was not used as evidence of a new regression.

Correction: indexed host bytecode resolves its recorded name from live authority at each call. This removes the stale table and full-program host preparation scan; it requires one map lookup per host call. Explicit prepared evaluators retain their documented frozen binding map.

Independent final compiled probe output:

```text
same_root_replacement=old,new
replacement_during_child=new
```

Reproducer: `HostAuthorityAudit.java`; fixed output: `host-authority-fixed.txt`.

### P1: bounded FIFO cache could evict the active root

Location: `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VM.kt:1134`.

One fixed root invoking 1,000 transient lambda programs recreated its prepared state 16 times. Each eviction reallocated the root's full pc-indexed numeric-site array. With preallocated callee bytecode and environments, fixed executed work allocated 272/841/2,325/4,370 bytes per invocation with 0/8,192/32,768/65,536 unexecuted root instructions. The root thus incurred avoidable amortized O(root bytecode size / cache capacity) preparation rather than one-time preparation.

Correction: eviction excludes the active root while preserving the total bound of 64 prepared programs. Switching to a distinct root still clears the previous working set.

Independent final compiled probe counted exactly one root preparation for 1,000 transient callees. Allocation for the same padding sizes was 368/400/304/304 bytes per invocation: the root-size-dependent growth disappeared. Absolute small-allocation/timing differences are affected by JVM optimization and are not interpreted as speed ratios.

Reproducers: `RootRetentionAudit.java`, `RootRetentionCostAudit.java`; before allocation record: `root-retention-before.csv`. Final probe outputs are packaged here as `root-retention-fixed.txt` and `root-retention-fixed.csv` (copied by the parent during evidence packaging).

## Reviewed coverage

- Numeric dispatch uses the active program's pc-indexed sites without structural Bytecode hashing; type variants and exact JVM overflow have targeted tests.
- Prepared-program identity lookup is bounded, the root stays pinned, and changing roots releases the previous numeric working set.
- Call frames retain caller environments without copying unrelated bindings. Lexical live capture, parameter shadowing, host effects, callback failures and caller restoration were examined.
- Callback-local exception handling, caller TRY boundaries, retries, loop unwinding and snapshot stack/call/try/loop order were reviewed.
- Compiler privacy invalidation covers expression/IR variants, aliases, whole control-flow nodes, rebinding, loop shadowing, captures, arbitrary calls and suspension. Private updates retain ordinary copy behavior for tracing, incremental execution and custom observing environments.
- Private array append and root SET preserve evaluation order and shared-input alias behavior. Nested path updates keep their ordinary semantics.
- AST/function snapshot traversal covers collection-bearing nodes. Implicit cache authority changes invalidate compilation; explicit preparation freezes AST/function/binding authority while keeping host closure state and environments live.
- New opcodes are appended to preserve existing ordinal values and are wired through construction, execution, formatting and serialization.

The heuristic complexity scanner reported no leads; it does not meaningfully analyze this Kotlin implementation. Findings above came from bounded source review and executable Java probes against current JVM classes with the frozen benchmark jar supplying dependencies.

## Verification and limits

The parent ran the final combined VM/interpreter/conformance/CLI JVM and Node gate plus JMH jar build successfully after the product corrections. This reviewer did not start a duplicate Gradle run. `final-gates.log` and `test-summary.json` record the gate. The recorded VM suites contain 136 JVM tests and 65 Node tests, with zero failures; the conformance JVM suite retains one skipped test. Exact Long-overflow assertions belong to the JVM numeric domain; shared tests retain numeric type-change coverage.

The parent owns final JMH measurements, evidence packaging, documentation and contour synchronization. Diagnostic probe results here establish the specific regressions and their resolution; they do not replace JMH confidence intervals.

Existing snapshot serialization does not preserve arbitrary `LambdaTemplate`/`LambdaValue` payloads and converts other unsupported values to strings. Pause/resume acceptance is limited to the supported serialized payload domain and tested bytecode; no universal closure snapshot claim is made. Full authority snapshotting in explicit preparation and copies required by observable aliases/tracing/incremental execution remain intentional costs. Workloads exceeding the bounded callee working set may require re-preparation after eviction.
