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

First follow the [one-command installation guide](../guides/install.md) with
**Java 21/25** or **Node 22/24**. It installs the published **v1.0.0-rc.1** CLI as
`bl` and explains runtime/platform limits.

**Already downloaded your edited files from the Playground?** Keep
`program.bl` and `input.json` together, skip the original downloads below, and run:

```bash
bl program.bl --input input.json
```

Use the expected result for your edit (see **Try one edit**), rather than the
original result above. For an ASSERT rejection, the CLI exits with an error and
emits no result JSON.

### Optional: get the original example

Only if you have no files yet, download fresh copies into a new directory.
These commands overwrite files with the same names:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-test-report/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-test-report/input.json
bl program.bl --input input.json
```

With these original files, compare with **Expected output** above.

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
