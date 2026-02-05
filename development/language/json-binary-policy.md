---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed JSON binary encoding policy aligned with extended type system."
---
# JSON Binary Policy

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: encoding and decoding of byte arrays in JSON.

## Summary
JSON has no native binary type. This proposal standardizes how Branchline represents binary values in JSON and contracts, so data can round-trip safely.

## Current Behavior
- No binary type in contracts or I/O handling.
- Callers must choose ad-hoc base64 or hex encodings.

## Goals
- Define a single canonical encoding for bytes in JSON.
- Provide a contract-level marker for binary fields.
- Avoid interoperability surprises across JVM/JS.

## Proposed Rules
- Binary values encode as base64 strings in JSON output.
- Use standard base64 alphabet with `=` padding and no line breaks.
- Bytes in JSON input are accepted only when the contract type is `bytes` (or via an explicit decode function).

## Contract and Schema
- Add `bytes` as a primitive type.
- JSON schema export should map `bytes` to `type: string` with `contentEncoding: base64`.

## CLI and API Surface
- Add helper functions `base64Encode` and `base64Decode` in the standard library if not already present.
- Optional CLI flag to validate `bytes` fields on output when contracts are enabled.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` and `docs/language/std-core.md` with base64 encoding rules and any helper functions.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for base64 encoding/decoding and JSON emission.
- Playground: add a `playground/examples/` case only if bytes are exposed in the language or standard library.

## Open Questions
- Whether to allow hex as an alternate input format for `bytes`.
- Whether to accept base64url (no `+` or `/`) as an input convenience.
