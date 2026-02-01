---
status: In Progress
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/responsibility.md and added YAML front matter."
  - date: 2026-02-01
    change: "Aligned to input binding wording."
---
# Responsibility Refactor — Status Update


## Status (as of 2026-01-31)
- Stage: In progress.
- Language/platform split remains incomplete; parser semantics and runtime helpers still mix responsibilities.
- Next: finish separation and update module responsibility docs.


## Repository Layout
- The public build registers `interpreter`, `vm`, `cli`, `test-fixtures`, and `conformance-tests` (settings.gradle still lists a `platform` include, but there is no compiler module in the repo and no `platform/` sources checked in). The old monolithic `language` module is gone, but the AST, parser, and VM code still live in the interpreter and VM modules rather than a clean language/platform split.【F:settings.gradle†L1-L7】
- Graph/orchestration constructs (`GraphBody`, `NodeDecl`, `Connection`, `GraphOutput`) remain in the core AST, so the language module still knows about platform wiring instead of exporting only the DSL surface.【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L9-L43】

## Syntax and Semantics
- Semicolons are optional. `optionalSemicolon()` accepts explicit `;` or line/brace boundaries across top-level declarations and block statements, with tests covering newline-terminated programs.【F:interpreter/src/commonMain/kotlin/Parser.kt†L99-L365】【F:interpreter/src/commonMain/kotlin/Parser.kt†L887-L910】【F:interpreter/src/jvmTest/kotlin/v2/ParserTests.kt†L71-L118】
- `SourceDecl` is still part of the AST but parsing a `SOURCE` line is a no-op; transforms default to buffer mode without a `{ mode }` header ( `{ stream }` is rejected).【F:interpreter/src/commonMain/kotlin/Parser.kt†L95-L152】【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L41-L75】

## Runtime Input Binding
- Helper utilities and runners now seed the execution environment with `input` as the canonical binding and add `row` as a compatibility alias; test helpers no longer inject a `SOURCE` stub before user code.【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L18-L67】【F:interpreter/src/jvmMain/kotlin/v2/ir/TransformRegistryJvm.kt†L7-L27】【F:interpreter/src/jsMain/kotlin/v2/ir/TransformRegistryJs.kt†L7-L27】【F:test-fixtures/src/jvmMain/kotlin/v2/testutils/Runners.kt†L14-L54】
- `SOURCE` declarations are still parsed for now but are no longer required; the long-term plan is to remove them in favour of per-transform options and the default `input` binding.

## Platform Module
- There is no checked-in `platform/` module despite the include in `settings.gradle`, so platform-facing logic (graph orchestration, mode selection, environment setup) still lives in interpreter code and test fixtures.【F:settings.gradle†L1-L7】【F:interpreter/src/commonMain/kotlin/v2/Ast.kt†L9-L43】

## Recommended Follow-Up
1. Move graph/orchestration AST nodes out of the interpreter module and into a dedicated platform layer, leaving the core AST focused on transforms, functions, shared declarations, and expressions.
2. Replace `SourceDecl(name, adapter)` with a thin structure that records only the optional type hint (`AUTO` by default). Update the semantic analyser to stop special-casing concrete source names.
3. Teach the runtime helpers to publish `input` as the primary input binding (and populate `row` as a temporary alias). After the transition window, deprecate `row` from the semantic whitelist.
4. Create a real platform module (or remove the stale include) that owns mode selection and runner scaffolding currently embedded in test fixtures, so applications can depend on a single entrypoint instead of reimplementing it.
