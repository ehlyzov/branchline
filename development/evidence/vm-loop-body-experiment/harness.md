# JVM whole-loop-body harness

Fixed primary sources, inputs and independent raw expected values come from the
unchanged `VMFactorialFixture`: `paths`, `reduction`, `holdoutProjection`, each at
small (10 orders x5 items) and large (500 x25). Four engines: `interpreter`,
`baseline` (direct ordinary VM), `preparedOptimized` (the previous runner), and
`loopBody`. `fallbackClosure` is a separate unsupported control.

Projection must actually enter generated code: validation requires a true
executed flag, positive kernel-entry count, positive lowered-loop/body counts,
one generated class, and no fallback. The entry count is per generated-kernel
entry, not per row or source instruction. Paths are a non-loop fallback control;
reduction may explicitly fall back if outside the bounded compiler shape.
Fallback must report its reason with no claimed generated execution. A partial
deoptimization must remain visible even if generated work occurred first. No
successful fallback result is counted as loop-body optimization.

Every execution setup checks three raw results and pristine input. The timed
operation creates the same fresh environment, executes the reusable engine and
returns its raw result for JMH consumption. Source parsing, preparation, JSON
serialization and metadata reads are excluded; runtime guards and all actual
execution costs are included. There is no invocation-level setup or harness
counter/report allocation in the timed method. Preparation is measured separately
from source through construction of the reusable executor, including ASM class
generation/loading for eligible plans. There is no global generated-class cache.
Class definition, preparation-path JIT effects and any GC/class unloading belong
to preparation measurements; repeatedly preparing the same source is not a
generated-code cache-hit measurement.

The baseline branch delegates to the unchanged old harness's direct VM branch.
New loop API symbols are isolated in a separate factory and are reached only for
`loopBody`, permitting an old runtime jar first on the classpath for baseline
stability checks. Public `VMLoopBodyHarness.prepareSource(source, engine)` and
`VMLoopBodyProbe.validateSource(source, input, expected, engine)` support parent
Java acceptance without editing fixed benchmark fixtures.

Primary JMH protocol: one thread, AverageTime microseconds, two forks,
3 x1 s warmup and 5 x1 s measurements, identical 256--768 MiB heap/JVM and GC
profiler. Repeat large cases in reverse engine order. Parent owns all serialized
builds, probes and measurements. Explicitly select these classes: the Gradle
default include filter omits them. Shorter preparation/fallback results, if used,
must be labeled separately and not pooled into primary execution results.

Memory commands use fresh JVMs and one shared fixture/input. `reuse` retains one
executor for 1000 executions; `many` retains 1000 independently prepared executors,
each executed once. Eligible loop plans own one generated class/loader each, so
this is not 1000 wrappers sharing compiled code. Only the final output has its
own separate root. Post-GC stages are primed fixture only; fixture + all prepared
but unexecuted executors + weak references; fixture + executed executors + weak
references + last output; after executor release; and after output/reference
release. All plans are prepared before the unexecuted sample and execution starts
only afterward. The fixture's source/input/expected values stay resident.
Use the prepared-only delta for approximate preparation metadata/code ownership.
The after-execution delta also includes runtime state and any result trees held
by each engine: ordinary/prepared/generated VMs may retain each executor's last
environment/output, whereas interpreter ownership may differ. It must not be
labeled plan memory. The separately retained last output is shared by the stages.
Metadata contains detached primitive/string values, never Class, ClassLoader,
MethodHandle, plan, environment or result references.

At every post-requested-GC stage the probe records heap used, all available
non-heap pool used/committed bytes (including HotSpot Metaspace and Compressed
Class Space where present), and ClassLoadingMXBean loaded/total-loaded/unloaded
counts. Pool names/availability come from that JVM. Snapshot storage and metric
classes are primed before the first GC sample. Class counts are process-wide;
they do not identify every generated class or prove loader unloading. Wrapper
weak references show wrapper collection only. Class unloading and committed
metaspace reclamation are separate observations, with no exact graph/leak claim.

Capture peak RSS externally with `/usr/bin/time -l` in three fresh processes per
engine/lifetime, alternating order and equal heap/JVM flags. macOS RSS is bytes
and includes cumulative preparation/JIT/native/code peaks. Keep GC allocation
B/op, approximate heap deltas, non-heap pools, class counts and RSS separate.
Forced-GC samples are not execution timings or minimum required heap estimates.

Reproduction (replace JAVA/JAR with the exact parent-owned runtime/artifact):

```bash
./gradlew :vm-benchmarks:jmhJar
JAVA -cp JAR io.github.ehlyzov.branchline.vm.VMLoopBodyProbe validate
JAVA -cp BEFORE_JAR:JAR org.openjdk.jmh.Main '.*VMLoopBodyBenchmark.execute' -p engine=baseline -p dataset=large -prof gc -rf json -rff old-baseline.json
JAVA -jar JAR '.*VMLoopBodyBenchmark.execute' -prof gc -rf json -rff forward.json
JAVA -jar JAR '.*VMLoopBodyBenchmark.execute' -p dataset=large -p engine=loopBody,preparedOptimized,baseline,interpreter -prof gc -rf json -rff reverse.json
JAVA -jar JAR '.*VMLoopBodyPreparationBenchmark.prepare' -p workload=holdoutProjection -p dataset=large -prof gc -rf json -rff preparation.json
JAVA -jar JAR '.*VMLoopBodyBenchmark.execute' -p workload=fallbackClosure -p dataset=large -prof gc -rf json -rff fallback.json
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMLoopBodyProbe memory loopBody holdoutProjection large reuse 1000
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMLoopBodyProbe memory loopBody holdoutProjection large many 1000
```

Record actual commands, exits, source/jar hashes, raw JMH distributions and JVM
bindings. Performance acceptance follows `development/planning/vm-loop-body-experiment.md`;
the presence of generated code or lower allocation alone establishes no win.
