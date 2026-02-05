---
status: Proposed
depends_on: [
  'language/json-duplicate-keys',
  'language/json-canonicalization',
  'language/large-number-contract',
  'language/json-numeric-keys',
  'language/json-binary-policy',
  'language/set-serialization',
  'language/xml-mapping-contract',
  'language/xml-namespaces',
  'language/xml-output-ordering',
  'docs/conversion-loss-audit',
  'runtime/cbor-internal-representation',
  'runtime/cbor-determinism',
]
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Defined milestone plan and agent instructions for I/O contract proposals."
  - date: 2026-02-05
    change: "Completed milestone 1 with duplicate-key rejection and conformance coverage."
  - date: 2026-02-05
    change: "Completed milestone 2 with canonical JSON output and conformance coverage."
  - date: 2026-02-05
    change: "Started milestone 3 for large-number handling."
  - date: 2026-02-05
    change: "Completed milestone 3 with large-number parsing/output and json-numbers modes."
---
# I/O Contracts Milestone Plan

## Purpose
Provide a staged implementation plan for the new I/O contract proposals, with explicit agent instructions and dependencies. This plan is intended for Codex agents to execute step-by-step.

## Global Agent Instructions
- Always run `git status -sb` before editing. If a file has unexpected changes, stop and ask.
- Do not edit `.env` files.
- Update `development/INDEX.md` if proposal status changes.
- When a proposal introduces a public-facing behavior change, update `docs/language/index.md` and related docs.
- Add conformance tests in `conformance-tests/src/commonTest` for all behavior changes.
- Add playground examples only when the behavior is user-visible and demonstrable.
- When syntax or keyword sets change, update Playground language schema in `playground/src/branchline-language.ts`.
- Keep JVM and JS behavior aligned for any parsing/serialization changes.

## Milestones

### Milestone 1: JSON Input Determinism (Duplicate Keys)
**Proposal:** `development/language/json-duplicate-keys.md`
**Status:** Completed (2026-02-05)

**Scope**
- Reject duplicate JSON object keys during input parsing.

