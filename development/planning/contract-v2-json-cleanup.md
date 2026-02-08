---
status: Implemented
depends_on: [
  'language/contract-model-v2',
  'language/contract-inference-static-analysis',
  'language/contract-diagnostics-v2',
  'planning/contract-inference-v2-milestone-plan',
]
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created Contract JSON V2 cleanup plan: canonical children-only structure, static evidence disabled, origin debug-gated, precision fixes for dynamic keys and empty-array unions."
  - date: 2026-02-08
    change: "Implemented C1-C7: development-first updates, renderer/validator/synthesizer cleanup, debug-gated origin metadata, quality-gate tests, and playground/docs refresh."
---
# Contract JSON V2 Cleanup

## Summary
This plan performs an in-place cleanup of `version: "v2"` contract JSON output to make it production-grade and predictable.

## Locked Decisions
- No version bump for this cleanup; public V2 JSON changes in place.
- Canonical object structure is `children` only.
- Static evidence emission is disabled.
- `origin` is hidden by default and shown only in debug contract output.
- Runtime sampling/fitting remains disabled, but extension points stay in place.

## Why Cleanup Is Needed
- Current output duplicates object fields in two places (`shape.schema.fields` and `children`).
- Static evidence appears authoritative but currently reflects synth rules, not sampled runtime behavior.
- Default output leaks internal debug information (`origin`) that is noisy for normal users.
- Precision regressions remain in examples with literal bracket access and empty-list accumulation flows.

## JSON Shape Direction
- Keep node tree structure under `root` and recursive `children`.
- Keep shape typing (`text|number|array|union|object`), but object field members are represented only in node `children`.
- Remove `open` from public JSON output. Closure is represented as `closed` where applicable.
- Keep `opaqueRegions` to explicitly communicate conservative boundaries.

## Debug Surface Direction
- Standard JSON output: no `origin`, no `evidence`, no spans.
- Debug output (`--contracts-debug` and Playground debug toggle): include `origin` and spans/debug metadata that are available.

## Precision Improvements
1. Dynamic path handling:
- Treat literal bracket keys/indexes as static segments.
- Mark opaque only for truly dynamic keys/indexes.
- Preserve base provenance path to avoid root-only opaque `[*]` where static prefixes exist.

2. Empty-collection typing:
- Introduce bottom-like handling for `[]` seeds in append/merge flows.
- Normalize unions to collapse redundant array branches and avoid `array<any> | array<object>` pollution.

## Milestones
1. Development docs alignment.
2. Canonical JSON renderer refactor.
3. Validator/model alignment with canonical semantics.
4. Static evidence shutdown.
5. Precision fixes for dynamic access and empty collections.
6. CLI/Playground debug wiring and tests.
7. Docs/examples refresh and release gates.

## Extension Point Commitments
- Runtime fitting extension points remain available through `ContractFitterV2` and runtime-fit metadata models.
- Dataflow type-eval extension points remain available through `BinaryTypeEvalRule` registration.
- Cleanup does not remove those APIs; it only removes static evidence from emitted contracts.
