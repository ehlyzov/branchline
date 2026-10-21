---
title: "Summarize paid sales"
---

<!-- Generated from playground/examples/recipe-sales-summary.json; run npm run sync:recipes in playground/. -->

# Summarize paid sales

Produce a small revenue summary from a single-currency sales feed. Count only paid records.

[Open this recipe in the Playground](../playground.md?example=recipe-sales-summary){ .md-button }

Prerequisites:

- currency is required. All sales must already use that one currency.
- sales is a required array. Each sale has status paid, pending, or refunded and a nonnegative integer amount_cents.

## Program

Save this complete transform as [program.bl](../downloads/recipe-sales-summary/program.bl):

--8<-- "includes/recipes/recipe-sales-summary/program.md"

## Input example

Save this JSON as [input.json](../downloads/recipe-sales-summary/input.json):

--8<-- "includes/recipes/recipe-sales-summary/input.md"

## Expected output

--8<-- "includes/recipes/recipe-sales-summary/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/recipe-sales-summary/expected.json).

## Why this works

- The assertions reject missing amounts, negative amounts, and unrecognized statuses before aggregation.
- paid keeps only records whose status is paid. SUM adds their amount_cents, including a legitimate zero amount.
- excluded_count reports records that did not contribute. An empty array has count 0 and total 0.

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-sales-summary/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-sales-summary/input.json
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

Change S-2 status from pending to paid, then run again.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/recipe-sales-summary/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/recipe-sales-summary/exercise-expected.md"

You can also download [exercise-input.json](../downloads/recipe-sales-summary/exercise-input.json)
and replace `--input input.json` with `--input exercise-input.json` in either command.

## Edge cases and limits

--8<-- "includes/recipes/recipe-sales-summary/edge-cases.md"

- A refunded record is excluded; this is gross paid revenue, not a ledger that subtracts refunds.
- No currency conversion, rounding, tax, duplicate detection, or currency minor-unit lookup is performed.
- Keep amounts and the aggregate within the cross-runtime safe integer range; this recipe makes no arbitrary-precision guarantee.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
