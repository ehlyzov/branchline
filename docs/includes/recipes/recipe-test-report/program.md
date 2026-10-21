<!-- Generated from playground/examples/recipe-test-report.json; run npm run sync:recipes in playground/. -->

```branchline
TRANSFORM SummarizeTests {
    ASSERT(input.tests != null, "Tests array is required")
    ASSERT(EVERY(input.tests, (test) -> test.name != null), "Every test needs a name")
    ASSERT(EVERY(input.tests, (test) -> test.duration_ms != null), "Every test needs duration_ms")
    ASSERT(EVERY(input.tests, (test) -> test.duration_ms >= 0), "Durations must be nonnegative")
    ASSERT(EVERY(input.tests, (test) -> test.status == "passed" || test.status == "failed" || test.status == "skipped"), "Unknown test status")
    LET passed = FILTER(input.tests, (test) -> test.status == "passed")
    LET failed = FILTER(input.tests, (test) -> test.status == "failed")
    LET skipped = FILTER(input.tests, (test) -> test.status == "skipped")
    LET status = CASE {
        WHEN LENGTH(failed) > 0 THEN "failing"
        WHEN LENGTH(passed) == 0 THEN "no-runs"
        ELSE "passing"
    }
    OUTPUT {
        status: status,
        total: LENGTH(input.tests),
        passed: LENGTH(passed),
        failed: LENGTH(failed),
        skipped: LENGTH(skipped),
        duration_ms: SUM(MAP(input.tests, (test) -> test.duration_ms)),
        failed_tests: MAP(failed, (test) -> test.name)
    }
}
```
