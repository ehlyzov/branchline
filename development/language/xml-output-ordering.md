---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-06
changelog:
  - date: 2026-02-05
    change: "Proposed deterministic XML output ordering rules."
  - date: 2026-02-06
    change: "Implemented XML output adapter with deterministic attribute/sibling ordering and @order support."
  - date: 2026-02-06
    change: "Added strict namespace prefix validation and conformance tests for ordering rules."
---
# XML Output Ordering

## Status (as of 2026-02-06)
- Stage: implemented.
- Scope: deterministic ordering of XML attributes and child elements on output.

## Summary
XML does not consider attribute order significant, but deterministic output is useful for tests, diffs, and hashing. This proposal defines ordering rules and how to preserve explicit sibling order when needed.

## Implemented Behavior
- XML output is available in CLI run/exec via `--output-format xml|xml-compact`.
- Attributes are emitted in lexicographic order by attribute name.
- Repeated sibling elements of the same name preserve their array order.
- Siblings with different names are emitted in lexicographic order.
- Prefixed element/attribute names are validated against in-scope `@xmlns` declarations in strict mode.

## Ordering Rules
- Attributes are emitted in lexicographic order by attribute name.
- Repeated sibling elements of the same name preserve their array order.
- Siblings with different names are emitted in lexicographic order unless explicit ordering is provided.

## Explicit Ordering
- `@order` is a reserved key that lists child element names in the desired order.
- When `@order` exists, children listed in `@order` are emitted first, then remaining children lexicographically.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with XML ordering rules for attributes and siblings.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that assert deterministic ordering.
- Playground: added `playground/examples/xml-output-ordering.json` to document `@order` output shape.

## Remaining Limitations
- Mixed-content ordering cannot be losslessly reconstructed from `$1`, `$2` plus named children because input mapping does not preserve full interleaving metadata.
