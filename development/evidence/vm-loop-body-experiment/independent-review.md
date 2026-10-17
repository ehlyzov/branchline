# Independent whole-loop-body review

The reviewer inspected the compiler, generated-operation interface, VM entry and
context lifecycle, and canonical helpers. The reviewer added 14 independent JVM
tests, but did not run builds, JVM processes, profiles, or benchmarks. Execution
results below come from the parent-owned acceptance runs. This document makes
no speed or memory advantage claim.

## Implementation and admissibility

`JvmLoopCompiler.prepare` owns a structural bytecode snapshot and accepts a
bounded compiler-shaped comprehension, rather than recognizing a fixture name,
source hash, field name, or input value. An eligible program has exactly one
INIT/NEXT pair, the expected backedge and exit, one array builder, one append,
and a builder finish immediately after the loop. The body has at most 128 source
instructions, including NEXT. At most one filter jump is accepted, and its false
target must be NEXT. Other body control flow, nested/multiple loops, body stores,
calls, closures, TRY, suspension, and mutation operations fall back explicitly.
Prefix and suffix instructions must belong to the permitted linear opcode set.

The preparation proof simulates stack shape with an array-builder marker. It
requires the builder immediately below the iterable at INIT, prevents body
operations from consuming the outer stack, and requires the same base stack at
the filter continuation and after append. The suffix must also have a supported
stack shape. This is a bounded opcode/stack proof, not SSA, register allocation,
general alias analysis, dominance analysis, liveness analysis, or type inference.
It does not prove that nested input values are ordinary containers.

ASM emits actual JVM filter and loop branches with fixed operation-specific
calls into `LoopBodyContext`. The generated body has no per-opcode switch,
instruction-array walk, or delegation to the ordinary bytecode dispatcher.
Its operations still use canonical VM stack/environment/numeric helpers and
boxed language values; this is not unboxed or SSA execution. Prefix and suffix
continue through ordinary VM dispatch. The parent-inspected projection artifact
has a 646-byte `run` method and a 1,546-byte class, recorded in
[kernel-method-sizes.json](kernel-method-sizes.json),
[generated-bytecode.txt](generated-bytecode.txt), and
[fixture-validation.log](fixture-validation.log).

## Effects, errors, snapshots, and tracing

The compiler preserves the emitted instruction order. In the reviewed object
projection, the compiler evaluates the last source field first; therefore the
first-element path can fail before score arithmetic or the final identifier
load. Object insertion order remains the source field order. No later row is
prevalidated and no completed row or partial current row is replayed.

Every generated operation first marks its original source PC and active PC.
Fixed helpers retain canonical push/pop order, numeric-site semantics, and
exception behavior. This matters for missing/null values, string addition,
overflow, and numeric subclasses; replacing these operations with primitive
arithmetic would not preserve the reviewed contract. On an unhandled body
failure, the active loop binding and LoopState remain, as in ordinary VM
execution. Outer bindings are restored on normal loop completion, rather than
in an unconditional cleanup. NEXT also retains canonical index advancement and
failure order when a custom list throws while reading the next element.

Custom collection and numeric callbacks may execute inside the generated body.
They see canonical VM state because this design does not defer named writes or
replace the operand stack with hidden registers. Observer operations check for
tracer activation after the current operation, emit its exit metadata, and
resume ordinary dispatch at the next original instruction. They do not replay
the callback or the row. Canonical prefix execution can also enable tracing;
the runtime rechecks it before entering the generated body.

Parent review identified a subtle branch boundary: an Exit callback must see
the canonical jump target minus one, before the ordinary loop's PC increment,
and an inactive active-PC marker. `LoopContext.after` now establishes that state
before Exit and then advances to the continuation. The independent regression
enables tracing inside BigInteger truth conversion and snapshots from the Exit
callback on both accepted and filtered branches.

Initial tracing, custom root environments, and unsupported plans use ordinary
execution with explicit status. An empty loop does not claim a generated entry.
Dynamic tracing after an actual generated entry can report both executed=true
and a continuation reason; those describe different stages of the invocation.
The non-reentrant lifetime includes tracer close in both generated and initial
fallback paths. Independent close callbacks verify rejection of nested
`executeLoopBodies`, `execute`, `begin`, and `step` before state is overwritten.

## Ownership and lifetime

Preparation copies opcode/operand/offset arrays, the constant-list structure,
debug maps, enter/exit event collections, and path-write segment/display-path
collections. Review identified the initially shallow path-write lists; the
runtime owner copied them, and an independent fallback-tracing test mutates the
original lists after preparation. Constant values and IR trace-node objects
retain their identities and are not deeply frozen. Concurrent mutation of those
objects, or sharing a mutable VM between threads, is outside the contract.

Each eligible plan owns one generated class through one child class loader,
along with its generated bytes and owned program. There is no global class
cache. The public byte-inspection method returns a copy. A VM reuses a context
and releases its plan reference in cleanup. Canonical VM fields can still retain
the last bytecode, environment, stack, and outputs; releasing the context is not
a claim that the VM retains no last-input references. Class unloading requires
the plan/kernel/loader to become unreachable and depends on JVM unloading/GC
behavior. Preparation cost, retained heap, metaspace, and RSS need separate
measurement. JVM-only generation establishes no JavaScript speed claim.

## Independent tests and parent execution evidence

`LoopBodyPlanTest` contributes seven tests: a non-fixture generated loop with an
independent expected output; empty-loop entry reporting; nested-loop, closure,
and body-host fallback without duplicate calls; malformed CFG/operands; source
array ownership; the body-size bound; and trace-metadata collection ownership.

`LoopBodyObservationTest` contributes seven tests: filter Exit-callback snapshot
state; NEXT custom-list failure after a completed row; failures at successive
projection stages with exact snapshots and subsequent reuse; independent
overflow/string/null/shadowing expectations; custom-map field/callback order and
in-callback snapshots; numeric-subclass callback/trace parity; and tracer-close
reentrancy in generated and initial fallback paths. Differential tests use the
ordinary VM as the exact error/state oracle; the numeric/output tests also use
explicit independent expectations where compatible.

The parent ran [gate1.log](gate1.log). Its actual XML counts in
[test-counts.json](test-counts.json) total 862 passed and one skipped, with no
failures or errors: VM JVM 208, VM Node 118, conformance JVM 359 including the
skip, conformance Node 109, CLI JVM 56, and CLI Node 13. The 14 new independent
tests are included in the VM JVM gate. Node verifies compatibility behavior,
not JVM class generation.

The separate parent program reports 299 passing checks in
[parent-acceptance.log](parent-acceptance.log), covering seeded independent
outputs/interpreter comparison, varied expressions, exact VM errors/environment
order/snapshots, overflow/null, recovery, and fallback. The parent also validated
32 fixture/engine/size combinations three times each in
[fixture-validation.log](fixture-validation.log). Projection in both sizes
reports an actual generated entry and no fallback; the closure control reports
zero entries and explicit fallback. Frozen source and jar bindings are in
[final-source-manifest.json](final-source-manifest.json) and
[environment.json](environment.json).

These gates support the bounded correctness and execution-mode claims above.
They do not prove all possible callback behaviors, arbitrary bytecode CFGs,
resource-failure equivalence, concurrent use, class-unloading timing, or a
performance advantage. Benchmark and memory conclusions belong to the parent's
separate final report.
