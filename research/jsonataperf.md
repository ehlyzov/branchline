# Goal
Make a benchmark to compare the performance of JSONata engines with Branchline
(interpreter + VM) and a pure Kotlin baseline.

# Current module snapshot (jsonata-benchmarks/)
- `jsonata-benchmarks/build.gradle` configures JMH and loads dashjoin/IBM jars
  from repo paths supplied via `-PdashjoinRepo`/`DASHJOIN_REPO` and
  `-PibmJsonataRepo`/`IBM_JSONATA_REPO` (plus optional `.env`). Hardcoded
  defaults are removed; missing paths are logged and engines are skipped so the
  module still compiles. JMH run length is configurable via Gradle properties,
  with fast defaults (1 warmup, 1 iteration, 200ms warmup/measurement).
- `JsonataEngineBenchmark` runs parse + evaluate for dashjoin and IBM engines
  across all JSONata test suite cases (scanned from `groups/`), with a
  `jsonata.caseFilter` override to run a subset.
- `JsonataTestSuite.loadCase` reads `groups/<group>/<case>.json`, captures
  expected-failure metadata (`code`, `undefinedResult`, `timelimit`, `depth`,
  `bindings`), and resolves datasets from `datasets/`.
- `CrossEngineBenchmark` reads a YAML case matrix and iterates all entries per
  engine across Kotlin, Branchline interpreter/VM, and JSONata engines.
- `JsonataCaseMatrix` loads
  `jsonata-benchmarks/src/jmh/resources/jsonata-case-matrix.yaml` to map JSONata
  case IDs to Branchline/Kotlin analogs and expected-failure flags.
- `BenchmarkTimeoutRunner` wraps per-case evaluation to avoid hangs and disables
  cases on timeout/error (default 500ms, configurable via
  `-Djsonata.benchTimeoutMs`; set to `0` to disable).
- `BranchlineCompiler` compiles buffer-mode transforms only and uses `StdLib`
  host functions for interpreter/VM runs.

# Setup
- Kotlin 2.3.0 with JVM toolchain 21 (override per module in
  `jsonata-benchmarks/build.gradle`).
- JSONata test suite is sourced from dashjoin's repo (see inputs below).
- Dashjoin and IBM JSONata engines are loaded via reflection at runtime.

# JSONata test suite inputs
Set one of the following; there is no committed default path:
- `-Djsonata.testSuiteRoot=/path/to/test-suite`
- `JSONATA_TEST_SUITE_ROOT=/path/to/test-suite`
- `DASHJOIN_REPO=/path/to/jsonata-java` (uses `jsonata/test/test-suite` subdir)
You can also place these in `.env` (uncommitted).

# JSONata engines
Set repository paths for jar discovery; do not commit local paths:
- Dashjoin: `-PdashjoinRepo=/path/to/jsonata-java` or `DASHJOIN_REPO=...`
- IBM: `-PibmJsonataRepo=/path/to/JSONata4Java` or `IBM_JSONATA_REPO=...`
`.env` is supported for these keys as well.

Both engines must be built so their jars are on the runtime classpath. Example:
```
cd /path/to/jsonata-java && mvn -q -DskipTests package
cd /path/to/JSONata4Java && mvn -q -DskipTests package
```

# JMH configuration
Defaults are tuned for quick runs. Override with Gradle properties:
- `-PjmhWarmupIterations=1`
- `-PjmhWarmupTime=200ms`
- `-PjmhIterations=1`
- `-PjmhTimeOnIteration=200ms`
- `-PjmhForks=1`
- `-PjmhProfilers=gc` (comma-separated)
- `-PjmhCaseIds=caseA,caseB` to run a subset of YAML cases (default: all YAML ids)
- `-Djsonata.caseFilter=caseA,caseB` to override case IDs at runtime
- `-Djsonata.benchTimeoutMs=500` to bound per-case evaluation (0 disables)

# Methodology (dashjoin-style)
- Parse benchmark compiles JSONata expressions only.
- Evaluate benchmark reuses compiled expressions and pre-parsed inputs.
- JMH reports ops/sec; divide by 1,000 to match dashjoin's kiloOps/sec figures.
- Engine/case failures are recorded to the error report and skipped so the
  remaining engines still emit results.

# JSONata case coverage
`JsonataEngineBenchmark` scans all JSONata test suite cases under `groups/`.
To narrow runs, set `-Djsonata.caseFilter=caseA,caseB` (or use the JMH
`caseFilter` parameter).

