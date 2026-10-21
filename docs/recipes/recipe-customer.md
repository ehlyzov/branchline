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

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-customer/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-customer/input.json
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
