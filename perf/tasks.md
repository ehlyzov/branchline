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

