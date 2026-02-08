---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-08
changelog:
  - date: 2026-02-08
    change: "Updated to Contract Inference V2 baseline: nested requirement/guarantee model, V2 diagnostics, and V2-only public contract JSON."
  - date: 2026-02-01
    change: "Migrated from research/types.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to current signature syntax and contract builder behavior."
---
# Transform contracts and signatures

## Status (as of 2026-02-08)
- Stage: Implemented.
- Signatures are parsed, validated in semantic analysis, and converted into explicit contracts.
- When no signature is provided, contracts are inferred via `TransformContractV2Synthesizer`.
- Public contract JSON output is V2 only (nested graph model with requirement expressions and evidence metadata).

## Signature syntax (implemented)
```
TRANSFORM Normalize(input: { customer: { name: text } }) -> { customer: { name: text } } {
    LET name = input.customer.name
    OUTPUT { customer: { name: name } }
}
```

Notes:
- The signature is **metadata**; it does not introduce new runtime variables. The input binding remains `input` (with `row` as a compatibility alias).
- The mode block is optional; buffer is the only supported mode.

## Contract behavior
- Explicit signatures are converted via `TransformContractBuilder` and lifted into V2 contracts for runtime/tooling.
- Missing signatures fall back to flow-sensitive inference (`TransformContractV2Synthesizer`).
- Semantic analysis validates `OUTPUT` against explicit signatures when possible.
- Runtime validation and diagnostics use V2 contracts (`ContractValidatorV2` / `ContractEnforcerV2`).
- Contracts are available to CLI and Playground as V2 JSON.

## V2 highlights
- Nested input requirements and output guarantees are represented as node trees (`root.children`).
- Conditional requirements are explicit expressions (`anyOf`, `allOf`, `pathPresent`, `pathNonNull`).
- Dynamic key/index access is tracked as opaque regions to avoid false precision.
- Evidence metadata is attached to inferred nodes for deterministic diagnostics.
- Extension points exist for:
  - runtime-assisted fitting from observed examples (`ContractFitterV2`)
  - dataflow type evaluation rule injection (`BinaryTypeEvalRule`)

## Supported type references
- Primitives: `text`/`string`, `number`, `boolean`, `null`, `any`.
- Records: `{ field: type, ... }`.
- Arrays: `[type]`.
- Unions: `A | B`.
- Enums: `enum { "a", "b" }`.
- Named types: `TypeName` (resolved via `TypeResolver`).

## Known limitations
- No `AUTO` keyword (omitted signature means inference).
- Runtime-assisted fitting hooks are available but profile/example-driven fitting is not enabled by default.
- Named type resolution errors surface during semantic analysis, not at runtime.

## References
- Parser signature parsing: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`
- Contract builder: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/TransformContractBuilder.kt`
- Inference (V2): `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/TransformContractV2Synthesizer.kt`
- Type-eval extension rules: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/ContractTypeEvalExtensions.kt`
- Runtime-fit extension API: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/ContractFitExtensionsV2.kt`
- Semantic validation: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/SemanticAnalyzer.kt`
- Descriptor wiring: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/TransformDescriptor.kt`
- Parser tests: `interpreter/src/jvmTest/kotlin/io/github/ehlyzov/branchline/ParserTests.kt`
