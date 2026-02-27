---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-27
changelog:
  - date: 2026-02-27
    change: "Updated to latest-only contract stack: single canonical contract API, no version switch in inspect JSON, and unified validator/enforcer/witness/satisfiability paths."
  - date: 2026-02-01
    change: "Migrated from research/types.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to current signature syntax and contract builder behavior."
---
# Transform contracts and signatures

## Status (as of 2026-02-27)
- Stage: Implemented.
- Signatures are parsed, validated in semantic analysis, and converted into canonical contracts.
- Missing signatures use flow-sensitive inference.
- Wildcard output signatures (`_`, `_?`, or aliases resolving to `any`) use hybrid mode: declared input type seeds inference and output remains inferred.

## Signature syntax (implemented)
```
TRANSFORM Normalize(input: { customer: { name: text } }) -> { customer: { name: text } } {
    LET name = input.customer.name
    OUTPUT { customer: { name: name } }
}
```

Notes:
- The signature is metadata; runtime input binding remains `input` (`row` alias for compatibility).
- The mode block is optional; `buffer` is currently supported.

## Contract behavior
- No signature: inferred contract.
- Signature with non-wildcard output: explicit contract conversion via `TransformContractBuilder`.
- Signature with wildcard output: hybrid mode (declared input seed + inferred output).
- Runtime strict validation uses `ContractValidator` / `ContractEnforcer`.

## Inspect JSON
- `bl inspect --contracts-json` emits one canonical JSON shape.
- `--contracts-witness` exposes generated witness input/output for strict-check sanity.
- `--contracts-debug` includes debug metadata (origin/spans/obligation metadata).

## Extension points retained
- Runtime-assisted fitting from examples remains available and is disabled by default.
- Dataflow type-eval extension (`BinaryTypeEvalRule`) remains available for arithmetic/logical shape propagation.

## Supported type references
- Primitives: `text`/`string`, `number`, `boolean`, `null`, `any`.
- Records: `{ field: type, ... }`.
- Arrays: `[type]`.
- Sets: `set<type>`.
- Unions: `A | B`.
- Enums: `enum { "a", "b" }`.
- Named types: `TypeName` (resolved via `TypeResolver`).

## References
- Parser signature parsing: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`
- Contract builder: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/TransformContractBuilder.kt`
- Inference: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/TransformContractSynthesizer.kt`
- Type-eval extension rules: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/ContractTypeEvalExtensions.kt`
- Runtime-fit extension API: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractFitExtensions.kt`
- Validation: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractValidator.kt`
