---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed audit of lossy conversion points in JSON/XML interchange."
---
# Conversion Loss Audit

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: document and test known lossy conversions in I/O.

## Summary
The data model research highlights where JSON and XML cannot fully preserve Branchline types. This proposal creates a formal audit list and warning policy so users understand when data fidelity is reduced.

## Known Loss Points
- Large integers and high-precision decimals when emitted as JSON numbers.
- Binary data emitted as base64 strings in JSON.
- JSON object key order differences when canonicalization is enabled.
- XML mixed content ordering when parsed into maps without explicit ordering.
- XML comments and processing instructions (currently dropped).
- XML namespaces (currently ignored on JVM).

## Proposed Deliverables
- A public table documenting each loss point, impact, and mitigation.
- Runtime warnings when lossy conversions occur (opt-out in non-strict mode).
- Conformance tests that cover each loss point.

## Docs, Tests, Playground
- Docs: publish a user-facing summary in `docs/language/index.md` or a new `docs/language/io-contracts.md`.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that exercise each loss point and warning.
- Playground: add a `playground/examples/` case only if loss warnings are surfaced in the UI.

## Open Questions
- Whether to make loss warnings default-on for CLI.
- Whether to add a `--lossy-ok` flag to silence warnings.
