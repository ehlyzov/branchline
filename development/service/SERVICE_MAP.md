# Service Map

## Purpose

Branchline is an experimental Kotlin Multiplatform data transformation language and toolchain for converting JSON-like and XML-like inputs into deterministic structured outputs. The repository contains the language interpreter, VM pipeline, CLI wrappers, conformance tests, documentation site, playground bundle, and benchmark tooling.

## Runtime Entrypoints

- `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/BlJvmMain.kt`: JVM `bl` CLI entrypoint.
- `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/BlcJvmMain.kt`: JVM compiler CLI entrypoint.
- `cli/src/jvmMain/kotlin/io/github/ehlyzov/branchline/cli/BlvmJvmMain.kt`: JVM VM CLI entrypoint.
- `cli/src/jsMain/kotlin/io/github/ehlyzov/branchline/cli/BlJsMain.kt`: Node CLI entrypoint.
- `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/ExperimentalJsonPipeline.kt`: opt-in, reusable JSON-text-to-compact-JSON API; preparation chooses selective materialization or an explicit eager fallback. This is not a default CLI input mode.
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/BranchlineFacade.kt`: common interpreter facade.
- `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/playground/PlaygroundFacade.kt`: playground-facing interpreter facade.
- `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VMLoop.kt`: VM loop used by the root `run` task through `:vm:runVmLoop`.
- `vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/vm/VM.kt`: explicit `prepareExperimental` / `executeExperimental` API for independently requested slots and instruction fusion; ordinary `execute` remains the default.
- The same VM exposes opt-in `prepareBlocks(bytecode, optimize=false)` / `executeBlocks(plan, env)`, backed by `PreparedBlockPlan.kt` and `PreparedBlockRuntime.kt`; both prepared modes use one resolved-instruction dispatch.
- `vm/src/jvmMain/kotlin/io/github/ehlyzov/branchline/vm/JvmLoopCompiler.kt`: opt-in `JvmLoopCompiler.prepare(bytecode)` creates a `LoopBodyPlan` for common `VM.executeLoopBodies(plan, env)`. Class generation is JVM-only; ordinary VM execution remains the default.
- `vm/src/jvmMain/kotlin/io/github/ehlyzov/branchline/vm/JvmLocalLoopCompiler.kt`: separate opt-in `JvmLocalLoopCompiler.prepare(bytecode)` creates a `LocalLoopPlan` for common `VM.executeLocalLoops(plan, env)`, retaining the prior loop backend as a control.

## Top-Level Module Map

- `interpreter/`: lexer, parser, AST, semantic analysis, contracts, IR conversion, common runtime, stdlib modules, JSON/XML/CBOR handling, tracing, and platform-specific I/O.
- `vm/`: bytecode model, compiler, bytecode I/O, VM execution, VM integration, and VM trace metadata.
- `cli/`: JVM and Node CLIs, runtime selection, JSON/XML interop, contract diffing, shared input loading, and output formatting.
- `conformance-tests/`: cross-platform behavior tests; `conformance-tests/src/commonTest` is the canonical parity surface.
- `test-fixtures/`: shared fixtures used by tests.
- `examples/`: example programs and fixtures.
- `playground/`: Vite/React playground source, Monaco language registration, worker integration, and example catalog.
- First-success fixture: `playground/examples/prepare-order.json` drives the editor,
  tracked `docs/downloads/prepare-order/` files and `docs/includes/prepare-order/`
  snippets through `playground/scripts/sync-first-success.mjs`. `npm test` rejects
  drift; MkDocs excludes standalone snippet pages while rendering their includes.
  Current-edit downloads are scoped to the JSON starter and preserve source/input.
- `docs/`: Diplodoc source plus generated playground assets under `docs/assets/`.
- `interpreter-benchmarks/`, `vm-benchmarks/`, `jsonata-benchmarks/`, `perf/`: benchmark tasks, scripts, data, and workflow notes.
- `jsonata-benchmarks/src/jmh/resources/price-ingest/`: independent expected inputs/outputs and common specification for the functional Branchline JVM, Dashjoin JSONata and Kotlin comparison. This is a correctness gate, separate from JMH performance results.
- `ktor-service/`: independent JVM price-ingest pilot build; Branchline contracts drive DTO generation and runtime validation, while Kotlin owns storage, ordering, idempotency and outbox effects.
- `development/`: proposals, implementation records, and planning notes. Planned repository changes must be recorded here first.

## Key Boundaries And Invariants

- Kotlin common source sets carry most language behavior; JVM and JS source sets should stay thin and platform-specific.
- Parser, semantic analyzer, contract inference, interpreter, VM, CLI, docs, and playground examples form one language surface. Syntax or semantics changes usually cross several of these boundaries.
- `conformance-tests/src/commonTest` is the source of truth for cross-runtime behavior.
- Prefer desugaring over adding VM or interpreter complexity when the syntax can be lowered cleanly.
- Canonical grammar for syntax changes lives at `interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt`.
- Playground keyword support lives at `playground/src/branchline-language.ts`.
- VM adapters (`VMExec`, `VMEval`, `VMFactory`, and VM `compileStream`) expose `VMFallbackPolicy` and per-invocation execution reports. Compatibility fallback is allowed only after compilation failure before execution; runtime failures propagate without interpreter replay. Strict `NONE` policy is available for bytecode-only verification. Reports describe sequential calls, not rollback or thread safety.
- Compiled-artifact JVM CLI fan-out creates at most min(jobs, entries) complete BranchlineProgram/VMExec worker contexts serially, then reuses each context exclusively on its worker thread. VM state and contract inference caches are isolated; bytecode is shared for reads. Results and the first failure retain input order, and all tasks join before the shared-store context is restored, including on collector interruption. CLI-owned traces and ambient CollectingTracer dispatch compiled jobs serially. Coordinated old-code corruption, worker reuse, errors and interruption evidence: `development/evidence/vm-json-pipeline/jobs-isolation.md`.
- JVM MATCH/REPLACE share a synchronized, bounded 64-entry regex cache. SharedStoreProvider remains global within the process: simultaneous independent CLI invocations are not isolated. Custom ambient tracers must support concurrent callbacks. VMFactory.Metrics diagnostic counters may lose concurrent increments; they do not determine execution output.
- ExperimentalJsonPipeline is sequential and stateful. Its conservative AST plan selects complete top-level values, preserves raw `input` alias shadowing, and validates every JSON value, including skipped values, before execution. Unsupported/whole-root/dynamic access, contracts ON and numeric-key mode use eager materialization; ambient tracing is checked on each call and also forces eager input. Report snapshots describe the current ambient plan, not execution history. Strict VM compilation/runtime failures propagate without interpreter replay. Returned trees are detached from source text, while the reusable VM retains its last environment/output. The low-level selected parser requires callers to prove omitted keys unobservable. API/planner limits: `development/evidence/vm-json-pipeline/prototype-design.md`.
- VM preparation and private collection ownership are performance-sensitive boundaries: `VMEval.prepare` captures authority, implicit expression caching is bounded, runtime prepared state is bounded to 64 programs, and private mutation is disabled for observable stepping/tracing/custom environments. Evidence and limits live in `development/planning/vm-performance-goal.md` and `development/evidence/vm-performance/`.
- VM tracing checks tracer availability before metadata lookup. Scalar stores skip old-value reads only for exact ordinary HashMap/LinkedHashMap environments when no tracer is active; custom maps and dynamic tracer activation retain their original observation boundaries. Runtime class checks are refreshed on environment replacement. Evidence: `development/evidence/vm-hotpath-optimization/`.
- Experimental slots/fusion plans retain the original bytecode and require it to remain unmodified after preparation; preparation does not change the public bytecode format or rewrite its instruction stream. VM instances, environments and last-invocation reports are sequential/thread-confined. A plan is not a snapshot, mutation guard or concurrency guarantee. Options default off; `collectDiagnostics=false` suppresses mechanism counters, while requested/effective flags and fallback reasons remain available.
- Experimental execution flushes named slots and materializes local aliases before host callbacks, then invalidates named caches. Unsupported programs, active tracing and custom root environments use ordinary VM execution; dynamically encountered observers or custom collections resume ordinary dispatch at the current original program counter. Errors and host effects are never replayed. Effective flags describe actual work, including any work before deoptimization; successful fallback is not optimized coverage. Contract/review evidence: `development/evidence/vm-slots-fusion-experiment/independent-review.md`.
- Slots/fusion performance promotion is rejected for the measured prototype: longer JMH found no repeatable speed win over current VM, all tested treatments increased allocation/prepared-executor memory, and the interpreter stayed faster on confirmed native workloads. Keep opt-in defaults; evidence and limits: `development/evidence/vm-slots-fusion-experiment/README.md`.
- Prepared-block plans own copies of source opcode/operand/offset arrays, constant-list structure, debug maps and trace-metadata collection structure. Source structural edits after preparation do not change the plan; constant values and IR trace nodes retain identity and must not be mutated during use. Preparation resolves operands, basic blocks, successors and observation sites. Optimized lowering uses bounded opcode classification and a whole-program write census for named slots, local aliases, mirror stores and direct static paths; it is not SSA, general liveness, alias or escape analysis. Actual nested containers/scalars still require runtime guards.
- Prepared-block execution is thread-confined and non-reentrant on one VM, including whole-plan fallback and tracer closing. Unsupported/malformed plans, tracing and custom root environments have explicit ordinary-VM fallback; dynamic observation boundaries materialize state and resume at the original current PC without replay. Host callbacks observe materialized locals/named bindings, with named-cache invalidation afterward. One VM caches one reusable frame by plan identity and clears its value/environment references in cleanup; canonical VM fields still retain the last execution state. Scalar executed/optimized/fallback getters describe actual latest-call work, possibly before deoptimization; static plan site counts are not execution counters. Contract/review evidence: `development/evidence/vm-prepared-block-experiment/independent-review.md`.
- Prepared-block promotion is rejected for the bounded measured candidate: optimized large reduction is about 26% faster than ordinary VM but about 10% slower than interpreter; projection/paths do not meet the joint speed gate. Allocation saves 32 B/op on reduction/projection, while 1,000 independent executors increase approximate retained heap from 6.161 to 8.603 MiB. A huge-method JIT eligibility defect was corrected before refreezing and restarting final measurements. Keep opt-in; evidence: `development/evidence/vm-prepared-block-experiment/README.md`.
- JVM loop-body plans structurally own bytecode/operand/metadata collections, including path-write segment/display lists; constant values and IR trace-node identities remain shared. The bounded compiler accepts one compiler-shaped comprehension with at most 128 body instructions and restricted control flow. It uses ASM 9.9 to emit fixed operation-specific helper calls and native filter/backedge branches, while values, numeric operations and source PCs stay on the canonical VM stack/state. Prefix and suffix use ordinary dispatch; the generated body has no opcode switch. This is not SSA/register lowering or numeric specialization. Unsupported layouts, nested loops, body calls and unsupported mutation opcodes have explicit fallback.
- Each eligible loop plan owns one generated class and isolated loader, with no global generated-class cache. A VM reuses one context and releases its plan reference in cleanup, while canonical VM state still retains its last input/output as applicable. Shared execution lifecycle guards remain thread-confined and non-reentrant across generated execution, initial fallback and tracer closing. Active tracing/custom root maps fall back before execution; callbacks inside canonical helpers preserve their effects, and tracing activated at an observation boundary resumes ordinary dispatch without replay. Actual kernel-entry scalars are distinct from static eligibility/class counts; an empty eligible loop enters no kernel. API, tests and memory/class-lifetime protocol: `development/evidence/vm-loop-body-experiment/harness.md`.
- The JVM-local loop backend assigns expression values to actual reference/long/tag/boolean JVM locals and uses guarded bounded integer arithmetic/comparisons. It is a restricted single-comprehension lowering, not general SSA, liveness analysis or a replacement numeric model. Exact supported container/type/range guards execute before the relevant operation; unsafe values, unsupported keys or stack capacity cause explicit ordinary continuation. Compiler-derived spill sites restore live operand values, original source PC, loop binding and completed builder state before canonical execution of the current instruction, with no replay of prior fields/rows/callbacks. Each admitted field lookup still executes, including access-order maps; custom/numeric observers run only after canonical state is available.
- Local-loop plans own source bytecode/metadata structure while constants/IR identities remain shared. One eligible plan owns one generated class/loader/classfile without a global cache. Per-VM context cleanup drops its plan reference; canonical VM last-state retention remains observable. The shared thread-confined/non-reentrant guard covers local execution, fallback and tracer closing. Actual entry/primitive-use/fallback scalars can describe partial work before deoptimization; static local/guard/spill site counts do not prove execution, and an empty eligible loop enters no kernel. API, semantic tests and five-stage memory protocol: `development/evidence/vm-local-loop-experiment/harness.md`.
- Whole-loop JVM code generation remains opt-in after measured acceptance: projection time is 11–16% lower than ordinary VM, with no proven interpreter advantage; the 15% promotion target did not repeat. Execution B/op equals VM, while preparation is 50.20 us versus 3.251 us and 1,000 prepared executors use about 9.468 versus 6.069 MiB incremental heap plus more Metaspace. Post-execution VM heap includes retained per-executor results. Evidence: `development/evidence/vm-loop-body-experiment/README.md`.
- Nested VM environments flatten lazily in one iterative merge; snapshot serializers still write per-frame state. Compiler ownership rollback logs changed candidates instead of copying the entire eligibility map. These are memory-sensitive boundaries covered by `development/planning/vm-performance-audit2.md`.
- The AI authoring subset excludes ambient clock/random calls as well as orchestration. Subset compatibility does not establish sandboxing, resource limits or correctness of business rules. JVM and JS numeric capabilities remain explicitly different.
- Generated documentation assets in `docs/assets/` are rebuildable outputs of `./gradlew playgroundBuildAssets`.

## Critical Integrations Visible From The Repo

- Gradle Kotlin Multiplatform builds JVM, JS browser, and JS Node outputs.
- `vm/build.gradle` pins `org.ow2.asm:asm:9.9` in `jvmMain` for experimental loop class generation; it adds no JavaCompiler or JS code-generation dependency.
- CI uses GitHub Actions in `.github/workflows/tests.yml` with Java 23, Node 18, Chrome, Gradle, and Branchline scripts that summarize JUnit XML.
- CLI release workflows publish JVM jar, Node tarball, and library artifacts through `.github/workflows/publish-cli-maven.yml`, `.github/workflows/publish-cli-node.yml`, and `.github/workflows/release-artifacts.yml`.
- Playground and docs build through `playground/package.json`, Vite, and the root `docsBuild`/`playgroundBuildAssets` Gradle tasks.
- Benchmark reporting uses `.github/scripts/*.bl`, benchmark modules, and `perf/` guidance.

## Dangerous Zones And Legacy Hotspots

- Contract inference and validation: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/contract/` and `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/sema/`.
- Numeric, JSON, XML, CBOR, and set serialization: `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/runtime/`, `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/json/`, `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/xml/`, `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/cbor/`, and CLI interop files.
- JVM/JS parity: multiplatform source-set splits in `interpreter/`, `vm/`, `cli/`, and shared `conformance-tests/src/commonTest`.
- CLI packaging: `cli/build.gradle`, `cli/js-package/`, and the `prepareJsCliPackage`/`packageJsCli` tasks.
- Playground asset path: `playground/` source and generated `docs/assets/` must remain aligned.
- CI test summarization depends on the Branchline CLI jar and `.github/scripts/junit-summary.bl`.

## Generated Overlays

- `development/service/generated/change-surface.json`: rebuildable path/module/change-surface overlay.
- `development/service/generated/hotspots.md`: rebuildable human hotspot summary.
- `development/service/generated/health-report.json`: rebuildable contour audit snapshot.

Regenerate overlays with `bin/refresh_contour.sh` after topology, entrypoint, verification, or hotspot changes.

## Review Triggers

The bounded 1.0 preparation adds guide-backed JVM/Node conformance examples
and explicit numeric profiles. The guide itself is a test input; generated docs
alone cannot verify examples. Candidate compatibility policy is in
`docs/guides/release-readiness.md`; the operational pilot and its host-owned
resource/rollback boundaries are in `docs/guides/pilot-plan.md`.

Update this file when repository topology, runtime entrypoints, integration boundaries, workflow shape, dangerous zones, or language-surface ownership changes.

Update `development/service/VERIFY.md` when build, test, lint, local-run, CI, or required evidence expectations change.

Selective JSON acceptance: explicit opt-in only. Sparse 16/64 MiB string fixtures and nested-record controls improve full-pipeline JVM time/allocation/RSS; dense 1 MiB regresses 44–56%, blocking default enablement. Exact measurements, fallback/trace/suspension rules and limits: `development/planning/vm-json-pipeline.md`.

## Prerelease publication boundary

- Canonical package version lives in root `build.gradle`; `CliVersion` is generated
  for both targets. Tags and CLI display add `v`; npm/package metadata use SemVer.
- Experimental VM APIs require `ExperimentalVMApi` opt-in for Kotlin consumers.
  Java consumers accept the documented unstable contract; ordinary execute stays
  default. Selective JSON APIs retain their explicitly named prototype contract.
- Compiled CLI writers emit format 3 with a mandatory marker; readers accept the
  tested legacy v1/v2 subsets and reject future/invalid formats before program loading.
  Explicit BigInt/BigDec use distinct textual tags. JVM preserves exact value and
  decimal scale; JS rejects integer overflow and decimal text that cannot roundtrip
  through its Double representation. This is not arbitrary-precision JS parity.
- The candidate Kotlin/CLI allowlist is `docs/guides/api-support.md`; the complete
  default stdlib registry and per-function domains are `docs/language/stdlib-support.md`.
  Conformance consumer/source-inventory tests and JVM/Node stdlib registry/oracle
  tests guard these boundaries. Public visibility alone grants no compatibility.
- Candidate platform policy and actual per-runtime/OS/browser results are in
  `docs/guides/platform-support.md`. Packaged macOS acceptance is distinct from
  the accepted Debian arm64/Linuxkit container cell and external pilot-host
  qualification/operation. Node filesystem
  system errors at supported CLI read/write boundaries are normalized to Kotlin
  exceptions before common I/O helpers produce status 74/structured errors;
  arbitrary native errors are not reclassified.
- Node JS library copies use `interpreter/build/dist/js/nodeProductionLibrary`; browser runtime
  stays in `interpreter/build/dist/js/productionLibrary`. Playground asset inputs include that
  runtime and example/config files, so runtime edits invalidate stale bundles.
- Release workflow validates checkout/tag/version, uploads exact-version artifacts
  and checksums before publishing a draft, then deploys Pages from built SHA.
  Public `/release-metadata.json` and `/build-info/` identify the deployed source.
- Actual Pages build uses MkDocs; local `docsBuild` is Diplodoc and is an additional
  check, not a replacement for the deployed site's generator.
