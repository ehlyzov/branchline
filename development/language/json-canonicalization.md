---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-05
changelog:
  - date: 2026-02-05
    change: "Proposed canonical JSON output mode aligned with I/O contract research."
---
# JSON Canonicalization Mode

## Status (as of 2026-02-05)
- Stage: proposal.
- Scope: JSON output ordering, numeric formatting, and escaping rules.

## Summary
Current JSON output is pretty or compact but not canonical. This proposal defines a deterministic JSON canonicalization mode for Branchline output that matches the I/O contract research, enabling stable hashing, signatures, and reproducible outputs across platforms.

## Current Behavior
- JSON output uses `formatJson` in `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/JsonInterop.kt` with Kotlinx serialization.
- Key ordering depends on map insertion order; no canonical sort.
- Numeric formatting is delegated to Kotlinx and JVM/JS defaults.

## Goals
- Deterministic JSON output across JVM, JS, and CLI.
- Canonical key ordering and numeric formatting.
- Optional mode that does not disrupt existing default behavior.

## Non-Goals
- Changing JSON input parsing behavior.
- Introducing a custom JSON dialect.

## Proposed Canonical Rules
- Key order: lexicographic by Unicode code points (JSON Canonicalization Scheme style).
- Numbers: shortest round-trip decimal form for 64-bit floats; no leading `+`, no trailing `.0`, no trailing zeros.
- Exponents: lowercase `e`.
- Disallow NaN or Infinity in JSON output; emit error or map to `null` under explicit option.
- Strings: standard JSON escaping; control characters escaped as `\uXXXX`.
- Objects and arrays are always emitted with deterministic ordering.

## CLI and API Surface
- Add `--output-format json-canonical`.
- Keep `json` and `json-compact` unchanged.
- Playground should expose the canonical option to verify behavior.

## Contract Implications
- Output contract should mention canonical JSON mode when enabled.
- Canonicalization does not change value semantics, only representation.

## Docs, Tests, Playground
- Docs: update `docs/language/index.md` with a JSON canonicalization section and note the `json-canonical` output format in CLI docs if added.
- Tests: add conformance tests in `conformance-tests/src/commonTest` that assert canonical key ordering and numeric normalization.
- Playground: add a `playground/examples/` case once canonical output mode is exposed in the UI.

## Open Questions
- Whether canonical mode should be the default for CLI `json-compact`.
- Whether to align strictly with JCS or allow a Branchline-specific subset.
