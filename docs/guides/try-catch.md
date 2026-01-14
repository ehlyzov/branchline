---
title: TRY/CATCH
---

# TRY/CATCH

`TRY/CATCH` handles failures without aborting the whole transform.

## When to use it
Use `TRY/CATCH` around risky lookups, parsing, or assertions so you can emit a safe fallback value.

## Syntax
```branchline
LET value = TRY parse(input.raw)
CATCH(err) => { ok: false, error: err.message };
```

## Example
```branchline
TRANSFORM SafeTotals {
    LET total = TRY SUM(input.items)
    CATCH(err) => 0;

    OUTPUT { total: total };
}
```

## Retry logic
Use `RETRY` with `TIMES` and optional `BACKOFF` for transient failures.

```branchline
LET result = TRY fetch(input.url)
CATCH(err) RETRY 3 TIMES BACKOFF "200ms" => null;
```

## Pitfalls
- Keep the `TRY` body small so errors are easy to interpret.
- Prefer `ASSERT` for explicit failure conditions; use `TRY/CATCH` to keep the pipeline running.

## Try it
- [error-handling-try-catch](../playground.md?example=error-handling-try-catch){ target="_blank" }
- [stdlib-debug-explain](../playground.md?example=stdlib-debug-explain){ target="_blank" }

## Related
- [Error Handling and Tracing](../learn/error-handling.md)
- [Statements](../language/statements.md#try)
