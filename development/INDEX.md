---
status: Implemented
depends_on: []
blocks: []
supersedes: ['planning/current-research-tasks']
superseded_by: []
last_updated: 2026-10-21
changelog:
  - date: 2026-10-21
    change: "Published first-success journey from c4ce37ab: corrected executable guides, task-first home, compact editor and current-edit downloads; independent JVM/Node/browser acceptance and CI39599783459 / Pages39600640882 passed. See planning/first-success-journey-2026-10-21.md and evidence/first-success-2026-10-21/."
  - date: 2026-10-21
    change: "Completed published documentation/example UX audit: seven flow captures, 40-example inventory, confirmed First Steps root-access error, four representative RC example checks and three hero probes. Positioning, site architecture and usability protocol: planning/ux-docs-audit-2026-10-21.md and ../output/ux-docs-2026-10-21/report.html. Research only; redesign not implemented."
  - date: 2026-10-21
    change: "Published v1.0.0-rc.1 from 98c7659: exact-commit CI, five public packages/checksums, 288 published-package matrix cases, eight parent cases and 14 public editor checks passed. Pilot preparation cancelled; see planning/one-zero-rc-pilot-2026-10-21.md, releases/1.0.0-rc.1.md and evidence/rc-1.0.0-2026-10-21/README.md."
  - date: 2026-10-21
    change: "Accepted macOS platform cells: 251 packaged CLI cases, 28 Chrome/WebKit checks, 99 CLI tests and 8 independent UTF-8/cross-artifact checks; repaired Node native I/O errors. Recovered Docker after host ENOSPC and accepted 144 additional packaged cases on Debian 13.7 arm64/Linuxkit with Java 21/25 and Node 22/24; bounded platform gate closed, actual pilot host and Firefox unqualified. See evidence/one-zero-platform-2026-10-21/README.md and planning/one-zero-platform-2026-10-21.md for the external pilot handoff."
  - date: 2026-10-18
    change: "Accepted complete candidate API/stdlib inventories (27 declarations, 68 names) and v3 exact-number persistence: 1280 passing JVM/Node tests, 31 CLI compatibility cases and 532 independent exact-number checks. See planning/one-zero-surface-codec-2026-10-18.md and evidence/one-zero-surface-2026-10-18.md; release platform/pilot gates remain open."
  - date: 2026-10-18
    change: "Accepted bounded 1.0 compatibility/numeric contracts, executable onboarding, numeric/bytecode corrections and pilot plan: 1221 passing test executions plus 24 independent CLI cases. See planning/one-zero-contracts-2026-10-18.md and evidence/one-zero-contracts-2026-10-18.md; external pilot and full stable-surface gates remain open."
  - date: 2026-10-17
    change: "Accepted playground UX fixes (drafts, stale results, Stop/recovery, XML validation and source diagnostics); 1.0 operational/compatibility gates remain open. See planning/playground-ux-2026-10-17.md and evidence/playground-ux-2026-10-17/."
  - date: 2026-10-17
    change: "Completed profiled JVM loop-body compilation: projection time improved 11–16% versus VM, no proven interpreter win, preparation and plan memory increased; keep opt-in."
  - date: 2026-10-17
    change: "Completed compiler-prepared VM experiment: large reduction time improved 26%, interpreter remains faster, projection/paths gate failed and prepared memory grew; keep opt-in."
  - date: 2026-10-17
    change: "Completed factorial VM slots/fusion experiment: correctness accepted, longer JMH found no repeatable speed win and memory increased; default enablement rejected."
  - date: 2026-10-17
    change: "Completed VM jobs isolation and opt-in selective JSON prototype: sparse full-pipeline gains confirmed, dense 1 MiB regression blocks default enablement; independent JVM/Node and memory acceptance recorded."
  - date: 2026-10-17
    change: "Completed VM trace hot-path optimization: 14–27% less time on confirmed native large workloads; JVM/Node correctness gates passed, interpreter advantage remains."
  - date: 2026-10-17
    change: "Completed existing native VM/interpreter JMH pairs and supplemental scaling: interpreter faster on native pairs; VM reduces comprehension allocation and wins dynamic SET."
  - date: 2026-10-17
    change: "Completed second VM speed/memory audit: fixed exponential overlay enumeration and quadratic ownership-scope copying; paired probes and independent acceptance passed."
  - date: 2026-10-17
    change: "Completed VM performance goal: linear private accumulation, prepared runtime/evaluation, bounded pinned-root cache; independent audit found no remaining confirmed P0/P1 in scope."
  - date: 2026-10-17
    change: "Completed controlled 30-task/270-session authoring experiment; Branchline 90/90, JSONata JVM 87/90, Kotlin 90/90; general advantage not established."
  - date: 2026-10-17
    change: "Delivered and accepted next-stage local P0/P1 core hardening, price-ingest pilot and three-engine comparison; external P2 gates remain open."
  - date: 2026-06-10
    change: "Completed Branchline DX T9-T18 after benchmark hardening; final playground/docs/test/contour verification passed."
  - date: 2026-06-11
    change: "Started representative JSONata benchmark expansion B1-B5 and public JSONata benchmark page link repair."
  - date: 2026-06-10
    change: "Closed Branchline DX T9/T16 and added the Playground Example Metadata development record."
  - date: 2026-06-10
    change: "Completed short benchmark hardening increment; JSONata summaries now consume state reports and exclude non-comparable/flagged rows from speedups."
  - date: 2026-06-10
    change: "Started short benchmark hardening increment before resuming Branchline DX T9-T18."
  - date: 2026-06-10
    change: "Closed benchmark readiness G7 as Ready with caveats and moved benchmark readiness planning/docs records to implemented."
  - date: 2026-06-10
    change: "Implemented CLI conversion warning single-pass audit for output warning collection."
  - date: 2026-06-10
    change: "Implemented ContractValidator lookup/domain cache task under Interpreter Performance Tasks."
  - date: 2026-05-29
    change: "Implemented Branchline DX hardening H3 for additive diagnostics model evolution."
  - date: 2026-06-10
    change: "Implemented VM array-comprehension linearization to remove copy-per-append behavior from compiler-generated VM comprehensions."
  - date: 2026-06-10
    change: "Implemented deterministic CBOR set encode reuse to emit already-sorted canonical element bytes."
  - date: 2026-05-29
    change: "Implemented Branchline DX T8 mutation runtime diagnostic adapters and CLI JSON error propagation."
  - date: 2026-05-29
    change: "Implemented Branchline DX T7 diagnostics taxonomy and payload model."
  - date: 2026-05-29
    change: "Implemented Branchline DX hardening H1/H2/H5 for facade envelope boundary, normalization rewrite invariants, and inspect JSON leakage guards."
  - date: 2026-05-05
    change: "Implemented Branchline DX T5/T6: added normalization corpus baseline and stable inspect JSON envelope with CLI tests and docs."
  - date: 2026-05-05
    change: "Implemented Branchline DX T4 docs drift audit: array comprehension docs now match parser/EBNF syntax."
  - date: 2026-05-05
    change: "Implemented Branchline DX T3 mutation example rewrite: named playground accumulators now use += and docs frame APPEND as expression-style."
  - date: 2026-05-05
    change: "Implemented Branchline DX T2 public canonical subset docs: added AI canonical subset page and inspect-first CLI workflow."
  - date: 2026-05-05
    change: "Implemented Docs Generate Diplodoc CLI Fix: docsGenerate now resolves @diplodoc/cli and toc.yaml declares linked docs pages so docsBuild passes."
  - date: 2026-05-05
    change: "Added Branchline DX product package, implementation plan, hardening plan, gap registry, and completed AI-Friendly DSL direction with friendly mutation operations and DX gates."
  - date: 2026-05-05
    change: "Implemented Two-Layer Mutation Syntax hard-cut: removed statement-level append syntax and introduced += for explicit local accumulators across parser, runtime, VM, docs, playground keywords, and tests."
  - date: 2026-04-30
    change: "Tracked and implemented playground Kotlin/JS dependency loading, docs/assets cleanup, and decimal-domain JMH summary fixes."
  - date: 2026-04-30
    change: "Started Normalization MVP slice under AI-Friendly DSL M3: AstRenderer + BranchlineFacade normalizedSource population + bl inspect --normalized flag."
  - date: 2026-04-30
    change: "Completed Service Knowledge Contour Bootstrap with startup docs, canonical service map, verification contract, generated overlays, and contour lifecycle scripts."
  - date: 2026-04-30
    change: "Started Service Knowledge Contour Bootstrap to add the minimal startup, service-map, verification, generated overlay, and contour lifecycle script layer."
  - date: 2026-04-20
    change: "Added AI-Friendly DSL umbrella proposal to coordinate canonical subset, machine interface, and AI authoring workflow work."
  - date: 2026-02-27
    change: "Completed Contract Type Precision Upgrade: latest-only contract stack, canonical inspect JSON, nullable precision, explicit closure preservation, domain propagation, and SemVer contract-diff gate."
  - date: 2026-02-26
    change: "Started latest-only contract stack hard-cut implementation for Contract Type Precision Upgrade (remove V2/V3 API layers, unify canonical contract model, and retire inspect contract-version switching)."
  - date: 2026-02-26
    change: "Added proposed Contract Type Precision Upgrade doc for nullable typing, closure preservation, domain propagation, and contract-diff CI gates."
  - date: 2026-02-11
    change: "Tracked playground docs embed dark-mode contrast fix for intro tip text/readability."
  - date: 2026-02-10
    change: "Completed playground examples modernization: semicolon-free programs, broader CASE usage, stdlib-focused rewrites, and docs sync."
  - date: 2026-02-10
    change: "Started aggressive playground example modernization tracking (semicolon-free style, CASE adoption, and stdlib-first rewrites)."
  - date: 2026-02-10
    change: "Refined Contract Model V3 semantics: structural dedupe of overlapping obligations, debug-only inferred-rule metadata, and optional-first inferred input nodes with obligation-driven requiredness."
  - date: 2026-02-09
    change: "Added and implemented Contract Model V3 index entry for strict obligations, quantified constraints, and witness-aware satisfiability checks."
  - date: 2026-02-08
    change: "Added Input-Type-Seeded Output Inference planning doc for hybrid wildcard-output inference using declared input type seeds."
  - date: 2026-02-08
    change: "Logged conformance-alignment follow-up for contract lattice precision (SET/GET/cast path-shape preservation under `any` top)."
  - date: 2026-02-08
    change: "Tracked post-cleanup follow-up for contract inference lattice (`Never` bottom) and playground `showContracts` preset reliability."
  - date: 2026-02-08
    change: "Completed Contract JSON V2 cleanup C1-C7; moved related contract docs/plans back to implemented and added cleanup plan record."
  - date: 2026-02-08
    change: "Started Contract JSON V2 cleanup track and moved related contract docs/plans to in-progress; added dedicated cleanup plan doc."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M9: removed public V1 contract JSON output, added extension hooks, quality-gate tests, and playground example; moved V2 docs/plans to implemented."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M8: CLI/playground/runtime moved to V2 contracts by default, with inspect JSON version switch."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M7: V2 validator/enforcer and deterministic diagnostics renderer."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M6: static stdlib summary rules with provenance-aware shape propagation."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M5: output guarantee extraction from final abstract values including local object mutations."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M4: provenance-driven nested input requirement extraction and dynamic opaque-region tracking."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M3: path-sensitive null/object guard refinements with bounded merges."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M2: flow-sensitive V2 synthesizer core and conformance tests."
  - date: 2026-02-08
    change: "Completed Contract Inference V2 M1: model classes, adapter, and versioned contract JSON renderer."
  - date: 2026-02-08
    change: "Added Contract Inference V2 proposal set, milestone plan, and quality gates."
  - date: 2026-02-08
    change: "Added and implemented coalesce-aware input contract inference (`requiredAnyOf`) proposal."
  - date: 2026-02-08
    change: "Updated docs/playground planning records to remove deprecated static-playground references and align GitHub Pages deploy behavior with routed /playground/ access."
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
  - date: 2026-02-06
    change: "Moved XML Output Ordering to implemented."
  - date: 2026-02-07
    change: "Moved Conversion Loss Audit to implemented."
  - date: 2026-02-07
    change: "Moved Internal CBOR Representation to implemented."
  - date: 2026-02-07
    change: "Moved CBOR Determinism Rules to implemented."
  - date: 2026-02-07
    change: "Added Playground XML I/O Support implementation doc."
