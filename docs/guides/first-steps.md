---
title: First Steps with Branchline
---

# First Steps with Branchline

A fast walkthrough from the Playground to a local run.

## Step 1: Open the Playground
- Visit the [interactive Playground](../playground.md).
- Load `customer-profile` or `collection-transforms`.
- Toggle tracing and run with Cmd/Ctrl + Enter.

## Step 2: Learn the essentials
- **Paths:** `$`/`INPUT` refers to the root payload.
- **Bindings:** `LET name = expr;` defines variables.
- **Control flow:** `IF/ELSE`, `FOR EACH`, and comprehensions.
- **Output:** shape results with `OUTPUT { ... }`.
- **Tracing:** `EXPLAIN`, `CHECKPOINT`, `ASSERT`.

## Step 3: Run locally (JVM)
```bash
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
```
Use `--input-format xml` for XML payloads.

## Step 4: Explore standard library behavior
Try example URLs in the Playground:
- `?example=stdlib-hof-overview`
- `?example=stdlib-strings-text`

## Step 5: Keep learning
- [Tour of Branchline](../learn/index.md)
- [Language Overview](../language/index.md)
- [Install Branchline](install.md)
