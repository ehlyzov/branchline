---
title: Error Handling and Tracing
---

# Error Handling and Tracing

Use `ASSERT` to make a data requirement explicit and `TRY/CATCH` to return a
fallback when that check fails.

## Program

Save as `program.bl`:

```branchline
TRANSFORM SafeValue {
    LET guard = TRY ASSERT(input.payload.value != null, "Missing value")
    CATCH(err) => { ok: false, error: err.message }
    OUTPUT { value: input.payload.value, guard: guard }
}
```

## Input example

Save as `input.json`:

```json
{"payload":{}}
```

## Expected output

```json
{"value":null,"guard":{"ok":false,"error":"Missing value"}}
```

## Why this works

Reading the missing `input.payload.value` produces `null`; the lookup itself does
not throw. `ASSERT` fails because the value is `null`. `TRY` evaluates that check,
and `CATCH(err)` turns the failure into an object with the error message.
The output keeps the missing value as `null` alongside the guard result.
A value of `0` passes the `!= null` check, producing `{"value":0,"guard":true}`.

`TRY/CATCH` handles one attempted expression. Retry requires explicit retry syntax;
see the [TRY/CATCH guide](../guides/try-catch.md). For tracing after a first working
result, explore [EXPLAIN](../playground.md?example=stdlib-debug-explain).

## Validation commands

Paste this program and input into the [Playground](../playground.md), then run and
compare the result. Or, after [local setup](../guides/install.md), run either CLI:

```bash
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json
node branchline-cli/bin/bl.cjs program.bl --input input.json
```

Finish the tour with [Real-world Recipes](recipes.md).
