---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed duplicate JSON key policy and enforcement modes."
---
# Duplicate JSON Key Policy

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: JSON input parsing rules for duplicate object keys.

## Summary
JSON allows duplicate object keys but does not define semantics. Branchline should make this deterministic and explicit.

## Current Behavior
- No explicit duplicate detection in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`.
- Behavior depends on the JSON parser and typically results in last-value wins.

## Proposed Modes
- `error` (default): reject JSON objects with duplicate keys.
- `last-wins`: accept duplicates and keep the last value.
- `first-wins`: accept duplicates and keep the first value.

## CLI and API Surface
- Add `--json-duplicates error|last|first`.
- Expose the setting to Playground to match CLI parsing behavior.

## Contract Implications
- When contracts are enabled, duplicate keys should be reported as contract violations in `error` mode.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with the duplicate-key policy and any CLI flags.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for `error`, `last-wins`, and `first-wins` modes.
- Playground: add a `playground/examples/` case only if the duplicate-key mode is exposed in the UI.

## Open Questions
- Whether to warn in `last-wins` and `first-wins` modes by default.
