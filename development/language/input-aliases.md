---
status: Implemented
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/input.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned to the current input binding (input + row alias)."
---
# Input binding and aliases

## Status (as of 2026-02-01)
- Stage: Implemented.
- `input` is the canonical binding and the source parameter.
- `row` is a compatibility alias.
- `SOURCE` has been removed from the language surface.

## Current behavior
- Default binding lives in `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/InputAliases.kt`:
  - `DEFAULT_INPUT_ALIAS = "input"`
  - `COMPAT_INPUT_ALIASES = setOf("row")`
- Runners seed both bindings:
  - `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/RunnerMP.kt`
  - `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/StreamCompiler.kt`
  - `interpreter/src/jvmMain/kotlin/io/github/ehlyzov/branchline/ir/TransformRegistryJvm.kt`
- Semantic analysis treats `input` and `row` as always-visible identifiers and prevents redefinition.

## Migration notes
- Use `input` directly in expressions; `row` remains as a compatibility alias.

## Deferred / future work
- Custom input aliases are deferred to the type system plan. When added, `input` will remain the default and `row` will be phased out on a defined timeline.
