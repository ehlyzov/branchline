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
    change: "Marked implemented after M9 completion and quality-gate enforcement."
---
# Contract Inference Static Analysis (V2)

## Summary
Replace syntax-only inference with a flow-sensitive abstract interpreter that tracks value shapes, provenance, and requirement conditions through control flow.

## Analysis Model
- Abstract environment maps variables to `AbstractValue`.
- `AbstractValue` includes:
  - possible `ValueShape` set
  - nullability
  - input provenance paths
  - confidence/evidence references
- Branch merge uses lattice join.
- Loops use bounded fixpoint with widening for deterministic convergence.

## Dataflow Type-Eval Extension Points
- `BinaryTypeEvalRule` registry for expression-level typing refinements.
- Initial rules:
  - arithmetic propagation (`z = a + b` with numeric operand evidence promotes numeric result)
  - text concat propagation (`x + y` with text operand yields text)
  - boolean/logical operator propagation
  - null-coalesce propagation
- Rule engine keeps confidence-aware evidence; low-confidence rules cannot create strict requiredness.
- Implemented anchors:
  - `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/ContractTypeEvalExtensions.kt`
  - `TransformContractV2Synthesizer(..., binaryTypeEvalRules = ...)`

## Static-First Output
- Emit V2 requirements and guarantees from final abstract state.
- Preserve conservative treatment of dynamic access (`input[key]` => opaque region).

## Future Runtime Example Fitting
- Static analyzer emits stable IDs and evidence hooks consumed later by runtime fitter.
- Observed examples can refine confidence and optionality but cannot override hard static contradictions.
