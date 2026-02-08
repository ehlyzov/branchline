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
  - date: 2026-02-08
    change: "Wired V2 validation and diagnostics into CLI run/exec and Playground contract warnings."
  - date: 2026-02-08
    change: "Marked implemented after M9 removal of public V1 contract output."
  - date: 2026-02-08
    change: "Reopened for cleanup: diagnostics decoupled from static evidence metadata, with debug-only origin/spans policy."
---
# Contract Diagnostics V2

## Summary
Diagnostics remain deterministic and path-rich, but they are being simplified to not depend on static inference evidence payloads.

## Goals
- Deterministic ordering across JVM/JS.
- Structured mismatch categories.
- Actionable message payloads for CLI and Playground.
- No dependence on static evidence objects.

## Violation Model (target)
- `ContractViolationV2` fields:
  - `path`
  - `kind`
  - `expected`
  - `actual`
  - `ruleId` (optional, validator/local rule identity)
  - `hints`

## Mismatch Categories
- Missing required path
- Missing conditional group
- Unexpected field
- Shape mismatch
- Nullability mismatch
- Opaque-region validation warning

## Cleanup Direction
- Validator must not rely on `node.evidence` for message quality.
- `origin` and spans are debug-facing metadata only.
- Runtime-fit evidence remains a future add-on and is currently disabled.

## Compatibility Policy
- V2 diagnostics remain canonical output.
- Cleanup is in-place for V2 with no extra version bump.