---
# Development Knowledge Base Index

This index is the canonical entry point for the /development knowledge base. All docs live under /development, and status is defined by YAML front matter.

## Status Index

### Proposed
- [AI-Friendly DSL](ai/ai-friendly-dsl.md) -- umbrella plan for canonical subset, stable machine interface, and contract-first AI authoring workflow.
- [Branchline DX Product Overview](product/branchline-dx/overview.md) -- product package for AI-friendly DSL completion, friendly mutations, canonical docs/examples, diagnostics, playground workbench, and DX gates.
- [Branchline DX Implementation Plan](planning/branchline-dx-implementation-plan.md) -- command-level T-task plan for the Branchline DX product scenarios.
- [Branchline DX Hardening Plan](planning/branchline-dx-hardening-plan.md) -- architecture/security/maintainability H-task plan for Branchline DX implementation.
- [Advanced CLI](tooling/advanced-cli.md) -- CLI+CI scripting plan.
- [Implicit Transform (CLI)](tooling/implicit-transform-cli.md) -- opt-in single-transform wrapper mode.
- [LLM Pipelines](ai/llm-pipelines.md) -- pipeline runtime spec.
- [Planitforme Integration](ai/planitforme-integration.md) -- migration sketches; depends on LLM Pipelines.
- [Tree Structures](runtime/tree-structures.md) -- arena/array tree structure recommendations.
- [I/O Contract Gap Analysis](language/io-contracts-gap-analysis.md) -- parser + I/O contract comparison and enrichment targets.
- [Two-Layer Mutation Syntax](language/two-layer-mutation-syntax.md) -- implemented two-layer mutation model with comprehensions plus += accumulators.
- [Docs Generate Diplodoc CLI Fix](docs/docs-generate-diplodoc-cli-fix.md) -- root-cause fix for docsGenerate npm executable resolution.
- [Docs + Playground Plan](docs/docs-playground-plan.md) -- docs plan; depends on Docs Refresh.
- [I/O Contracts Milestone Plan](planning/io-contracts-milestone-plan.md) -- implementation order and agent instructions.
- [Input-Type-Seeded Output Inference](planning/input-type-seeded-output-inference.md) -- hybrid plan to infer output from flow while seeding inference with declared input types when output is wildcard.

