---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/types.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to current signature syntax and contract builder behavior."
---
# Transform contracts and signatures

## Status (as of 2026-02-01)
- Stage: Implemented.
- Signatures are parsed, validated in semantic analysis, and converted into explicit contracts.
- When no signature is provided, contracts are inferred via `TransformShapeSynthesizer`.

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
- Explicit signatures are converted into a `TransformContract` via `TransformContractBuilder`.
- Missing signatures fall back to inference (`TransformShapeSynthesizer`).
- Semantic analysis validates `OUTPUT` against explicit signatures when possible.
- Contracts are stored on `TransformDescriptor` and are available to tooling (e.g., Playground).

## Supported type references
- Primitives: `text`/`string`, `number`, `boolean`, `null`, `any`.
- Records: `{ field: type, ... }`.
- Arrays: `[type]`.
- Unions: `A | B`.
- Enums: `enum { "a", "b" }`.
- Named types: `TypeName` (resolved via `TypeResolver`).

## Known limitations
- No `AUTO` keyword (omitted signature means inference).
- No runtime contract enforcement beyond semantic checks.
- Named type resolution errors surface during semantic analysis, not at runtime.

## References
- Parser signature parsing: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/Parser.kt`
- Contract builder: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/TransformContractBuilder.kt`
- Inference: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/TransformShapeSynthesizer.kt`
- Semantic validation: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/SemanticAnalyzer.kt`
- Descriptor wiring: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/TransformDescriptor.kt`
- Parser tests: `interpreter/src/jvmTest/kotlin/io/github/ehlyzov/branchline/ParserTests.kt`
