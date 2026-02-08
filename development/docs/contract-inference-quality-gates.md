---
status: In Progress
depends_on: ['planning/contract-inference-v2-milestone-plan']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created quality gate definitions for Contract Inference V2."
---
# Contract Inference Quality Gates

## Precision Gates
- Curated example unknown-shape ratio must be `<= 0.35`.
- `playground/examples/junit-badge-summary.json` unknown-shape ratio must be `<= 0.20`.

## Determinism Gates
- Contract JSON output for the same script is byte-stable per platform.
- JVM and JS outputs are semantically equivalent after stable sort normalization.

## Validation Gates
- Warn/strict behavior parity with existing semantics where rules overlap.
- V2 diagnostics ordering is deterministic.

## Performance Gates
- V2 inference must not increase total CLI inspect time by more than 2.0x on curated examples.

## Reporting
- Add a scripted metric runner that emits:
  - unknown/total shape count
  - unknown ratio
  - per-example failures
