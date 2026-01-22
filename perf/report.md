# JSONata benchmarks investigation

Timestamp (UTC): 2026-01-20T17:28:52Z

## Scope
- Focus: Branchline interpreter performance vs JSONata (Dashjoin), plus reasons for divergence vs published snapshot.
- JMH suite: `jsonata-benchmarks` CrossEngineBenchmark (local fixtures).
- JFR/stack sampling: interpreter only; VM ignored.

## Commands run (local)
- `./gradlew :jsonata-benchmarks:jmh`
- `java -jar jsonata-benchmarks/build/libs/jsonata-benchmarks-v0.11.0-SNAPSHOT-jmh.jar ... -prof stack`
- `java -jar jsonata-benchmarks/build/libs/jsonata-benchmarks-v0.11.0-SNAPSHOT-jmh.jar ... -prof jfr`
- `jfr print --json --events jdk.ExecutionSample --stack-depth 20 <profile.jfr>`

## Key findings
- Published snapshot results are inflated for JSONata suite cases because CI used a Dashjoin jar path as `DASHJOIN_REPO`, so the JSONata test suite was not found and cases fell back to the error path.
- Local results (using embedded test suite) show realistic throughput around ~1e5 ops/s for suite cases and ~3e4 ops/s for inline cases.
- Dashjoin anomaly filter (0.01x-100x median) excludes `performance/case001` as an outlier.

## Dashjoin-only losses (anomalies excluded)
Interpreter slower than Dashjoin:
- `string-concat/case000` 0.926x
- `simple-array-selectors/case000` 0.948x
- `function-count/case000` 0.954x
- `function-sum/case000` 0.969x
- `function-zip/case002` 0.989x
- `range-operator/case000` 0.995x

## Interpreter faster than Dashjoin (top cases, anomalies excluded)
- `partial-application/case002` 2.160x
- `function-sift/case004` 1.255x
- `hof-zip-map/case000` 1.226x
- `hof-map/case000` 1.170x
- `summary_stats` 1.109x
- `sum_prices` 1.093x
- `map_discount` 1.084x
- `numeric-operators/case000` 1.051x

## Interpreter hotspots (JFR ExecutionSample)
`performance/case001` (dominant slow case):
- `io.github.ehlyzov.branchline.ir.Exec.evalExpr`
- `io.github.ehlyzov.branchline.ir.Exec.handleFuncCall`
- `io.github.ehlyzov.branchline.std.StdHofModule$register$2.invoke`
- `io.github.ehlyzov.branchline.ir.Exec.handleArrayComp`
- `io.github.ehlyzov.branchline.ir.Exec.handleBinary`
- `io.github.ehlyzov.branchline.ir.Exec.handleObject`
- `io.github.ehlyzov.branchline.ir.Exec.propertiesToMap$lambda$0$0$0`

`simple-array-selectors/case000`:
- `StdHofModuleKt.fnMAP`
- `Exec.handleFuncCall`
- `Exec.evalExpr`
- `Exec.handleLambda$lambda$1`

`function-count/case000`:
- `Exec.evalExpr`
- `StdHofModuleKt.fnMAP`
- `Exec.handleFuncCall`
- `Exec.handleLambda$lambda$1`

`function-sum/case000`:
- `StdHofModuleKt.fnMAP`
- `Exec.evalExpr`
- `Exec.handleFuncCall`
- `Exec.handleLambda$lambda$1`

Notes:
- Small cases (e.g., `string-concat/case000`) showed mostly harness/timeout-thread samples; not enough runtime to capture useful interpreter frames.

## Artifacts
- JMH results: `perf/jmh/results-baseline.json`
- JFR/stack logs: `perf/interpreter-stack.txt`, `perf/interpreter-jfr-performance.txt`
- JFR JSON: `perf/performance-exec.json`
- JFR extracts: `perf/jfr/`, `perf/jfr-long/`
- Raw JFR recordings: `perf/jfr-recordings/`
