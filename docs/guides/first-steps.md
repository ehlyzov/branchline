---
title: First Steps
---

# First Steps

A short path to confident usage.

## Use the Playground
- Open the [Playground](../playground.md).
- Load an example: `customer-profile`, `pipeline-health-gating`, or `order-shipment`.
- Toggle tracing and run with Cmd/Ctrl + Enter.

## Learn the essentials
- **Paths:** `INPUT`/`$` for the root payload.
- **Bindings:** `LET name = expr;` for variables.
- **Control flow:** `IF/ELSE`, `FOR EACH`, and comprehensions.
- **Output:** shape results with `OUTPUT { ... }`.
- **Tracing:** `EXPLAIN`, `CHECKPOINT`, `ASSERT`.

## Run locally
```bash
java -jar branchline-cli-<tag>-all.jar path/to/program.bl --input sample.json
```
Use `--input-format xml` for XML payloads.

## Keep going
- [Getting Started](getting-started.md)
- [Tour of Branchline](../learn/index.md)
- [Install Branchline](install.md)
