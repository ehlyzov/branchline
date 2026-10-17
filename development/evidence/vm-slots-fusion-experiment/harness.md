# Matched factorial harness

This preregistered harness adds new files only. Existing native benchmarks and
program constants remain unchanged. Parent chat owns all Gradle/JMH runs.

Primary cases, fixed before timings: `paths`, `comprehension`, `reduction`,
`holdoutProjection`, and `holdoutDeepPaths`, each at `small` and `large`.
The first three reuse `VMTransformPrograms` verbatim and `BenchDatasets`.
The projection holdout filters orders and combines arithmetic, object creation,
and item paths. The deep-path holdout wraps the same native input in three
ordinary LinkedHashMap objects and reads both first/last elements. Wrapper map
classes were fixed before timing to match ordinary materialized JSON maps.
These test different instruction
mixes; none is selected from timing results. `fallbackClosure` is a separate
unsupported control, excluded from supported-workload performance claims.

Execution uses one prepared program and one resident Map/List input per JMH
trial. Every measured call creates the same fresh HashMap environment through
`BenchDatasets.buildEnv` and executes/consumes the raw result. Parsing, IR,
bytecode and experimental-plan preparation are excluded. Environment creation
is INCLUDED in both elapsed time and `gc.alloc.rate.norm`; unlike the existing
native harness, there is no Level.Invocation setup. Results must not be pooled
with existing native benchmark absolute times. `VMFactorialPreparationBenchmark`
separately measures source parsing through construction of a reusable executor;
it excludes execution and input/environment creation.
The baseline directly calls `VM.execute(bytecode, env)` and does not construct an
experimental plan or update diagnostic reports. Its preparation boundary ends at
native bytecode + VM construction. This baseline branch uses no new VM API, so
the same harness can run with the preserved before jar first on the classpath.

Each setup checks three raw outputs against independent expected values and
checks input equality against a freshly generated pristine fixture. The probe
prints requested/effective modes, fallback reason and actual mechanism counts
after separate diagnostics-enabled executions. Timed execution and preparation
use `collectDiagnostics=false`; the memory roots also use diagnostics-disabled
executors. The memory output includes a nested untimed diagnostic report.
Effective mechanisms require positive counters during validation. Zero mechanism
counts and explicit fallback must remain visible;
an optimized flag alone is not proof that its mechanism executed.
Every requested primary treatment must report its effective mechanism and no
fallback; setup fails otherwise. The closure control requires explicit fallback
and neither effective optimization.

Default JMH annotations: AverageTime, microseconds, one thread, two JVM forks,
2 x 500 ms warmup, 4 x 500 ms measurements, `-Xms256m -Xmx768m`. Explicitly add
`-prof gc`; the repository Gradle include filter omits these new benchmarks.
Parent should capture exact commands/source/jar hashes and run engines serially.
For a bounded preparation run, override `-p workload=reduction -p dataset=large`;
the full execution matrix is 50 states, while fallback is reported separately.

The fresh-JVM memory probe has two lifetimes. `reuse` retains one executor and
executes it `count` times. `many` retains `count` independently parsed/prepared
executors, executing each once. Both share one input graph and retain only the
last output. Stages use explicit static roots: fixture only after class priming;
fixture + executor(s) + weak references + final output; fixture + weak references
and final output after executors release; fixture only after output/reference
release. The fixture includes resident input, source and expected output.
Post-GC heap-used and deltas are approximate
process heap readings, not exact graph retained sizes. Weak references report
only collection of executor wrapper objects. Use `/usr/bin/time -l` in separate
fresh processes to capture peak RSS (macOS maximum resident set size is bytes).
RSS includes JVM/native/code memory and cumulative peaks, so it is distinct from
post-GC heap-used and JMH allocation B/op. Forced-GC stages are not timing data.

Commands (replace JAR/JAVA with the exact parent-owned built artifact/runtime):

```bash
JAVA -cp JAR io.github.ehlyzov.branchline.vm.VMFactorialProbe validate
JAVA -jar JAR '.*VMFactorialBenchmark.execute' -prof gc -rf json -rff factorial.json
JAVA -jar JAR '.*VMFactorialPreparationBenchmark.prepare' -prof gc -rf json -rff preparation.json
JAVA -jar JAR '.*VMFactorialBenchmark.execute' -p workload=fallbackClosure -prof gc -rf json -rff fallback.json
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMFactorialProbe memory both reduction large reuse 1000
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMFactorialProbe memory both reduction large many 1000
```

Use identical fresh-process heap arguments for every engine/lifetime/count;
repeat readings before interpreting small deltas. The probe validates outputs
outside the GC samples. Parent owns independent acceptance beyond this harness.
