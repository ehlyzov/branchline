---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Updated to Contract Inference V2 baseline: nested requirement/guarantee model, V2 diagnostics, and V2-only public contract JSON."
  - date: 2026-02-08
    change: "Reopened for V2 JSON cleanup: children-only object structure, static evidence off, and debug-gated origin/spans."
  - date: 2026-02-01
    change: "Migrated from research/types.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to current signature syntax and contract builder behavior."
---
# Transform contracts and signatures

## Status (as of 2026-02-08)
- Stage: In progress (V2 JSON cleanup).
- Signatures are parsed, validated in semantic analysis, and converted into explicit contracts.
- Missing signatures use flow-sensitive inference via `TransformContractV2Synthesizer`.

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
- Explicit signatures are converted via `TransformContractBuilder`.
- Missing signatures fall back to V2 static inference.
- Runtime validation uses `ContractValidatorV2` / `ContractEnforcerV2`.

## V2 cleanup targets
- Public object member structure is canonicalized to `root.children` only.
- `shape.schema.fields` is not used as a duplicate field source in public JSON.
- Public JSON no longer emits `open`; closure is represented via `closed`.
- Static evidence metadata is not emitted.
- `origin` and spans are hidden by default and only shown in debug contract mode.

## Extension points retained
- Runtime-assisted fitting from examples (`ContractFitterV2`) remains available but disabled by default.
- Dataflow type-eval extension (`BinaryTypeEvalRule`) remains available for arithmetic/logical shape propagation.

## Supported type references
- Primitives: `text`/`string`, `number`, `boolean`, `null`, `any`.
- Records: `{ field: type, ... }`.
- Arrays: `[type]`.
- Unions: `A | B`.
- Enums: `enum { "a", "b" }`.
- Named types: `TypeName` (resolved via `TypeResolver`).

## References
- Parser signature parsing: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`
- Contract builder: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/TransformContractBuilder.kt`
- Inference (V2): `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/TransformContractV2Synthesizer.kt`
- Type-eval extension rules: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/ContractTypeEvalExtensions.kt`
- Runtime-fit extension API: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractFitExtensionsV2.kt`
- Validation: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractValidationV2.kt`
