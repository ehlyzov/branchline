---
status: Implemented
depends_on: ['language/contract-inference-static-analysis']
blocks: []
supersedes: ['language/contract-model-v2']
superseded_by: []
last_updated: 2026-02-27
changelog:
  - date: 2026-02-27
    change: "Converged to a single canonical contract model (latest-only): node kinds, obligations, domains, satisfiability metadata, and witness support."
---
# Contract Model (Canonical)

## Summary
Branchline uses a strict canonical contract model for inferred and explicit transform contracts.

## Goals
- Keep inferred contracts sound (no local-symbol leakage into input requirements).
- Encode array/set element requirements explicitly.
- Represent value-domain constraints (enum text and numeric ranges).
- Support quantified obligations (`ForAll`/`Exists`) for collection-heavy transforms.
- Attach satisfiability diagnostics and witness generation hooks.

## Data model
- `TransformContract(input, output, source, metadata)`
- `RequirementSchema(root, obligations, opaqueRegions, evidence)`
- `GuaranteeSchema(root, obligations, mayEmitNull, opaqueRegions, evidence)`
- `Node` with explicit `kind`
  - object / array / set / union / scalar(any|null|boolean|number|bytes|text|never)
  - `children` for object members
  - `element` for array/set members
  - `options` for union members
- `ContractObligation(expr, confidence, ruleId, heuristic)`
- `ConstraintExpr`
  - `PathPresent`, `PathNonNull`
  - `OneOf`, `AllOf`
  - `ForAll`, `Exists`
  - `DomainConstraint`
- `ValueDomain`
  - `EnumText(values)`
  - `NumberRange(min, max, integerOnly)`
  - `Regex(pattern)`

## Enforcement policy
- Strict enforcement consumes obligations.
- Debug evidence is non-blocking and diagnostic-only.
- Confidence threshold policy:
  - strict mode enforces sound obligations and can skip heuristic-only obligations below threshold.
- Structural canonicalization policy:
  - node structure and node-level domains are canonical for structural/value checks,
  - obligations are retained for non-structural logic (for example one-of presence requirements).

## JSON visibility policy
- Non-debug contract JSON keeps obligations but strips inferred-rule metadata fields (`confidence`, `ruleId`, `heuristic`).
- Debug JSON (`--contracts-debug`) includes those fields plus origin/spans where available.

## Input optionality semantics
- For inferred input contracts, discovered read paths are descriptive and optional-first in the node tree.
- Hard requiredness remains obligation-driven (`PathNonNull`, `OneOf`, etc.).

## Satisfiability
- Contracts are checked for consistency before publication.
- Unsatisfiable obligation sets degrade to a safe subset and emit diagnostics in metadata.
- Deterministic witness generation is part of the satisfiability check path.

## Compatibility
- CLI/Playground use canonical contract JSON only.
- Version switching is removed from inspect contract output.
