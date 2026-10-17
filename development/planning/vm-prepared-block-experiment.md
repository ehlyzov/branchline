---
status: Implemented
depends_on: ['planning/vm-slots-fusion-experiment']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Compiler-prepared execution experiment

User authorized execution in goal mode after the bounded architecture proposal.
Use GPT-6.1 Sol High implementation agents; parent owns scope, independent
acceptance and serialized measurements. This Branchline thread is not the
separately designated Codeclew research/coding pair. Preserve the existing
working tree from this chat, including all earlier VM work; do not touch the
user's deep-research reports. No default enablement, commit or publication.

## Hypothesis and bounded implementation

Move decisions from execution to preparation: basic-block boundaries, resolved
operands, frame layout, observation/materialization sites, and eligible direct
operations. Compare the same new runner with optimization disabled/enabled to
separate runner cost from transformation benefit. Ordinary VM/interpreter remain
controls. Avoid another runtime pattern recognizer or a wrapper around previous
slots/fusion modes. A lowering pass after existing IR/bytecode compilation is
acceptable; document precisely which work moved and which did not.

Do not mix numeric representation changes, JIT/code generation, parser changes,
parallelism or selective JSON into this slice. Keep input/output semantics,
exception order/partial effects, environment order and host observation intact.
Conservative explicit fallback is allowed outside the supported domain, but all
three primary workloads must actually execute the requested new mode. No replay
of effects on failure/deoptimization. Preserve absent/null, stale local vs live
named values after callbacks, numeric overflow, loop shadowing, original PCs and
snapshots where supported. Prepared bytecode ownership and thread/reentrancy
constraints must be explicit. Do not remove a required compatibility observation
to make the benchmark faster.

Prefer a single prepared dispatch and preallocated/reused frame with references
cleared appropriately. Compiler-known bindings and operand indices, direct static
paths and mirror elimination are bounded candidate transformations. Guard/effect
analysis must be conservative; ordinary root containers do not prove nested
values harmless. Materialization metadata is not permission to replay operations.

## Acceptance gates

1. Record pre-change source/jar hashes; compare changes against this working-tree
   baseline, not git HEAD. Assign exclusive file ownership before edits.
2. Common JVM/Node tests and independent parent differential probes for both new
   modes, with independent expected outputs and fallback controls. Reuse relevant
   previous adversarial tests; add material-delta tests for new prepared semantics.
3. Primary fixed programs are existing VMFactorialFixture paths, reduction and
   holdoutProjection with resident Map/List input. Large=500 orders x25 items;
   small=10x5 is a separate fixed-cost check. Timed execution includes identical
   fresh environment creation; parsing/compilation/serialization excluded.
4. Four engines: interpreter, current VM, prepared control, prepared optimized.
   Validate effective mode outside timing; no diagnostics/report allocations
   solely for timed instrumentation. All treatment runtime costs are included.
5. Freeze source before timing. JMH one thread, >=2 forks, >=3x1s warmup and
   >=5x1s measurements with identical heap/JVM and GC allocation profiler. Reverse
   order repeat for the final large decision; no concurrent own CPU-heavy work.
   If convergence/order uncertainty prevents a decision, report it and use a
   bounded targeted repeat. No favorable short-run selection.
6. Separate source-to-prepared cost, allocation B/op, three fresh-process memory
   repeats per engine/lifetime (one reused vs 1000 retained executors), peak RSS.
   Heap observations are approximate; distinguish shared plan vs independent
   plan lifetime. Keep runtime source hashes tied to every result.
7. First promotion gate: optimized time <=0.85 current VM on both large reduction
   and projection; paths <=1.05 current VM; no B/op increase on primary cases,
   correctness intact. Interpreter comparison is mandatory but a separate claim.
   A failed bounded experiment is a valid completed goal: report facts, causes
   versus hypotheses and do not expand optimizations automatically.
8. Final report, INDEX and service contour updated. Keep experimental opt-in.

## Ownership and progress

Parent: this plan, INDEX, independent probes/evidence, serialized builds and JMH,
final decision and contour. Implementation assignments recorded in agent messages.

## Bounded implementation correction before final measurement

The first frozen candidate passed correctness, but the partial forward JMH exposed
an order-of-magnitude prepared-control regression. Classfile inspection found
executeBlocks had 8,935 bytes of JVM code; the local JVM defaults
DontCompileHugeMethods=true. A same-jar diagnostic with that restriction disabled
reduced small reduction from approximately 13.2 to 1.48 microseconds. The original
partial matrix, jar hash and source manifest were archived in evidence under
initial-candidate; these results are not final optimization evidence.

Parent authorized one bounded structural correction: extract the unchanged hot
loop from Kotlin's try/finally expansion and extract cleanup, preserving behavior,
guards and optimized patterns. No new optimization is added. Rebuild/reverify,
measure method sizes and refreeze, then restart the complete final matrix using
ordinary JVM flags for every engine. The diagnostic JVM flag will not be used to
claim a production advantage. Stop architectural expansion if the corrected
bounded candidate fails the predeclared gate.

## Completed acceptance and disposition

Implemented by the assigned GPT-6.1 Sol High agents, independently accepted by
parent. Final gate: 846 passing JVM/Node tests, one existing skip; 538 independent
comparisons; 32 fixture configurations each executed three times. Hot loop after
the bounded correction is 3,120 JVM bytes. Final source/jar hashes match.

Completed both full JMH orders, separate preparation/fallback protocols and
24 fresh-process memory probes. Optimized large reduction improves time versus
ordinary VM by 26.1% / 26.6%, but remains 9.8% / 10.4% slower than interpreter.
Projection changes from 7.1% slower to 6.2% faster than ordinary VM; paths is
17.3% / 1.0% slower. The joint speed gate fails. Execution allocation is equal on
paths and 32 B/op lower on reduction/projection; independently prepared-executor
heap rises from 6.161 to 8.603 MiB per 1,000 executors. Keep opt-in, do not promote.

Bounded goal complete with a mixed research result, not a general speed win.
No additional architectural expansion is authorized by this experiment. Details,
uncertainty, baseline check and next hypotheses: `../evidence/vm-prepared-block-experiment/README.md`.
