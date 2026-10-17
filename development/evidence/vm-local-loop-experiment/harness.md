# JVM-local loop harness

The design was recorded before implementation and the runtime API is approved.
Parent owns builds/JVM probes and all serialized measurements. The harness is
implemented; compilation/acceptance remains parent-owned. No new performance or
correctness result is implied here.

The fixed primary matrix reuses unchanged `VMFactorialFixture` sources, resident
Map/List inputs and independent raw expected values: `paths`, `reduction`, and
`holdoutProjection`, each at small (10 orders x5 items) and large (500 x25).
Engines are `interpreter`, direct ordinary `baseline`, prior generated `loopBody`,
and new `localLoop`. `fallbackClosure` remains a separate unsupported control.
The previous loop compiler/harness and fixtures stay unchanged.

New `VMLocalLoopBenchmark.kt` includes a source-to-executor helper callable
from independent Java acceptance via `@JvmStatic prepareSource(source, engine)`.
The first three engines delegate to the unchanged `VMLoopBodyHarness`. New local
compiler/plan/runtime symbols are isolated in a separate factory reached only
for `localLoop`, preserving old-jar-first direct-baseline checks. A new probe
exposes raw execution and detached scalar metadata, plus optional caller-
supplied raw expected values; metadata must never retain Class/ClassLoader/
MethodHandle, plan, environment or output objects.

Approved runtime API: `JvmLocalLoopCompiler.prepare`
returns `LocalLoopPlan`, executed by `VM.executeLocalLoops(plan, env)`. Scalars
`localLoopExecuted`, `localLoopEntries`, `localLoopPrimitiveUsed`, and
`localLoopFallbackReason` describe actual latest-call work. Static plan metadata
includes eligible/lowered loop counts, local-value/primitive/spill/guard sites,
body instruction count, generated-class count and classfile bytes. Static sites
are not dynamic executions; entries count kernel invocations rather than rows.

Each setup checks three raw outputs and pristine input. Unchanged projection
must actually execute local lowering, enter a kernel, use the primitive path,
have positive local-value/primitive sites, and report no fallback. Prior loopBody
must genuinely run its kernel on projection. Paths and closure are explicit
fallback controls; reduction may fall back if outside the bounded shape. Partial
local/primitive work before deoptimization remains visible through scalar flags
and a fallback reason, never counted as complete optimized execution. Empty
eligible loops may execute no kernel and need not be a fallback.

Execution timing includes identical fresh environment construction and reusable
execution, returning raw results for JMH consumption. Parsing, preparation,
serialization and metadata reads stay outside timing. Guards, spill/state
reconstruction, ordinary continuation and actual runtime costs stay included.
There is no harness-only per-row counter/report allocation. Preparation is a
separate source-to-executor benchmark including ASM definition/loading; same-
source preparation is not a cache-hit measurement because plans use isolated
loaders/classes with no global cache. Class-definition/JIT/unloading effects
must remain disclosed.

Primary timing follows the plan: one thread, two forks, AverageTime microseconds,
3 x1 s warmup and 5 x1 s measurements, identical heap/JVM, GC B/op, both sizes,
and reverse-order large confirmation. Preparation and fallback may use a bounded
separately labelled protocol. Select the new benchmark names explicitly because
the Gradle default include filter omits experimental harnesses. Source/jar hashes
must freeze after independent correctness acceptance and before measurement.

The fresh-process memory probe retains one shared projection fixture and
use five post-requested-GC stages: primed fixture; all prepared but unexecuted
executors plus weak wrappers; executed executors and last output; after executor
release; after output/weak-reference release. `reuse` prepares one executor and
executes 1000 times; `many` prepares 1000 independent plans before execution,
then executes each once. Record generated-class/classfile-byte totals separately
from actual process class counts. Prepared-only heap approximates preparation
ownership; after-execution heap also includes retained runtime state and per-VM
result trees, so it must not be labelled plan memory.

At each stage record heap used, available non-heap pool used/committed bytes
(Metaspace and Compressed Class Space where present), and loaded/total-loaded/
unloaded class counts. Prime metric classes/preallocate scalar sample storage
before the first sample. Use three fresh processes per engine/lifetime with
equal heap/JVM and external `/usr/bin/time -l` peak RSS. Weak wrappers establish
wrapper collection only; class unloading/committed metaspace reclamation are
separate JVM-dependent observations. No claim of exact graph size, universal
reclamation, minimum heap or leak absence follows from those proxies.

Reproduction (replace JAVA/JAR with the exact parent-owned runtime/artifact):

```bash
./gradlew :vm-benchmarks:jmhJar
JAVA -cp JAR io.github.ehlyzov.branchline.vm.VMLocalLoopProbe validate
JAVA -cp BEFORE_JAR:JAR org.openjdk.jmh.Main '.*VMLocalLoopBenchmark.execute' -p engine=baseline -p dataset=large -prof gc -rf json -rff old-baseline.json
JAVA -jar JAR '.*VMLocalLoopBenchmark.execute' -prof gc -rf json -rff forward.json
JAVA -jar JAR '.*VMLocalLoopBenchmark.execute' -p dataset=large -p engine=localLoop,loopBody,baseline,interpreter -prof gc -rf json -rff reverse.json
JAVA -jar JAR '.*VMLocalLoopPreparationBenchmark.prepare' -p workload=holdoutProjection -p dataset=large -prof gc -rf json -rff preparation.json
JAVA -jar JAR '.*VMLocalLoopBenchmark.execute' -p workload=fallbackClosure -p dataset=large -prof gc -rf json -rff fallback.json
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMLocalLoopProbe memory localLoop holdoutProjection large reuse 1000
/usr/bin/time -l JAVA -Xms32m -Xmx768m -cp JAR io.github.ehlyzov.branchline.vm.VMLocalLoopProbe memory localLoop holdoutProjection large many 1000
```

`VMLocalLoopProbe.validateSource(source, input, expected, engine)` is JVM-callable
for independent parent raw fixtures. Its custom cases do not silently expand the
primary benchmark matrix. Record source/jar/JVM bindings, exact exits and raw
measurements; report eligible, fully optimized and partial/fallback cases separately.

Acceptance and final release disposition remain in
`development/planning/vm-local-loop-experiment.md`. The release inventory is
`release-inventory.md` alongside this note. No default enablement, version bump,
tag, commit, workflow dispatch or publication is part of harness work.
