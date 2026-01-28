# Interpreter Performance Tasks (Codex)

This file lists discrete tasks for Codex, each with context, goals, and verification steps. The intent is to improve Branchline interpreter performance in the JSONata benchmark cases. After completing any task, re-run the JFR workflow described in the Verification section to measure impact.

## Task 1: MAP/FILTER/FLATTEN fast paths

Context:
- JFR and stack samples show heavy time in `StdHofModuleKt.fnMAP`, `StdHofModuleKt.fnFILTER`, `StdArraysModuleKt.fnFLATTEN`, plus lambda dispatch (`Exec.handleFuncCall` / `Exec.handleLambda`).
- Slow cases: `simple-array-selectors/case000`, `function-count/case000`, `function-sum/case000`.

Goals:
- Reduce per-element overhead in MAP/FILTER/FLATTEN by avoiding repeated lambda allocation, Env construction, and dynamic list growth.
- Preserve existing semantics and null handling.

Suggestions:
- Pre-size result arrays (e.g., `ArrayList(capacity)`), avoid intermediate lists where possible.
- Special-case lambdas that are known to be pure/inlineable if available (safe only if semantics unchanged).
- Keep the public API unchanged.

Verification:
- Run: `./gradlew :jsonata-benchmarks:jmh` and compare interpreter scores for the listed cases.
- Run JFR sampling (interpreter only) for `simple-array-selectors/case000`, `function-count/case000`, `function-sum/case000`:
  - `java -jar jsonata-benchmarks/build/libs/jsonata-benchmarks-*-jmh.jar io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineBenchmark.evaluate -p engineId=branchline-interpreter -p caseId=simple-array-selectors/case000,function-count/case000,function-sum/case000 -wi 1 -w 500ms -i 3 -r 2s -f 1 -prof jfr -o perf/jfr-long/<case>-jfr.txt`
- Extract top frames with JFR print (see Task 6).

## Task 2: Function call dispatch and lambda invocation

Context:
- JFR shows hotspots in `Exec.handleFuncCall` and `Exec.handleLambda` across slow cases and `performance/case001`.

Goals:
- Reduce dynamic dispatch overhead in the interpreter function call path.
- Avoid redundant map lookups for host functions and reduce boxing where possible.

Suggestions:
- Cache resolved function handles in IR or a lookup table keyed by function id.
- Pre-resolve static host function references if the call target is known at compile time.

Verification:
- Run: `./gradlew :jsonata-benchmarks:jmh` and check interpreter speed for `performance/case001`, `function-sum/case000`, `function-count/case000`.
- JFR sampling for `performance/case001`:
  - `java -jar jsonata-benchmarks/build/libs/jsonata-benchmarks-*-jmh.jar io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineBenchmark.evaluate -p engineId=branchline-interpreter -p caseId=performance/case001 -wi 1 -w 500ms -i 3 -r 2s -f 1 -prof jfr -o perf/jfr-long/performance-case001-jfr.txt`

## Task 3: Property access and object/map construction

Context:
- Samples show `Exec.handleAccess`, `HashMap.getNode`, and `propertiesToMap` in hotspots.

Goals:
- Reduce overhead for property reads and object construction during interpreter execution.

Suggestions:
- Pre-size maps in `propertiesToMap` based on known field counts.
- Reduce string key comparisons by using symbol IDs or interned keys if safe.

Verification:
- Run: `./gradlew :jsonata-benchmarks:jmh` and check interpreter speed for `string-concat/case000`, `fields/case000`, `numeric-operators/case000`.
- JFR sampling for `performance/case001` to see if map-related frames decrease.

## Task 4: Array comprehension and range evaluation

Context:
- `performance/case001` and `range-operator/case000` create large arrays and loop indices.

Goals:
- Avoid constructing intermediate range arrays when used in comprehensions/loops.
- Keep semantics identical.

Suggestions:
- Desugar RANGE loops to indexed for-loops in interpreter execution.
- Avoid `RANGE(0, n)` list allocations when iterating directly.

Verification:
- Run: `./gradlew :jsonata-benchmarks:jmh` and check interpreter speed for `performance/case001` and `range-operator/case000`.
- JFR sampling for `performance/case001`.

## Task 5: Debug/trace checks in hot paths

Context:
- `Debug.getTracer` appears in JFR samples, indicating hot-path debug checks.