### In Progress
- [VM Jobs and JSON Pipeline](planning/vm-json-pipeline.md) -- worker isolation, opt-in JSON prototype and measured sparse/dense acceptance.
- [VM Memory Model Validation](planning/vm-memory-model-validation.md) -- separate design assessment of selective JSON, insertion costs, addressing and parallelism.
- [VM Hot-Path Optimization](planning/vm-hotpath-optimization.md) -- completed; [accepted fix and native JMH measurements](evidence/vm-hotpath-optimization/README.md).
- [VM/Interpreter Comparison](planning/vm-interpreter-comparison.md) -- completed; [native JMH results and supplemental scaling](evidence/vm-interpreter-comparison/README.md).
- [Second VM Speed/Memory Audit](planning/vm-performance-audit2.md) -- implemented; [measurements](evidence/vm-performance-audit2/README.md), [independent acceptance](evidence/vm-performance-audit2/independent-audit.md).
- [VM Performance Goal](planning/vm-performance-goal.md) -- implemented; [measurements and reproduction](evidence/vm-performance/README.md), [independent acceptance](evidence/vm-performance/independent-audit.md).
- [Branchline Next Stage](planning/branchline-next-stage.md) -- prioritized core guarantees, local price-ingest pilot, fair JSONata/Kotlin comparison and external adoption gates.
- [Docs Refresh](docs/docs-refresh.md) -- draft content and structure.
- [Multiplatform Migration](architecture/multiplatform-migration.md) -- JS VM parity gaps.
- [Module Responsibilities](architecture/module-responsibilities.md) -- platform/language split clean-up.
- [VM/Interpreter Split](architecture/vm-interpreter-split.md) -- remove VM fallback.
- [Runtime Optimizations](runtime/runtime-optimizations.md) -- hot path backlog.
- [Conformance Suite](quality/conformance-suite.md) -- parity coverage expansion.
- [JSONata Benchmarking](perf/jsonata-benchmarking.md) -- case matrix + validation.
- [Branchline JSONata Representative Benchmarks Plan](planning/branchline-jsonata-representative-benchmarks-plan.md) -- B1-B5 plan for representative JSONata case expansion, string-concat split, controlled long JMH, and public comparison repair.
- [Interpreter Performance Tasks](perf/interpreter-performance-tasks.md) -- perf task backlog.

