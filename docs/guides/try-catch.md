---
title: Error Handling with TRY/CATCH
---

# Error Handling with TRY/CATCH

Use `TRY` / `CATCH` to handle failures and optionally retry operations. `TRY`
is an expression, so it can be used in `LET`, `CASE`, or output templates.

## Catching failures

```branchline
TRY riskyCall()
CATCH(e) => { err: e.message };
```

If `riskyCall()` throws, the fallback block produces an error object. The
identifier in `CATCH(...)` is bound to a small error object with `message` and
`type` fields.

## Retrying

Add `RETRY n TIMES` to re-run the expression before giving up.

```branchline
TRY fetch(input.id)
CATCH(e) RETRY 3 TIMES => { err: "failed" };
```

The call is attempted three additional times. If all attempts fail, the fallback runs.

## With backoff
```branchline
TRY remoteService(input)
CATCH(err) RETRY 2 TIMES BACKOFF 500ms => {
    error: err.message ?? "service failed"
};
```
Adds a fixed backoff between attempts. (Host must support `CALL` and backoff semantics.)

## Guardrails with ASSERT
Combine `ASSERT` to fail fast on bad states:
```branchline
LET result = TRY risky(input) CATCH(e) => NULL;
ASSERT(result != NULL, "Risky call failed");
```

## Tips
- `TRY` wraps expressions; use it sparingly and close to the failing call.
- Provide clear fallback values/objects for downstream logic.
- `BACKOFF` units follow host runtime defaults (e.g., ms).
- Pair with `CHECKPOINT` when you need progress markers around retries.

## Try it
Open the [pipeline-health-gating](../playground.md?example=pipeline-health-gating) example in the playground to see ASSERT and tracing in action; adapt it to wrap `TRY/CATCH` around external calls in your own snippets.
