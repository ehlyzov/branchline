---
status: Implemented
depends_on: ['planning/vm-performance-audit2']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-10-17
---
# Optimized VM versus interpreter

User requested direct speed/memory comparison. Current VM source and benchmark jar match the accepted second audit manifest. This task measures, without product optimizations.

Plan: seven workloads (path reads, numeric reduction, filtering, object projection, lambda calls, array accumulation, dynamic object SET), 1000/4000 rows except fixed path reads at1000. Parse/IR once, build VM bytecode and interpreter Exec once; measure reusable execution with identical fresh environment creation included for both. Direct VM execution prevents interpreter fallback. Verify both against independent expected outputs and input immutability before timing.

Use external JMH harness against the same current fat jar: two forks, three300ms warmups, four300ms measurements, same heap limits, average time and gc.alloc.rate.norm. Memory here means bytes allocated per transform (GC pressure), not peak RSS or retained heap. Exclude parsing, bytecode compilation, I/O, tracing and snapshot persistence; report this explicitly. Preserve raw distributions and confidence intervals; do not turn aggregate microbenchmark results into a universal performance claim. Record sources, commands, hashes and reproducible artifacts under development/evidence/vm-interpreter-comparison and update this index.

## Correction after repository-wide discovery

Initial inspection was limited to vm-benchmarks and missed existing paired suites. User correctly pointed this out. Canonical comparison uses unchanged InterpreterTransformBenchmark and VMTransformBenchmark pathExpressions/arrayComprehensions/typicalTransform at small/medium/large; interpreter collectionType=hash matches VM data. Their source programs and dataset generators match. CrossEngineBenchmark also has both engine IDs but compileVm uses compatibility fallback, so pure-VM claims require additional path evidence there; it is not the primary run here.

Run existing native pairs as primary evidence with equal JMH forks/warmup/measurement/heap settings, output validation and gc profiler. The already-started custom13scenario run remains explicitly supplemental (fresh environment creation is timed there, unlike native Level.Invocation setup). Do not mix their absolute times or aggregate their ratios into one score.

## Completed

Existing native JMH pairs: 9 validated pairs, two forks per engine/state, GC allocation profiler; interpreter 1.92–2.47× faster, VM comprehensions allocate up to 5.79× less. Supplemental 13 validated pairs show the specialized dynamic SET advantage (4000 rows: VM ~100.5× faster and ~1791× fewer allocated bytes), without a general VM advantage. Raw results, confidence intervals, commands and unchanged source/jar hashes are recorded in [the evidence report](../evidence/vm-interpreter-comparison/README.md). Memory scope is allocation per operation, not retained heap or peak RSS. No product edits were made.