# Cross-engine cases (JSONata vs Branchline vs Kotlin)
Equivalence cases are defined in:
`jsonata-benchmarks/src/jmh/resources/jsonata-case-matrix.yaml`.

Each case provides:
- JSONata test suite ID (default) or inline JSONata + input JSON.
- Branchline program (interpreter + VM).
- Kotlin evaluator ID (resolved in code).
- Optional `expectedFailures` map keyed by engine ID.
The current YAML seeds the original custom cases plus the initial JSONata suite
IDs, and now includes Branchline/Kotlin mappings for every listed entry. Full
suite coverage is still pending.
Errors are reported to:
`jsonata-benchmarks/build/reports/jsonata-benchmarks/error-report.log`.

# Run
```
./gradlew :jsonata-benchmarks:jmh
```

Results are emitted to:
`jsonata-benchmarks/build/results/jmh/results.json`

# Flaws in the task description
- It implied default local paths for repos and test suite; this is not
  acceptable for committed docs or build scripts.
- It does not state that current coverage is a curated subset; the cross-engine
  benchmark is based on custom cases rather than the JSONata suite.
- It omits that many JSONata cases are excluded (errors, undefinedResult,
  timelimit, depth, bindings), which affects coverage expectations.
- It does not define how "four engines" should be interpreted (JSONata engines
  vs Branchline interpreter/VM vs Kotlin baseline).

# Updated requirements (feedback)
- Each JSONata test case should be exercised across all required engines:
  dashjoin, IBM, Branchline interpreter, Branchline VM, and Kotlin (Kotlin is
  part of the suite; Branchline interpreter is the baseline).
- Include error/undefined cases with expected failures instead of skipping.
- Semantically equivalent JSON outputs are acceptable for validation.
- Keep the case matrix in YAML and maintain it manually.
- Surface expected failures via a separate report file.
- Validation compares cross-engine outputs only (ignore JSONata `result` values).
- Never hardcode local repository paths in source control. Use env vars/Gradle
  properties or dotenv, but do not commit them.

# Plan (approved)
- Step 1 (done): Remove hardcoded defaults for repo/test suite paths and update
  docs to require explicit config.
- Step 2 (done): Make JMH tuning configurable with fast defaults.
- Step 3 (in progress): Define a YAML case matrix that maps every JSONata test
  suite case to a Branchline analog and Kotlin version, plus expected-failure
  flags (seeded cases now have analogs; full-suite mapping pending).
- Step 4 (done): Expand `CrossEngineBenchmark` to read the YAML matrix and run
  dashjoin, IBM, Branchline interpreter, Branchline VM, and Kotlin for each
  case.
- Step 5 (done): Add expected-failure handling (errors/undefined/bindings/
  depth/timelimit/unsupported) and surface it in results instead of skipping
  (errors are logged per engine/case).
- Step 6 (pending): Add semantic-output validation (normalized JSON) before
  benchmarking; document equivalence rules.
- Step 7 (done): Document local path configuration (env/Gradle properties,
  optional dotenv) and ensure `.env` is ignored.
- Step 8 (done): Guard per-case execution with a timeout and disable failing
  cases to keep JMH runs finite.
- Step 9 (done): Run CrossEngineBenchmark per YAML case by default so each task
  gets its own JMH result entry.

# Progress log
- Step 1: Reviewed module files and documented current behavior (why: establish
  baseline for plan).
- Step 1: Removed hardcoded repo/test-suite defaults and clarified configuration
  requirements (why: avoid committing local paths).
- Step 2: Made JMH tuning configurable with fast defaults (why: keep full-suite
  runs quick while still allowing longer runs).
- Step 3: Added a YAML case matrix and loader seeded with current cases (why:
  establish a single mapping source and make coverage gaps explicit).
- Step 4: Refactored `CrossEngineBenchmark` to read the YAML matrix, apply the
  case filter, and execute all engines per entry (why: align with required
  engine set).
- Step 5: Captured expected-failure metadata from the JSONata test suite and
  allowed expected failures in benchmarks (why: include error/undefined cases
  without hard skips).
- Step 5: Added per-engine error capture and a report file, so failures are
  logged and the run continues (why: ensure all engines emit results).
- Step 7: Added dotenv support in build/runtime path resolution and ignored
  `.env` in git (why: keep builds working without committed paths).
