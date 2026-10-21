# Verification Contract

## Fastest Local Setup Path

- Use the repository wrapper: `./gradlew`.
- Java is required for Gradle/Kotlin tasks. CI uses Java 23; the Gradle build config requests Java 21 toolchains for Kotlin compilation.
- Node 18+ is required for playground assets and JS CLI packaging.
- Browser tests require Chrome through `CHROME_BIN`; without it, interpreter JS browser tests are disabled by build logic.

## Narrow Verification Path

- Release installer: `sh -n docs/install.sh` and `python3 bin/test_installer.py`.
  Pages runs these checks before publication, plus
  `sh bin/test_installer_clean.sh` in fresh non-root Debian containers without
  Java/Node/Python. Both default private JRE and explicit private Node must install,
  execute independent JSON, reject malformed input and survive reinstall. Set
  BRANCHLINE_INSTALLER_URL to the public HTTPS script for post-deploy acceptance.
  Also run on macOS with restricted PATH; fixture archives do not establish real
  package/runtime acceptance. Pin vendor hashes/versions and preserve licenses. Installer changes do not rebuild CLI
  packages unless package layout itself changes.

- Interpreter JVM behavior: `./gradlew :interpreter:jvmTest`.
- Interpreter JS behavior: `./gradlew :interpreter:jsTest`.
- VM JVM behavior: `./gradlew :vm:jvmTest`.
- VM JS behavior: `./gradlew :vm:jsTest`.
- Cross-runtime conformance JVM: `./gradlew :conformance-tests:jvmTest`.
- Cross-runtime conformance JS: `./gradlew :conformance-tests:jsTest`.
- CLI JVM/Node surface: `./gradlew :cli:jvmTest :cli:jsNodeTest`.
- Packaged platform matrix: build `:cli:blShadowJar :cli:packageJsCli`, then use
  `development/evidence/one-zero-platform-2026-10-21/cli-matrix.py` with explicit
  jar/tarball paths and runtime config. It extracts fresh installations, checks
  independent JSON/errors and crossed/legacy artifacts. Record actual OS/CPU,
  vendor/update and hashes; macOS results do not establish Linux or Windows.
  The pinned Linux arm64 wrapper is `development/evidence/one-zero-platform-2026-10-21/linux-harness.py`;
  its report records runtime preparation, isolated mounts and disk reserve.
  Container results do not qualify a different pilot host/architecture.
  Native Node file-error changes require unfiltered `:cli:jsNodeTest`, including
  NodeFileIoJsTest, and actual packaged Node launchers returning I/O status 74.
- Compiled CLI worker isolation: `./gradlew :cli:jvmTest --tests '*CompiledFanoutIsolationTest' --tests '*ParallelWorker*' :interpreter:jvmTest --tests '*RegexCacheConcurrencyTest'`.
- Experimental JSON parser/planner/pipeline JVM checks: `./gradlew :interpreter:jvmTest --tests '*JsonInputSelectionTest' :cli:jvmTest --tests '*ExperimentalJsonPipelineTest'`. Node coverage: `./gradlew :interpreter:jsNodeTest :cli:jsNodeTest`; run Node suites unfiltered and inspect XML test counts, since JVM-style filters can select zero Node tests.
- Playground assets: `./gradlew playgroundBuildAssets`.
- First-success docs/editor changes: `cd playground && npm test` includes canonical
  `prepare-order` fixture/download/snippet drift checks, executable introductory
  and tour programs through the actual JS facade, and current-edit export checks.
  After changing the fixture run `npm run sync:first-success`; generated files
  under `docs/downloads/prepare-order/` and `docs/includes/prepare-order/` are tracked.
  Build Pages using `mkdocs build` with `requirements.txt` after playground assets;
  root `docsBuild` targets Diplodoc and does not replace the Pages/MkDocs check.
  Independently execute browser-downloaded edited files with published JVM/Node
  CLI, then verify the public home -> example -> edit -> local-run journey against
  deployed release-metadata.json SHA. See evidence/first-success-2026-10-21/.
