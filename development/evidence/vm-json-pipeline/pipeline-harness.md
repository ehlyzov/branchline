# Selective JSON full-pipeline measurement harness

Prepared 2026-10-17. This file defines the harness and commands; it is not a record
of measurements. The implementation agent did not build or run this harness.
Parent owns validation, measurement, acceptance and runtime budget.

## Scope and fixtures

`SelectiveJsonPipelineBenchmark.fullPipeline` measures the same reusable-transform
boundary for both engines: JSON text -> full validation and eager/selected
materialization -> environment -> strict bytecode VM -> compact JSON string.
Source generation, input generation, preparation/compilation and independent
output/plan validation happen in trial setup. The timed method consumes the
returned string with a Blackhole. State is per thread; use one benchmark thread
for paired results. There is no forced GC in timed code.

Every fixture has exactly 100 top-level fields f0..f99. `selectedPercent` is 1,
10 or 100 fields read through LENGTH and summed into the tiny `{ "total": N }`
output. Expected totals are calculated from the fixture specification, outside
the engine; output JSON is parsed and checked in setup/validation.

- `shape=strings`: plain ASCII strings distributed uniformly across fields.
  Documents are exactly 1, 16 or 64 MiB. Field lengths differ by at most one byte.
- `shape=records`: uniformly sized arrays of identical objects containing an id
  and a 64-character payload. Arrays contain the same number of records. The
  document is rounded down by less than one record per field from the requested
  size; actual bytes are reported.
- `accessPattern=static` (default): direct input.fN accesses; selective mode must
  report SELECTIVE and precisely the selected fN keys plus the planner's mandatory
  `input` alias-shadow guard. Fixtures contain no actual `input` field, so that
  guard does not add a materialized value or change the access percentage. The
  100% case is the dense control and executes the same small-output transform.
- `accessPattern=dynamic` (explicit separate control): LET-bound string keys and
  input[kN] accesses, with the same length sum/tiny output. Both engine modes must
  report EAGER. This tests transparent eager fallback, not sparse selection.

`engineMode=eager` sets forceEager. Both modes use contracts OFF and the same
public pipeline API, JSON options and strict VM. Contracts ON are a correctness
fallback gate, not mixed into this performance comparison. Default CLI parsing
is not changed by this harness.

## Build and validation commands (run by parent)

The repository already supplies `:vm-benchmarks:jmhJar` and the CLI dependency.
The Gradle default benchmark include filter intentionally remains unchanged;
run the new benchmark explicitly using the jar entrypoint. The filename below
matches the current version; use the actual generated filename if it changes.

```sh
./gradlew :vm-benchmarks:jmhJar
PIPELINE_BENCH_JAR=vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar
java -cp "$PIPELINE_BENCH_JAR" io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe generate /tmp/branchline-json-pipeline/strings-16.json 16 strings
java -cp "$PIPELINE_BENCH_JAR" io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe validate /tmp/branchline-json-pipeline/strings-16.json 16 strings 1 eager
java -cp "$PIPELINE_BENCH_JAR" io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe validate /tmp/branchline-json-pipeline/strings-16.json 16 strings 1 selective
java -cp "$PIPELINE_BENCH_JAR" io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe validate /tmp/branchline-json-pipeline/strings-16.json 16 strings 1 selective dynamic
```

The probe prints one compact JSON result, including SHA-256, actual input bytes,
independent expected total, effective mode, reason and selected keys. A wrong
output, plan mode, materialized length or declared fixture size fails the process.
Use generate for each distinct shape/size before any memory runs; input files
are shared identically across eager/selective processes.

## Timed and allocation comparison (run by parent)

Example primary strings matrix, 18 parameter states. JVM/heap/collector and
warmup settings are explicit and identical. Parent may narrow the matrix or
extend iterations/forks when uncertainty warrants it; record actual settings.
Do not overlap runs with Gradle or other benchmark processes.

