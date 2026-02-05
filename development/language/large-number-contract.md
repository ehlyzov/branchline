---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed large-number parsing and serialization rules (I-JSON aligned)."
  - date: 2026-02-05
    change: "Started milestone 3 implementation work."
---
# Large Number Handling Contract

## Status (as of 2026-02-05)
- Stage: in progress.
- Scope: JSON parsing and output of large integers and high-precision decimals.

## Summary
The current JSON pipeline uses `Long` and `Double` only, which can lose precision. This proposal defines a safe, explicit contract for BigInt and BigDecimal handling aligned with I-JSON recommendations and the extended numeric model described in the research.

## Current Behavior
- JSON parsing uses `longOrNull` then `doubleOrNull` in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt`.
- Any `Number` is serialized as `Double` in `toJsonElement`, which can truncate large integers.
- No explicit I-JSON safety checks or warnings.

## Goals
- Preserve large integer precision on input.
- Avoid silent loss of precision on output.
- Make JSON interchange predictable across platforms.

## Non-Goals
- Changing numeric semantics inside the VM arithmetic pipeline.

## Proposed Rules
- JSON input:
  - Integers within safe range become 64-bit integers.
  - Integers outside safe range become `BigInt`.
  - Decimals that require precision beyond binary64 become `BigDecimal`.
  - Optional strict mode rejects numbers outside safe range rather than promoting.
- JSON output:
  - Safe numbers emit as JSON numbers.
  - `BigInt` and `BigDecimal` emit as JSON strings by default.
  - Optional extended mode emits large numbers as numeric JSON for systems that can parse them.

## Contract and Schema
- Extend contract types to represent `bigint` and `decimal`.
- Update JSON schema export to encode these as strings with metadata (for example `format: bigint` or `format: decimal`).

## CLI and API Surface
- Add `--json-numbers strict|safe|extended`.
- Add warnings when `BigInt` or `BigDecimal` is emitted as a string.

## Docs, Tests, Playground
- Docs: update `docs/language/numeric.md` and `docs/language/index.md` with BigInt/BigDecimal JSON rules.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that cover large integers, decimals, and JSON string emission rules.
- Playground: add a `playground/examples/` case once large-number behavior is visible in the runtime and UI.

## Open Questions
- Whether the default mode should be `safe` or `strict`.
- Whether numeric strings should be auto-coerced when input contracts require a numeric type.
