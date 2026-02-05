---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed numeric key interpretation rules for JSON objects."
---
# Numeric JSON Key Interpretation

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: how JSON object keys map to string vs numeric keys in the internal model.

## Summary
The extended data model allows integer map keys. JSON object keys are always strings, so this proposal defines how and when numeric-looking keys can be interpreted as integers.

## Current Behavior
- JSON keys are always strings in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`.
- Map keys are stringified on output.

## Proposed Rules
- Default: keep all JSON keys as strings.
- Schema-driven conversion: if a contract specifies a numeric-keyed map, convert keys that match `^-?\d+$` into integers.
- Optional CLI override: `--json-key-mode string|numeric` where `numeric` converts only keys that are pure integers without leading zeros (except `0`).

## Contract and Schema
- Add `map<key:number, value:T>` or a similar construct to express numeric keys.
- JSON schema export should map numeric keys via `propertyNames` with a numeric regex when possible.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with numeric key coercion rules and schema guidance.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for schema-driven key conversion.
- Playground: add a `playground/examples/` case only if numeric key conversion is user-visible.

## Open Questions
- Whether to allow negative keys.
- Whether to treat `"007"` as a numeric key or a string key even in numeric mode.