Goals:
- Ensure debug/trace checks are fully bypassed when tracing is disabled.
- Avoid repeated volatile reads or allocations in tight loops.

Suggestions:
- Cache tracer references in the interpreter execution loop if safe.
- Ensure any debug helpers are no-ops in the default path.

Verification:
- Run: `./gradlew :jsonata-benchmarks:jmh` and compare interpreter speeds for `performance/case001`.
- Confirm no tracing behavior changes.

## Task 6: Standardize JFR analysis workflow

Context:
- We need consistent evidence for changes.

Goals:
- Provide a consistent, repeatable JFR extraction process.

Verification (workflow):
- Generate JFR with: `-prof jfr` (see tasks above).
- Extract samples:
  - `/Library/Java/JavaVirtualMachines/liberica-jdk-21.jdk/Contents/Home/bin/jfr print --json --events jdk.ExecutionSample --stack-depth 20 <profile.jfr> > perf/jfr/<case>-exec.json`
- Summarize top frames using a script or manual inspection and update `perf/report.md`.

## Progress

- Task 1 (MAP/FILTER/FLATTEN fast paths):
  - Reused a mutable 3-slot argument list for MAP/FILTER and pre-sized FLATTEN with capacity growth to reduce list churn and dynamic resizing.
  - JMH baseline (pre-change) for branchline-interpreter:
    - simple-array-selectors/case000: 17,074.661 ops/s
    - function-count/case000: 19,162.461 ops/s
    - function-sum/case000: 17,864.502 ops/s
  - JMH after change:
    - simple-array-selectors/case000: 18,909.554 ops/s (+10.75%)
    - function-count/case000: 19,198.496 ops/s (+0.19%)
    - function-sum/case000: 18,724.201 ops/s (+4.81%)
  - Net effect: interpreter throughput improved on all three cases; biggest gain in simple-array-selectors.
- Task 2 (Function call dispatch and lambda invocation):
  - Replaced argument list mapping with pre-sized collection and avoided zip allocations for parameter binding in calls/lambdas.
  - JMH baseline (pre-change) for branchline-interpreter:
    - performance/case001: 688.462 ops/s
    - function-sum/case000: 16,673.914 ops/s
    - function-count/case000: 17,313.300 ops/s
  - JMH after change:
    - performance/case001: 696.959 ops/s (+1.23%)
    - function-sum/case000: 18,091.117 ops/s (+8.50%)
    - function-count/case000: 19,452.802 ops/s (+12.36%)
  - Net effect: interpreter throughput improved across all three cases.
- Task 3 (Property access and object/map construction):
  - Pre-sized object and property maps based on field counts to reduce growth overhead.
  - JMH baseline (pre-change) for branchline-interpreter:
    - string-concat/case000: 28,711.926 ops/s
    - fields/case000: 25,447.772 ops/s
    - numeric-operators/case000: 22,633.035 ops/s
  - JMH after change:
    - string-concat/case000: 27,194.529 ops/s (-5.28%)
    - fields/case000: 28,234.726 ops/s (+10.95%)
    - numeric-operators/case000: 26,415.076 ops/s (+16.71%)
  - Net effect: improvements in fields/numeric-operators, slight regression in string-concat.
- Task 4 (Array comprehension and range evaluation):
  - Pre-sized RANGE output based on computed length to reduce allocation growth in range-heavy cases.
  - JMH baseline (pre-change) for branchline-interpreter:
    - performance/case001: 767.579 ops/s
    - range-operator/case000: 22,890.288 ops/s
  - JMH after change:
    - performance/case001: 652.753 ops/s (-14.96%)
    - range-operator/case000: 25,682.958 ops/s (+12.20%)
  - Net effect: range-operator improved, performance/case001 regressed.
- Task 5 (Debug/trace checks in hot paths):
  - Cached tracer resolution during execution to avoid repeated Debug.tracer lookups in hot paths.
  - JMH baseline (pre-change) for branchline-interpreter:
    - performance/case001: 741.443 ops/s
  - JMH after change:
    - performance/case001: 767.896 ops/s (+3.57%)
  - Net effect: modest improvement on performance/case001.
- Task 6 (Standardize JFR analysis workflow):
  - Added `perf/scripts/jfr_extract.sh` to standardize extracting `jdk.ExecutionSample` stacks with consistent depth.
  - No runtime performance change; no JMH delta recorded.
