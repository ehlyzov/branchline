---
status: Implemented
depends_on: ['language/contract-model-v2', 'language/contract-inference-static-analysis']
blocks: []
supersedes: ['language/contract-model-v2']
superseded_by: []
last_updated: 2026-02-10
changelog:
  - date: 2026-02-10
    change: "Aligned V3 output semantics: structural-first deduping of overlapping obligations, debug-only inference metadata in JSON, and optional-by-default inferred input nodes with obligation-driven requiredness."
  - date: 2026-02-09
    change: "Introduced Contract Model V3 with explicit node kinds, quantified obligations, value-domain constraints, satisfiability metadata, and witness-generation support."
---
# Contract Model V3

## Summary
Contract Model V3 is a strict, production-focused contract model for inferred and explicit transform contracts. It upgrades V2 by making collection element schemas first-class, adding quantified/value-domain obligations, and separating enforceable obligations from debug evidence.

## Goals
- Keep inferred contracts sound (no local-symbol leakage into input requirements).
- Encode strict array/set element requirements explicitly.
- Represent value-domain constraints (enum text and numeric ranges).
- Support quantified obligations (`ForAll`/`Exists`) for collection-heavy transforms.
- Attach satisfiability diagnostics and witness generation hooks.

## Data model
- `TransformContractV3(input, output, source, metadata)`.
- `RequirementSchemaV3(root, obligations, opaqueRegions, evidence)`.
- `GuaranteeSchemaV3(root, obligations, mayEmitNull, opaqueRegions, evidence)`.
- `NodeV3` with explicit `kind`:
  - object / array / set / union / scalar(any|null|boolean|number|bytes|text|never)
  - `children` for object members
  - `element` for array/set members
  - `options` for union members
- `ContractObligationV3(expr, confidence, ruleId, heuristic)`.
- `ConstraintExprV3`:
  - `PathPresent`, `PathNonNull`
  - `OneOf`, `AllOf`
  - `ForAll`, `Exists`
  - `ValueDomain`
- `ValueDomainV3`:
  - `EnumText(values)`
  - `NumberRange(min, max, integerOnly)`
  - `Regex(pattern)`

## Enforcement policy
- Strict enforcement consumes obligations only.
- Debug evidence is non-blocking and diagnostic-only.
- Confidence threshold policy:
  - default strict mode enforces sound obligations and skips heuristic-only obligations below threshold.
- Structural canonicalization policy:
  - node structure and node-level domains are canonical for structural/value checks,
  - obligations are retained only for non-structural logic (for example one-of presence requirements).

## JSON visibility policy
- Non-debug contract JSON keeps obligations but strips inferred-rule metadata fields (`confidence`, `ruleId`, `heuristic`).
- Debug JSON (`--contracts-debug`) includes those fields plus origin/spans where available.

## Input optionality semantics
- For inferred input contracts, discovered read paths are descriptive and optional-first in the node tree.
- Hard requiredness remains obligation-driven (`PathNonNull`, `OneOf`, etc.).
- This keeps strict validation aligned with program fallback behavior (`??` defaults do not force fields to be present).

## Satisfiability
- Contracts are checked for consistency before publication.
- Unsatisfiable obligation sets degrade to a safe subset and emit diagnostics in metadata.
- Deterministic witness generation is part of the satisfiability check path.

## Compatibility
- CLI/Playground default to V3 JSON.
- V2 export remains available during migration via version switch.
