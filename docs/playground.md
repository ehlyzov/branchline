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
    <a href="../playground/demo.html" target="_blank" rel="noreferrer" class="md-button md-button--primary">Open in new tab</a>
    <span class="playground-embed__hint">Use <code>?example=&lt;id&gt;</code> in the URL to preload an example (e.g., <code>?example=stdlib-hof-overview</code>).</span>
  </div>
</div>

<script type="module" src="../assets/playground.js"></script>

## Curated examples (all preserved)
- [collection-transforms](?example=collection-transforms){ target="_blank" } — MAP/FILTER/REDUCE style array work
- [customer-profile](?example=customer-profile){ target="_blank" } — basic enrichment and null coalescing
- [explain-derived-total](?example=explain-derived-total){ target="_blank" } — enable tracing to see EXPLAIN output
- [junit-badge-summary](?example=junit-badge-summary){ target="_blank" } — XML parsing and normalization
- [order-shipment](?example=order-shipment){ target="_blank" } — object reshaping and path navigation
- [pipeline-health-gating](?example=pipeline-health-gating){ target="_blank" } — ASSERT/CHECKPOINT with trace
- [error-handling-try-catch](?example=error-handling-try-catch){ target="_blank" } — TRY/CATCH with ASSERT fallbacks
- [shared-memory-basics](?example=shared-memory-basics){ target="_blank" } — SHARED declarations and writes
- Standard library coverage:
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