- Task recipes: `cd playground && npm run test:recipes` checks generated recipe
  pages/downloads and executes base, exercise and edge cases through the JS facade.
  Run `npm run sync:recipes` after changing canonical recipe fixtures. Independently
  execute all six recipe files with the published JVM/Node CLI and verify task
  filtering plus expanded settings at desktop/mobile widths in a real browser.
- Playground UX/input changes: `cd playground && npm test` checks worker dependencies,
  catalog metadata, XML input validation and actual-runtime wrapper diagnostics.
  The browser interpreter distribution is required by that last test. Run the asset build and actual browser
  acceptance for draft preservation, stale results, current-setting shortcuts,
  Stop/recovery, input/syntax errors and narrow-screen controls; static build success
  does not establish these interactions. Facade wrapping changes require
  `./gradlew :interpreter:jvmTest --tests '*PlaygroundFacadeTest'` and unfiltered
  `./gradlew :interpreter:jsNodeTest`, with the same CHROME_BIN setting as the local
  Yarn lock; inspect actual JUnit XML counts.
- Contour integrity: `bin/audit_contour.sh`.
- Price-ingest functional comparison: `./gradlew :jsonata-benchmarks:priceIngestComparison`. Requires the pinned Dashjoin JVM dependency; all three engines are mandatory. Reports business-transform cases separately from shared wire-boundary rejects and makes no performance claim.
- Comparison sensitivity: `./gradlew :jsonata-benchmarks:priceIngestComparison -PpriceIngestCorruptEngine=kotlin`. Expected result is nonzero exit with the injected `ordinary-create` mismatch; a successful exit fails this negative check.
- Independent Ktor pilot: from `ktor-service/`, run `./gradlew -Pbranchline.useLocal=true test`. Uses local composite dependencies, generates contracts/DTOs and exercises synthetic HTTP/state/outbox replay without GitHub Packages credentials.

Choose the narrow path that matches the touched surface and report the exact command output status.

## Bounded 1.0 contract checks

- API/stdlib inventory changes require `:conformance-tests:jvmTest` and unfiltered
  `:conformance-tests:jsNodeTest`. StableApiConsumerTest compiles real consumers;
  StableApiInventoryJvmTest compares exact source signatures and CLI behavior;
  StdlibSupportContractTest compares every registered name and documented row,
  and executes independent candidate oracles through interpreter and strict VM.
  Gradle tracks inventory documents and admitted API source files as test inputs.
- Numeric bytecode changes require interpreter, VM, CLI and conformance JVM/Node
  suites plus `:cli:blShadowJar`. Check BigInt kind/value, BigDec value/scale on JVM,
  JS admission/rejection, nested values and actual suspended snapshots. Preserve
  genuine old-writer fixtures; relabelling a current artifact is only a rejection
  control. Test a retained old reader against an actual new-writer artifact.
  Independent CLI probes and fixture/reader hashes for v3 are recorded in
  `development/evidence/one-zero-surface-2026-10-18.md`.
- Introductory programs are read from `docs/guides/getting-started.md`, including
  marked inputs and expected outputs; independent test oracles also pin results.
  Run `./gradlew :conformance-tests:jvmTest --tests '*GettingStartedExamplesTest'`
  and unfiltered `./gradlew :conformance-tests:jsNodeTest`. Both interpreter and
  ordinary strict VM are required. Wrong-program and stale-doc negative controls
  must fail their internal gates; matching fallback output does not count as VM.
- Numeric contract changes require interpreter/conformance JVM and Node tests,
  exact boundary values, explicit platform divergences and actual strict VM mode.
  The bounded common profile and exclusions are in `docs/language/numeric.md`.
