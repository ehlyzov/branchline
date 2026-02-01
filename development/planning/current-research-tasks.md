---
status: Superseded
depends_on: []
blocks: []
supersedes: []
superseded_by: ['INDEX']
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/current.md; superseded by INDEX."
  - date: 2026-02-01
    change: "Updated references to /development paths."
---
# Current Research Tasks (as of 2026-01-31)

> Superseded by `development/INDEX.md`. This snapshot is retained for historical context.

## Research inventory summary
- Implemented: `development/tooling/cli-rollout.md`, `development/language/optional-semicolons.md`, `development/language/reserved-words.md`, `development/language/numeric-semantics.md`, `development/runtime/interpreter-refactor.md`, `development/quality/complex-tests.md`.
- In progress/partial: `development/quality/conformance-suite.md`, `development/language/input-aliases.md`, `development/architecture/multiplatform-migration.md`, `development/architecture/module-responsibilities.md`, `development/architecture/vm-interpreter-split.md`, `development/runtime/runtime-optimizations.md`, `development/perf/jsonata-benchmarking.md`.
- Proposed/draft: `development/tooling/advanced-cli.md`, `development/ai/llm-pipelines.md`, `development/language/case-when.md`, `development/docs/docs-playground-plan.md`, `development/docs/benchmarks-docs-fix-plan.md`, `development/ai/mcp-module-superseded.md`, `development/ai/planitforme-integration.md`, `development/language/numeric-refactor.md`, `development/language/transform-options.md`, `development/language/transform-contracts.md`, `development/runtime/tree-structures.md`.

## development/perf/interpreter-performance-tasks.md summary
- Tasks 1, 2, and 5 delivered measurable gains in target cases.
- Task 3 is mixed (string-concat regressed).
- Task 4 regressed `performance/case001` despite range improvements.
- Task 6 standardized JFR extraction without runtime impact.

## Task backlog (multi-level)

### Performance

#### PERF-1: Resolve `performance/case001` regression from Task 4 (Impact: 5)
- Goal: remove the ~15% regression while keeping range improvements.
- References: `development/perf/interpreter-performance-tasks.md` (Task 4), `development/runtime/runtime-optimizations.md`.
- Subtasks:
  - Re-run JFR on `performance/case001` to find new hotspots.
  - Gate the range pre-size change behind heuristics or revert if needed.
  - Re-benchmark with `:jsonata-benchmarks:jmh` and update baselines.

#### PERF-2: Fix `string-concat/case000` regression from Task 3 (Impact: 4)
- Goal: recover string-concat throughput while preserving map-pre-size gains.
- References: `development/perf/interpreter-performance-tasks.md` (Task 3).
- Subtasks:
  - Identify why map pre-sizing hurts string-concat (allocation profile).
  - Add conditional sizing or alternative structure for string-heavy cases.
  - Re-run JMH and update results.

#### PERF-3: VM hot-path work (env mirroring, tracing, allocators, inline caches) (Impact: 4)
- Goal: reduce VM overhead in common paths that remain hotspots.
- References: `development/runtime/runtime-optimizations.md`, `development/architecture/vm-interpreter-split.md`, `development/runtime/tree-structures.md`.
- Subtasks:
  - Remove/limit env mirroring where not required.
  - Reduce tracing overhead when disabled.
  - Prototype inline caches for numeric/field access.

### Language & Semantics

#### LANG-1: Implement `OPTIONS` block + input/SOURCE migration (Impact: 5)
- Goal: standardize per-transform execution options and finish input binding cleanup.
- References: `development/language/transform-options.md`, `development/language/input-aliases.md`.
- Subtasks:
  - Add grammar + AST for `OPTIONS`.
  - Thread options through interpreter/VM runners.
  - Add conformance tests and docs; define deprecation timeline for `SOURCE`.

#### LANG-2: Transform contracts and type annotations (Impact: 4)
- Goal: enable explicit input/output schemas and inferred contracts.
- References: `development/language/transform-contracts.md`.
- Subtasks:
  - Add parser/AST signature support.
  - Implement contract model + inference pass.
  - Add conformance tests and tooling output.

