---
status: In Progress
depends_on: ['language/transform-contracts', 'language/transform-contracts-next']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created Contract Model V2 proposal with nested requirements, evidence metadata, and runtime-fit extension points."
---
# Contract Model V2

## Summary
Contract Model V2 replaces the flat field map model with a nested graph model that can represent deep object/array requirements and guarantees inferred from program flow.

## Goals
- Represent nested requirements and guarantees (`input.testsuites.testsuite[*].@name`).
- Capture conditional requirements as explicit expressions.
- Keep dynamic-access handling conservative to avoid false precision.
- Attach inference evidence metadata (spans, rules, confidence).
- Define extension points for future runtime-assisted fitting from observed examples.

## Non-goals
- Runtime example fitting in this phase.
- Any language syntax changes.

## Core Model Additions
- `TransformContractV2(input, output, source, metadata)`
- `RequirementNode` and `GuaranteeNode` tree with per-node shape.
- `RequirementExpr` for conditional requirements:
  - `AllOf`
  - `AnyOf`
  - `PathPresent`
  - `PathNonNull`
- `OpaqueRegion` marker for dynamic access zones.
- `InferenceEvidence`:
  - `sourceSpans`
  - `ruleId`
  - `confidence` (0..1)
  - `notes`

## Runtime-Fit Extension Points (future)
- `ObservedContractEvidence` hook to attach sample-derived hints without mutating static facts.
- `ContractFitter` interface:
  - accepts base static contract + observed examples
  - returns additive evidence and confidence deltas
  - never upgrades opaque dynamic regions to strict guarantees without explicit policy.
- Versioned merge policy so static and observed evidence remain attributable.

## Migration Direction
- V2 becomes the only public contract format after implementation is complete.
- V1 compatibility code may exist temporarily during development but is removed before roadmap completion.