- Use the same CHROME_BIN setting as the local Yarn lock; inspect JUnit XML
  failure/error counts even if Gradle exits zero. Tests may ignore failures for
  CI summary generation.
- Operational pilot admission and observation are separate from repository tests:
  `docs/guides/pilot-plan.md` and
  `development/planning/one-zero-pilot-run-record.md` define the unfilled record.
  Do not infer representative traffic, independent adoption, resource ceilings or
  rollback completion from the synthetic price/Ktor gates.

## Full Local Verification Path

- Full clean build: `./gradlew clean build`.
- Documentation plus playground asset build: `./gradlew docsBuild`.
- Benchmark tasks are not part of the default full local verification path; use them only when performance-sensitive code changed.

## CI-Only Checks

- GitHub Actions in `.github/workflows/tests.yml` run CLI jar build plus interpreter, VM, conformance, and CLI jobs.
- CI configures Java 23, Node 18, Chrome, Gradle cache, JUnit summary scripts, and status badges.
- Release and publish workflows are under `.github/workflows/` and should be checked when artifact packaging or publishing changes.
- RC publication: qualify the exact commit with Test Suite, checking run-tests step outcomes as well as JUnit summaries. Dispatch release-artifacts.yml with full_release=true and optional benchmarks disabled when unchanged; validate published five-package checksums/version/metadata and public Pages editor against deployed SHA. Local package hashes alone do not certify CI-built downloads.

## Risk-Triggered Extra Checks

