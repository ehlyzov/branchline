---
status: Implemented
depends_on: ['language/transform-contracts-next']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created proposal for coalesce-aware (`??`) input contract inference."
  - date: 2026-02-08
    change: "Implemented requiredAnyOf contract groups, validator enforcement, and docs/test updates."
---
# Coalesce-Aware Input Contract Inference

## Problem Statement
Inferred contracts treated `a ?? b` as two sequential required reads and marked both inputs as required.  
For input fallback patterns like `input.testsuites ?? input.testsuite`, this produced false-positive required-field contracts.

## Scope
- Add coalesce-aware input inference for static input paths.
- Add contract model support for conditional one-of requirements.
- Enforce conditional one-of requirements in warn/strict validation modes.
- Render conditional requirements in contract JSON.

## Non-goals
- No dynamic-key coalesce inference in this iteration.
- No contract transport renames.
- No output contract behavior changes.

## Contract Model Changes
- `SchemaRequirement` now includes:
  - `requiredAnyOf: List<RequiredAnyOfGroup>`
- New type:
  - `RequiredAnyOfGroup(alternatives: List<AccessPath>)`

## Inference Behavior
- For `COALESCE` expressions with static input path alternatives:
  - Inference records a one-of fallback group in `requiredAnyOf`.
  - Top-level fallback fields in the group are relaxed from unconditional required flags.
- Non-coalesce binary operators keep previous sequential merge behavior.

## Validation Behavior
- Input validation now checks each `requiredAnyOf` group:
  - At least one alternative path must resolve to a non-null value.
  - If none resolve, emits `MISSING_ANY_OF_GROUP`.
- Applies in `warn`/`strict`; `off` remains unchanged.

## Contract JSON Rendering
- Input contract JSON now includes:
  - `requiredAnyOf`
- Shape:
  - Array of groups.
  - Each group is an array of alternative access-path arrays.

## Tests
- Added conformance synthesis coverage for coalesce fallback groups.
- Added validation tests for:
  - warn-mode group violations
  - strict-mode failure on missing group
  - success when one alternative is present
- Added playground facade test ensuring `requiredAnyOf` is present in rendered input contract JSON.

## Validation Commands
- `./gradlew --no-daemon :interpreter:jvmTest :conformance-tests:jvmTest`
- `./gradlew --no-daemon :interpreter:jsTest :conformance-tests:jsTest`
