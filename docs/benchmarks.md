---
title: Benchmarks
---

# Benchmarks

This report tracks interpreter/VM performance for common workloads (path
expressions, array comprehensions, and a typical transform loop) using
consistent input datasets.

## Baseline datasets

| Dataset | Orders | Items per order | Approx. items |
| --- | ---: | ---: | ---: |
| Small | 10 | 5 | 50 |
| Medium | 100 | 10 | 1,000 |
| Large | 500 | 25 | 12,500 |

Input shape:

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

## Benchmarks

| Area | Interpreter benchmark | VM benchmark |
| --- | --- | --- |
| Path expressions | `InterpreterTransformBenchmark.pathExpressions` | `VMTransformBenchmark.pathExpressions` |
| Array comprehensions | `InterpreterTransformBenchmark.arrayComprehensions` | `VMTransformBenchmark.arrayComprehensions` |
| Typical transform | `InterpreterTransformBenchmark.typicalTransform` | `VMTransformBenchmark.typicalTransform` |

## Running the suite

```bash
./gradlew :interpreter-benchmarks:jmh
./gradlew :vm-benchmarks:jmh
```

Opcode histogram snapshot (for VM optimization tracking):

```bash
./gradlew :vm-benchmarks:vmOpcodeSnapshot
```

## Report

### Runtime (p95) and allocation baselines

Populate this table from the JMH JSON output\n+(`interpreter-benchmarks/build/reports/jmh/jmh*.json`,\n+`vm-benchmarks/build/reports/jmh/jmh*.json`).\n CI performance gates use the **medium** dataset and thresholds defined in\n `.github/perf-gates.json`.
CI performance gates use the **medium** dataset and thresholds defined in
`.github/perf-gates.json`.

| Runtime | Dataset | p95 (µs/op) | Alloc (B/op) | Notes |
| --- | --- | ---: | ---: | --- |
| Interpreter path expressions | Medium | — | — | |
| Interpreter array comprehensions | Medium | — | — | |
| Interpreter typical transform | Medium | — | — | |
| VM path expressions | Medium | — | — | |
| VM array comprehensions | Medium | — | — | |
| VM typical transform | Medium | — | — | |

### VM opcode sequences (before/after)

Opcode histograms are written to `docs/benchmarks/vm-opcodes.json` by the
`vmOpcodeSnapshot` task. Capture the "before" snapshot before landing an
optimization, then re-run after and summarize differences here. The checked-in
file is a placeholder until the snapshot task is executed locally.

| Program | Before (instruction count) | After (instruction count) | Notes |
| --- | ---: | ---: | --- |
| Path expressions | — | — | |
| Array comprehensions | — | — | |
| Typical transform | — | — | |

### VM allocation tracking (before/after)

Track per-op allocation deltas using the `gc.alloc.rate.norm` metric from JMH.

| Program | Before (B/op) | After (B/op) | Notes |
| --- | ---: | ---: | --- |
| Path expressions | — | — | |
| Array comprehensions | — | — | |
| Typical transform | — | — | |
