---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed Set type semantics and JSON/XML serialization rules."
  - date: 2026-02-05
    change: "Implemented set<T> contracts, JSON schema export, and deterministic JSON ordering."
---
# Set Serialization Semantics

## Status (as of 2026-02-05)
- Stage: implemented.
- Scope: Set type and deterministic serialization.

## Summary
The extended data model includes Sets, but current contracts and I/O do not represent them. This proposal defines how Sets behave and how they serialize to JSON and XML.

## Current Behavior
- Contracts support `set<T>` and JSON schema export emits `uniqueItems: true`.
- JSON output serializes sets as arrays with deterministic ordering.

## Goals
- Represent unordered unique collections explicitly.
- Ensure deterministic serialization for Sets.

## Implemented Rules
- Add `set<T>` to the contract type system.
- Internally, Sets enforce uniqueness using Branchline equality rules.
- JSON output: Sets serialize as arrays with deterministic ordering.
- XML output: Sets serialize as repeated elements in deterministic order.

## Deterministic Ordering
- Sort by type rank: null, boolean, number, string, array, object.
- For numbers, sort by numeric value.
- For strings, sort lexicographically (Unicode code points).
- For arrays and objects, sort by their canonical JSON encoding.

## Contract and Schema
- JSON schema export: represent `set<T>` as `type: array` with `uniqueItems: true` and `items` mapped from `T`.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` and `docs/language/expressions.md` with set type semantics and JSON/XML serialization.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for uniqueness and deterministic ordering.
- Playground: add a `playground/examples/` case only if a set type or literal becomes user-visible.

## Open Questions
- Whether to allow heterogeneous Sets or require a single element type.
