---
title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
hide:
  - navigation
  - toc
---

<div class="playground-page-intro" markdown="1">

# Interactive Playground

Choose a task, run an example, then edit the input and run again. No installation needed.

</div>

<link rel="stylesheet" href="../assets/playground.css">

<div class="playground-embed">
  <div class="playground-frame">
    <div class="bl-playground" data-default-example="prepare-order" role="region" aria-label="Branchline playground embed"></div>
  </div>
</div>

<script type="module" src="../assets/playground.js"></script>

## Choose a recipe

| Your task | Full recipe | Run now |
| --- | --- | --- |
| Normalize a customer | [Guide](recipes/recipe-customer.md) | [Open example](?example=recipe-customer) |
| Prepare an order payload | [Guide](recipes/prepare-order.md) | [Open example](?example=prepare-order) |
| Summarize sales | [Guide](recipes/recipe-sales-summary.md) | [Open example](?example=recipe-sales-summary) |
| Handle incomplete data | [Guide](recipes/recipe-incomplete-data.md) | [Open example](?example=recipe-incomplete-data) |
| Summarize a JSON test report | [Guide](recipes/recipe-test-report.md) | [Open example](?example=recipe-test-report) |
| Guard a pipeline | [Guide](recipes/recipe-pipeline-guard.md) | [Open example](?example=recipe-pipeline-guard) |

Use **Task** above the editor to filter examples by what you want to do. **Example** groups common and advanced tasks. Browse the [complete example catalog](guides/examples.md) for XML, numeric, text, tracing, contract, shared-state, and configuration examples.

Filtering keeps your current draft selected, even when it falls outside the filter. Open **Run settings & details** for AI subset filters and execution options. AI compatible examples support the inspect-first authoring subset; this status does not establish the correctness of your business rules.

## Editing and running

- Provide a JSON object (or mapped XML) as input. Reference it with `input`, for example `OUTPUT { name: input.name }`.
- The program accepts a transform body or a complete `TRANSFORM` program. With multiple transforms, the playground runs `Playground` if present, otherwise the first transform.
- Edits and run settings are kept per example in memory for this tab only. Reloading or closing the tab discards them. **Reset example** asks before discarding a modified draft.
- Editing a program, input, or run setting marks output and Inspect as out of date. Run again to refresh them.
- **Stop** terminates a running worker, including a program that does not finish. Run starts a fresh worker after Stop or a worker failure.
- On smaller screens, switch between **Input**, **Program**, and **Output**. Use the left/right arrow keys, Home, or End while a panel tab is focused. Run opens Output; syntax and input errors remain visible above the panels.
- All six recipes offer **Download program.bl** and **Download input.json**. These contain your current edits, including the complete `TRANSFORM` program. Fix JSON syntax errors before downloading. Follow [Run locally](guides/getting-started.md#run-locally) with these filenames; downloads do not include browser run settings.

## Troubleshooting
- If the editor does not appear, reload the page and check that JavaScript is enabled.
- XML inputs are parsed into objects; attributes use the `@attr` convention and text nodes use `#text`.
- The playground runs entirely in your browser using the Kotlin/JS interpreter; no data leaves your machine.

## Contract JSON in Playground
- Enable **Show input/output contracts** under **Run settings & details**, then expand **Input/output contracts** in Output.
- Keep debug off for clean JSON intended for docs/tooling.
- Enable **Contract debug** when you need `origin` and available span metadata.

## Inspect workbench
- Run an example and expand **Inspect source and diagnostics → Normalized source** to copy the canonical form for docs, agents, and repair loops.
- Use **Diagnostics** for parser, contract, and runtime-facing messages before executing a changed program repeatedly.
- Use **Subset blockers** when an example is runnable Branchline but not AI-canonical; blockers are driven by the same inspect compatibility result used by CLI automation.
