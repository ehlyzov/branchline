---
title: "Handle incomplete contact data"
---

<!-- Generated from playground/examples/recipe-incomplete-data.json; run npm run sync:recipes in playground/. -->

# Handle incomplete contact data

Prepare incomplete contact records for a follow-up queue while preserving the distinction between an unknown contact and a known value.

[Open this recipe in the Playground](../playground.md?example=recipe-incomplete-data){ .md-button }

Prerequisites:

- id is required. contact is optional, with optional email and phone text fields or null.
- tags is an optional array of text or null. An absent or null tags field means no tags for this workflow.

## Program

Save this complete transform as [program.bl](../downloads/recipe-incomplete-data/program.bl):

--8<-- "includes/recipes/recipe-incomplete-data/program.md"

## Input example

Save this JSON as [input.json](../downloads/recipe-incomplete-data/input.json):

--8<-- "includes/recipes/recipe-incomplete-data/input.md"

## Expected output

--8<-- "includes/recipes/recipe-incomplete-data/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/recipe-incomplete-data/expected.json).

## Why this works

- The required identifier is checked before producing a result.
- contact uses an empty object before reading optional leaves. A missing leaf is null; indexing a null parent would fail.
- email and phone stay null when absent or explicitly null. No placeholder contact detail is invented.
- tags uses an empty collection only for absent or null tags. needs_contact is true when both contact fields are null.

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-incomplete-data/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-incomplete-data/input.json
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

Set contact.email to "ada@example.com", then run again.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/recipe-incomplete-data/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/recipe-incomplete-data/exercise-expected.md"

You can also download [exercise-input.json](../downloads/recipe-incomplete-data/exercise-input.json)
and replace `--input input.json` with `--input exercise-input.json` in either command.

## Edge cases and limits

--8<-- "includes/recipes/recipe-incomplete-data/edge-cases.md"

- This checks presence only. Blank strings count as present; normalize and validate contacts first when blank text should mean missing.
- Wrong input types are outside this recipe schema; tags must be an array when supplied.
- The program does not send messages or update customer records.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
