---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Added parser + I/O contract gap analysis and proposal enrichment matrix."
  - date: 2026-02-05
    change: "Linked proposal documents and added conversion loss audit."
---
# I/O Contract and Parser Gap Analysis

## Scope
This document compares current Branchline parsing and I/O handling against the proposal ideas derived from the data model and I/O contract research. It focuses on JSON/XML input parsing, JSON output formatting, and transform contract shapes.

## Current Handling Summary
- JSON input parsing is implemented in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`. It requires a top-level JSON object, parses numbers into `Long` or `Double` when possible, and falls back to strings when parsing fails. There is no explicit duplicate-key detection or I-JSON safety gate.
- JSON output formatting is implemented via `formatJson` in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`. It emits either pretty or compact JSON but does not canonicalize key order or number format. `Number` values are coerced to `Double`, and map keys are always stringified.
- XML input parsing differs by platform. JVM uses DOM in `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/JvmPlatform.kt` with `isNamespaceAware=false`, `isIgnoringComments=true`, and `textContent.trim()`. It collapses mixed content and does not preserve node ordering for heterogeneous siblings. JS uses `fast-xml-parser` in `cli/src/jsMain/kotlin/io/github/ehlyzov/branchline/cli/JsPlatform.kt` with `@` attributes and `#text` nodes; numeric and boolean values are stringified. The Playground mirrors the JS settings in `playground/src/worker.ts` and also stringifies numeric and boolean XML values.
- Transform contracts are shape-level only. The parser and type system support `text`, `number`, `boolean`, `null`, and `any` (see `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt` and `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Ast.kt`). Contract validation treats all numeric kinds the same and cannot represent bytes, sets, or numeric precision (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractValidation.kt`). JSON schema export is limited to those primitives (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/schema/JsonSchemaCodec.kt`).

## Proposal Enrichment Matrix

| Proposal | Current behavior | Gaps and enrichment targets |
| --- | --- | --- |
| [JSON canonicalization mode](json-canonicalization.md) | Output preserves insertion order only; no canonical key ordering or numeric normalization (`cli/.../JsonInterop.kt`). | Define canonical key ordering and numeric formatting rules (JCS-like). Add `json-canonical` output mode and ensure deterministic sorting independent of map iteration order. |
| [Large number handling contract](large-number-contract.md) | Numbers parsed to `Long` or `Double`, else string. Output coerces any `Number` to `Double` (`cli/.../JsonInterop.kt`). | Define I-JSON safety policy. Introduce BigInt/BigDecimal preservation in parsing, and JSON string emission rules for out-of-range values. Update contract types and JSON schema mapping accordingly. |
| [Binary in JSON policy](json-binary-policy.md) | No binary type; no base64 standardization; any binary is caller-defined. | Define base64 encoding rules (alphabet, padding, line breaks). Add contract syntax or metadata to mark byte fields and standardize encode/decode helpers. |
| [Set serialization semantics](set-serialization.md) | No Set type in contracts or output; arrays are the only collection exposed. | Define Set as an internal type with deterministic ordering on JSON output. Update contract shapes to represent sets and export to JSON schema with clear semantics. |
| [XML mapping contract](xml-mapping-contract.md) | JVM parser uses DOM with trimmed `textContent`; JS uses fast-xml-parser with `#text`. Mixed content order and namespaces are not preserved (`cli/.../JvmPlatform.kt`, `cli/.../JsPlatform.kt`). | Specify a stable mapping: attributes with `@`, text with `$` or `#text`, arrays for repeated siblings, and rules for empty elements. Align JS/JVM parsing to the same mapping and document mixed-content behavior. |
| [Namespace handling in XML](xml-namespaces.md) | JVM parser has `isNamespaceAware=false`; JS parser ignores namespace declarations. | Add namespace capture (e.g., `@xmlns`) and ensure prefixes are preserved. Decide on default behavior for undeclared prefixes. |
| [Duplicate JSON key policy](json-duplicate-keys.md) | Parser relies on Kotlinx default behavior; no explicit enforcement. | Add explicit duplicate detection with `error` or `last-wins` modes, and document the default in the I/O contract. |
| [Numeric key interpretation](json-numeric-keys.md) | JSON keys remain strings; numeric keys are not interpreted. Map output stringifies all keys (`cli/.../JsonInterop.kt`). | Define schema-driven or opt-in numeric key conversion and document round-trip behavior. |
| [XML output ordering](xml-output-ordering.md) | Output ordering is not specified; heterogeneous sibling order is lost during parsing. | Document deterministic ordering rules for attributes and siblings; add explicit array-based ordering for mixed element sequences. |
| [Branchline-to-Branchline CBOR determinism](../runtime/cbor-determinism.md) | Not implemented; only JSON/XML supported in CLI. | Use deterministic encoding rules from `development/runtime/cbor-internal-representation.md` and define encoder mode selection. |
| [Conversion loss audit](../docs/conversion-loss-audit.md) | Lossy conversions are not centrally documented. | Define a formal audit list, mitigation guidance, and warning policy. |

## Parser and Contract Enrichment Targets
- Add new primitive types (bytes, bigint, bigdecimal, set) to the parser (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`) and AST (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Ast.kt`).
- Extend `TransformContract` shapes to represent new scalar and collection types (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/*`).
- Update JSON schema encoding to preserve numeric precision and binary hints (`interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/schema/JsonSchemaCodec.kt`).

## Next Steps
- Pick which proposal to advance first and convert its row into a full `/development/*` proposal with implementation milestones.
- Align CLI and Playground XML parsing to the same mapping rules to avoid cross-platform mismatches.
- Decide whether JSON canonicalization is always-on or a separate output format.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with an I/O contracts overview that links to the proposals as they ship.
- Tests: add conformance tests as each proposal moves into implementation.
- Playground: add examples only for proposals that are user-visible in the UI.
