---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-06
changelog:
  - date: 2026-02-05
    change: "Proposed XML namespace capture and serialization rules."
  - date: 2026-02-06
    change: "Implemented namespace-aware XML input mapping on JVM/JS with @xmlns normalization and conformance tests."
  - date: 2026-02-06
    change: "Documented XML output prefix validation as deferred until XML output mode is implemented."
---
# XML Namespace Handling

## Status (as of 2026-02-06)
- Stage: implemented for XML input namespace capture.
- Scope: `@xmlns` capture in XML input; XML output declaration/validation rules documented for future output mode.

## Summary
Branchline captures XML namespaces using `@xmlns` so prefix-to-URI mappings can round-trip through XML input parsing on JVM and JS.

## Implemented Behavior
- JVM XML parsing enables `isNamespaceAware=true`.
- JVM and JS parsers normalize namespace declarations into `@xmlns`.
- Default namespace is stored at `@xmlns.$`.
- Prefixed declarations are stored at `@xmlns.<prefix>`.
- Prefixed element and attribute names are preserved (for example `x:Item`, `@x:id`).

## Output Rules (Planned)
- Namespace declarations are stored under `@xmlns` as a map.
- On XML output, all prefixes used in element or attribute names must be declared in an in-scope `@xmlns`.

## Error Handling (Planned for XML Output)
- If a prefixed name is used without a matching `@xmlns` declaration, output should error in strict mode.
- In non-strict mode, output may emit undeclared prefixes with a warning.

## Implementation Notes
- XML output mode is not currently available in the CLI, so output-side prefix validation is deferred.
- Namespace capture behavior is covered by `conformance-tests/src/commonTest`.

## Docs, Tests, Playground
- Docs: `docs/language/index.md` now documents `@xmlns` handling and prefix preservation.
- Tests: conformance tests cover namespace capture and scoping.
- Playground: namespace mapping example added.

## Open Questions
- Whether XML output should default to strict undeclared-prefix validation or require an explicit strict mode flag.
