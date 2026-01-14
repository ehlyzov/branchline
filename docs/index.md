---
title: Branchline DSL
description: Official documentation for the Branchline domain specific language.
---

# Branchline DSL Documentation

Branchline is a Kotlin-based DSL for transforming structured data. These docs are written for workflow and ETL builders who need clear, debuggable transforms that run on JVM and JS.

## Start in three steps
1. **Try it in the browser:** Open the [Playground](playground.md) and run a real example.
2. **Learn the basics:** Follow the [Tour of Branchline](learn/index.md).
3. **Look up details:** Use the [Language Reference](language/index.md) and [Standard Library](language/std-core.md).

## Why Branchline?
- **Fast to read:** small syntax for common data reshaping tasks.
- **Safe to run:** `TRY/CATCH`, `ASSERT`, and `EXPLAIN` make pipelines observable.
- **ETL-ready:** first-class support for arrays, aggregations, and higher-order functions.

## Quickstart (local)
```bash
# 1) Clone and verify toolchain
git clone https://github.com/ehlyzov/branchline-public.git
cd branchline-public
./gradlew --version

# 2) Run a Branchline program on JVM
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"

# 3) Run via Node
./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"
```
Need more detail? See [Install Branchline](guides/install.md) and [First steps](guides/first-steps.md).

## Minimal example
```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```

## Real-world workflows to try
- [Customer profile enrichment](playground.md?example=customer-profile){ target="_blank" }
- [Order shipment normalization](playground.md?example=order-shipment){ target="_blank" }
- [Pipeline health gating](playground.md?example=pipeline-health-gating){ target="_blank" }
- [JUnit report summaries](playground.md?example=junit-badge-summary){ target="_blank" }

## Production and advanced topics
- [Production Use](guides/production-use.md)
- [Migration Guides](guides/migrations.md)
- [Release Readiness & Stability](guides/release-readiness.md)
- [Benchmarks](benchmarks.md)
