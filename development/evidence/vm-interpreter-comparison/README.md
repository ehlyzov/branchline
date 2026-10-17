# Optimized Branchline VM versus interpreter — 2026-10-17

## Primary evidence: existing repository JMH pairs

The primary comparison runs the existing, unchanged `VMTransformBenchmark` and `InterpreterTransformBenchmark` methods `pathExpressions`, `arrayComprehensions` and `typicalTransform`. These pairs are documented in `docs/benchmarks.md`. Both input generators and transformation programs match; interpreter `collectionType=hash` matches the VM's LinkedHashMap/ArrayList input. Sizes are small 10 orders × 5 items, medium 100 × 10, large 500 × 25.

NativePairValidation instantiates the actual benchmark states, verifies equal input datasets and initial environments, and checks both results against independently calculated expected values for all 9 pairs. These VM benchmarks call VM.execute directly, so compatibility fallback cannot impersonate VM execution. Both generated jars contain identical current Exec, VM and Compiler class bytes; jar/source hashes are recorded. The other existing CrossEngineBenchmark also offers both Branchline engine IDs, but its VM adapter uses compilation fallback by default; it is not used as primary pure-VM evidence here.

Runtime: Apple M4 Pro, 48 GiB RAM, macOS 27.0.1, Temurin 21.0.11. JMH 1.36, one thread, two independent JVM forks per state, three 300 ms warmups and four 300 ms measurement iterations per fork, average time in microseconds, heap 256–768 MiB, GC profiler. Native annotation SampleTime is overridden equally to AverageTime by CLI. VM then interpreter run sequentially with no concurrent Gradle/agent benchmarking. This is a local short benchmark, not randomized publication evidence or a production latency guarantee. Raw 99.9% confidence intervals are preserved; overlapping intervals must not be called a clear timing win.

Both engines are prepared once. Parsing, compilation, I/O, tracing and snapshot persistence are outside the measured region. Native Level.Invocation environment setup is outside measured time, but its allocation is included by the GC profiler. Memory means normalized bytes allocated per transform, including garbage-collection pressure; it does NOT measure retained heap, peak RSS or a minimum required heap. Input data remains resident and shared across invocations.

## Supplementary scaling evidence

EngineComparisonBench adds 13 validated cases: path reads at 1000 rows and sum/filter/object projection/lambda/array+=/objectSET at 1000 and 4000 rows. Both engines run the same source/IR/input with an object-shaped OUTPUT boundary supported by both; direct scalar OUTPUT was rejected by interpreter during preflight and was never measured. Three setup runs check independent expected values and unchanged input. Bytecode/Exec are reused, with a fresh environment on every operation.

This supplemental harness INCLUDES environment creation in timed execution. Its absolute times are therefore separate from the existing native benchmarks, and ratios from the two suites are not pooled. The new file does not replace or modify existing benchmark/product code. Supplemental runs completed before native jar build/runs, without concurrent workload benchmarks.

## Reproduction

```bash
./gradlew :interpreter-benchmarks:jmhJar :vm-benchmarks:jmhJar
python3 development/evidence/vm-interpreter-comparison/run_native.py \
  --java-home /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  --output-dir /tmp/branchline-native-recheck
python3 development/evidence/vm-interpreter-comparison/run.py \
  --java-home /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  --jar vm-benchmarks/build/libs/vm-benchmarks-v0.11.0-SNAPSHOT-jmh.jar \
  --output /tmp/branchline-extra-recheck.json
```

NativePairValidation.java can be compiled/run with both built jars on the classpath; it reads the existing benchmark fields reflectively for correctness checks only, outside timed execution. Exact native command arrays are saved with raw results. Source files, validation outputs, build log and manifests accompany the tables. No product optimization was made during comparison.

## Results: existing native pairs (primary)

Lower time and allocation are better. A time ratio above 1 means VM is faster; below 1 means interpreter is faster. All 9 native timing pairs have disjoint JMH 99.9% confidence intervals in this run. These are short sequential local measurements, so this does not remove order, thermal or longer-run uncertainty.

