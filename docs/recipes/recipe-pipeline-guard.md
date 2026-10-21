---
title: "Reject invalid pipeline input"
---

<!-- Generated from playground/examples/recipe-pipeline-guard.json; run npm run sync:recipes in playground/. -->

# Reject invalid pipeline input

Refuse to create a deployment payload when the pipeline feed is incomplete, empty, or reports an unsuccessful check.

[Open this recipe in the Playground](../playground.md?example=recipe-pipeline-guard){ .md-button }

Prerequisites:

- run_id and branch are required text identifiers. checks is a required nonempty array.
- Every check has a name and status passed or failed. Pending, skipped, missing, and unknown statuses must not authorize deployment.

## Program

Save this complete transform as [program.bl](../downloads/recipe-pipeline-guard/program.bl):

--8<-- "includes/recipes/recipe-pipeline-guard/program.md"

## Input example

Save this JSON as [input.json](../downloads/recipe-pipeline-guard/input.json):

--8<-- "includes/recipes/recipe-pipeline-guard/input.md"

## Expected output

--8<-- "includes/recipes/recipe-pipeline-guard/expected.md"

Compare JSON values; whitespace and object key order can differ.
[Download the expected result](../downloads/recipe-pipeline-guard/expected.json).

## Why this works

- ASSERT stops execution on the first violated requirement, before OUTPUT is reached.
- An explicit nonempty check prevents EVERY on an empty array from authorizing a deploy.
- Only a run whose checks all passed reaches OUTPUT. The payload preserves run_id, branch, and the checked names.

## Validation commands

In the Playground, select **Run** and compare the Result with the expected output.
Then try the edit below. Enable tracing only if you want to inspect execution.

To run locally, choose **Java 21+** or **Node 18+** and follow the
[installation guide](../guides/install.md) to download the published
**v1.0.0-rc.1** CLI. That guide includes the release URLs, extraction commands,
and platform requirements. Save the program and input in the same directory as
your CLI, or download the original files:

```bash
curl -fL -o program.bl https://ehlyzov.github.io/branchline/downloads/recipe-pipeline-guard/program.bl
curl -fL -o input.json https://ehlyzov.github.io/branchline/downloads/recipe-pipeline-guard/input.json
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

Change package status from passed to failed, then run again. Expect an error and no deploy payload.
Keep the program unchanged and use this edited input:

--8<-- "includes/recipes/recipe-pipeline-guard/exercise-input.md"

Expected result after the edit:

--8<-- "includes/recipes/recipe-pipeline-guard/exercise-expected.md"

You can also download [exercise-input.json](../downloads/recipe-pipeline-guard/exercise-input.json)
and replace `--input input.json` with `--input exercise-input.json` in either command.

## Edge cases and limits

--8<-- "includes/recipes/recipe-pipeline-guard/edge-cases.md"

- This is a data gate, not a deployment command or authorization system.
- A trusted producer must supply the complete expected check list. One supplied passing check does not prove other required checks ran.
- Branch protection, identity, timestamps, signatures, and allowed branches need separate policies.

[Choose another recipe](../learn/recipes.md) · [Browse all examples](../guides/examples.md)
