---
status: Implemented
depends_on: [
  'language/contract-model-v3',
  'language/transform-contracts',
]
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-27
changelog:
  - date: 2026-02-27
    change: "Completed latest-only hard cut for contract stack: removed versioned API surfaces/switches, unified canonical runtime APIs, and added SemVer gate for contract diff."
  - date: 2026-02-27
    change: "Implemented precision upgrades: parser `typeTerm?`, explicit output closure preservation, and explicit enum domain propagation into node domains."
  - date: 2026-02-26
    change: "Added proposal for contract type precision upgrade (nullable syntax, explicit closure preservation, domains/enum propagation, and CI compatibility gates)."
---
# Contract Type Precision Upgrade (Branchline Core)

## Summary
Completed precision and hard-cut upgrade for contracts in Branchline Core.

## Delivered
1. Latest-only contract stack:
   - one canonical contract API (`TransformContract`, `RequirementSchema`, `GuaranteeSchema`, `Node`, `ContractObligation`, `ConstraintExpr`, `ValueDomain`),
   - one runtime API (`ContractValidator`, `ContractEnforcer`, `ContractWitnessGenerator`, `ContractSatisfiability`),
   - no inspect contract version switch.
2. Nullable precision:
   - parser supports `typeTerm?` across type terms,
   - `_?` retained as legacy fallback,
   - typed nullable maps to precise union (`T | null`) instead of wildcard degradation.
3. Explicit closure preservation:
   - explicit output records remain closed (`open=false`) for root and nested records by default.
4. Domain propagation:
   - explicit enum constraints propagate into `Node.domains` recursively,
   - nullable-union semantics preserved.
5. Contract diff SemVer gate:
   - supports TYPE/schema mode and latest-contract JSON mode,
   - emits `PATCH`/`MINOR`/`MAJOR`,
   - `--fail-on-major` exits with code `2` on `MAJOR`.

## Acceptance
- Canonical inspect JSON has no contract `version` field.
- Legacy contract-version inspect switch is removed.
- Contract validation/coercion/runtime/playground run through canonical latest APIs.
