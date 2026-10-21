<!-- Generated from playground/examples/recipe-test-report.json; run npm run sync:recipes in playground/. -->

- **Empty report has no runs:** produce `{"status":"no-runs","total":0,"passed":0,"failed":0,"skipped":0,"duration_ms":0,"failed_tests":[]}`.
- **Skipped-only report is not passing:** produce `{"status":"no-runs","total":1,"passed":0,"failed":0,"skipped":1,"duration_ms":0,"failed_tests":[]}`.
- **Missing duration is rejected:** reject with `Every test needs duration_ms`.
- **Unknown status is rejected:** reject with `Unknown test status`.
