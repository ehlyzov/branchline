---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed numeric key interpretation rules for JSON objects."
  - date: 2026-02-05
    change: "Implemented numeric key conversion with opt-in CLI support and conformance tests."
---
# Numeric JSON Key Interpretation

## Status (as of 2026-02-05)
- Stage: implemented.
- Scope: opt-in numeric key conversion for JSON object keys.

## Summary
The extended data model allows integer map keys. JSON object keys are always strings, so numeric-looking keys can be converted into integers when explicitly enabled.

## Behavior (Implemented)
- Default: JSON keys remain strings.
- `--json-key-mode numeric` converts keys matching `^(0|[1-9]\d*)$` to integers.
- Top-level input keys remain strings; nested object keys are converted.
- JSON output always stringifies map keys.

## Rules (Implemented)
- Only non-negative integers are eligible for conversion.
- Leading zeros disable conversion (except `0`).

## Contract and Schema
- Schema-driven numeric-key conversion is not implemented yet.

## Docs, Tests, Playground
- Docs: updated `docs/language/index.md` and CLI docs with numeric key coercion rules.
- Tests: added conformance coverage for numeric key conversion.
- Playground: no UI exposure yet.

## Open Questions
- None at this time.