- Syntax changes: update `interpreter/src/jvmTest/resources/io/github/ehlyzov/branchline/ebnf.txt`, parser tests, conformance tests, language docs, playground keywords, and playground examples.
- Contract changes: run relevant interpreter tests, conformance tests, CLI contract tests, and update contract docs/examples.
- JSON/XML/CBOR/numeric changes: run targeted conformance tests plus CLI interop tests.
- Compiled fan-out changes: require a coordinated overlap regression with independent per-entry expected output, jobs=1 and jobs>1, strict contracts, report/summary order, first-error/no-partial-output behavior, worker reuse, serial collecting traces, and completion before shared-store teardown on error/interruption. Run the affected interpreter/CLI JVM and unfiltered Node suites. Existing diagnostic metrics and process-global providers are not a whole-process concurrency guarantee.
- Selective JSON changes: include malformed and duplicate keys in skipped values, numeric/Unicode behavior, null/missing distinctions, alias collisions, detached values/reuse, unsupported syntax and contract/key-mode fallback. Check ambient tracing enabled after preparation and immutable effective-plan snapshots. Compare eager output/errors through strict bytecode execution; equal output alone does not prove the selective plan ran. The common parser/pipeline tests run on JVM and Node.
- Selective pipeline performance: build with `./gradlew :vm-benchmarks:jmhJar`, then invoke `io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineBenchmark.fullPipeline` explicitly through the resulting JMH jar; the Gradle default include filter omits this experimental benchmark. Preparation/source/input generation stay outside measurement; each measured operation includes complete JSON validation/materialization, environment/contracts, strict VM execution and compact output serialization. Pair eager/selective with identical inputs, heap, JVM, warming and lifetimes, plus dense/records/dynamic-fallback controls. Independent expected-output/plan validation, exact invocations, fresh-process live-heap stages and RSS commands are in `development/evidence/vm-json-pipeline/pipeline-harness.md`. Report elapsed time, JMH allocated B/op, post-GC live/retained heap and peak process RSS separately; do not treat the probe's forced-GC stages as timed samples. Acceptance uses the predeclared gate in `development/planning/vm-json-pipeline.md` and requires saved raw results; JVM results do not establish JS performance.
- CLI packaging changes: run `./gradlew :cli:blShadowJar :cli:packageJsCli :cli:jvmTest :cli:jsNodeTest`.
- Playground changes: run `./gradlew playgroundBuildAssets` and inspect the playground locally when UI behavior is affected.
- Performance-sensitive interpreter or VM changes: run the relevant benchmark task and record the dataset and command used. The VM performance acceptance harness and before/after evidence are in `development/evidence/vm-performance/`; build its runtime with `./gradlew :vm-benchmarks:jmhJar`. Include VM common JVM/JS tests for preparation, private collection ownership and snapshot/callback behavior; do not infer JS speed from JVM timing.
- Numeric-library or deterministic-subset changes: interpreter and conformance JVM/JS tests, numeric/subset docs, and applicable playground example expectations. Numeric tests must assert values/overflow behavior, not just runtime wrapper types.
- VM trace fast-path changes: include common `VMTraceFastPathTest` on JVM/Node (host/tracer/custom-map callbacks, tracer precedence and instruction counts) and existing tracing/snapshot suites. Re-run unchanged VM/interpreter native JMH pairs with `collectionType=hash`, independent expected outputs and GC allocation profiling; distinguish B/op from retained heap/RSS. Reproduction and before/after evidence: `development/evidence/vm-hotpath-optimization/`.
- Nested-environment/compiler-ownership changes: include `VMOverlayEnumerationTest` and `PrivateCollectionScopeTest` through VM JVM/Node gates; allocation/enumeration and compile-equivalence probes are in `development/evidence/vm-performance-audit2/`.
- VM fallback-policy changes: VM JVM/JS regression tests must assert actual bytecode vs compilation fallback, strict rejection, and a single execution of host effects on error; runtime failure must not replay a transform. Do not equate a successful compatibility result with bytecode coverage.
- Experimental VM slots/fusion changes: run the gate below and inspect actual JVM/Node XML counts. Cover all four flags with diagnostics on/off, independent expected outputs, input immutability, final environment/order/snapshots, overflow/null/missing, loop restoration, host-time observation, dynamic tracing, custom collections/maps, errors and explicit closure/suspension fallback. Plans require unmodified bytecode after preparation; mutation and concurrent execution are outside this experimental contract. Parent-owned independent checks are in `development/evidence/vm-slots-fusion-experiment/FactorialAcceptance.java`; raw gate/acceptance evidence is `final-gate3.log`, `test-counts.json`, `parent-after2.log`, and `fixture-validation.log` in the same directory.
- Experimental factorial measurements: `VMFactorialBenchmark` uses the same prepared input and includes fresh environment construction in every timed engine call. The baseline calls original `VM.execute` directly; treatments use diagnostics-disabled plans, with separate diagnostics-enabled validation proving actual mechanisms and rejecting primary-case fallback. Preparation is measured separately. The old jar can precede the current harness jar on the classpath for the direct-baseline comparison. Run JMH explicitly because the Gradle include filter omits these classes. Commands and fixed cases: `development/evidence/vm-slots-fusion-experiment/harness.md`; exact measurement arguments: `commands.json` in that directory. Report time, GC allocation B/op, fresh-process post-GC heap-used, and peak RSS separately. Weak references establish wrapper collection only; forced-GC stages are not timing results. Performance acceptance requires completed raw measurements against `development/planning/vm-slots-fusion-experiment.md`; correctness alone establishes no speed or memory advantage.
- Completed factorial acceptance: `development/evidence/vm-slots-fusion-experiment/README.md` records 797 passing tests, one existing skip, 1,016 parent comparisons, 50 exploratory JMH states, 15 longer confirmation states and 30 memory processes. The apparent short-run reduction gain did not repeat; default promotion is rejected. Keep the exploratory and confirmation datasets separate and use `confirmation-command.json` for the longer repeat. Source/jar freeze checks are in `freeze-verification.json`.
- Prepared-block changes: run the separate gate/probe below for both prepared modes. Preserve raw results, final environment/insertion order and original-PC snapshots through errors, host callbacks, dynamic tracing, custom nested containers, loop restoration and reuse after failure. Include source-array ownership, malformed operand JVM/JS parity, custom JVM bignum observation, and same-VM reentrancy rejection through ordinary fallback and tracer closing. Independent probes and review are `development/evidence/vm-prepared-block-experiment/PreparedBlockAcceptance.java` and `independent-review.md`; accepted gate evidence is `gate5.log`, `test-counts-final.json`, `parent-acceptance-final.log` and `fixture-validation-final.log` in that directory. Structural ownership does not prove deep immutability of shared constant values or IR trace nodes, and clearing the reusable frame does not establish that canonical VM last-state references were released.
- Prepared-block measurements: validate `preparedBlockExecuted`, `preparedBlockOptimized` and fallback reason outside timing. Primary prepared cases must run the new dispatch without fallback; optimized cases must execute lowered work and have positive static eligible-site counts. Static counts are not dynamic instruction counts. `VMPreparedBlockBenchmark` times identical fresh environments plus reusable execution across interpreter, direct baseline, prepared control and prepared optimized; both new modes use the same runner, with no diagnostic report/counter allocation solely for timing. Use two forks, 3 x1 s warmup and 5 x1 s measurements, GC profiling and a reverse-order large repeat; measure source-to-prepared cost separately. The parent serial runner is `development/evidence/vm-prepared-block-experiment/measure.py`, with bindings in `environment.json` and `final-source-manifest.json`; it writes exact `commands.json`, `forward.json`, `reverse.json`, `preparation.json`, `fallback.json`, and three fresh-process memory repeats per engine/lifetime. The explicit reuse/many memory roots, weak-wrapper limits and external RSS protocol are in `harness.md` in that directory. Keep B/op, approximate post-GC heap-used and peak RSS distinct. Performance acceptance requires completed raw data against `development/planning/vm-prepared-block-experiment.md`; the correctness gate alone makes no performance claim.
- JVM loop-body changes: focused coverage is `./gradlew :vm:jvmTest --tests '*LoopBodyPlanTest' --tests '*LoopBodyRuntimeTest' --tests '*LoopBodyObservationTest'`. Run the broad JVM/Node gate below for shared VM lifecycle changes. These tests cover independent comprehension shapes, bounded/unsupported/malformed plans, owned source structure, empty-loop status, generated native backedges/fixed helpers without opcode switches, exact errors/partial builders/PCs/snapshots, overflow/null/shadowing, custom collection/numeric callbacks, dynamic tracing and non-reentrancy through fallback/tracer closing. Independent parent checks are `development/evidence/vm-loop-body-experiment/LoopBodyAcceptance.java`; accepted raw evidence is `gate1.log`, `test-counts.json`, `parent-acceptance.log`, `fixture-validation.log` and `old-jar-validation.log` in that directory. JVM code generation requires the pinned ASM dependency; Node gates protect common behavior and establish no generated-code performance.
- JVM loop-body measurements: `VMLoopBodyBenchmark` compares interpreter, direct VM, prior prepared optimization and generated loops on unchanged fixtures with identical fresh-environment-plus-execution timing. Projection must report actual `loopBodyExecuted`, positive `loopBodyEntries` and generated/lowered counts with no fallback; paths/reduction are explicit controls if unsupported. An eligible empty loop can have zero entries without fallback. Entries count kernel invocations, not rows. Query metadata only outside timing, measure source-to-executor/class definition separately, and use two forks, 3 x1 s warmup, 5 x1 s measurements, GC profiling and reverse-order large confirmation. Profiles are diagnostic samples, not timing evidence. Exact commands and source/JVM bindings are recorded by `development/evidence/vm-loop-body-experiment/measure.py`, `environment.json` and `final-source-manifest.json`; generated-bytecode inspection is in `generated-bytecode.txt` and `kernel-method-sizes.json` in that directory. The parent owns serialized runs and source freeze; acceptance follows `development/planning/vm-loop-body-experiment.md` only after completed raw results.
- Loop-code memory evidence must separate five post-GC stages: primed fixture; all prepared but unexecuted executors; executed executors plus last output; after executor release; and after output/weak-reference release. Use one reused executor versus 1,000 independently prepared executors on projection, three fresh processes per engine/lifetime. Prepared-only heap is distinct from executed state/result-tree retention. Record non-heap pool used/committed bytes (Metaspace/Compressed Class Space where available), loaded/total-loaded/unloaded class counts, generated-class/classfile-byte totals and external peak RSS. One class/loader per eligible plan and no global cache means repeated source preparation includes class definition and possible unloading; classfile bytes are not loaded metaspace size. Wrapper collection does not prove generated-class unloading or exact graph reclamation. Reproduction and caveats: `development/evidence/vm-loop-body-experiment/harness.md`.
- JVM-local loop changes: focused coverage is `./gradlew :vm:jvmTest --tests '*LocalLoopPlanTest' --tests '*LocalLoopObservationTest'`. Check varied filtered/unfiltered scalar/object shapes, actual primitive/local execution, raw Int/Long/BigInteger boundaries, pre-opcode spill snapshots after prior fields/rows, error/reuse, exact standard/access-order/collision-map lookups, custom numeric/container callbacks, NEXT failures, branch Exit PCs, malformed/bounded plans and source ownership. Unsupported/guarded values must resume the current opcode once with canonical state and no replay. Run the broad gate below for shared VM lifecycle/packaging/docs. Independent acceptance is `development/evidence/vm-local-loop-experiment/LocalLoopAcceptance.java`; observed logs are `final-gate.log`, `parent-acceptance1.log` and `fixture-validation1.log` in that directory. Generated inspection is `generated-bytecode.txt` and `kernel-method-sizes.json`; static site counts are not runtime proof.
- Local-loop timing uses unchanged fixtures and identical fresh environments across interpreter, direct VM, previous loopBody and localLoop. Projection setup requires actual local entry and primitive use, positive static local/primitive sites and no fallback. Paths/reduction and closure fallback must be labelled by actual mode. Use two forks, 3 x1 s warmup, 5 x1 s measurements, GC B/op and reverse-order large confirmation; source-to-executor/class definition is separate. `development/evidence/vm-local-loop-experiment/harness.md` provides exact benchmark/probe commands and the same five-stage heap/non-heap/class/RSS protocol; `measure.py` in that directory records serialized arguments and raw outputs after source freeze. No performance acceptance follows from successful correctness/packaging; it requires completed measurements against `development/planning/vm-local-loop-experiment.md`.
- Local artifact smoke: use `development/evidence/vm-local-loop-experiment/packaging-protocol.md` and saved `artifact-smoke.sh` / `artifact-oracles.py`. Current JVM/Node source, compiled and crossed current-artifact executions passed; the corrected JSON oracle treats omitted default `version` as 1, matching `CompiledArtifact` serialization. `artifact-oracles.log` records class major 65, npm package `v0.11.0-SNAPSHOT`, CLI output `Branchline CLI v0.10.0` and false release-version alignment. Packaging success does not close stale CLI-version or Java-17-documentation versus Java-21-target alignment, old/new compiled-format compatibility, experimental API/dependency scope, registry or CI/browser release gates. The release inventory in that directory distinguishes these remaining decisions from accepted local fixes.