#### LANG-3: CASE/WHEN expression (Impact: 3)
- Goal: add readable multi-branch expression syntax.
- References: `development/language/case-when.md`.
- Subtasks:
  - Implement parser + desugaring to nested IF.
  - Update grammar, docs, and playground examples.
  - Add conformance tests.

#### LANG-4: Numeric refactor (DEC + numeric kinds) (Impact: 3)
- Goal: unify numeric kinds with deterministic precision handling.
- References: `development/language/numeric-refactor.md`, `development/language/numeric-semantics.md`.
- Subtasks:
  - Decide compatibility with current numeric semantics.
  - Prototype interpreter/VM specializations and conformance tests.

### Tooling & CLI

#### TOOL-1: Advanced CLI shared IO + CI script migration (Impact: 4)
- Goal: replace Node `.mjs` scripts with Branchline scripts and shared IO.
- References: `development/tooling/advanced-cli.md`.
- Subtasks:
  - Implement shared write syntax + SharedStore wiring.
  - Add CLI flags for shared mapping/output extraction.
  - Update CI workflows and docs.

#### TOOL-2: MCP module + pipeline runtime MVP (Impact: 5)
- Goal: enable YAML-defined LLM pipelines with REST/MCP execution.
- References: `development/ai/llm-pipelines.md`, `development/ai/mcp-module-superseded.md`, `development/ai/planitforme-integration.md`.
- Subtasks:
  - Define pipeline schema + runtime core.
  - Implement minimal REST run endpoint.
  - Add MCP tool manifest + call wrapper.

### Docs & Adoption

#### DOC-1: Docs refresh + embedded playground (Impact: 4)
- Goal: improve onboarding and interactive examples.
- References: `development/docs/docs-refresh.md`, `development/docs/docs-playground-plan.md`.
- Subtasks:
  - Move draft content into `docs/` and update nav.
  - Embed playground and add example coverage.
  - Validate MkDocs build.

#### DOC-2: Benchmarks docs fixes (Impact: 3)
- Goal: fix broken links/assets and numeric formatting in benchmark pages.
- References: `development/docs/benchmarks-docs-fix-plan.md`.
- Subtasks:
  - Apply workflow/script changes (Agent A/B/C).
  - Validate generated site output.

### Quality & Benchmarking

#### QA-1: JSONata perf validation + full-case mapping (Impact: 3)
- Goal: semantic output validation and complete suite mapping.
- References: `development/perf/jsonata-benchmarking.md`.
- Subtasks:
  - Implement normalized output comparison.
  - Complete YAML case matrix for full suite.

#### QA-2: Conformance suite parity (Impact: 3)
- Goal: finish alias migration and JS VM assertions.
- References: `development/quality/conformance-suite.md`.
- Subtasks:
  - Update alias usage to `input`.
  - Add JS VM parity checks.

### Platform

#### PLAT-1: VM JS backend parity or scoped support (Impact: 4)
- Goal: decide scope and fill missing VM JS instructions or document limits.
- References: `development/architecture/multiplatform-migration.md`, `development/architecture/vm-interpreter-split.md`.
- Subtasks:
  - Inventory missing VM JS ops.
  - Implement or explicitly mark unsupported paths in docs/tests.

## Top 5 priorities (impact-weighted)

1) PERF-1 (Impact 5): fixes a confirmed regression in `performance/case001`, which directly affects benchmark credibility and overall interpreter throughput.
2) LANG-1 (Impact 5): `OPTIONS` + input migration standardizes execution semantics and unblocks multiple language/runtime cleanups.
3) TOOL-2 (Impact 5): pipeline runtime + MCP unlocks the new product direction (LLM pipelines + Prompt Studio).
4) DOC-1 (Impact 4): docs + embedded playground are the main onboarding surface; without them, adoption and contributor velocity stall.
5) PLAT-1 (Impact 4): VM JS parity decisions affect the multiplatform story and determine whether JS is production-grade or explicitly scoped.
