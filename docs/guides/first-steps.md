---
title: First Steps with Branchline
---

# First Steps with Branchline

New to Branchline? This walkthrough takes you from zero to running code and inspecting traces.

## 1) Open the playground
- Visit the [embedded playground](../playground.md).
- Load `customer-profile` or `collection-transforms`.
- Toggle tracing and run with Cmd/Ctrl + Enter.
- Paste your own JSON (or XML) in the input pane and rerun.

## 2) Learn the essentials
- **Paths:** `$`/`INPUT` refers to the root payload. Use `input.field`, `items[0]`, slices `[start:end]`, predicates `[expr]`, and wildcards `.*`.
- **Bindings:** `LET name = expr;` defines variables. Use `??` to coalesce nulls.
- **Control flow:** `IF/ELSE`, `FOR` or `FOR EACH`, comprehensions `[FOR (x IN xs) => expr]`, `TRY/CATCH` for safe handling.
- **Output:** shape your result with `OUTPUT { ... }`.
- **Tracing:** `EXPLAIN("var")`, `CHECKPOINT("label")`, `ASSERT(condition, "msg")`.

## 3) Run locally (JVM)
```bash
./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"
```
Use `--input-format xml` for XML payloads.

## 4) Explore the standard library interactively
- Open any stdlib example in the playground via URL, e.g. `?example=stdlib-hof-overview` or `?example=stdlib-strings-text`.
- Edit the inputs to see how functions behave with nulls, empty arrays, and regex patterns.

## 5) Inspect provenance
- Enable tracing, run `stdlib-debug-explain`, and observe `EXPLAIN("total")`.
- Add `CHECKPOINT` markers around expensive sections to see progress.

## 6) Keep learning
- Read the [Language Overview](../language/index.md) for syntax details and semantics.
- Use the [Install guide](install.md) when you want to run Branchline offline.
- Browse the stdlib pages for per-function behavior and gotchas, each linked to an interactive example.
