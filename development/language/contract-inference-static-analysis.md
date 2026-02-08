---
status: Implemented
depends_on: ['language/contract-model-v2']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created static analysis plan for flow-sensitive contract inference with dataflow type-eval extension points."
  - date: 2026-02-08
    change: "Implemented V2 flow-sensitive synthesizer core with abstract environment joins and numeric binary-op type-eval rule hook."
  - date: 2026-02-08
    change: "Implemented M3 path-sensitive refinements for null/object guards with bounded branch/loop merge behavior."
  - date: 2026-02-08
    change: "Implemented M4 provenance-based input shape extraction, nested path promotion, and cast/numeric expectation propagation."
  - date: 2026-02-08
    change: "Implemented M5 output guarantee extraction from final abstract variable state with SET/APPEND/MODIFY updates."
  - date: 2026-02-08
    change: "Implemented M6 static stdlib summaries (LISTIFY/GET/APPEND/PREPEND and array/core helpers) with provenance-aware shape propagation."
  - date: 2026-02-08
    change: "Implemented M9 type-eval rule extension API (`BinaryTypeEvalRule`) and runtime-example fitter hook wiring in TransformContractBuilder."
  - date: 2026-02-08
    change: "Reopened for JSON cleanup follow-up: literal bracket key precision, empty-array union cleanup, and static evidence shutdown."
  - date: 2026-02-08
    change: "Completed cleanup precision pass: literal bracket key/index access treated as static, opaque regions restricted to truly dynamic paths, and empty-array append flow stabilized."
---
# Contract Inference Static Analysis (V2)

## Summary
Flow-sensitive static inference remains the contract source of truth. Current cleanup focuses on precision and cleaner serialization output.

## Current Analyzer Baseline
- Tracks variable shape flow through assignments, branches, loops, and object/array mutation.
- Extracts input provenance and conditional requirements.
- Produces output guarantees from final abstract values.

## Active Cleanup Targets
1. Dynamic key precision:
- Bracket access with literal key/index should be treated as static path segments.
- Opaque regions should be emitted only for truly dynamic expressions.
- Preserve static prefixes for opaque-region paths.

2. Empty-array precision:
- Represent empty-array seed with bottom-like element behavior.
- Avoid degrading append-heavy flows into redundant unions (`array<any> | array<object>`).
- Add union normalization for array/object combinations produced by joins.

3. Evidence policy:
- Static analyzer does not persist static evidence payloads into contracts.
- Runtime fitting remains the only future producer of evidence payloads.

## Dataflow Type-Eval Extension Points
- `BinaryTypeEvalRule` stays as the extension surface for expression-level typing.
- Initial behavior supports numeric/text/boolean operator propagation.
- This remains the hook for future richer dataflow constraints (`z = a + b` propagation and similar rules).

## Future Runtime Example Fitting
- Runtime fitting hooks remain available but disabled by default.
- Static facts remain authoritative unless runtime-fit policy explicitly allows refinement.