```sh
java -jar "$PIPELINE_BENCH_JAR" 'io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineBenchmark.fullPipeline' \
  -p engineMode=eager,selective -p inputMiB=1,16,64 -p selectedPercent=1,10,100 \
  -p shape=strings -p accessPattern=static -t 1 -f 2 -wi 3 -i 5 -w 500ms -r 500ms \
  -jvmArgs '-Xms512m -Xmx2g -XX:+UseSerialGC' -prof gc -rf json \
  -rff development/evidence/vm-json-pipeline/pipeline-strings-jmh.json
```

Run records controls separately with `-p inputMiB=16 -p selectedPercent=1,100
-p shape=records -p accessPattern=static` and a distinct result filename. Run
fallback separately with `-p inputMiB=16 -p selectedPercent=1 -p shape=strings
-p accessPattern=dynamic`. A whole-root output would add a large serialization
cost and is deliberately excluded from this tiny-output comparison.

JMH score is full-pipeline elapsed ms/op. `gc.alloc.rate.norm` is allocated B/op,
not retained/live heap or RSS. Keep raw JSON and error/confidence fields; do not
infer a gain from allocation alone or from a difference smaller than measurement
uncertainty. JMH's prepare-time correctness validation is mandatory, including
reported plan mode. No setup/parse-only score should be compared to this score.

## Fresh-process retained-heap and RSS probe (run by parent)

The memory command reads an existing file. It never generates full-size input in
that process. Source/planner/strict VM setup and a tiny matching-shape execution
prime runtime infrastructure before the first measured heap stage. The baseline
therefore includes the raw text, prepared pipeline and a tiny previous VM
environment, but no full-size materialized input graph.

Each stage uses System.gc/runFinalization/System.gc and records JVM heap used.
Strong volatile roots explicitly retain the current raw string/pipeline/map/
output, and stage methods return before GC to avoid accidental caller-local
retention. GC request behavior depends on the JVM; do not disable explicit GC.
There are no timings in this probe.

1. `rawAndPreparedVm`: raw input plus prepared/primed pipeline baseline.
2. `rawPreparedAndMaterialized`: additionally retain materialize(text)'s map.
3. `rawPreparedAfterMaterializedRelease`: release that map and collect.
4. `rawAndReusableVmLastEnvironment`: execute the full pipeline and retain its
   last environment through the reusable VM, along with raw text and tiny output.
5. `detachedOutputOnly`: drop raw text and pipeline, retain the tiny output along
   with fixed fixture/measurement metadata and runtime infrastructure. The name
   describes the remaining measured pipeline data, not total process contents.
   Weak references must show raw/pipeline collectible; output is checked again.

Absolute used-heap observations and descriptive differences are printed. A
negative/noisy difference should be reported as such. Materialized live-graph
cost, reusable-VM retention, and raw-text lifetime are distinct observations.
This probe does not establish that all classes of selected output are detached;
semantic/parser tests own that broader guarantee.

Example matched processes on macOS; `/usr/bin/time -l` reports peak RSS separately:

```sh
/usr/bin/time -l java -Xms512m -Xmx2g -XX:+UseSerialGC -cp "$PIPELINE_BENCH_JAR" \
  io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe memory \
  /tmp/branchline-json-pipeline/strings-16.json 16 strings 1 eager \
  > development/evidence/vm-json-pipeline/memory-strings-16-1-eager.json \
  2> development/evidence/vm-json-pipeline/memory-strings-16-1-eager.time
/usr/bin/time -l java -Xms512m -Xmx2g -XX:+UseSerialGC -cp "$PIPELINE_BENCH_JAR" \
  io.github.ehlyzov.branchline.vm.SelectiveJsonPipelineProbe memory \
  /tmp/branchline-json-pipeline/strings-16.json 16 strings 1 selective \
  > development/evidence/vm-json-pipeline/memory-strings-16-1-selective.json \
  2> development/evidence/vm-json-pipeline/memory-strings-16-1-selective.time
```

Batch the same command in separate fresh JVMs for each requested mode/size/
selection/shape. Generation remains in earlier processes. Process peak RSS
includes startup, file-read temporary buffers, compilation, GC machinery and all
stages; it cannot be labelled as selected graph size or parser-only memory. Heap
used excludes non-heap/native storage, and allocation B/op excludes retention.
Keep the three measurements separate. Parent selects a bounded batch, including
sparse 16/64 MiB and the dense/records/fallback controls where practical.
