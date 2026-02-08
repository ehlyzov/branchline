---
status: Implemented
depends_on: ['language/transform-contracts', 'language/transform-contracts-next']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created Contract Model V2 proposal with nested requirements, evidence metadata, and runtime-fit extension points."
  - date: 2026-02-08
    change: "Implemented core Contract V2 data model and migration adapter in interpreter contract package."
  - date: 2026-02-08
    change: "Marked implemented after M9: V2-only public contract JSON and runtime-fit/type-eval extension hooks."
  - date: 2026-02-08
    change: "Reopened for JSON cleanup: canonical children-only object structure, static evidence disabled, origin debug-only, no V2 version bump."
  - date: 2026-02-08
    change: "Contract JSON V2 cleanup completed: children-only object members in public JSON, static evidence emission disabled, origin exposed only in debug mode."
---
# Contract Model V2

## Summary
Contract Model V2 keeps the nested contract graph approach, but the public JSON rendering is being cleaned up to remove duplication and debug noise.

## Goals
- Represent nested requirements and guarantees (`input.testsuites.testsuite[*].@name`).
- Keep object member structure canonical in `children` only.
- Keep dynamic-access handling conservative to avoid false precision.
- Hide debug metadata from default output.
- Preserve extension points for runtime-assisted fitting and dataflow type-eval customization.

## Non-goals
- Enabling runtime example fitting in this phase.
- Language syntax changes.

## Active Cleanup Decisions
- Public V2 JSON is changed in place (no version bump).
- `shape.schema.fields` is not used as a second field source in public output.
- Static evidence records are not emitted.
- `origin` is emitted only in debug contract mode.
- `open` is not emitted publicly; closure is represented by `closed`.

## Runtime-Fit Extension Points (future)
- `ObservedContractEvidence`/`RuntimeFitEvidenceV2` for sample-derived hints.
- `ContractFitterV2` merge hook remains available through `TransformContractBuilder.buildV2(..., runtimeExamples = ...)`.
- Policy remains conservative for opaque regions and contradictions.

## Dataflow Type-Eval Extension Points
- `BinaryTypeEvalRule` registry remains available and enabled for static analysis extensibility.
- Rule pipeline supports arithmetic/logical/coalesce inference and future domain-specific rules.

## Migration Direction
- Public output remains V2 only.
- Cleanup is applied directly to V2 payload shape.