The experimental slots/fusion correctness gate and independent parent probe are reproducible from the repository root:

```bash
./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar --console=plain
mkdir -p /tmp/branchline-vm-factorial/acceptance
javac -proc:none -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar -d /tmp/branchline-vm-factorial/acceptance development/evidence/vm-slots-fusion-experiment/FactorialAcceptance.java
java -cp /tmp/branchline-vm-factorial/acceptance:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar FactorialAcceptance
java -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar io.github.ehlyzov.branchline.vm.VMFactorialProbe validate
```

The separate prepared-block gate and independent parent probe are reproducible from the repository root. Run them before freezing sources for serialized measurements:

```bash
./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar --continue --console=plain
mkdir -p /tmp/branchline-vm-prepared/acceptance
javac -proc:none -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar -d /tmp/branchline-vm-prepared/acceptance development/evidence/vm-prepared-block-experiment/PreparedBlockAcceptance.java
java -cp /tmp/branchline-vm-prepared/acceptance:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar PreparedBlockAcceptance
java -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar io.github.ehlyzov.branchline.vm.VMPreparedBlockProbe validate
```

The loop-body correctness gate and independent JVM probe are reproducible before source freeze:

```bash
./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar --continue --console=plain
mkdir -p /tmp/branchline-vm-loop-body/acceptance
javac -proc:none -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar -d /tmp/branchline-vm-loop-body/acceptance development/evidence/vm-loop-body-experiment/LoopBodyAcceptance.java
java -cp /tmp/branchline-vm-loop-body/acceptance:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar LoopBodyAcceptance
java -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar io.github.ehlyzov.branchline.vm.VMLoopBodyProbe validate
```

