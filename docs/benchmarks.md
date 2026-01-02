---
title: Benchmarks
---

# Benchmarks

This report tracks interpreter/VM performance for common workloads (path
expressions, array comprehensions, and a typical transform loop) using
consistent input datasets. It is both a reference for how we run performance
tests and a place to publish results.

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

## JMH configuration

- Mode: SampleTime (percentiles are meaningful for tail latency).
- Warmup: 2 iterations.
- Measurement: 5 iterations, 500ms each.
- Forks: 1.
- Profilers: `gc`.

## Running the suite

```bash
./gradlew :interpreter-benchmarks:jmh
./gradlew :vm-benchmarks:jmh
```

Results are written to:

- `interpreter-benchmarks/build/results/jmh/results.json`
- `vm-benchmarks/build/results/jmh/results.json`

Generate a release-friendly summary:

```bash
node .github/scripts/jmh-report.mjs build/benchmarks \
  interpreter-benchmarks/build/results/jmh/results.json \
  vm-benchmarks/build/results/jmh/results.json
```

Outputs:

- `build/benchmarks/jmh-summary.md` (summary table + interpreter/VM ratio table)
- `build/benchmarks/jmh-summary.csv` (machine-readable summary)

Latest published summaries are attached to the latest GitHub Release:
https://github.com/ehlyzov/branchline-public/releases/latest

Asset names:

- `branchline-jmh-summary-<tag>.md`
- `branchline-jmh-summary-<tag>.csv`
- `branchline-jmh-interpreter-<tag>.json`
- `branchline-jmh-vm-<tag>.json`

Opcode histogram snapshot (for VM optimization tracking):

```bash
./gradlew :vm-benchmarks:vmOpcodeSnapshot
```

## Comparing interpreter vs VM results

Compare on the same machine, back-to-back, using the same JDK and dataset.
For reporting, normalize by program + dataset (for example,
`pathExpressions`, `arrayComprehensions`, `typicalTransform` with
`small/medium/large` sizes). Favor p50 or p95 for typical latency and
`gc.alloc.rate.norm` for allocation.

To make comparisons reproducible, record:

- Commit SHA and release tag.
- JDK version and JVM flags.
- OS/CPU details.
- JMH settings (mode, iterations, forks).

## Making results useful

- Publish both the raw JSON and a short summary table (mean + p95 + alloc).
- Keep one result set per release tag to make regressions easy to spot.
- Show deltas vs the previous release rather than only absolute numbers.
- Note any non-default JVM flags or unusual hardware constraints.

Release automation publishes per-tag JMH assets (raw JSON plus summary
Markdown/CSV) so results can be compared across releases.

## Report

### Runtime (p95) and allocation baselines

Populate this table from the JMH JSON output
(`interpreter-benchmarks/build/results/jmh/results.json`,
`vm-benchmarks/build/results/jmh/results.json`).

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
