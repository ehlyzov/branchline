---
title: Benchmarks
---

# Benchmarks

Performance results for the Branchline interpreter and VM.

## Latest release

--8<-- "benchmarks/latest.md"

## Release history

--8<-- "benchmarks/releases/list.md"

Full archive: [Release benchmark history](benchmarks/releases/index.md).

## What we measure

| Area | Interpreter benchmark | VM benchmark |
| --- | --- | --- |
| Path expressions | `InterpreterTransformBenchmark.pathExpressions` | `VMTransformBenchmark.pathExpressions` |
| Array comprehensions | `InterpreterTransformBenchmark.arrayComprehensions` | `VMTransformBenchmark.arrayComprehensions` |
| Typical transform | `InterpreterTransformBenchmark.typicalTransform` | `VMTransformBenchmark.typicalTransform` |

Datasets: Small (10x5), Medium (100x10), Large (500x25).

## Run locally
```bash
./gradlew :interpreter-benchmarks:jmh :vm-benchmarks:jmh
```

## Notes
- Compare runs on the same machine and JDK.
- Report deltas against the previous release.
