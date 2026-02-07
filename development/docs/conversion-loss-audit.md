---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-07
changelog:
  - date: 2026-02-05
    change: "Proposed audit of lossy conversion points in JSON/XML interchange."
  - date: 2026-02-07
    change: "Published user-facing I/O contracts audit table in docs/language/io-contracts.md."
  - date: 2026-02-07
    change: "Added CLI conversion-loss warning hooks for XML input and JSON/XML output loss points."
  - date: 2026-02-07
    change: "Added conformance tests for conversion-loss warning detection."
---
# Conversion Loss Audit

## Status (as of 2026-02-07)
- Stage: implemented.
- Scope: documented and test-covered lossy conversions in JSON/XML I/O.

## Summary
The data model research highlights where JSON and XML cannot fully preserve Branchline types. This proposal creates a formal audit list and warning policy so users understand when data fidelity is reduced.

## Implemented Loss Points
- Large integers and high-precision decimals when emitted as JSON numbers.
- Binary data emitted as base64 strings in JSON.
- JSON object key order differences when canonicalization is enabled.
- XML mixed content ordering when parsed into maps without explicit ordering.
- XML comments and processing instructions dropped during parsing.

## Delivered Artifacts
- Public audit table with triggers, warning behavior, and mitigation guidance.
- CLI conversion warnings for known lossy conversion points.
- Conformance tests that exercise warning detection across input/output scenarios.

## Docs, Tests, Playground
- Docs: publish a user-facing summary in `docs/language/index.md` or a new `docs/language/io-contracts.md`.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that exercise each loss point and warning.
- Playground: add a `playground/examples/` case only if loss warnings are surfaced in the UI.

## Open Questions
- Whether to add a `--lossy-ok` flag to silence conversion warnings in automation-heavy workflows.