### Implemented
- [Controlled Authoring Experiment](experiments/authoring-2026-10-17/REPORT.md) -- 30 tasks, 270 GPT-6.1 Sol sessions, hidden-test results, independent audit and no established general advantage.
- [Branchline Local Price Pilot Evidence](evidence/branchline-price-pilot.md) -- three-engine functional comparison, adversarial precision checks and Ktor replay; external adoption/authoring gates remain open.
- [Service Knowledge Contour Bootstrap](docs/service-knowledge-contour-bootstrap.md) -- minimal repository contour and lifecycle scripts.
- [Branchline Benchmark Readiness Plan](planning/branchline-benchmark-readiness-plan.md) -- benchmark-first gate completed as Ready with caveats; DX T9-T18 may resume while public speed claims remain gated.
- [Branchline Benchmark Hardening Plan](planning/branchline-benchmark-hardening-plan.md) -- row-state and credibility guard completed before Branchline DX T9-T18 resumes.
- [Branchline Benchmark Agent Goals](planning/branchline-benchmark-agent-goals.md) -- G0-G7 goal-mode benchmark readiness sequence completed.
- [Benchmarks Docs Fix Plan](docs/benchmarks-docs-fix-plan.md) -- MkDocs/benchmark publication plumbing and public framing repaired locally; release-asset validation remains external.
- [Playground And CI Regression Fixes](docs/playground-ci-regression-fixes.md) -- Kotlin/JS dependency loading, docs/assets cleanup, and decimal JMH summary arithmetic.
- [CLI Conversion Warning Single-Pass Audit](tooling/cli-conversion-warning-single-pass-audit.md) -- single recursive output warning audit for CLI JSON/XML conversion warnings.
- [CBOR Deterministic Encode Reuse](runtime/cbor-deterministic-encode-reuse.md) -- reuse canonical deterministic set element bytes during CBOR output.
- [Playground Example Metadata](docs/playground-example-metadata.md) -- retrieval metadata schema and first migrated playground example slice.
- [VM Array Comprehension Linearization](runtime/vm-array-comprehension-linearization.md) -- replace compiler-generated copy-per-append VM comprehension bytecode with a linear accumulator path.
- [Contract Type Precision Upgrade](language/contract-type-precision-upgrade.md) -- latest-only contract stack hard-cut plus nullable precision, closure/domain fixes, and SemVer contract-diff gates.
- [CLI Rollout](tooling/cli-rollout.md) -- JVM/JS CLI shipped.
- [Playground Examples Modernization](docs/playground-examples-modernization.md) -- aggressive modern-style rewrite of all playground examples with validation coverage.
- [Complex Tests](quality/complex-tests.md) -- demo tests + examples.
- [Interpreter Refactor](runtime/interpreter-refactor.md) -- Exec/caps refactor.
- [Optional Semicolons](language/optional-semicolons.md) -- parser accepts newlines.
- [Reserved Words](language/reserved-words.md) -- keyword rules.
- [Numeric Refactor](language/numeric-refactor.md) -- DEC + numeric kinds redesign.
- [Numeric Semantics](language/numeric-semantics.md) -- current numeric behavior.
- [CASE/WHEN](language/case-when.md) -- guard-only CASE expression implemented.
- [Input Aliases](language/input-aliases.md) -- `input` default with `row` compatibility alias.
- [Transform Contracts](language/transform-contracts.md) -- signatures and canonical contract inference/runtime behavior.
- [Transform Contracts Next Phase](language/transform-contracts-next.md) -- runtime validation and contract JSON export.
- [Contract Model](language/contract-model-v2.md) -- historical contract-model foundation and migration notes.
- [Contract Model (Strict)](language/contract-model-v3.md) -- strict node-kinded model with quantified obligations, domains, satisfiability, and witnesses.
- [Contract Inference Static Analysis](language/contract-inference-static-analysis.md) -- flow-sensitive inference with literal bracket precision and empty-array append stability.
- [Contract Diagnostics](language/contract-diagnostics-v2.md) -- deterministic mismatch reporting without static evidence dependency.
- [Diagnostics Taxonomy](language/diagnostics-taxonomy.md) -- structured diagnostic categories and optional payload model for repair-oriented Branchline authoring.
- [Contract Inference Milestone Plan](planning/contract-inference-v2-milestone-plan.md) -- completed milestone sequence and cleanup order.
- [Contract Inference Quality Gates](docs/contract-inference-quality-gates.md) -- precision/parity/performance plus structural/debug metadata gates.
- [Contract JSON Cleanup](planning/contract-v2-json-cleanup.md) -- completed canonical JSON cleanup and rollout notes.
- [Transform Options](language/transform-options.md) -- per-transform OPTIONS blocks.
- [Duplicate JSON Key Policy](language/json-duplicate-keys.md) -- error/last/first handling.
- [JSON Canonicalization Mode](language/json-canonicalization.md) -- deterministic JSON ordering + numeric formatting.
- [Large Number Handling Contract](language/large-number-contract.md) -- BigInt/BigDecimal input and output policy.
- [Numeric JSON Key Interpretation](language/json-numeric-keys.md) -- opt-in numeric key conversion for JSON objects.
- [JSON Binary Policy](language/json-binary-policy.md) -- base64 rules for binary data in JSON.
- [Set Serialization Semantics](language/set-serialization.md) -- set type behavior and deterministic ordering.
- [XML Mapping Contract](language/xml-mapping-contract.md) -- unified XML-to-Branchline mapping rules.
- [XML Namespace Handling](language/xml-namespaces.md) -- @xmlns namespace capture and prefix-preserving XML input mapping.
- [XML Output Ordering](language/xml-output-ordering.md) -- deterministic XML serialization rules and `@order` override.
- [Conversion Loss Audit](docs/conversion-loss-audit.md) -- documented loss points and CLI warning policy for JSON/XML conversions.
- [Playground XML I/O Support](docs/playground-xml-io-support.md) -- XML input parity and XML output formats in Playground.
- [Coalesce-Aware Input Contract Inference](docs/coalesce-aware-input-contract-inference.md) -- `??` fallback inference adds `requiredAnyOf` conditional input requirements.
- [Internal CBOR Representation](runtime/cbor-internal-representation.md) -- lossless Branchline-to-Branchline wire format with extended type tags and key policy enforcement.
- [CBOR Determinism Rules](runtime/cbor-determinism.md) -- deterministic CBOR encode option with canonical map/set ordering and byte-stable output.

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
- **Product**: `product/*`
- **Quality**: `quality/*`
- **Runtime**: `runtime/*`
- **Tooling**: `tooling/*`
- **Planning (legacy)**: `planning/*`

