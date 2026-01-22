---
title: Control Flow for Pipelines
---

# Control Flow for Pipelines

Use `IF`, `FOR EACH`, and array comprehensions to filter and reshape data.

## Goal
Only ship healthy builds and annotate the output when checks fail.

## Example
```branchline
TRANSFORM PipelineHealth {
    LET status = INPUT.build.status;

    IF status == "success" {
        OUTPUT { deploy: true, status: status };
    } ELSE {
        OUTPUT { deploy: false, status: status, reason: INPUT.build.reason };
    }
}
```

## Try it in the Playground
- [pipeline-health-gating](../playground.md?example=pipeline-health-gating){ target="_blank" }

## Related guides
- [FOR EACH Loops](../guides/for-each.md)
- [Array Comprehensions](../guides/array-comprehension.md)

## Next steps
Continue to [Error Handling and Tracing](error-handling.md).