| Case | VM µs/op | Interpreter µs/op | Interpreter time / VM time | VM B/op | Interpreter B/op |
|---|---:|---:|---:|---:|---:|
| arrayComprehensions/small | 1.208 | 0.489 | 0.41 | 800 | 1,376 |
| arrayComprehensions/medium | 7.103 | 3.128 | 0.44 | 800 | 5,112 |
| arrayComprehensions/large | 45.409 | 22.561 | 0.50 | 5,656 | 32,768 |
| pathExpressions/small | 0.449 | 0.235 | 0.52 | 704 | 784 |
| pathExpressions/medium | 0.510 | 0.242 | 0.48 | 704 | 784 |
| pathExpressions/large | 0.470 | 0.245 | 0.52 | 704 | 784 |
| typicalTransform/small | 1.272 | 0.597 | 0.47 | 824 | 824 |
| typicalTransform/medium | 10.335 | 4.849 | 0.47 | 2,264 | 2,264 |
| typicalTransform/large | 54.545 | 27.494 | 0.50 | 8,680 | 8,737 |

The interpreter is 1.92–2.47 times faster across the existing pairs. The optimized VM does not demonstrate a general runtime advantage here. VM allocation is lower for comprehensions (5.79 times less at large), about 10% lower for path reads, and essentially equal for typicalTransform. Dataset size is not a common unit of work: path reads access a fixed number of fields; the other programs visit different subsets of the orders/items.

## Results: supplemental workloads

| Case | VM µs/op | Interpreter µs/op | Interpreter time / VM time | VM B/op | Interpreter B/op |
|---|---:|---:|---:|---:|---:|
| paths:1000 | 0.259 | 0.155 | 0.60 | 544 | 656 |
| sum:1000 | 108.617 | 56.439 | 0.52 | 16,216 | 16,296 |
| filter:1000 | 62.234 | 29.332 | 0.47 | 7,152 | 9,352 |
| project:1000 | 186.044 | 114.587 | 0.62 | 237,341 | 306,456 |
| lambda:1000 | 181.621 | 76.132 | 0.42 | 333,424 | 266,496 |
| append:1000 | 122.999 | 55.048 | 0.45 | 13,632 | 178,624 |
| set:1000 | 194.415 | 5417.018 | 27.86 | 57,133 | 25,478,087 |
| sum:4000 | 464.044 | 227.406 | 0.49 | 64,249 | 64,326 |
| filter:4000 | 259.492 | 121.664 | 0.47 | 33,975 | 29,656 |
| project:4000 | 744.456 | 452.176 | 0.61 | 938,345 | 1,224,305 |
| lambda:4000 | 688.434 | 315.993 | 0.46 | 1,322,401 | 1,064,321 |
| append:4000 | 506.881 | 227.568 | 0.45 | 65,697 | 733,447 |
| set:4000 | 813.415 | 81773.643 | 100.53 | 226,345 | 405,480,034 |

Dynamic object SET is the clear exception: at 4000 rows VM takes about 0.813 ms versus 81.774 ms and allocates about 226 KB versus 405 MB (decimal units). From 1000 to 4000 rows, interpreter SET time and allocation grow about 15–16 times while VM grows about 4 times. This supports a quadratic-copying bottleneck for this interpreter workload, but is not a general VM speed claim.

For array accumulation at 4000 rows, VM allocates roughly 11 times less, yet interpreter remains about 2.2 times faster. Other supplemental workloads also favor interpreter timing. Lambda mapping even allocates more on VM. Consequently lower allocation alone does not establish lower execution time.

## Conclusion and limits

Keep interpreter as the performance baseline for ordinary transformations; VM currently has a demonstrated advantage for repeated dynamic object SET and selected allocation-sensitive workloads. A follow-up performance investigation should profile VM instruction dispatch, variable/property access and call overhead on the existing paired benchmarks before selecting changes; these measurements do not isolate the responsible mechanism. No additional product change was made in this comparison.

Correctness validation passed all 9 native pairs and 13 supplemental cases. Both suites have two JVM forks per state; raw JSON retains confidence intervals and per-iteration results. CSV tables preserve score errors and whether timing intervals are disjoint. No aggregate score is computed across different workloads or suites. Startup/compilation cost, retained heap, peak RSS, concurrent throughput and non-JVM platforms were not measured. In particular, B/op must not be described as total resident memory consumption.

Artifacts: `native-{vm,interpreter}.json`, matching logs and exact command arrays; `comparison-native.csv`; supplemental `results.json` and `comparison-supplemental.csv`; validation logs; source and jar SHA-256 manifests. Existing source/jar hashes were rechecked after measurements and matched.
