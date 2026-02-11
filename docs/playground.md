---
title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
---

# Interactive Playground

Experiment with Branchline directly in your browser—no installs required. Pick an example, edit the program and input, enable tracing, and run.

<link rel="stylesheet" href="../assets/playground.css">

<div class="playground-embed">
  <div class="playground-embed__intro"><strong>Tips:</strong> Cmd/Ctrl + Enter runs. Switch JSON/XML input. Toggle tracing for <code>EXPLAIN(...)</code>. Changing examples resets the editors.</div>
  <div class="playground-frame">
    <div class="bl-playground" data-default-example="customer-profile" role="region" aria-label="Branchline playground embed"></div>
  </div>
  <div class="playground-embed__actions">
    <a href="../playground/" target="_blank" rel="noreferrer" class="md-button md-button--primary" data-playground-open-new-tab>Open in new tab</a>
    <span class="playground-embed__hint">Use <code>?example=&lt;id&gt;</code> in the URL to preload an example (e.g., <code>?example=stdlib-hof-overview</code>).</span>
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

## Troubleshooting
- If the embed does not appear, open the new-tab link above.
- XML inputs are parsed into objects; attributes use the `@attr` convention and text nodes use `#text`.
- The playground runs entirely in your browser using the Kotlin/JS interpreter; no data leaves your machine.

## Contract JSON in Playground
- Enable **Contracts** to inspect inferred input/output contracts.
- Keep debug off for clean JSON intended for docs/tooling.
- Enable **Contract debug** when you need `origin` and available span metadata.
