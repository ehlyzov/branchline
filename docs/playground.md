---
title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
---

# Interactive Playground

Pick an example, edit the program and input, then Run Branchline in your browser.

<link rel="stylesheet" href="../assets/playground.css">

<div class="playground-embed">
  <div class="playground-frame">
    <div class="bl-playground" data-default-example="customer-profile" role="region" aria-label="Branchline playground embed"></div>
  </div>
  <div class="playground-embed__actions">
    <a href="../playground/" target="_blank" rel="noreferrer" class="md-button md-button--primary" data-playground-open-new-tab>Open original example in new tab</a>
    <span class="playground-embed__hint">The new tab opens the original example; it does not transfer your edits. Use <code>?example=&lt;id&gt;</code> to preload an example.</span>
  </div>
</div>

<script type="module" src="../assets/playground.js"></script>
<script>
  (() => {
    const link = document.querySelector('[data-playground-open-new-tab]');
    if (!link || typeof window === 'undefined') {
      return;
    }
    const setHref = () => {
      const target = new URL(link.getAttribute('href') || '../playground/', window.location.href);
      target.search = '';
      const params = new URLSearchParams(window.location.search);
      const fromUrl = params.get('example');
      const selector = document.querySelector('[data-playground-example-select]');
      const fromPlayground = selector && 'value' in selector ? selector.value : null;
      const example = fromPlayground || fromUrl;
      if (example) {
        target.searchParams.set('example', example);
      }
      link.setAttribute('href', target.toString());
    };
    setHref();
    link.addEventListener('click', setHref);
  })();
</script>

## Curated examples (all preserved)

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

## Troubleshooting
- If the embed does not appear, open the new-tab link above.
- XML inputs are parsed into objects; attributes use the `@attr` convention and text nodes use `#text`.
- The playground runs entirely in your browser using the Kotlin/JS interpreter; no data leaves your machine.

## Contract JSON in Playground
- Enable **Contracts** to inspect inferred input/output contracts.
- Keep debug off for clean JSON intended for docs/tooling.
- Enable **Contract debug** when you need `origin` and available span metadata.

## Inspect workbench
- Run an example and use **Inspect → Normalized source** to copy the canonical form for docs, agents, and repair loops.
- Use **Diagnostics** for parser, contract, and runtime-facing messages before executing a changed program repeatedly.
- Use **Subset blockers** when an example is runnable Branchline but not AI-canonical; blockers are driven by the same inspect compatibility result used by CLI automation.
