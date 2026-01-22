---
title: Branchline DSL
description: Official documentation for the Branchline domain specific language.
---

# Branchline DSL

Branchline is a compact DSL for transforming structured data (JSON today; XML supported in the CLI). It runs on JVM and JS, with first-class tracing and error handling.

## Quick start
1. Open the [Playground](playground.md) and run an example.
2. Follow the [Tour of Branchline](learn/index.md).
3. Use the [Language Reference](language/index.md) and [Standard Library](language/std-core.md) for details.

## Minimal transform
```branchline
TRANSFORM Hello {
    OUTPUT { greeting: "Hello, " + input.name };
}
```

## Run locally
```bash
java -jar branchline-cli-<tag>-all.jar path/to/program.bl --input sample.json
```
Need setup help? See [Install Branchline](guides/install.md).

## What to read next
- [Getting Started](guides/getting-started.md)
- [First Steps](guides/first-steps.md)
- [TRY/CATCH](guides/try-catch.md)
- [Production Use](guides/production-use.md)

## Examples to explore
- [Customer profile enrichment](playground.md?example=customer-profile){ target="_blank" }
- [Order shipment normalization](playground.md?example=order-shipment){ target="_blank" }
- [Pipeline health gating](playground.md?example=pipeline-health-gating){ target="_blank" }
- [JUnit report summaries](playground.md?example=junit-badge-summary){ target="_blank" }
