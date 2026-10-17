# Compiler-prepared block harness

Fixed primary matrix: `paths`, `reduction`, `holdoutProjection`, each at `small`
and `large`, with `interpreter`, `baseline`, `preparedControl`, and
`preparedOptimized`. Sources, resident input and independent expected values
come directly from the unchanged `VMFactorialFixture`. Small is 10 orders x5
items; large is 500 x25. `fallbackClosure` is a separate explicit unsupported
control and is excluded from optimized-workload performance claims.

The execution benchmark prepares source/IR/bytecode/block plans outside timing.
Each measured operation creates the same fresh environment through the fixture,
then executes and returns the raw result for JMH consumption. There is no
Level.Invocation setup, serialization or per-operation harness metadata call.
The baseline directly invokes `VM.execute`; it never prepares or invokes the
new runner. Both prepared modes use the same new runner, with optimizations
selected before execution. `VMPreparedBlockPreparationBenchmark` separately
measures source parsing through construction of a reusable executor, excluding
fixture/environment construction and execution.

Validation checks three raw outputs against independent expected values and
checks input equality against a freshly generated pristine fixture. Every
primary prepared mode must report that the new runner actually executed, with
no fallback. Optimized primary cases additionally require an actual executed
optimization plus a positive prepared direct-path or mirrored-store site count.
Static eligible-site counts are not executed instruction counts. Control mode
must not report optimized execution. Closure control requires ordinary-VM
fallback and neither prepared nor optimized execution. Metadata is queried only
outside timing; the runner uses scalar effective-mode fields and introduces no
diagnostic counter/report allocation solely for these measurements.

JMH annotations fix one thread, AverageTime microseconds, two JVM forks,
3 x1 second warmup and 5 x1 second measurements, heap 256--768 MiB. Always add
`-prof gc` and capture exact command/source/jar/JVM bindings. The parent owns all
Gradle, JVM validation, JMH and memory processes; run CPU-heavy work serially.
Repeat the large decision in reverse engine order. This harness does not modify
the previous factorial harness or enable experimental execution by default.

Fresh-process memory commands use `reuse` (one independently prepared executor,
executed `count` times) and `many` (`count` independently parsed/prepared
executors, executed once each). Both share one fixture/input graph and retain
only the last output separately. Thus `many` measures independent plans, not
many wrappers sharing one plan. The explicit static roots progress from fixture
only after class priming to fixture + executors + weak references + last output,
then release executors, then release output/weak references. The fixture retains
input, source and expected values throughout. Primitive/string metadata retains
no executor, plan, environment or execution graph.

Post-GC heap-used and differences are approximate process heap observations,
not exact retained graph sizes. Weak references establish wrapper collection
only. The runner's frame/last-environment ownership is included wherever reachable
from a retained executor. Use three fresh JVM repetitions per engine/lifetime
with identical heap/JVM flags and external `/usr/bin/time -l` peak RSS. macOS
maximum resident set size is bytes and includes JVM/native/code memory and
cumulative peaks. Keep allocation B/op, post-GC heap-used and peak RSS separate;
forced-GC stages are not timed samples or evidence of minimum required heap.

Reproduction (substitute the exact JAVA executable and built JAR):

```bash
./gradlew :vm-benchmarks:jmhJar
JAVA -cp JAR io.github.ehlyzov.branchline.vm.VMPreparedBlockProbe validate
JAVA -jar JAR '.*VMPreparedBlockBenchmark.execute' -prof gc -rf json -rff blocks.json
JAVA -jar JAR '.*VMPreparedBlockPreparationBenchmark.prepare' -p workload=reduction,holdoutProjection -p dataset=large -prof gc -rf json -rff preparation.json
JAVA -jar JAR '.*VMPreparedBlockBenchmark.execute' -p workload=fallbackClosure -p dataset=large -prof gc -rf json -rff fallback.json
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMPreparedBlockProbe memory preparedOptimized reduction large reuse 1000
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMPreparedBlockProbe memory preparedOptimized reduction large many 1000
```

The Gradle default JMH include filter omits these new classes, so select their
names explicitly. For independent custom acceptance, the public JVM-callable
`VMPreparedBlockHarness.prepareSource(source, engine)` returns an executor with
raw `execute(env)` and detached scalar `metadata()`. The probe also exposes
`validateSource(source, input, expected, engine)` for caller-supplied raw expected
values; this is separate from the fixed primary cases and does not replace
parent-owned acceptance.
