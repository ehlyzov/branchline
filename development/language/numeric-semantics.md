---
status: Implemented
depends_on: []
blocks: ['language/numeric-refactor']
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/numbers.md and added YAML front matter."
---
# Numeric semantics and performance

## Status (as of 2026-01-31)
- Numeric equality/comparison now treat all numeric types (`Int`/`Long`/`Float`/`Double`/`BLBigInt`/`BLBigDec`) as value-equal; `NUMBER("0") == 0` works in both interpreter and VM.
- Truthiness avoids extra BigDec allocations for primitives and BigInts/BigDecs, using direct sign checks.
- CLI regression tests cover interpreter + VM execution paths for numeric equality on JVM/JS.
- JS BigNum helpers pool common zero/one instances to reduce allocations for frequent literals.
- Cross-type assertions (BigInt/BigDec vs primitives, mixed ordering) now live in conformance common tests; optional JVM benchmarks exercise interpreter vs VM comparison throughput.

## Perf posture (current)
- Arithmetic already fast-paths primitives; comparisons/equality now fast-path primitives and only promote to BigDec when needed (mixed precision or existing BigDec/BigInt).
- Remaining hotspots: BigDec creation in mixed numeric paths, JS BigDec emulation cost, zero/boolean checks in tight loops.

## Next steps
1) Cache numeric literal types in IR/bytecode so comparison dispatch can skip type inference on every op.
2) Measure and tune JS BigDec paths (pooling, inline helpers) where mixed precision remains common; add microbenchmarks to track regressions.
