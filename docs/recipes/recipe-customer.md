---
title: "Normalize a customer"
---

<!-- Generated from playground/examples/recipe-customer.json; run npm run sync:recipes in playground/. -->

# Normalize a customer

Convert a customer record into a consistent API payload without inventing an email address or city.

[Open this recipe in the Playground](../playground.md?example=recipe-customer){ .md-button }

Prerequisites:

- Use JSON input. id is a required customer identifier; name is required text.
- email is optional text or null; address is an optional object or null with an optional city.

## Program

Save this complete transform as [program.bl](../downloads/recipe-customer/program.bl):

--8<-- "includes/recipes/recipe-customer/program.md"

## Input example

Save this JSON as [input.json](../downloads/recipe-customer/input.json):

--8<-- "includes/recipes/recipe-customer/input.md"

## Expected output

--8<-- "includes/recipes/recipe-customer/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/recipe-customer/expected.json).

## Why this works

- ASSERT rejects absent required fields and a name that becomes blank after trimming.
- name removes outer whitespace. email removes outer whitespace and lowercases text; blank or absent email becomes null.
- The output explicitly maps id to customer_id and keeps an unknown city as null.
- address uses an empty object before reading city: a missing leaf returns null, but reading through a null parent would fail.

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
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-customer/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-customer/input.json
bl program.bl --input input.json
```

With these original files, compare with **Expected output** above.

## Try one edit

Change email to "  ADA+NEWS@EXAMPLE.COM  ", then run again.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/recipe-customer/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/recipe-customer/exercise-expected.md"

You can also download [exercise-input.json](../downloads/recipe-customer/exercise-input.json)
and replace `--input input.json` with `--input exercise-input.json` in either command.

## Edge cases and limits

--8<-- "includes/recipes/recipe-customer/edge-cases.md"

- This performs text normalization, not email validation or identity verification.
- The entire email is lowercased by this recipe policy. Keep the original address if your mail system requires case preservation.
- Unknown optional fields stay null; other input fields are omitted.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
