---
title: "Summarize a JSON test report"
---

<!-- Generated from playground/examples/recipe-test-report.json; run npm run sync:recipes in playground/. -->

# Summarize a JSON test report

Turn a flat JSON test report into a build summary with counts, recorded duration, and names to investigate.

[Open this recipe in the Playground](../playground.md?example=recipe-test-report){ .md-button }

Prerequisites:

- tests is a required array. Each test needs a name, status passed/failed/skipped, and a nonnegative integer duration_ms.
- This input is plain JSON, not a JUnit XML document.

## Program

Save this complete transform as [program.bl](../downloads/recipe-test-report/program.bl):

--8<-- "includes/recipes/recipe-test-report/program.md"

## Input example

Save this JSON as [input.json](../downloads/recipe-test-report/input.json):

--8<-- "includes/recipes/recipe-test-report/input.md"

## Expected output

--8<-- "includes/recipes/recipe-test-report/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/recipe-test-report/expected.json).

## Why this works

- The assertions prevent missing durations from silently becoming zero and reject unknown outcomes.
- The three filters classify each record. failed_tests keeps the original order of failed names.
- The status is failing if any test failed, no-runs if none passed or failed, and passing otherwise.
- duration_ms sums all recorded durations; an empty report has a zero sum.

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-test-report/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-test-report/input.json
```

Run either command from that directory:

```bash
# JVM: the downloaded release jar
java -jar branchline-cli-v1.0.0-rc.1-all.jar program.bl --input input.json

# Node: the release package extracted into branchline-cli
node branchline-cli/bin/bl.cjs program.bl --input input.json
```

Both commands use the same program and JSON input and should produce the expected
result above. For an ASSERT rejection, the CLI exits with an error and emits no
result JSON.

## Try one edit

Change checkout status from failed to passed, then run again.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/recipe-test-report/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/recipe-test-report/exercise-expected.md"

You can also download [exercise-input.json](../downloads/recipe-test-report/exercise-input.json)
and replace `--input input.json` with `--input exercise-input.json` in either command.

## Edge cases and limits

--8<-- "includes/recipes/recipe-test-report/edge-cases.md"

- Summed durations are recorded test time, not wall-clock build duration when tests run in parallel.
- Retries and duplicate names are counted as separate records. The recipe does not parse test-runner-specific formats.
- Keep durations and totals within the cross-runtime safe integer range.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
