---
title: Production Use
---

# Production Use

Operational patterns for running Branchline in ETL pipelines.

Branchline v1.0.0-rc.1 is a release candidate. Stable 1.0 adoption and production
readiness remain open; the external pilot has not started. Use the
[operational pilot plan](pilot-plan.md) for a bounded
JVM trial, including independent installation, resource limits, observation and
rollback. Local example tests do not establish production readiness.

## Recommended workflow
1. Commit `.bl` programs alongside pipeline code.
2. Run Branchline in CI with fixed inputs for regression checks.
3. Enable tracing in staging when introducing new transforms.

## Observability
- Use `EXPLAIN` to trace derived values.
- Add `CHECKPOINT` labels around expensive steps.
- Use `ASSERT` to fail fast on invalid data.

## Error handling
- Handle expected domain failures explicitly; preserve a visible rejection rather
  than silently replacing corrupted or invalid output with a default.
- Retry only classified transient host failures with host-owned idempotency.
  Runtime failure must not replay side effects through another execution engine.

## Performance tips
- Cache expensive paths with `LET`.
- Prefer `MAP`/`FILTER`/`REDUCE` for collections.
- Keep transforms focused; compose multiple transforms if needed.

## Related
- [TRY/CATCH](try-catch.md)
- [Debug Utilities](../language/std-debug.md)
- [Benchmarks](../benchmarks.md)
- [Operational Pilot](pilot-plan.md)
