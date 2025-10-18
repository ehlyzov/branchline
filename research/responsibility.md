# Responsibility Refactor — Status Update

> **Status:** ⚙️ In progress — language/platform split is still incomplete; parser semantics and runtime helpers retain legacy behaviours.【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L14-L35】【F:interpreter/src/commonMain/kotlin/Parser.kt†L95-L198】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L26-L39】

## Repository Layout
- The public build registers `interpreter`, `vm`, `compiler`, `test-fixtures`, `conformance-tests`, and `platform`. The old monolithic `language` module is gone, but the AST, parser, and VM code still live in the interpreter and VM modules rather than a clean language/platform split.【F:settings.gradle†L1-L7】
- Graph/orchestration constructs (`GraphBody`, `NodeDecl`, `Connection`, `GraphOutput`) remain in the core AST, so the language module still knows about platform wiring instead of exporting only the DSL surface.【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L9-L43】

## Syntax and Semantics
- Semicolons are still mandatory. Every statement parser calls `consume(TokenType.SEMICOLON, …)`, including top-level `SOURCE`, `SHARED`, `TYPE`, `LET`, expression statements, and append/set operations.【F:interpreter/src/commonMain/kotlin/Parser.kt†L95-L272】【F:interpreter/src/commonMain/kotlin/Parser.kt†L338-L359】
- `SourceDecl` continues to require a named identifier plus an optional adapter reference; the AST and semantic analyser both treat it as a concrete declaration, not as an optional type hint.【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L41-L75】【F:interpreter/src/commonMain/kotlin/v2/sema/SemanticAnalyzer.kt†L313-L319】

## Runtime Input Binding
- Helper utilities that compile and run DSL snippets still wrap user code with `SOURCE row;` and populate the execution environment with a `row` map. There is no automatic `in` alias yet, so tests and platform callers must keep using `row` explicitly.【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L35-L76】
- The multiplatform runner (`buildRunnerFromIRMP`) and both `TransformRegistry` actuals seed the environment by copying the input map into `row`, reinforcing the legacy name in cross-platform code paths.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L26-L39】【F:interpreter/src/jvmMain/kotlin/v2/ir/TransformRegistryJvm.kt†L7-L35】【F:interpreter/src/jsMain/kotlin/v2/ir/TransformRegistryJs.kt†L7-L27】

## Platform Module
- The `platform` Gradle project exists but currently exposes no source sets; it only declares dependencies on the interpreter, VM, and compiler modules. All platform-facing logic (graph orchestration, mode selection, environment setup) still lives in the language modules.【F:platform/build.gradle†L1-L7】【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L9-L43】

## Recommended Follow-Up
1. Move graph/orchestration AST nodes out of the interpreter module and into a dedicated platform layer, leaving the core AST focused on transforms, functions, shared declarations, and expressions.
2. Introduce newline-sensitive parsing so statements can be separated by `;` **or** line breaks, matching the original spec. Once implemented, add tests that cover both styles.
3. Replace `SourceDecl(name, adapter)` with a thin structure that records only the optional type hint (`AUTO` by default). Update the semantic analyser to stop special-casing concrete source names.
4. Teach the runtime helpers to publish `in` as the primary input binding (and populate `row` as a temporary alias). After the transition window, deprecate `row` from the semantic whitelist.
5. Flesh out the `platform` module with the mode-selection logic and runner scaffolding currently embedded in the test fixtures, so applications can depend on a single entrypoint instead of reimplementing it.
