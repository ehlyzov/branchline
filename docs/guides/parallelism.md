---
title: Parallel Blocks
---

# Parallel Blocks

`PARALLEL` groups statements that may run concurrently. Use it when independent operations can execute at the same time. Host support is required for actual concurrency; otherwise blocks run sequentially.

```branchline
PARALLEL {
    LET a = fetchA(row);
    LET b = fetchB(row);
}
OUTPUT { a: a, b: b };
```

Each branch inside the block executes in parallel and its results are
available after the block completes. Parallel execution is currently
an experimental feature and may not be supported by all runtimes.

## When to use
- Independent fetches (e.g., user profile + billing) that can run in parallel.
- Work that does not share mutable state or require ordering.

## Tips
- Keep parallel branches side-effect free where possible.
- Avoid shared mutable state inside branches unless guarded.
- Pair with `CHECKPOINT` around the `PARALLEL` block to trace progress.

## Try it
There is no playground-hosted concurrency; use your runtime to exercise `PARALLEL`. For a similar pattern, open [pipeline-health-gating](../playground.md?example=pipeline-health-gating) to see structured aggregation with guards, then adapt it with `PARALLEL` in your host environment.
