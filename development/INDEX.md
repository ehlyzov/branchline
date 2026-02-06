---
status: Implemented
depends_on: []
blocks: []
supersedes: ['planning/current-research-tasks']
superseded_by: []
last_updated: 2026-02-06
changelog:
  - date: 2026-02-01
    change: "Created development knowledge base index."
  - date: 2026-02-01
    change: "Updated language doc status listings (CASE, input aliases, contracts)."
  - date: 2026-02-01
    change: "Moved Transform Options to implemented."
  - date: 2026-02-01
    change: "Added implicit transform CLI proposal and moved Numeric Refactor to implemented."
  - date: 2026-02-03
    change: "Added Transform Contracts next-phase implementation."
  - date: 2026-02-05
    change: "Added I/O contract proposal set and CBOR determinism docs."
  - date: 2026-02-05
    change: "Removed runtime/tree-structures-ru.md (superseded by runtime/tree-structures.md)."
  - date: 2026-02-05
    change: "Moved Duplicate JSON Key Policy to implemented."
  - date: 2026-02-05
    change: "Moved JSON Canonicalization Mode to implemented."
  - date: 2026-02-05
    change: "Moved Large Number Handling Contract to in progress."
  - date: 2026-02-05
    change: "Moved Numeric JSON Key Interpretation to implemented."
  - date: 2026-02-05
    change: "Moved JSON Binary Policy to implemented."
  - date: 2026-02-05
    change: "Moved Set Serialization Semantics to implemented."
  - date: 2026-02-05
    change: "Moved XML Mapping Contract to implemented."
  - date: 2026-02-06
    change: "Moved XML Namespace Handling to implemented."
---
# Development Knowledge Base Index

This index is the canonical entry point for the /development knowledge base. All docs live under /development, and status is defined by YAML front matter.

## Status Index

### Proposed
- [Advanced CLI](tooling/advanced-cli.md) -- CLI+CI scripting plan.
- [Implicit Transform (CLI)](tooling/implicit-transform-cli.md) -- opt-in single-transform wrapper mode.
- [LLM Pipelines](ai/llm-pipelines.md) -- pipeline runtime spec.
- [Planitforme Integration](ai/planitforme-integration.md) -- migration sketches; depends on LLM Pipelines.
- [Tree Structures](runtime/tree-structures.md) -- arena/array tree structure recommendations.
- [Internal CBOR Representation](runtime/cbor-internal-representation.md) -- lossless Branchline-to-Branchline wire format.
- [CBOR Determinism Rules](runtime/cbor-determinism.md) -- deterministic encoding constraints for internal CBOR.
- [I/O Contract Gap Analysis](language/io-contracts-gap-analysis.md) -- parser + I/O contract comparison and enrichment targets.
- [XML Output Ordering](language/xml-output-ordering.md) -- deterministic attribute and sibling ordering.
- [Conversion Loss Audit](docs/conversion-loss-audit.md) -- catalog of lossy conversions and warnings.
- [Benchmarks Docs Fix Plan](docs/benchmarks-docs-fix-plan.md) -- MkDocs/benchmarks patch plan.
- [Docs + Playground Plan](docs/docs-playground-plan.md) -- docs plan; depends on Docs Refresh.
- [I/O Contracts Milestone Plan](planning/io-contracts-milestone-plan.md) -- implementation order and agent instructions.

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
- [Numeric Refactor](language/numeric-refactor.md) -- DEC + numeric kinds redesign.
- [Numeric Semantics](language/numeric-semantics.md) -- current numeric behavior.
- [CASE/WHEN](language/case-when.md) -- guard-only CASE expression implemented.
- [Input Aliases](language/input-aliases.md) -- `input` default with `row` compatibility alias.
- [Transform Contracts](language/transform-contracts.md) -- signatures and contract inference.
- [Transform Contracts Next Phase](language/transform-contracts-next.md) -- runtime validation and contract JSON export.
- [Transform Options](language/transform-options.md) -- per-transform OPTIONS blocks.
- [Duplicate JSON Key Policy](language/json-duplicate-keys.md) -- error/last/first handling.
- [JSON Canonicalization Mode](language/json-canonicalization.md) -- deterministic JSON ordering + numeric formatting.
- [Large Number Handling Contract](language/large-number-contract.md) -- BigInt/BigDecimal input and output policy.
- [Numeric JSON Key Interpretation](language/json-numeric-keys.md) -- opt-in numeric key conversion for JSON objects.
- [JSON Binary Policy](language/json-binary-policy.md) -- base64 rules for binary data in JSON.
- [Set Serialization Semantics](language/set-serialization.md) -- set type behavior and deterministic ordering.
- [XML Mapping Contract](language/xml-mapping-contract.md) -- unified XML-to-Branchline mapping rules.
- [XML Namespace Handling](language/xml-namespaces.md) -- @xmlns namespace capture and prefix-preserving XML input mapping.

### Deprecated
- None.

### Superseded (with replacements)
- [MCP Module (Superseded)](ai/mcp-module-superseded.md) -> superseded by [LLM Pipelines](ai/llm-pipelines.md).
- Tree Structures (RU) (removed 2026-02-05) -> superseded by [Tree Structures](runtime/tree-structures.md).
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
