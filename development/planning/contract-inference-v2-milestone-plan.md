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
  - date: 2026-02-08
    change: "Completed M9: removed public V1 contract JSON output, added runtime-fit/type-eval extension hooks, quality-gate tests, and new playground example."
  - date: 2026-02-08
    change: "Started post-M9 Contract JSON V2 cleanup milestones (no version bump, evidence-off, debug-gated origin, precision fixes)."
---
# Contract Inference V2 Milestone Plan

## Baseline
- Unknown-shape ratio across runnable playground examples: `176/198 = 0.889`.
- `junit-badge-summary` unknown-shape ratio: `1.000`.

## Targets
- Curated examples unknown-shape ratio `<= 0.35`.
- `junit-badge-summary` unknown-shape ratio `<= 0.20`.
- JVM/JS parity maintained.

## Completed Milestones
1. [x] M0: Ratify and index V2 proposal set, baseline metrics, quality gates.
2. [x] M1: Add V2 model + JSON renderer + adapters used for migration only during development.
3. [x] M2: Add flow-sensitive abstract environment inference core.
4. [x] M3: Add path-sensitive refinement and bounded loop widening.
5. [x] M4: Add input provenance and nested requirement extraction.
6. [x] M5: Add output guarantee extraction from final abstract values.
7. [x] M6: Add static stdlib shape summaries.
8. [x] M7: Add validator V2 and diagnostics V2.
9. [x] M8: Wire CLI/playground/runtime to V2 contracts as default.
10. [x] M9: Remove V1 public contract output and finalize docs/tests/examples.

## Cleanup Milestones (active)
1. [ ] C1: Development docs alignment for V2 cleanup scope and gates.
2. [ ] C2: Contract JSON renderer canonicalization (children-only object members, no public `open`).
3. [ ] C3: Validator/model alignment with no static-evidence dependency.
4. [ ] C4: Static evidence emission shutdown.
5. [ ] C5: Precision fixes for literal bracket access and empty-array union normalization.
6. [ ] C6: CLI/Playground debug metadata gating and integration tests.
7. [ ] C7: Docs/playground examples refresh and release gate validation.

## Guardrails
- Always update `development/INDEX.md` when milestone status changes.
- Keep development docs aligned with shipped behavior after each milestone.
- Make one git commit per milestone.

## Extension Point Commitments
- Runtime-assisted fitting hooks remain in V2 APIs but are disabled by default.
- Dataflow type-eval rule registry remains in analyzer design for arithmetic/logical propagation and future custom rules.
