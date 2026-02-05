---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed unified XML mapping rules across JVM/JS parsers."
---
# XML Mapping Contract

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: XML input mapping into Branchline data model and round-trip expectations.

## Summary
XML parsing behavior differs between JVM and JS platforms today. This proposal defines a unified XML-to-Branchline mapping aligned with the extended type system.

## Current Behavior
- JVM uses DOM with `isNamespaceAware=false` and `textContent.trim()` in `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/JvmPlatform.kt`.
- JS uses `fast-xml-parser` with `@` attributes and `#text` nodes in `cli/src/jsMain/kotlin/io/github/ehlyzov/branchline/cli/JsPlatform.kt`.
- Mixed content order is not preserved on JVM, and text nodes are trimmed.

## Goals
- A single mapping for XML input across platforms.
- Deterministic and round-trip-safe representation.

## Proposed Mapping
- Elements map to objects keyed by element name.
- Attributes map to `@attrName` keys.
- Text content maps to `$` for pure text nodes, and `$1`, `$2`, ... for mixed content segments.
- Repeated sibling elements of the same name map to arrays.
- Empty elements map to `""` when no attributes or children are present.

## Compatibility Strategy
- Accept `#text` as an alias for `$` during input to preserve existing JS behavior.
- Provide a migration flag to emit `$` only.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with the XML mapping rules and reserved keys.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that compare JVM and JS XML parsing results.
- Playground: add a `playground/examples/` case demonstrating attributes and mixed content XML.

## Open Questions
- Whether to always emit `$` even for pure text nodes to avoid schema ambiguity.
- Whether to preserve insignificant whitespace or only significant text.
