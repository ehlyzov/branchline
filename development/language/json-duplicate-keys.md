---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed duplicate JSON key policy and enforcement modes."
  - date: 2026-02-05
    change: "Implemented duplicate JSON key handling in CLI and Playground, plus conformance tests."
---
# Duplicate JSON Key Policy

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: JSON input parsing rules for duplicate object keys.

## Summary
JSON allows duplicate object keys but does not define semantics. Branchline rejects duplicates at input time.

## Current Behavior
- No explicit duplicate detection in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`.
- Behavior depends on the JSON parser and typically results in last-value wins.

## Policy
- Duplicate object keys are rejected.

## CLI and API Surface
- No override flag; input parsing fails on duplicates.
- Playground matches CLI parsing behavior.

## Contract Implications
- When contracts are enabled, duplicate keys should be reported as contract violations in `error` mode.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with the duplicate-key policy.
- Tests: add conformance tests that assert duplicate-key rejection.
- Playground: no example required.

## Open Questions
- None.
