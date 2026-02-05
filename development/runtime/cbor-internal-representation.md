---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Drafted internal CBOR representation proposal based on data model and I/O contract research."
---
# Internal CBOR Data Representation

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: internal Branchline-to-Branchline representation and in-process serialization.
- Source: derived from the data model, I/O contract, and canonicalization rules.

## Summary
Branchline needs a lossless internal wire format that preserves extended types (big numbers, bytes, set semantics, mixed key types) and is deterministic across platforms. CBOR fits this role and is already referenced in the data model and I/O contract work. This proposal fixes the precise CBOR mapping, key rules, and deterministic encoding constraints so that Branchline-to-Branchline interchange can be exact and reproducible.

## Goals
- Preserve full extended type fidelity across Branchline boundaries without JSON or XML conversion.
- Encode binary data as bytes, not base64.
- Preserve numeric precision and subtype distinctions (I, F, BI, BD).
- Allow string and integer map keys; forbid exotic key types.
- Produce a deterministic CBOR byte sequence when required.

## Non-Goals
- External JSON or XML serialization rules.
- Alternative binary formats (MessagePack or custom formats).
- Schema negotiation or type inference beyond what the runtime already knows.

## CBOR Mapping

| Branchline type | CBOR encoding | Notes |
| --- | --- | --- |
| Null | Simple value `null` | Direct mapping. |
| Boolean | Simple values `true` / `false` | Direct mapping. |
| String | Major type 3 (text) | UTF-8. |
| Bytes | Major type 2 (byte string) | No base64. |
| Array | Major type 4 (array) | Preserve order. |
| Object (map) | Major type 5 (map) | Keys limited to text or int. |
| Number (I/F) | Major type 0/1 for integers, float for F | Prefer shortest encoding per deterministic CBOR. |
| BigInt (BI) | Tag 2 / Tag 3 with byte string | Tag 2 for positive, Tag 3 for negative. |
| BigDecimal (BD) | Tag 4 (decimal fraction) | Encode as `[exponent, mantissa]`. |
| Set | Tagged array | Use a Branchline tag for unordered unique collection. |

## Map Key Policy
- Only text or integer keys are allowed in CBOR maps.
- If a key is not text or integer, the runtime must reject it or require explicit conversion.
- Deterministic map ordering follows CBOR canonical ordering, so numeric keys sort by numeric value and text keys by bytewise order. This may differ from JSON lexicographic ordering and is expected.

## Numeric Representation
- Use CBOR int major types for 64-bit integers when within range.
- Use CBOR floats for 64-bit float values.
- Use Tag 2 or Tag 3 for BigInt values beyond 64-bit.
- Use Tag 4 for BigDecimal with exponent and mantissa to preserve exactness.
- Avoid NaN or Infinity in internal representation, or map them to an agreed sentinel only if runtime numeric rules require it.

## Set Representation
- Represent Set as a CBOR array tagged with a Branchline tag (proposal: `267`, provisional).
- Elements must be unique by Branchline equality rules.
- For deterministic encoding, order set elements by their canonical CBOR encoding bytes before serialization.
- For decoding, if the tag is present, reconstruct Set. If it is absent, treat the value as an Array unless a schema or runtime contract indicates Set.

## Determinism and Canonicalization
- Use RFC 8949 deterministic CBOR (Section 4.2) when determinism is required.
- Emit definite-length arrays and maps in deterministic mode.
- Sort map keys using canonical ordering.
- For numbers, use the shortest encoding that preserves the value.
- When determinism is not required, the runtime may use a faster encoding, but it must still preserve type fidelity.

## Interchange Contract
- Branchline-to-Branchline exchange uses a single CBOR data item for the pipeline output.
- Use `application/cbor` content type.
- This format is lossless for all core and extended Branchline types and avoids JSON numeric or binary loss.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with a Branchline-internal CBOR interchange summary and type fidelity guarantees.
- Tests: add runtime tests for CBOR encode/decode mapping once implemented, plus conformance tests for cross-platform parity.
- Playground: not needed for internal CBOR interchange.

## Open Questions
- Finalize and register the Branchline tag for Set (default proposal: `267`).
- Decide whether to always enforce deterministic encoding or allow a fast path with a clear flag.
- Confirm behavior for NaN or Infinity if they can appear in runtime numeric operations.
