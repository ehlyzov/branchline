---
status: Implemented
depends_on: ['planning/vm-prepared-block-experiment']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Whole-loop-body compilation experiment

User authorized goal mode, GPT-6.1 Sol agents and parent independent acceptance.
Preserve this chat's existing dirty worktree and unrelated deep-research reports.
This Branchline thread is not the separately designated Codeclew research/coding
pair. No commit, publication or default enablement. Profile before product edits.

## Bounded outcome

First capture CPU profiles for unchanged large holdoutProjection in interpreter,
ordinary VM and preparedOptimized. Preserve jar/source hashes; do not treat
instrumented timing as performance evidence. Identify measured samples separately
from source-based inference and note sampling limitations.

Implement one opt-in whole-loop-body compilation experiment that removes the
per-bytecode dispatch from a supported body. Select its representation only after
profiling and design review. No fixture/source/hash/value-specific recognition.
A bounded general shape is acceptable; document unsupported code and explicit
fallback. If JVM-only, preserve other-platform behavior and do not claim JS speed.
Avoid unrelated parser/numeric/parallelism/selective JSON changes.

Primary projection must actually execute the compiled body. Paths and reduction
are fixed controls; do not claim loop-body execution if they fall back. Compare
interpreter, direct ordinary VM, prior preparedOptimized and the new executor on
unchanged VMFactorialFixture source/input (small 10x5, large 500x25). Additional
independent and non-fixture programs prevent benchmark-shape overfitting.

## Correctness and acceptance

Preserve raw output, exact errors/source PC/partial state, insertion order,
missing/null, overflow, shadowing and loop restoration, host/tracer/custom nested
collection and numeric-subclass observation, failure then reuse. Unsupported
operations may fall back before effects; dynamic deoptimization must resume at
the precise original instruction without replay. Guard cost belongs in timing.
Plans/frames/compiled-code lifetime, mutation and thread constraints explicit.

Parent owns serialized builds, broad JVM/Node gate and independent differential
acceptance; agents own disjoint implementation/harness/test files. Freeze after
acceptance and before measurements. No own concurrent heavy processes.

JMH primary: 2 forks, 3x1s warmup, 5x1s measurement, 1 thread, identical heap/JVM,
GC B/op, both sizes; reverse-order large repeat. Separate preparation and fallback
protocols; three fresh-process memory repeats per engine/lifetime (one reused vs
1000 independently prepared executors), heap and RSS separately, metaspace if
runtime-generated classes. Parsing/serialization outside execution timing.

Promotion target: >=15% lower large projection time than ordinary VM, repeatable
in both orders, no execution B/op growth. Interpreter advantage must be directly
measured and uncertainty reported, never inferred from beating VM. Controls
should not regress >5% repeatably. Report preparation and retained-memory costs;
a speed win alone is not enough to recommend default enablement. A negative
bounded outcome completes the goal; no unbounded optimizer expansion.

## Ownership

Parent: baseline profile, plan/INDEX, independent acceptance, serial measurements,
final report and contour. factorial_runtime: runtime/planner and own regressions;
factorial_fusion: independent adversarial tests/review; factorial_harness: new
benchmark/probe and reproduction documentation. Implementation waits for profile
and an agreed API/contract. Existing workloads and old harnesses stay unchanged.

## Profile-driven design decision

JFR measured 557 ordinary-VM, 460 preparedOptimized and 592 interpreter worker
samples. Ordinary VM leaf attribution: executeOpcode 28.0%, run 17.1%; prepared
runner 24.1%, with material deque work. These are sampled method locations,
including inlined work, not a promise of removable dispatch-only percentages.
Raw JFR and summary are in `../evidence/vm-loop-body-experiment/`.

Implement JVM bytecode generation for one compiler-shaped comprehension loop,
<=128 body instructions, using locally available pinned ASM 9.9 in jvmMain only.
Emit actual JVM branches/filter/backedge and fixed op-specific VM bridge calls,
with canonical stack/environment/PC. No per-opcode switch, instruction array walk,
or interpreter delegation inside the generated body. Keep stack/numeric helpers
and exact observation/effect order; register/SSA lowering remains outside scope.
Reject nested loops, body calls, TRY/suspend/mutations and unsupported CFG before
effects. Paths/reduction can fall back explicitly; projection must enter kernel.

API contract: JvmLoopCompiler.prepare(bytecode): LoopBodyPlan;
VM.executeLoopBodies(plan, env); scalar loopBodyExecuted/loopBodyEntries/
loopBodyFallbackReason. One loader/class per eligible plan, no global classcache;
plan holds owned bytecode structure, not deep immutable constant values.
Measure compilation, generated class lifetime/metaspace and unloading explicitly.
No JavaCompiler source compilation dependency or runtime generated source text.

## Completed outcome

Implemented bounded JVM ASM backend with one generated loop, 646-byte run method
for fixed projection; canonical stack/helpers retained, no opcode dispatch inside
body. 862 tests passed with one existing skip, 299 parent comparisons, 32 fixture
configurations x3; final 77 source hashes and jar match. All serialized measurement
commands and before/after JFR profiles completed. Compiler/workloads unchanged.

Large projection time improved 16.1% /11.2% versus ordinary VM across forward /
reverse series; interpreter means were only 2.1% /1.1% higher, with overlapping
intervals (no proven interpreter advantage). No execution B/op growth versus VM.
The >=15% target did not repeat. Preparation rose 3.251 to50.20us. Prepared-only
heap for1000 executors rose6.069 to9.468MiB, plus generated-case3.505MiB incremental
Metaspace used. Executed-state memory includes retained result trees and is not
plan memory. Default promotion rejected; retain opt-in research implementation.

Remaining sample attribution centers on ArrayDeque, comparison/numeric helpers
and PC stores; typed JVM-local lowering with explicit state maps is a future
hypothesis, not part of this goal. No further architectural expansion performed.
Report, uncertainty and raw evidence: `../evidence/vm-loop-body-experiment/README.md`.
