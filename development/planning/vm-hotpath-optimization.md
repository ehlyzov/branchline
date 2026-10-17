---
status: Implemented
depends_on: ['planning/vm-interpreter-comparison']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# VM native-JMH hot-path optimization

User authorized diagnosis, a concrete plan, fixes by GPT-6.1 Sol High agents, independent parent correctness verification and repeated native JMH timing/allocation comparison. Preserve all existing accepted working-tree changes and benchmark workloads.

## Plan and acceptance gates

1. Preserve the current built VM/interpreter jars and source hashes. Investigate existing native path/comprehension/reduction workloads; capture a separate CPU stack profile. Distinguish interpreter specialization, dispatch/stack costs, and redundant per-instruction work. Agents investigate in parallel; JVM workloads run sequentially.
2. Rank evidence-backed fixes and assign non-overlapping implementation scope. Prefer removing unneeded trace bookkeeping and redundant hot-path operations without new language semantics or benchmark-specific shortcuts. Record final selected fixes here before editing product code.
3. Independently inspect implementation and edge cases: tracing changes during host calls, custom environments, stepping/snapshot restore, exceptions and function frames, numeric overflow and collection ownership. Add focused meaningful regression coverage and run VM JVM/Node and cross-runtime gates appropriate to changes.
4. Build current jars, revalidate native expected outputs and identical inputs. Repeat all 9 existing native pairs with identical JMH settings and GC profiler, serialized. Keep short comparison with original run and longer warmed before/after checks for material findings. Report time and B/op separately, raw intervals, limitations and any regressions. Do not claim total heap/RSS from allocation.
5. Complete evidence report and contour records for changed invariants. Stop at tested, measured improvements; do not broaden into a new VM architecture merely to beat every interpreter microbenchmark.

## Selected slice 1

CPU stack profiling on the preserved baseline large typicalTransform confirms time in VM dispatch and HashMap lookups. Source inspection identifies two trace metadata map lookups per opcode even with no tracer, plus PathWrite metadata lookup on stores and unconditional old-value reads. First fix: check tracer/step options before metadata access and remove unneeded old-value reads only where environment side effects cannot be observed. Preserve dynamic tracer activation/deactivation inside host callbacks, custom map getter/setter behavior and incremental execution. Agent vm_hotpath owns VM.kt and focused common regression tests; another agent independently examines methodology and final correctness. No opcode/compiler ABI changes in this slice.

## Selected slice 2 experiment

The first trace-only quick run improves large typicalTransform from the longer baseline ~53.5 us to ~39.5 us, with paths ~0.496 to ~0.366 us; these unequal-duration exploratory runs select the next hypothesis, not final acceptance. Dispatch remains: executeOpcode is a large JVM method called once per instruction. Experiment with Kotlin source inlining that single-call-site dispatcher into run, preserving exact instruction/trace/step semantics and bytecode ABI. Keep only with repeatable JMH benefit; otherwise discard this isolated change. No fused opcode or benchmark-specific shortcut. Agent vm_hotpath implements, parent measures and independently verifies.

## Dispatch experiment decision

Inlining the entire dispatcher regressed the exploratory typicalTransform (~48.5 us versus trace-only ~39.5 us). Reject that modifier-only experiment; its raw measurements remain in evidence. One final bounded dispatch experiment splits frequent operations into a small inline dispatcher while delegating the remaining operations to the existing large handler. Move cases rather than duplicate semantics; preserve all tracing/exception/step control and instruction ABI. This tests JIT method-size/call-boundary sensitivity without compiler changes. Retain only with benefit over trace-only; otherwise retain trace-only and report residual dispatch cost.

## Final implementation scope

Compact split dispatch also regressed versus trace-only (exploratory large paths 0.595 vs 0.366 us, reduction 47.279 vs 39.473 us). Discard both dispatch experiments, preserve their evidence, restore exact trace-only source and run final gates. Retain the proven elimination of unused trace metadata lookups and safe old-value reads. Residual stack-dispatch work is inherent in the current eight-instruction loop lowering; new fused opcodes would require a separate bytecode/stepping/serialization change and are deferred rather than claimed fixed. Final matched long measurements determine the actual accepted improvement, not these exploratory estimates.

## Acceptance completed

Accepted only the tracing/environment patch and six common regression tests. All nine native pairs passed independent expected-output and pristine-input checks; runtime class hashes matched across rebuilt jars. VM JVM/Node: 227 passed; conformance: 467 passed and one existing skip. Matched longer large-case confirmation reduced VM time by 14.4% (comprehensions), 25.7% (paths), 26.5% (reduction), with unchanged VM allocation. Interpreter remains faster; no general VM advantage is claimed. Rejected both dispatcher experiments and restored exact reviewed trace-only source. Evidence and reproducible commands: [report](../evidence/vm-hotpath-optimization/README.md). Contour invariant and verification records updated. Memory-model ideas have a separate proposal, not an unmeasured implementation.
