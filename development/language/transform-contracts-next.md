---
status: Implemented
depends_on: ['language/transform-contracts']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-03
changelog:
  - date: 2026-02-01
    change: "Initial proposal for next-phase transform contract work (runtime enforcement, precedence, diagnostics)."
  - date: 2026-02-03
    change: "Implemented runtime validation modes, contract JSON rendering, and tooling toggles."
---
# Transform Contracts — Next Phase Proposal

## Summary
The current contract system provides parse-time signatures, inference, and semantic validation. This proposal strengthens contracts as an execution and tooling boundary by adding runtime enforcement, defining contract-source precedence, improving diagnostics, and expanding conformance coverage.

## Goals
- Make contracts actionable at runtime (opt-in validation mode).
- Remove ambiguity about how contracts are sourced and prioritized.
- Improve developer experience with path-rich mismatch diagnostics.
- Increase confidence in cross-platform parity via conformance tests.
- Keep compatibility with existing programs and signatures.

## Non-goals
- Full “advanced type system” (generics, polymorphism, higher-kinded types).
- Breaking changes to existing signature syntax.
- Replacing inference; inference remains the default when signatures are omitted.

## Proposed Changes

### 1) Runtime Contract Enforcement (opt-in)
- Add a validation mode that checks input/output values at runtime against the resolved contract.
- Support two levels:
  - `warn` (default if enabled): records warnings but allows execution.
  - `strict`: throws on contract mismatch.
- Hook into CLI and Playground to allow toggling enforcement.

### Mode Details (proposed)
- `off`: no runtime checks (current default behavior).
- `warn`: validate input before execution and output at `OUTPUT`, record warnings with field paths, continue execution.
- `strict`: validate input before execution and output at `OUTPUT`, throw on the first mismatch and stop execution.

### 2) Contract Source Precedence
Define a single source-of-truth ordering for contract resolution:
1. Explicit signature on `TRANSFORM`.
2. `OPTIONS { ... }`-declared input/output schema (when `transform-options` ships).
3. Inferred contract from `TransformShapeSynthesizer`.

If more than one source is present, higher precedence wins and lower precedence is still recorded for tooling (e.g., “explicit overrides inferred”).

### 3) Diagnostics Upgrades
- Include a field path for mismatches (`output.customer.address.zip`), expected/actual shapes, and location span.
- Consolidate duplicate errors so a single mismatch does not cascade into a dozen messages.
- Make diagnostics deterministic between JVM and JS (same message content and ordering).

### 4) Contract Output Ergonomics
- Render `ValueShape.Unknown` as `any` in exported JSON (keep the internal type unchanged).
- Keep fully-qualified class names out of user-facing contract output (CLI/Playground) to improve readability.

### 5) Span Metadata (optional)
- Preserve source spans as optional metadata for tooling and diagnostics.
- Allow spans to be omitted in production exports to keep contracts compact.
- Do not include spans in Playground output unless a debug mode toggle is enabled.

### 6) Conformance Coverage
Add common tests for:
- Nested unions and enums in output.
- Array element shape validation.
- Optionality inference on missing fields across branches.
- Dynamic access propagation to contracts (`input[key]`).
- Runtime enforcement behavior for both `warn` and `strict`.

## Implementation Outline
1. **Contract resolution**
   - Centralize contract resolution into a single helper (signature > options > inferred).
   - Store both chosen contract and provenance metadata on `TransformDescriptor`.

2. **Runtime validator**
   - Implement a `ContractValidator` that can validate `Value` instances against `ValueShape`.
   - Add per-transform validation hooks around input binding and output emission.
   - Ensure validator is available in interpreter and VM paths.

3. **Diagnostics**
   - Add path tracking to validation and semantic mismatch reports.
   - Ensure stable ordering by sorting paths before emission.

4. **Serialization/ergonomics**
   - Map `ValueShape.Unknown` to `any` for user-facing contract JSON.
   - Provide a switch for including/excluding span metadata in exports.

5. **Tooling**
   - CLI flags: `--contracts=off|warn|strict`.
   - CLI debug toggle: `--contracts-debug` (includes spans in exported contract JSON).
   - Playground toggle for runtime validation (defaults to off to preserve behavior).
   - Playground debug toggle: show spans only when debug mode is enabled.

6. **Tests**
   - Extend `conformance-tests/src/commonTest` with runtime validation cases.
   - Add parser/sema tests for contract precedence once `OPTIONS` is available.

## User Impact

### Gains
- Faster feedback: incorrect output shapes are caught immediately during execution.
- Clearer errors: explicit path-based messages reduce debugging time.
- Safer production runs: `strict` mode can block bad outputs early.
- Stronger guarantees for tooling integrations and schema export.

### Losses / Costs
- Runtime validation adds overhead in strict/warn modes (can be disabled).
- Some programs that currently run will start emitting warnings or failing in strict mode.
- Additional configuration surface (`--contracts`) needs documentation and defaults.

## Viable Use Cases
1. CI validation for data pipelines: run transforms with `--contracts=strict` to ensure outputs match declared schemas before deployment.
2. Safe ingestion from third-party sources: validate input payloads against declared contracts in `warn` mode to detect drift without blocking processing.
3. Playground debugging: enable contract checks to surface the exact field path where output shape diverges from expectations.
4. Schema publishing: export resolved contracts (explicit or inferred) to generate JSON Schema for downstream systems.
5. Multi-transform files: keep inference for exploratory transforms while enforcing strict contracts on production transforms in the same file.

## Migration / Compatibility
- Default behavior remains unchanged unless validation is enabled.
- Existing signatures continue to work.
- When `OPTIONS` ships, it becomes a higher-priority source than inference but lower than explicit signatures.

## Open Questions
- Should runtime validation be enabled by default in Playground?
- How strict should enum handling be when values are derived from runtime strings?
- Should contract mismatches in `OUTPUT` produce `null` fields in warn mode, or preserve raw values?

## Current State Benefits (today)
Even before runtime enforcement, users already get value from contracts:
1. Write a signature to document expected input/output shapes.
2. The parser + semantic analysis builds a contract from the signature or inference.
3. Mismatches in `OUTPUT` emit warnings (or errors in strict semantic mode).
4. Tooling can surface inferred or explicit contracts (e.g., Playground panes).
5. Conformance tests keep inference behavior aligned across platforms.

## Step-by-Step User Benefit (after this proposal)
1. Add or keep a transform signature (or rely on inference).
2. Run the transform in `warn` mode locally to surface shape drift without breaking runs.
3. Fix contract mismatches using path-rich diagnostics (e.g., `output.customer.address.zip`).
4. Promote to `strict` mode in CI or production to block invalid outputs.
5. Export the resolved contract for downstream schema consumers and documentation.
