---
status: Implemented; parent acceptance pending
last_updated: 2026-10-17
---

# Playground XML input validation

Bounded addendum to `playground-ux-2026-10-17.md`, authorized by the parent
UX review. The parent development index entry covers this workstream.

## Confirmed problem

The browser worker calls fast-xml-parser without validation. Local execution of
the installed parser confirms that mismatched tags are accepted, plain text
parses as no elements, and multiple roots parse as multiple elements. The worker
then silently keeps the first root or supplies an empty object. Successful
transformation output can therefore hide malformed input and dropped data.

## Change and acceptance

- Extract existing XML normalization into a focused helper without changing valid
  XML mapping, including attributes, namespaces, repeated elements and mixed text.
- Validate with the existing pinned dependency's XMLValidator before parsing.
  Require exactly one root and reject empty input rather than inventing `{}`.
- Report an input-specific error with location when available.
- Add regression checks for malformed XML, multiple roots (including self-closing
  roots), plain text, empty input and valid normalization. Coordinate the npm test
  entry with the frontend owner; do not edit their component or package file.

No language/runtime changes, dependency upgrades or worker lifecycle changes.

## Verification

`node playground/scripts/test-xml-input.mjs` passes explicit expected mappings
for empty elements, attributes, repeated children, namespaces, mixed text, CDATA,
XML declarations, processing instructions, BOM and internal DOCTYPE entities.
It also checks all checked-in XML examples and rejects malformed/multiple-root,
plain-text, missing-root and trailing-text inputs. The installed validator accepts
multiple self-closing roots and trailing text, so a parser-only synthetic container
makes those cases visible before normalization. No second XML grammar is added.

A read-only comparison against HEAD's converter confirmed identical normalized
output for the XML example, BOM/declaration input and a DOCTYPE/entity fixture.
The pinned parser preserves numeric character references with its existing
settings (`&#65;` stays literal); this change preserves that behavior and makes no
claim of full XML standards validation. Parent owns the full build/browser gate.
