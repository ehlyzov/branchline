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

First follow the [one-command installation guide](../guides/install.md).
**No preinstalled Java or Node is needed:** it downloads a private runtime and the
published **v1.0.0-rc.1** CLI as `bl`, and explains runtime/platform limits.

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
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-sales-summary/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-sales-summary/input.json
bl program.bl --input input.json
```

With these original files, compare with **Expected output** above.

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
