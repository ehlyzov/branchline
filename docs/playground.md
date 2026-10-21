---
title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
hide:
  - navigation
  - toc
---

<div class="playground-page-intro" markdown="1">

# Interactive Playground

Run the example, then change PEN’s quantity. No installation needed.

</div>

<link rel="stylesheet" href="../assets/playground.css">

<div class="playground-embed">
  <div class="playground-frame">
    <div class="bl-playground" data-default-example="prepare-order" role="region" aria-label="Branchline playground embed"></div>
  </div>
</div>

<script type="module" src="../assets/playground.js"></script>

## Curated examples (all preserved)

- [prepare-order](?example=prepare-order) — keep items with a positive quantity, then download your edited program and JSON input for the [local run](guides/getting-started.md#run-locally).

Open **Filters & run settings** to narrow the catalog by category and AI subset. Filtering keeps your current draft selected, even if it falls outside the filter. Examples marked AI compatible are intended for inspect-first authoring loops; examples marked incompatible are still runnable language examples, but Inspect will explain why they are outside the AI canonical subset.

- [collection-transforms](?example=collection-transforms){ target="_blank" } — MAP/FILTER/REDUCE style array work
- [contract-deep-composition](?example=contract-deep-composition){ target="_blank" } — deep nested output inferred from stdlib-first composition
- [contract-literal-brackets-static](?example=contract-literal-brackets-static){ target="_blank" } — literal bracket keys inferred as static paths
- [contract-empty-array-append](?example=contract-empty-array-append){ target="_blank" } — stable array element typing via `FOR EACH ... WHERE` and `[] + APPEND`
- [customer-profile](?example=customer-profile){ target="_blank" } — concise profile shaping with FORMAT/GET and CASE fallback
- [explain-derived-total](?example=explain-derived-total){ target="_blank" } — enable tracing to see EXPLAIN output
- [junit-badge-summary](?example=junit-badge-summary){ target="_blank" } — XML normalization via FILTER/MAP/SUM pipelines
- [order-shipment](?example=order-shipment){ target="_blank" } — shipping snapshot with GET/FORMAT and CASE classification
- [pipeline-health-gating](?example=pipeline-health-gating){ target="_blank" } — CASE-driven health gating with ASSERT/CHECKPOINT + trace
- [error-handling-try-catch](?example=error-handling-try-catch){ target="_blank" } — TRY/CATCH with ASSERT fallbacks
- [shared-memory-basics](?example=shared-memory-basics){ target="_blank" } — SHARED declarations and writes
- Standard library coverage:
  [stdlib-case-basic](?example=stdlib-case-basic){ target="_blank" },
  [stdlib-core-keys-values](?example=stdlib-core-keys-values){ target="_blank" },
  [stdlib-core-put-delete](?example=stdlib-core-put-delete){ target="_blank" },
  [stdlib-core-append-prepend](?example=stdlib-core-append-prepend){ target="_blank" },
  [stdlib-core-walk](?example=stdlib-core-walk){ target="_blank" },
  [stdlib-arrays-overview](?example=stdlib-arrays-overview){ target="_blank" },
  [stdlib-agg-overview](?example=stdlib-agg-overview){ target="_blank" },
  [stdlib-strings-casts](?example=stdlib-strings-casts){ target="_blank" },
  [stdlib-strings-text](?example=stdlib-strings-text){ target="_blank" },
  [stdlib-numeric-precision](?example=stdlib-numeric-precision){ target="_blank" },
  [stdlib-hof-overview](?example=stdlib-hof-overview){ target="_blank" },
  [stdlib-debug-explain](?example=stdlib-debug-explain){ target="_blank" },
  [stdlib-time-now](?example=stdlib-time-now){ target="_blank" }

## Editing and running

- Provide a JSON object (or mapped XML) as input. Reference it with `input`, for example `OUTPUT { name: input.name }`.
- The program accepts a transform body or a complete `TRANSFORM` program. With multiple transforms, the playground runs `Playground` if present, otherwise the first transform.
- Edits and run settings are kept per example in memory for this tab only. Reloading or closing the tab discards them. **Reset example** asks before discarding a modified draft.
- Editing a program, input, or run setting marks output and Inspect as out of date. Run again to refresh them.
- **Stop** terminates a running worker, including a program that does not finish. Run starts a fresh worker after Stop or a worker failure.
- On smaller screens, switch between **Input**, **Program**, and **Output**. Use the left/right arrow keys, Home, or End while a panel tab is focused. Run opens Output; syntax and input errors remain visible above the panels.
- The order starter offers **Download program.bl** and **Download input.json**. These contain your current edits, including the complete `TRANSFORM` program. Fix JSON syntax errors before downloading. Follow [Run locally](guides/getting-started.md#run-locally) with these filenames; downloads do not include browser run settings.

## Troubleshooting
- If the editor does not appear, reload the page and check that JavaScript is enabled.
- XML inputs are parsed into objects; attributes use the `@attr` convention and text nodes use `#text`.
- The playground runs entirely in your browser using the Kotlin/JS interpreter; no data leaves your machine.

## Contract JSON in Playground
- Enable **Show input/output contracts** under **Filters & run settings**, then expand **Input/output contracts** in Output.
- Keep debug off for clean JSON intended for docs/tooling.
- Enable **Contract debug** when you need `origin` and available span metadata.

## Inspect workbench
- Run an example and expand **Inspect source and diagnostics → Normalized source** to copy the canonical form for docs, agents, and repair loops.
- Use **Diagnostics** for parser, contract, and runtime-facing messages before executing a changed program repeatedly.
- Use **Subset blockers** when an example is runnable Branchline but not AI-canonical; blockers are driven by the same inspect compatibility result used by CLI automation.
