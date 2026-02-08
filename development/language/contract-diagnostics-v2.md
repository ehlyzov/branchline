---
status: In Progress
depends_on: ['language/contract-model-v2', 'language/contract-inference-static-analysis']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created diagnostics V2 proposal with deterministic mismatch categories and evidence-aware messaging."
  - date: 2026-02-08
    change: "Implemented ContractValidatorV2, ContractEnforcerV2, and deterministic diagnostics rendering."
---
# Contract Diagnostics V2

## Summary
Contract diagnostics are upgraded to report deterministic, path-rich mismatches tied to inference evidence.

## Goals
- Deterministic ordering across JVM/JS.
- Structured mismatch categories.
- Actionable message payloads for CLI and Playground.
- Evidence traceability for inferred constraints.

## Violation Model
- `ContractViolationV2` fields:
  - `path`
  - `kind`
  - `expected`
  - `actual`
  - `ruleId`
  - `confidence`
  - `evidenceSpans`
  - `hints`

## Mismatch Categories
- Missing required path
- Missing conditional group
- Unexpected field
- Shape mismatch
- Nullability mismatch
- Opaque-region validation warning

## Rendering
- Human renderer (CLI/playground warnings)
- JSON renderer for tooling

## Compatibility Policy
- V2 diagnostics are canonical output.
- Legacy V1 diagnostics are removed once V2 runtime validation is fully wired.
