---
status: Proposed
depends_on: [
  'language/transform-contracts',
  'language/contract-model-v2',
  'language/contract-inference-static-analysis',
]
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created Input-Type-Seeded Output Inference plan: hybrid wildcard-output inference using declared input type seeds."
---
# Input-Type-Seeded Output Inference

## Summary
Use declared transform input types as static seeds for V2 flow inference so output contracts can be inferred more precisely when output type is wildcard (`_` or `_?`), without changing language syntax.

## Problem
Current `buildV2` behavior is explicit-or-inferred:
- If a signature exists, explicit contract path is used.
- If no signature exists, inferred contract path is used.

This prevents a useful middle mode where declared input typing can strengthen flow analysis while output remains inferred.

## Locked Decisions
1. Keep syntax unchanged.
2. Hybrid behavior is enabled when output type is wildcard (`_` / `_?` or aliases resolving to `any` / `any?`).
3. Input type strength is hard for static paths, conservative fallback for dynamic paths.
4. Dynamic access continues to produce opaque regions and blocks over-precise claims.
5. Runtime sampling/fitting remains disabled for this work.

## Target Behavior
1. No signature:
- Keep current inferred V2 behavior.

2. Signature with non-wildcard output:
- Keep current explicit contract behavior.

3. Signature with wildcard output:
- Build input constraints from declared input type.
- Run flow-sensitive inference for output using type-seeded input reads.
- Emit inferred output guarantees with better precision than pure `any`-seed analysis.

## Public/API Implications
1. Contract builder routing:
- `TransformContractBuilder.buildV2(...)` gains hybrid branch logic for wildcard output signatures.

2. Synthesizer entrypoint:
- `TransformContractV2Synthesizer` accepts optional input-type seed context.

3. Metadata:
- Keep `source = inferred` for hybrid contracts.
- Add a metadata marker indicating input-type seed participation (for tooling/debug transparency).

4. CLI/Playground:
- No new flags required.
- Existing inspect/playground contract surfaces should automatically reflect improved output inference in hybrid cases.

## Implementation Plan
### M1: Development-First Documentation
1. Update `development/language/transform-contracts.md` with hybrid wildcard-output mode semantics and example.
2. Update `development/language/contract-inference-static-analysis.md` with seeded path typing and dynamic fallback rules.
3. Update `development/language/contract-model-v2.md` with metadata marker notes.
4. Update `development/docs/contract-inference-quality-gates.md` with new hybrid gate scenarios.
5. Update `development/INDEX.md`.

Exit criteria:
- Docs specify routing, merge rules, and acceptance criteria with no open semantic decisions.

### M2: Builder Routing
1. Add wildcard output detection helper in `TransformContractBuilder`.
2. Route `buildV2` to:
- explicit branch (non-wildcard output),
- hybrid branch (wildcard output + typed input),
- inferred branch (no signature).

Exit criteria:
- Deterministic branch selection with tests.

### M3: Seeded Synthesizer
1. Extend `TransformContractV2Synthesizer.synthesize(...)` to accept optional input seed.
2. Implement seeded static path descent for `input.*` reads.
3. Keep dynamic segments conservative (`opaqueRegions` + `any` fallback at affected subtree).

Exit criteria:
- Static typed paths influence inferred shapes; dynamic paths remain conservative.

### M4: Input Schema Composition
1. Use declared input schema as baseline in hybrid mode.
2. Merge inferred requirement expressions (e.g., `anyOf` from coalesce) and opaque regions.
3. Merge precedence:
- Declared non-`any` static type wins.
- `any` may be narrowed by inferred concrete type.

Exit criteria:
- Hybrid input contract is both schema-aware and flow-aware without false precision.

### M5: Validation, Tests, and Surface Parity
1. Add builder routing tests.
2. Add conformance tests for typed-input/wildcard-output precision.
3. Verify JVM/JS parity.
4. Verify CLI and Playground output consistency.

Exit criteria:
- Interpreter/conformance/CLI tests green and no parity regressions.

## Test Cases and Scenarios
1. Typed input + wildcard output:
```branchline
TYPE Payload = { name: text }
TYPE Input = { payload?: Payload }
TRANSFORM T(input: Input) -> _ {
    LET payload = input.payload;
    IF payload != NULL THEN {
        OUTPUT { name: payload.name }
    } ELSE {
        OUTPUT { name: "none" }
    }
}
```
Expected: output `name` inferred as `text`.

2. `GET` static key with typed input:
- `GET(input.metrics, "count", 0)` infers numeric output when `metrics.count` is typed number.

3. Dynamic access:
- `input[key]` keeps opaque region and avoids deep false claims.

4. Routing invariants:
- non-wildcard output remains explicit,
- wildcard output triggers hybrid,
- no signature remains inferred.

## Risks
1. `any`-top lattice can erase precision if merges are not path-aware.
2. Over-constraining dynamic paths could cause false positives.
3. Metadata/source labeling must stay stable for existing tooling.

## Acceptance Criteria
1. Hybrid mode improves output precision in typed-input/wildcard-output transforms.
2. Dynamic path handling remains conservative.
3. JVM/JS produce equivalent contracts.
4. Existing explicit and pure-inferred behavior remains unchanged.

## Assumptions
1. Wildcard output means resolved `any`/`any?`.
2. Input type aliases are fully resolved via `TypeResolver`.
3. Runtime evidence remains off until separately enabled.

## New Agent Prompt
```text
Implement “Input-Type-Seeded Output Inference” in /Users/eugene/repo/research/branchline-public.

Rules:
- Follow AGENTS.md (development-first workflow, update development/INDEX.md).
- Do not change syntax.
- Keep explicit branch for non-wildcard output signatures.
- Add hybrid branch when output is wildcard (_ / _? / aliases resolving to any).
- In hybrid branch, use declared input type as static inference seed.
- Keep dynamic access conservative (opaque region + no deep false precision).
- Keep runtime sampling disabled.

Execution order:
1) Docs first:
   - development/language/transform-contracts.md
   - development/language/contract-inference-static-analysis.md
   - development/language/contract-model-v2.md
   - development/docs/contract-inference-quality-gates.md
   - development/INDEX.md
2) Builder routing in TransformContractBuilder.buildV2.
3) Extend TransformContractV2Synthesizer with optional input seed.
4) Merge input schema + inferred requirements/opaque regions for hybrid mode.
5) Add tests for routing, typed static paths, dynamic fallback, and parity.

Validation:
- ./gradlew :interpreter:jvmTest :interpreter:jsTest
- ./gradlew :conformance-tests:jvmTest :conformance-tests:jsTest
- ./gradlew :cli:jvmTest

Deliver:
- Clean commits by milestone.
- Final summary with file references and behavior deltas.
```
