---
title: Error Handling and Tracing
---

# Error Handling and Tracing

Branchline is built for debuggable pipelines. Use `TRY/CATCH` for retries and `EXPLAIN`/`ASSERT` to keep data quality visible.

## Goal
Handle failures safely and emit trace output for diagnostics.

## Example
```branchline
TRANSFORM SafeParse {
    TRY {
        OUTPUT { value: INPUT.payload.value };
    } CATCH {
        OUTPUT { value: null, error: "Missing value" };
    }
}
```

## Try it in the Playground
- [error-handling-try-catch](../playground.md?example=error-handling-try-catch){ target="_blank" }
- [stdlib-debug-explain](../playground.md?example=stdlib-debug-explain){ target="_blank" }

## Related guide
- [TRY/CATCH](../guides/try-catch.md)

## Next steps
Finish the tour with [Real-world Recipes](recipes.md).
