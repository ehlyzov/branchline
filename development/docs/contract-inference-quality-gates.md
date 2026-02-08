---
status: Implemented
depends_on: ['planning/contract-inference-v2-milestone-plan']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created quality gate definitions for Contract Inference V2."
  - date: 2026-02-08
    change: "Implemented CLI JVM quality-gate tests for curated unknown-ratio and junit-badge-summary thresholds."
  - date: 2026-02-08
    change: "Reopened for V2 JSON cleanup gates: structural no-duplication check and debug-metadata visibility policy checks."
  - date: 2026-02-08
    change: "Implemented structural/debug gates in CLI quality tests (no duplicate schema keys; origin debug-only assertions)."
---
# Contract Inference Quality Gates

## Precision Gates
- Curated example unknown-shape ratio must be `<= 0.35`.
- `playground/examples/junit-badge-summary.json` unknown-shape ratio must be `<= 0.20`.
- Curated quality-gate set:
  - `contract-deep-composition`
  - `error-handling-try-catch`
  - `junit-badge-summary`
  - `stdlib-core-append-prepend`
  - `stdlib-core-listify-get`
  - `stdlib-strings-casts`
  - `stdlib-strings-text`
  - `xml-input-output-roundtrip`

## Structural JSON Gates (new)
- Contract JSON must not duplicate object members in both `shape.schema.fields` and `children`.
- Public JSON must not include `open`.
- Public JSON must not include static `evidence` payloads.

## Debug Visibility Gates (new)
- Default JSON output omits `origin` and spans.
- Debug output includes `origin` and available spans/debug metadata.
- CLI inspect and Playground debug toggle must match behavior.

## Determinism Gates
- Contract JSON output for the same script is byte-stable per platform.
- JVM and JS outputs are semantically equivalent after stable sort normalization.

## Validation Gates
- Warn/strict behavior parity with existing semantics where rules overlap.
- V2 diagnostics ordering is deterministic.

## Performance Gates
- V2 inference must not increase total CLI inspect time by more than 2.0x on curated examples.

## Reporting
- Metric + structure/debug runner: `cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/ContractInferenceQualityGateTest.kt`.
- Asserts:
  - unknown/total shape ratio gates
  - no duplicate `schema` object member source in V2 JSON
  - `origin` absent in standard JSON and present in debug JSON
