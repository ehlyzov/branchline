---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed XML namespace capture and serialization rules."
---
# XML Namespace Handling

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: namespace capture in XML input and declaration on XML output.

## Summary
Branchline currently ignores XML namespaces. This proposal introduces a consistent representation using `@xmlns` so that prefix-to-URI mappings can round-trip.

## Current Behavior
- JVM XML parsing uses `isNamespaceAware=false`.
- JS parser does not treat `xmlns` specially.

## Proposed Rules
- Namespace declarations are stored under `@xmlns` as a map.
- Default namespace uses key `$` in the `@xmlns` map.
- Element and attribute names include prefixes (for example `x:Item`).
- On output, all prefixes used in element or attribute names must be declared in an in-scope `@xmlns`.

## Error Handling
- If a prefixed name is used without a matching `@xmlns` declaration, output should error in strict mode.
- In non-strict mode, output may emit undeclared prefixes with a warning.

## Implementation Notes
- JVM parsing must enable `isNamespaceAware=true` and capture namespace URIs and prefixes.
- JS parsing should keep `xmlns` attributes and normalize them into `@xmlns`.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with `@xmlns` handling and prefix rules.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for namespace capture and output.
- Playground: add a `playground/examples/` case only after namespace parsing is implemented.

## Open Questions
- Whether to always lift `xmlns` attributes into `@xmlns`, or to keep them as raw attributes when in a non-namespace mode.