The JVM-local loop final gate includes interpreter parity, CLI distributions and documentation before source freeze:

```bash
./gradlew :vm:jvmTest :vm:jsNodeTest :conformance-tests:jvmTest :conformance-tests:jsNodeTest :interpreter:jvmTest :interpreter:jsNodeTest :cli:jvmTest :cli:jsNodeTest :vm-benchmarks:jmhJar :cli:blShadowJar :cli:packageJsCli docsBuild --continue --console=plain
mkdir -p /tmp/branchline-vm-local-loop/acceptance
javac -proc:none -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar -d /tmp/branchline-vm-local-loop/acceptance development/evidence/vm-local-loop-experiment/LocalLoopAcceptance.java
java -cp /tmp/branchline-vm-local-loop/acceptance:vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar LocalLoopAcceptance
java -cp vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar io.github.ehlyzov.branchline.vm.VMLocalLoopProbe validate
```

## Required Change Evidence

Every non-trivial change should report:

- whether a contour trigger fired, and if not, why not;
- changed surface summary;
- commands run;
- what was not verified locally;
- risk notes for language behavior, runtime parity, packaging, generated assets, or CI.

## What Cannot Be Verified Locally

- GitHub badge update behavior from `.github/workflows/tests.yml`.
- Package publishing credentials and GitHub Packages release behavior.
- Repository-specific GitHub Pages deployment behavior unless the workflow is run in GitHub Actions.

