---
status: In Progress
depends_on: [
  'language/contract-model-v2',
  'language/contract-inference-static-analysis',
  'language/contract-diagnostics-v2',
]
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Created end-to-end milestone plan for Contract Inference V2 implementation."
  - date: 2026-02-08
    change: "Completed M1 with TransformContractV2 model classes, V1<->V2 adapter, and versioned JSON rendering."
  - date: 2026-02-08
    change: "Completed M2 with flow-sensitive TransformContractV2Synthesizer and conformance coverage."
  - date: 2026-02-08
    change: "Completed M3 with path-sensitive guard refinements (null/object) and bounded merges."
  - date: 2026-02-08
    change: "Completed M4 with provenance-based nested input requirement extraction and conservative dynamic opaque regions."
  - date: 2026-02-08
    change: "Completed M5 with output guarantee extraction from final abstract values and local object mutation tracking."
  - date: 2026-02-08
    change: "Completed M6 with static stdlib summary rules and conformance coverage."
  - date: 2026-02-08
    change: "Completed M7 with ContractValidatorV2 and deterministic diagnostics model/rendering."
  - date: 2026-02-08
    change: "Completed M8 by wiring CLI/playground contract paths to V2 by default with JSON version switch support."
---
# Contract Inference V2 Milestone Plan

## Baseline
- Unknown-shape ratio across runnable playground examples: `176/198 = 0.889`.
- `junit-badge-summary` unknown-shape ratio: `1.000`.

## Targets
- Curated examples unknown-shape ratio `<= 0.35`.
- `junit-badge-summary` unknown-shape ratio `<= 0.20`.
- JVM/JS parity maintained.

## Milestones
1. [x] M0: Ratify and index V2 proposal set, baseline metrics, quality gates.
2. [x] M1: Add V2 model + JSON renderer + adapters used for migration only during development.
3. [x] M2: Add flow-sensitive abstract environment inference core.
4. [x] M3: Add path-sensitive refinement and bounded loop widening.
5. [x] M4: Add input provenance and nested requirement extraction.
6. [x] M5: Add output guarantee extraction from final abstract values.
7. [x] M6: Add static stdlib shape summaries.
8. [x] M7: Add validator V2 and diagnostics V2.
9. [x] M8: Wire CLI/playground/runtime to V2 contracts as default.
10. [ ] M9: Remove V1 public contract output and finalize docs/tests/examples.

## Guardrails
- Always update `development/INDEX.md` when milestone status changes.
- Keep development docs aligned with shipped behavior after each milestone.
- Make one git commit per milestone.

## Extension Point Commitments
- Runtime-assisted fitting hooks are designed in V2 APIs but not enabled in this static-first roadmap.
- Dataflow type-eval rule registry is included in analyzer design for arithmetic/logical propagation.
