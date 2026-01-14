---
title: Debug Standard Library
---

# Debug

Tracing and assertion helpers for observability.

## Functions
- `CHECKPOINT(label)` → mark a trace checkpoint.
- `ASSERT(condition, message)` → throw when condition is false.
- `EXPLAIN(label)` → return provenance for a labeled value (requires tracing).

## Example
- [stdlib-debug-explain](../playground.md?example=stdlib-debug-explain)

## Notes
- Enable tracing in the Playground or CLI to view `EXPLAIN` output.

## Related
- [TRY/CATCH](../guides/try-catch.md)
- [Production Use](../guides/production-use.md)
