---
title: "Prepare an order payload"
---

<!-- Generated from playground/examples/prepare-order.json; run npm run sync:recipes in playground/. -->

# Prepare an order payload

Prepare a shipment preview containing an order identifier, a city, and only the SKUs whose quantities are positive.

[Open this recipe in the Playground](../playground.md?example=prepare-order){ .md-button }

Prerequisites:

- id is an order identifier. shipping must be an object; city is optional text or null.
- items is an array of objects with sku text and numeric qty. The input producer supplies these required fields.

## Program

Save this complete transform as [program.bl](../downloads/prepare-order/program.bl):

--8<-- "includes/recipes/prepare-order/program.md"

## Input example

Save this JSON as [input.json](../downloads/prepare-order/input.json):

--8<-- "includes/recipes/prepare-order/input.md"

## Expected output

--8<-- "includes/recipes/prepare-order/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/prepare-order/expected.json).

## Why this works

- order_id copies input.id. The output explicitly selects three fields, leaving other order data out.
- The comprehension keeps only items with qty > 0 and returns their sku values in input order.
- An absent or null city becomes the display label "Not provided". The label is not a deliverable address; the payload is a shipment preview.

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/prepare-order/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/prepare-order/input.json
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

Change PEN qty from 0 to 1, then run again.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/prepare-order/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/prepare-order/exercise-expected.md"

Edit the JSON input in the browser, or save the edited JSON over input.json and rerun the same local command.

## Edge cases and limits

--8<-- "includes/recipes/prepare-order/edge-cases.md"

- This starter assumes valid item objects and a shipping object; it does not validate them.
- Zero and negative quantities are excluded. Duplicate SKUs are preserved, and quantities are not included in the result.
- Validate a real shipping address before using the result for fulfillment.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
