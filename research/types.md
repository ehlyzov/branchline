# Transform Contracts Implementation Plan

> **Status:** ⏳ Proposed — transforms still require explicit `{ stream }` clauses and do not accept signature metadata yet.【F:interpreter/src/commonMain/kotlin/Parser.kt†L110-L138】【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L75-L80】

## Goals
- Allow `TRANSFORM` definitions to omit the `{ stream }` clause while defaulting the mode to `STREAM`.
- Support optional type annotations (`AUTO -> AUTO` by default) that describe input/output schemas.
- Synthesize schema requirements and guarantees automatically when authors rely on the `AUTO` default.

## Step-by-Step Plan

1. **Document Grammar Changes**
   - Update the language reference and EBNF fixtures to show that the mode block is optional and defaults to `{ stream }`.
   - Describe the new optional `: InputType -> OutputType` signature syntax and clarify that both sides default to `AUTO` when omitted.

2. **Parser Adjustments**
   - Modify `Parser.parseTransform` to treat the mode block as optional and return `Mode.STREAM` when absent.
   - Extend the parser to recognize an optional signature following the parameter list: `: TypeRef -> TypeRef`.
   - Capture signature metadata in a new `TransformSignature` data class attached to `TransformDecl`.
   - Ensure diagnostics cover malformed mode blocks and signature syntax errors.

3. **AST & Data Model Updates**
   - Add `signature: TransformSignature?` to `TransformDecl`, storing resolved token spans for later error reporting.
   - Introduce contract model types (`TransformContract`, `SchemaRequirement`, `SchemaGuarantee`, `FieldConstraint`, `FieldShape`, `ValueShape`, etc.) in a shared module accessible to the semantic analyzer and runtime.

## Proposed Contract Types

| Type | Purpose | Key Fields | Notes |
| --- | --- | --- | --- |
| `TransformSignature` | Captures optional type annotations supplied in source code so the compiler can distinguish between explicit and inferred contracts. | `input: TypeRef?`, `output: TypeRef?`, `token: Token` | Lives on the AST; a `null` entry for either side means `AUTO` should be inferred. Token span powers precise diagnostics. |
| `TransformContract` | Represents the final, resolved schema contract every compiled transform exposes, regardless of whether it came from inference or an explicit signature. | `input: SchemaRequirement`, `output: SchemaGuarantee`, `source: ContractSource` (`EXPLICIT` or `INFERRED`) | Stored alongside runtime artifacts so both tooling and execution paths share a consistent schema view. |
| `SchemaRequirement` | Describes what the transform needs from its upstream input object/row. | `fields: LinkedHashMap<FieldName, FieldConstraint>`, `open: Boolean`, `dynamicAccess: List<DynamicAccess>` | `open` indicates whether additional, unspecified fields are tolerated. Dynamic access records computed keys for conservative downstream warnings. |
| `SchemaGuarantee` | Details what structure the transform promises to emit downstream. | `fields: LinkedHashMap<FieldName, FieldShape>`, `mayEmitNull: Boolean`, `dynamicFields: List<DynamicField>` | Mirrors `SchemaRequirement` but differentiates between required and optional outputs; `mayEmitNull` flags branches that can return `null`. |
| `FieldConstraint` | Tracks read-side expectations for a specific input field. | `required: Boolean`, `shape: ValueShape`, `sourceSpans: List<Token>` | `required` becomes `false` when accesses are guarded; source spans enable precise error messaging and tooling links. |
| `FieldShape` | Expresses what a transform writes for a specific output field. | `required: Boolean`, `shape: ValueShape`, `origin: OriginKind` (`SET`, `MODIFY`, `APPEND`, etc.) | `origin` helps IDEs and analyzers explain how a field is produced (e.g., direct assignment vs accumulation). |
| `DynamicAccess` / `DynamicField` | Represents computed keys or indices encountered during reads/writes. | `path: AccessPath`, `valueShape: ValueShape?`, `reason: DynamicReason` | Allows downstream tooling to understand partial coverage and highlight places where inference could not resolve concrete field names. |
| `ValueShape` | Defines the lattice of possible runtime values flowing through fields. | Variants such as `Primitive(kind)`, `Array(element: ValueShape, aggregation: ArrayAggregation)`, `Object(schema: SchemaGuarantee, closed: Boolean)`, `Union(options: List<ValueShape>)`, `Unknown` | Serves both requirement and guarantee types, enabling merges across control flow and preserving extensibility for future literal types or refinements. |

4. **Semantic Analysis Enhancements**
   - Resolve `TypeRef` identifiers in `TransformSignature`, producing typed schemas when available.
   - When signatures are explicit, validate that referenced schema types exist and align with the transform parameters.
   - Prepare semantic context objects to carry either the explicit contract or a placeholder indicating inference is required.

5. **Schema Inference Pass**
   - Implement a `TransformShapeSynthesizer` that walks the transform AST/IR collecting:
     - Input requirements from parameter-rooted access expressions.
     - Output guarantees from `Set`, `Modify`, `AppendTo`, and `Output` statements.
     - Control-flow merges that downgrade required fields to optional when necessary.
     - Dynamic accesses recorded separately for conservative downstream handling.
   - Produce a complete `TransformContract` for every transform, marking the source as `INFERRED` when no signature is present.

6. **Runtime & Registry Integration**
   - Extend the runtime transform descriptor to store the resolved `TransformContract` alongside compiled bodies.
   - Ensure execution paths (interpreter, VM) receive the contract for tooling and validation purposes.

7. **Validation Between Signatures and Inference**
   - When both an explicit signature and inference are available, compare them and surface semantic errors on mismatch.
   - Allow the explicit contract to bypass inference when validation succeeds.

8. **Testing Strategy**
   - **Parser Tests:** Add cases for transforms without mode blocks, with explicit signatures, and with malformed signatures.
   - **Semantic Tests:** Verify mode defaults, signature resolution, and error diagnostics for missing types.
   - **Inference Unit Tests:** Feed representative transforms into the synthesizer and assert inferred requirements/guarantees.
   - **Integration Tests:** Compile transforms through the full pipeline (interpreter and VM) and confirm contracts propagate to runtime descriptors.
   - **Documentation Tests:** Regenerate docs/fixtures to ensure examples reflect the optional mode and signature syntax.

9. **Tooling & Serialization**
   - If contracts are serialized (e.g., for IDE support), update serialization schemas and adapters to include the new contract fields.
   - Provide temporary feature flags if gradual rollout is required for downstream consumers.

10. **Follow-up Tasks**
    - Coordinate with documentation and developer tooling teams to expose inferred contracts in editor extensions.
    - Monitor performance impact of inference and cache results where necessary.
