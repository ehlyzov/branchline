---
title: Debug Standard Library
---

# Debug

Tools for diagnosing programs.

- `CHECKPOINT(label?)` → marks a step; returns `true`. Useful for profiling or logging.
- `ASSERT(cond[, msg])` → errors if `cond` is falsey; otherwise returns `true`. Provide a message for clearer failures.
- `EXPLAIN(varName)` → provenance info for a named variable when tracing is enabled; otherwise returns `{ "var": name, "info": "no provenance" }`.

Enable tracing in the playground or runtime to see `EXPLAIN` output.

Run it: [Debugging example](../playground.md?example=stdlib-debug-explain) (toggle tracing).