- Step 3: Filled Branchline/Kotlin analogs for every YAML entry (why: enforce
  all-engine coverage for the defined case set).
- Step 2: Added a configurable warmup time via `jmh.warmup` (why: eliminate the
  default 10s warmup and keep runs fast).
- Step 8: Added a per-case timeout (default 500ms) and disable-on-error behavior
  (why: keep long or stuck cases from stalling the suite).
- Step 9: Parameterized CrossEngineBenchmark with `caseId` and defaulted the JMH
  parameters to all YAML cases (why: produce per-task comparisons instead of a
  single aggregate).

# Branchline issues and notes
- Array comprehension syntax differs from the JSONata-style examples; Branchline
  supports `[expr FOR EACH name IN list]`, not `[FOR (name IN list) => expr]`.
  We now use `MAP`/`RANGE` to avoid unsupported syntax.
- Some JSONata functions (`$zip`, `$sift`, partial application) have no direct
  Branchline equivalents; the analogs are hand-written and may diverge in edge
  cases.
- Array-root datasets (ex: `dataset4`) require Branchline programs that operate
  on `input` as a list; object-only assumptions break those cases.
- Branchline analogs rely on `StdLib` functions (`MAP`, `FILTER`, `FLATTEN`,
  `RANGE`, `ZIP`, `SUM`, `COUNT`); any missing semantics or ordering differences
  will show up as cross-engine mismatches.
- Branchline VM warns and falls back to interpreter when hitting non-terminating
  decimal expansions (seen during the VM benchmark). This likely depresses VM
  throughput and should be traced to the specific case(s) that trigger it.
- Branchline VM throughput is substantially below the interpreter in the latest
  run (713 ops/s vs 6587 ops/s), suggesting fallback/overhead dominates current
  VM performance for these cases.

# Latest run (2026-01-06)
Command:
```
./gradlew :jsonata-benchmarks:jmh
```

Config:
- Warmup/measurement: 1 iteration, 200ms each.
- Per-case timeout: 500ms (default).

Results (ops/sec, from `jsonata-benchmarks/build/results/jmh/results.json`):
- CrossEngineBenchmark.evaluate (kotlin): 1349.548
- CrossEngineBenchmark.evaluate (branchline-interpreter): 6587.212
- CrossEngineBenchmark.evaluate (branchline-vm): 713.000
- CrossEngineBenchmark.evaluate (dashjoin): 2.368
- CrossEngineBenchmark.evaluate (ibm): 478.412
- JsonataEngineBenchmark.evaluate (dashjoin): 1.377
- JsonataEngineBenchmark.evaluate (ibm): 1.233
- JsonataEngineBenchmark.parse (dashjoin): 5.479
- JsonataEngineBenchmark.parse (ibm): 4.778

Notes:
- Dashjoin logs `Proc not found 2` during evaluation; it does not throw, so the
  error reporter does not capture it. We should decide how to treat stdout
  errors from dashjoin and whether to convert them into recorded failures.
- No error report file was produced for this run (no exceptions were thrown
  during case evaluation).

# Next steps (detailed)
- Identify which case(s) trigger VM fallback: rerun with
  `-Djsonata.caseFilter=caseId` for each YAML entry until the
  "Non-terminating decimal expansion" warning appears, then capture the case ID
  and data set. Decide whether to fix VM numeric handling or mark expected
  failure for VM in the YAML.
- Capture dashjoin stdout errors: find where dashjoin prints
  "Proc not found 2" and decide if this is an error path; if yes, wrap the
  dashjoin engine evaluation to detect this output and record it via
  `BenchmarkErrorReporter` so it shows up in the error log.
- Produce a per-case error report: update `BenchmarkErrorReporter` to always
  create `jsonata-benchmarks/build/reports/jsonata-benchmarks/error-report.log`
  and include any timeouts, compile errors, or evaluation errors.
- Expand the YAML to cover the full JSONata test suite: for each JSONata case,
  add a Branchline analog and Kotlin evaluator (or mark expected failure),
  so the cross-engine benchmark truly exercises all tests.
- Add semantic-output validation: normalize outputs (ordering, numeric formats,
  null/undefined handling) and compare engine outputs before timing; document
  the normalization rules and the output comparison mode.
- Run longer baselines: increase warmup and measurement (for example 5x 1s) and
  keep the per-case timeout in place to produce stable numbers without hangs.
