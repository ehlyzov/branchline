<!-- Generated from playground/examples/recipe-pipeline-guard.json; run npm run sync:recipes in playground/. -->

- **Empty checks are rejected:** reject with `At least one pipeline check is required`.
- **Missing checks are rejected:** reject with `Pipeline checks array is required`.
- **Unknown check status is rejected:** reject with `Unknown pipeline check status`.
- **Missing run id is rejected:** reject with `Pipeline run_id is required`.
- **One passing check is accepted:** produce `{"run_id":"build-106","branch":"release","deploy":true,"checked_names":["smoke"]}`.