**Agent Tasks**
- Implement duplicate key detection in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`.
- Ensure Playground uses the same parsing behavior.
- Add conformance tests covering duplicate-key rejection.

**Docs/Tests/Playground**
- Update `docs/language/index.md` and CLI docs.
- Add `conformance-tests/src/commonTest` cases for duplicates.
- Playground example not required.

**Exit Criteria**
- Duplicate keys are rejected deterministically.
- Conformance tests pass on JVM and JS.

---

### Milestone 2: JSON Canonical Output
**Proposal:** `development/language/json-canonicalization.md`
**Status:** Completed (2026-02-05)

**Scope**
- Deterministic JSON output formatting (key ordering, numeric normalization).

**Agent Tasks**
- Add `json-canonical` output mode for CLI and internal output rendering.
- Implement canonical key ordering and numeric formatting.
- Ensure the canonical output is byte-stable across JVM and JS.

**Docs/Tests/Playground**
- Update `docs/language/index.md` and CLI output format docs.
- Add conformance tests for canonical ordering and number formatting.
- Playground example if canonical output can be selected.

**Exit Criteria**
- Canonical output is identical across platforms for the same data.

---

### Milestone 3: Large Numbers (BigInt/BigDecimal)
**Proposal:** `development/language/large-number-contract.md`
**Status:** Completed (2026-02-05)

**Scope**
- Safe parsing of large integers/decimals and JSON emission rules.

**Agent Tasks**
- Extend JSON parsing to preserve large integers and decimals as `BLBigInt`/`BLBigDec`.
- Update JSON output to emit numeric strings when outside safe range.
- Add CLI flags for numeric mode (`strict|safe|extended`).

**Docs/Tests/Playground**
- Update `docs/language/numeric.md` and `docs/language/index.md`.
- Add conformance tests for large numbers and JSON string emission.
- Playground example if behavior is visible in runtime output.

**Exit Criteria**
- No silent precision loss on JSON input/output.

---

### Milestone 4: Numeric Key Interpretation
**Proposal:** `development/language/json-numeric-keys.md`

**Scope**
- Schema-driven or opt-in numeric key conversion.

**Agent Tasks**
- Add schema or CLI-driven numeric key conversion.
- Ensure JSON output round-trips numeric keys as strings when required.
- Update contract system to express numeric-keyed maps (if needed).

**Docs/Tests/Playground**
- Update `docs/language/index.md`.
- Add conformance tests for numeric key conversion.
- Playground example only if exposed in UI.

**Exit Criteria**
- Numeric key conversion is deterministic and opt-in.

---

### Milestone 5: JSON Binary Policy
**Proposal:** `development/language/json-binary-policy.md`

**Scope**
- Define and implement base64 encoding for bytes.

**Agent Tasks**
- Add `bytes` type to contracts and JSON schema export.
- Implement base64 encoding/decoding helpers.
- Ensure JSON output uses base64 with padding and no line breaks.

**Docs/Tests/Playground**
- Update `docs/language/index.md` and any standard library docs.
- Add conformance tests for base64 encoding and decoding.
- Playground example only if bytes are user-visible.

**Exit Criteria**
- Binary data round-trips safely via JSON.

---

### Milestone 6: Set Serialization
**Proposal:** `development/language/set-serialization.md`

**Scope**
- Add set semantics and deterministic serialization.

**Agent Tasks**
- Add `set<T>` to contract system and JSON schema export.
- Implement deterministic ordering for set output.

**Docs/Tests/Playground**
- Update `docs/language/index.md` and `docs/language/expressions.md`.
- Add conformance tests for uniqueness and ordering.
- Playground example only if sets are user-visible.

**Exit Criteria**
- Sets serialize deterministically in JSON and XML.

---

### Milestone 7: XML Mapping Unification
**Proposal:** `development/language/xml-mapping-contract.md`

**Scope**
- Align JVM/JS XML parsing to a single mapping.

**Agent Tasks**
- Update JVM parsing to match JS mapping rules (attributes, text keys, arrays for repeated siblings).
- Add parser normalization for mixed content and reserved keys.
- Ensure Playground matches CLI behavior.

**Docs/Tests/Playground**
- Update `docs/language/index.md` with XML mapping rules.
- Add conformance tests comparing JVM and JS outputs.
- Playground example showing attributes and mixed content.

**Exit Criteria**
- Same XML input yields the same structure on JVM and JS.

---

### Milestone 8: XML Namespace Handling
**Proposal:** `development/language/xml-namespaces.md`

**Scope**
- Capture `@xmlns` data and preserve prefixes.

**Agent Tasks**
- Enable namespace-aware parsing on JVM.
- Normalize namespace declarations into `@xmlns`.
- Validate prefixed names on output.

**Docs/Tests/Playground**
- Update `docs/language/index.md`.
- Add conformance tests for namespace round-trip.
- Playground example only after namespace support ships.

**Exit Criteria**
- Namespaces round-trip without loss.

---

### Milestone 9: XML Output Ordering
**Proposal:** `development/language/xml-output-ordering.md`

**Scope**
- Deterministic attribute and sibling ordering rules.

**Agent Tasks**
- Implement deterministic ordering in XML output adapter.
- Add explicit ordering mechanism (`@order` or equivalent) if approved.

**Docs/Tests/Playground**
- Update `docs/language/index.md`.
- Add conformance tests for ordering behavior.
- Playground example if ordering is user-visible.

**Exit Criteria**
- XML output ordering is deterministic and documented.

---

### Milestone 10: Conversion Loss Audit
**Proposal:** `development/docs/conversion-loss-audit.md`

**Scope**
- Document known lossy conversions and warnings.

**Agent Tasks**
- Write the user-facing audit table in docs.
- Add warning hooks in CLI/runtime for loss points.
- Add conformance tests that assert warnings.

**Docs/Tests/Playground**
- Update `docs/language/index.md` or add a dedicated doc.
- Add tests for each loss point.
- Playground example only if warnings are visible.

**Exit Criteria**
- Users can identify and reason about lossy conversions.

---

### Milestone 11: Internal CBOR Representation
**Proposal:** `development/runtime/cbor-internal-representation.md`

**Scope**
- Implement CBOR encoding/decoding for internal interchange.

**Agent Tasks**
- Add CBOR encoder/decoder with support for extended types.
- Define map key restrictions (text and int only).
- Add tags for BigInt, BigDecimal, and Set.

**Docs/Tests/Playground**
- Update `docs/language/index.md` with CBOR summary.
- Add runtime tests for encode/decode and cross-platform parity.
- Playground not required.

**Exit Criteria**
- Internal CBOR round-trips preserve all type fidelity.

---

### Milestone 12: CBOR Determinism Rules
**Proposal:** `development/runtime/cbor-determinism.md`

**Scope**
- Deterministic CBOR encoding mode.

**Agent Tasks**
- Implement deterministic encoding rules (RFC 8949 Section 4.2).
- Add a runtime flag to enable deterministic CBOR output.

**Docs/Tests/Playground**
- Update `docs/language/index.md`.
- Add bytewise comparison tests for deterministic output.
- Playground not required.

**Exit Criteria**
- Deterministic CBOR output is stable and test-verified.

## Cross-Milestone Notes
- Any new primitives must update:
  - Parser (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`)
  - AST (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Ast.kt`)
  - Contracts (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/*`)
  - JSON schema (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/schema/JsonSchemaCodec.kt`)
- Keep Playground and CLI behavior aligned for JSON/XML parsing.