## v0.11.0-alpha.1 release gate

Use the current canonical version printed by `./gradlew -q printReleaseVersion`
for artifact paths; historical reproduction commands above retain their original
snapshot filenames. Release checks and raw results are recorded under
`development/evidence/release-0.11.0-alpha.1/`.

Run `CHROME_BIN=/path/to/chrome ./gradlew clean build :cli:blShadowJar
:interpreter:packageInterpreterJs :vm-benchmarks:jmhJar docsBuild --continue`.
Inspect every JUnit XML failure/error count: existing test tasks may ignore exit
failures for CI summary generation. When enabling browser tests changes the local
ignored Yarn lock, run `kotlinUpgradeYarnLock` with the same CHROME_BIN environment.
Then run the release artifact smoke, independent numeric observer probe and
`verify-optin.py`; unmarked preview compilation must fail while ordinary execution
and explicitly opted-in consumers compile.

Build MkDocs with pinned `requirements.txt` after playground assets. Publish only
the independently reviewed candidate and check Test Suite CI for that SHA. Tag it,
then dispatch Release Artifacts with that tag, full_release=true and timing jobs
false (existing benchmark evidence is unchanged). Verify asset checksums and
metadata, exact deployed SHA, and actual public-browser transforms/edit/error
recovery plus console/resource loading before calling the release accepted.
