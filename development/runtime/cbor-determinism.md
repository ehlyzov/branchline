---
status: Proposed
depends_on: ['runtime/cbor-internal-representation']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed deterministic CBOR encoding rules for Branchline interchange."
---
# CBOR Determinism Rules

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: deterministic encoding constraints for internal CBOR output.

## Summary
Branchline-to-Branchline exchange benefits from a stable CBOR byte sequence for hashing, signatures, and caching. This proposal formalizes when deterministic CBOR encoding is required and how it is applied.

## Current Behavior
- CBOR interchange is implemented with type-fidelity guarantees; deterministic ordering/shortest-form guarantees are still pending.

## Proposed Rules
- Deterministic mode uses RFC 8949 Section 4.2 encoding.
- Use shortest-length integer encodings.
- Use definite-length arrays and maps.
- Sort map keys by canonical CBOR key ordering.
- For Sets, sort elements by their canonical CBOR encoding bytes before writing.

## Modes
- `deterministic`: required for hashing, signatures, and cache keys.
- `fast`: may skip ordering or shortest form when not required, but must preserve type fidelity.

## API Surface
- Add an internal flag for deterministic encoding in any CBOR encoder used by CLI or runtime APIs.
- Expose a CLI flag only if CBOR output is added to the CLI.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with deterministic CBOR requirements and when they apply.
- Tests: add runtime tests that compare bytewise CBOR output under deterministic mode.
- Playground: not needed for internal CBOR determinism.

## Open Questions
- Whether deterministic mode should be the default once CBOR interchange ships.
