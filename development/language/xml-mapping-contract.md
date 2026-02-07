---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed unified XML mapping rules across JVM/JS parsers."
  - date: 2026-02-05
    change: "Implemented unified XML mapping across JVM/JS with mixed-content normalization."
---
# XML Mapping Contract

## Status (as of 2026-02-05)
- Stage: implemented.
- Scope: XML input mapping into Branchline data model and round-trip expectations.

## Summary
XML parsing behavior differs between JVM and JS platforms today. This proposal defines a unified XML-to-Branchline mapping aligned with the extended type system.

## Current Behavior
- JVM and JS now use one normalized mapping contract for XML input.
- Mixed content text segments are preserved as `$1`, `$2`, ... in document order.

## Goals
- A single mapping for XML input across platforms.
- Deterministic and round-trip-safe representation.

## Implemented Mapping
- Elements map to objects keyed by element name.
- Attributes map to `@attrName` keys.
- Text content maps to `$` for pure text nodes, and `$1`, `$2`, ... for mixed content segments.
- Repeated sibling elements of the same name map to arrays.
- Empty elements map to `""` when no attributes or children are present.

## Compatibility Strategy
- Mapping emits `$` / `$n` keys directly on both JVM and JS.
- `#text` is accepted as a compatibility alias for `$` on pure text nodes.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with the XML mapping rules and reserved keys.
- Tests: conformance coverage added in `conformance-tests/src/commonTest` to compare JVM and JS XML parsing results.
- Playground: example added demonstrating the mapped structure for attributes and mixed content.

## Open Questions
- Whether to always emit `$` even for pure text nodes to avoid schema ambiguity.
- Whether to preserve insignificant whitespace or only significant text.
