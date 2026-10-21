<!-- Generated from playground/examples/recipe-pipeline-guard.json; run npm run sync:recipes in playground/. -->

```branchline
TRANSFORM PipelineGuard {
    ASSERT(input.run_id != null, "Pipeline run_id is required")
    ASSERT(input.branch != null, "Pipeline branch is required")
    ASSERT(input.checks != null, "Pipeline checks array is required")
    ASSERT(LENGTH(input.checks) > 0, "At least one pipeline check is required")
    ASSERT(EVERY(input.checks, (check) -> check.name != null), "Every check needs a name")
    ASSERT(EVERY(input.checks, (check) -> check.status == "passed" || check.status == "failed"), "Unknown pipeline check status")
    ASSERT(EVERY(input.checks, (check) -> check.status == "passed"), "Pipeline checks must pass")
    OUTPUT {
        run_id: input.run_id,
        branch: input.branch,
        deploy: true,
        checked_names: MAP(input.checks, (check) -> check.name)
    }
}
```
