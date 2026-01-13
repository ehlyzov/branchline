---
title: JSONata Benchmarks
---

# JSONata Benchmarks

This page will host Branchline vs JSONata benchmark results once CI publishing
is wired up.

## Status

- Placeholder page; no published results yet.
- CI automation depends on external JSONata engines and the JSONata test suite.

## Run locally

```bash
./gradlew :jsonata-benchmarks:jmh -PjmhIncludes=io.github.ehlyzov.branchline.benchmarks.jsonata.CrossEngineBenchmark
```

## Planned outputs

- Per-release summary table (mean, p95, allocation).
- Cross-engine comparison tables (Branchline interpreter/VM vs JSONata engines).
- Links to raw JSON artifacts.
