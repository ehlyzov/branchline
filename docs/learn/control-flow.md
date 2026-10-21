---
title: Control Flow for Pipelines
---

# Control Flow for Pipelines

Use `IF` to choose an output from a build's status.

## Program

Save as `program.bl`:

```branchline
TRANSFORM PipelineHealth {
    LET status = input.build.status
    IF status == "success" THEN {
        OUTPUT { deploy: true, status: status }
    } ELSE {
        OUTPUT { deploy: false, status: status, reason: input.build.reason }
    }
}
```

## Input example

Save as `input.json`:

```json
{"build":{"status":"failed","reason":"Tests failed"}}
```

## Expected output

```json
{"deploy":false,"status":"failed","reason":"Tests failed"}
```

## Why this works

`status` reads `input.build.status`. Only the `"success"` value takes the first
branch; every other value takes the second. With `{"build":{"status":"success"}}`,
the result is `{"deploy":true,"status":"success"}`. This example assumes a build
object; a missing status on that object yields `null`, takes the second branch, and keeps
`deploy` false. A missing reason also yields `null`.

For collections, continue with [FOR EACH](../guides/for-each.md) and
[array comprehensions](../guides/array-comprehension.md).

## Validation commands

Paste the program and input into the [Playground](../playground.md), then run and
compare the result. Or, after [local setup](../guides/install.md), run either CLI:

```bash
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json
node branchline-cli/bin/bl.cjs program.bl --input input.json
```

Continue to [Error Handling and Tracing](error-handling.md).
