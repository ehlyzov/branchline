---
title: Benchmarks
---

# Benchmarks

Performance results for the Branchline interpreter and VM.

## Latest release results

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

Sample input shape:

```json
{
  "customer": { "name": "Customer-*", "segment": "B2B" },
  "orders": [
    {
      "id": 1,
      "total": 125,
      "status": "OPEN",
      "items": [
        { "sku": "SKU-1-0", "qty": 1, "price": 10 }
      ]
    }
  ]
}
```

## JSONata benchmarks

Branchline vs JSONata tracking lives on the [JSONata Benchmarks](benchmarks/jsonata.md) page.

## Raw artifacts

Latest assets live on the [latest GitHub release](https://github.com/ehlyzov/branchline-public/releases/latest).

Asset names:

- `branchline-jmh-summary-<tag>.md`
- `branchline-jmh-summary-<tag>.csv`
- `branchline-jmh-interpreter-<tag>.json`
- `branchline-jmh-vm-<tag>.json`

## Run locally

```bash
./gradlew :interpreter-benchmarks:jmh :vm-benchmarks:jmh
./gradlew :cli:runBl --args "$PWD/.github/scripts/jmh-report.bl --shared-file jmh=$PWD/interpreter-benchmarks/build/results/jmh/results.json --shared-file jmh=$PWD/vm-benchmarks/build/results/jmh/results.json --shared-format json --shared-key relative --write-output --write-output-dir $PWD/build/benchmarks"
```

Outputs:

- `build/benchmarks/jmh-summary.md`
- `build/benchmarks/jmh-summary.csv`

## Interpreting results

- Compare runs on the same machine and JDK.
- Prefer p50/p95 for latency and `gc.alloc.rate.norm` for allocation.
- Report deltas against the previous release, not just absolute values.

## VM opcode snapshot

Opcode histograms are written to `docs/benchmarks/vm-opcodes.json` by:

```bash
./gradlew :vm-benchmarks:vmOpcodeSnapshot
```
