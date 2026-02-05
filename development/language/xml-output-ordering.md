---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed deterministic XML output ordering rules."
---
# XML Output Ordering

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: deterministic ordering of XML attributes and child elements on output.

## Summary
XML does not consider attribute order significant, but deterministic output is useful for tests, diffs, and hashing. This proposal defines ordering rules and how to preserve explicit sibling order when needed.

## Current Behavior
- XML output ordering is not specified; JVM parsing loses mixed-content order.
- JS and JVM inputs can yield different structures, affecting output order.

## Proposed Rules
- Attributes are emitted in lexicographic order by attribute name.
- Repeated sibling elements of the same name preserve their array order.
- Siblings with different names are emitted in lexicographic order unless explicit ordering is provided.

## Explicit Ordering Option
- Allow a reserved key (for example `@order`) that lists element names in the desired order.
- When `@order` exists, emit children in that order and then emit remaining children lexicographically.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with XML ordering rules for attributes and siblings.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that assert deterministic ordering.
- Playground: add a `playground/examples/` case only if XML output ordering is surfaced in the UI.

## Open Questions
- Whether to encode explicit ordering using an array of `{name, value}` pairs instead of `@order`.
- How to preserve ordering in mixed content when `$1`, `$2` segments are present.
