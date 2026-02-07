---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed JSON binary encoding policy aligned with extended type system."
  - date: 2026-02-05
    change: "Implemented bytes contracts, base64 JSON encoding, and standard library helpers."
---
# JSON Binary Policy

## Status (as of 2026-02-05)
- Stage: implemented.
- Scope: encoding and decoding of byte arrays in JSON and contract-driven input coercion.

## Summary
JSON has no native binary type. This proposal standardizes how Branchline represents binary values in JSON and contracts, so data can round-trip safely.

## Current Behavior
- Contracts support a `bytes` primitive, and JSON schema export marks `bytes` as `type: string` with `contentEncoding: base64`.
- JSON output encodes byte arrays as base64 strings with `=` padding and no line breaks.
- JSON input decodes base64 strings only when the contract declares `bytes` (or callers use explicit decoding helpers).

## Goals
- Define a single canonical encoding for bytes in JSON.
- Provide a contract-level marker for binary fields.
- Avoid interoperability surprises across JVM/JS.

## Implemented Rules
- Binary values encode as base64 strings in JSON output.
- Use standard base64 alphabet with `=` padding and no line breaks.
- Bytes in JSON input are accepted only when the contract type is `bytes` (or via an explicit decode function).

## Contract and Schema
- Add `bytes` as a primitive type.
- JSON schema export should map `bytes` to `type: string` with `contentEncoding: base64`.

## CLI and API Surface
- Added helper functions `BASE64_ENCODE(bytes)` and `BASE64_DECODE(text)` in the standard library.
- Contract validation handles `bytes` fields in input and output.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` and `docs/language/std-strings.md` with base64 encoding rules and helper functions.
- Tests: add conformance tests in `conformance-tests/src/commonTest` for base64 encoding/decoding and JSON emission.
- Playground: add a `playground/examples/` case demonstrating base64 helpers.

## Open Questions
- None at this time.
