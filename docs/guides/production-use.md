---
title: Production Use
---

# Production Use

Operational patterns for running Branchline in ETL pipelines.

## Recommended workflow
1. Commit `.bl` programs alongside pipeline code.
2. Run Branchline in CI with fixed inputs for regression checks.
3. Enable tracing in staging when introducing new transforms.

## Observability
- Use `EXPLAIN` to trace derived values.
- Add `CHECKPOINT` labels around expensive steps.
- Use `ASSERT` to fail fast on invalid data.

## Error handling
- Wrap risky logic in `TRY/CATCH` and return fallbacks.
- Use retry modifiers for transient host failures.

## Performance tips
- Cache expensive paths with `LET`.
- Prefer `MAP`/`FILTER`/`REDUCE` for collections.
- Keep transforms focused; compose multiple transforms if needed.

## Related
- [TRY/CATCH](try-catch.md)
- [Debug Utilities](../language/std-debug.md)
- [Benchmarks](../benchmarks.md)
