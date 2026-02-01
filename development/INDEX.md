---
status: Implemented
depends_on: []
blocks: []
supersedes: ['planning/current-research-tasks']
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Created development knowledge base index."
  - date: 2026-02-01
    change: "Updated language doc status listings (CASE, input aliases, contracts)."
---
# Development Knowledge Base Index

This index is the canonical entry point for the /development knowledge base. All docs live under /development, and status is defined by YAML front matter.

## Status Index

### Proposed
- [Advanced CLI](tooling/advanced-cli.md) -- CLI+CI scripting plan.
- [LLM Pipelines](ai/llm-pipelines.md) -- pipeline runtime spec.
- [Planitforme Integration](ai/planitforme-integration.md) -- migration sketches; depends on LLM Pipelines.
- [Transform Options](language/transform-options.md) -- OPTIONS block proposal; depends on Input Aliases.
- [Numeric Refactor](language/numeric-refactor.md) -- DEC + numeric kinds redesign.
- [Tree Structures](runtime/tree-structures.md) -- arena/array tree structure recommendations.
- [Benchmarks Docs Fix Plan](docs/benchmarks-docs-fix-plan.md) -- MkDocs/benchmarks patch plan.
- [Docs + Playground Plan](docs/docs-playground-plan.md) -- docs plan; depends on Docs Refresh.

### In Progress
- [Docs Refresh](docs/docs-refresh.md) -- draft content and structure.
- [Multiplatform Migration](architecture/multiplatform-migration.md) -- JS VM parity gaps.
- [Module Responsibilities](architecture/module-responsibilities.md) -- platform/language split clean-up.
- [VM/Interpreter Split](architecture/vm-interpreter-split.md) -- remove VM fallback.
- [Runtime Optimizations](runtime/runtime-optimizations.md) -- hot path backlog.
- [Conformance Suite](quality/conformance-suite.md) -- parity coverage expansion.
- [JSONata Benchmarking](perf/jsonata-benchmarking.md) -- case matrix + validation.
- [Interpreter Performance Tasks](perf/interpreter-performance-tasks.md) -- perf task backlog.

### Implemented
- [CLI Rollout](tooling/cli-rollout.md) -- JVM/JS CLI shipped.
- [Complex Tests](quality/complex-tests.md) -- demo tests + examples.
- [Interpreter Refactor](runtime/interpreter-refactor.md) -- Exec/caps refactor.
- [Optional Semicolons](language/optional-semicolons.md) -- parser accepts newlines.
- [Reserved Words](language/reserved-words.md) -- keyword rules.
- [Numeric Semantics](language/numeric-semantics.md) -- current numeric behavior.
- [CASE/WHEN](language/case-when.md) -- guard-only CASE expression implemented.
- [Input Aliases](language/input-aliases.md) -- `input` default with `row` compatibility alias.
- [Transform Contracts](language/transform-contracts.md) -- signatures and contract inference.

### Deprecated
- None.

### Superseded (with replacements)
- [MCP Module (Superseded)](ai/mcp-module-superseded.md) -> superseded by [LLM Pipelines](ai/llm-pipelines.md).
- [Tree Structures (RU)](runtime/tree-structures-ru.md) -> superseded by [Tree Structures](runtime/tree-structures.md).
- [Current Research Tasks](planning/current-research-tasks.md) -> superseded by this index.

## Areas
- **AI**: `ai/*`
- **Architecture**: `architecture/*`
- **Docs**: `docs/*`
- **Language**: `language/*`
- **Perf**: `perf/*`
- **Quality**: `quality/*`
- **Runtime**: `runtime/*`
- **Tooling**: `tooling/*`
- **Planning (legacy)**: `planning/*`