- [VM Slots and Fusion Experiment](planning/vm-slots-fusion-experiment.md) -- implemented; [final report](evidence/vm-slots-fusion-experiment/README.md), [measurements](evidence/vm-slots-fusion-experiment/TABLES.md), [fusion preparation design](experiments/vm-fusion-plan.md). Correctness accepted; no repeatable speed/memory advantage, default enablement rejected.
- [Compiler-Prepared Block Experiment](planning/vm-prepared-block-experiment.md) -- completed four-engine experiment: reduction gain, failed joint promotion gate, correctness and memory evidence.

- [Whole-Loop-Body Experiment](planning/vm-loop-body-experiment.md) -- completed JVM code-generation experiment, projection gain and preparation/memory tradeoffs; no proven interpreter advantage.

- [JVM-Local Loop Experiment](planning/vm-local-loop-experiment.md) -- completed; projection gain over interpreter, preparation/memory tradeoffs and evidence-based next-release scope.

- [Next Release Scope](planning/next-release-scope.md) -- selected Alpha/preview contents and original release gates; execution in the alpha.1 release plan.

- [v0.11.0-alpha.1 Release](planning/release-0.11.0-alpha.1.md) -- authorized release hardening, publication and independent public playground acceptance.
- [Compiled Artifact Compatibility](evidence/release-0.11.0-alpha.1/compatibility.md) -- format 2 gate, previous-writer fixture, migration policy and JVM/Node checks.

- [Alpha.1 Release Acceptance](evidence/release-0.11.0-alpha.1/README.md) -- parent package/browser/compiler checks, compatibility fixtures and release workflow evidence.

- [Task Recipes and Form Usability](planning/task-recipes-2026-10-21.md) -- six executable recipes, task catalog, expanded controls and public acceptance.

- [One-command installation](planning/simple-installer-2026-10-21.md) -- checksum-verified published CLI installer, `bl` launcher and local handoff.

- [Managed runtime installation](planning/managed-runtime-2026-10-21.md) -- private pinned Java/Node runtimes and clean-container acceptance.
