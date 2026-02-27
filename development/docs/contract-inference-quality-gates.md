---
status: Implemented
depends_on: ['planning/contract-inference-v2-milestone-plan']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-27
changelog:
  - date: 2026-02-27
    change: "Aligned gates to canonical latest-only contracts: structural/debug JSON checks, strict validation checks, and satisfiability/witness checks."
---
# Contract Inference Quality Gates

## Precision Gates
- Curated example unknown-shape ratio must be `<= 0.35`.
- `playground/examples/junit-badge-summary.json` unknown-shape ratio must be `<= 0.20`.

## Structural JSON Gates
- Contract JSON must not duplicate object members in both `shape.schema.fields` and `children`.
- Canonical JSON must not include contract version fields.
- Public JSON must not include static `evidence` payloads.

## Debug Visibility Gates
- Default JSON output omits `origin` and spans.
- Debug output includes `origin` and available spans/debug metadata.
- CLI inspect and Playground debug toggle must match behavior.

## Determinism Gates
- Contract JSON output for the same script is byte-stable per platform.
- JVM and JS outputs are semantically equivalent after stable sort normalization.

## Validation Gates
- Warn/strict behavior parity with existing semantics where rules overlap.
- Strict gate for `junit-badge-summary`:
  - real sample input passes strict validation,
  - no bogus `input.suite.*` requirement leaks from local loop vars,
  - output `suites[*]` enforces element field contract.
- Domain strictness gate:
  - inferred enum domain on output `status` rejects non-domain values.

## Satisfiability + Witness Gates
- Every inferred contract must be satisfiable or explicitly degraded to safe subset with diagnostics.
- Witness generator must produce at least one strict-valid input and output sample per contract.
- Generated witnesses must pass validator in JVM and JS.

## Performance Gates
- Inference must not increase total CLI inspect time by more than 2.0x on curated examples.

## Reporting
- Metric + structure/debug runner: `cli/src/jvmTest/kotlin/io/github/ehlyzov/branchline/cli/ContractInferenceQualityGateTest.kt`.
