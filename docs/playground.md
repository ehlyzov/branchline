title: Interactive Playground
description: Try Branchline in your browser with the interactive playground.
---

# Interactive Playground

Experiment with Branchline directly in your browser—no installs required. Pick an example, edit the program and input, enable tracing, and run.

<link rel="stylesheet" href="../assets/playground.css">

<div class="playground-embed">
  <div class="playground-embed__intro">
    <p><strong>Tips:</strong> Cmd/Ctrl + Enter runs the playground. Switch input between JSON and XML. Toggle tracing to view <code>EXPLAIN(...)</code> provenance. Switching examples resets the editors.</p>
  </div>
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
- `collection-transforms` — MAP/FILTER/REDUCE style array work
- `customer-profile` — basic enrichment and null coalescing
- `explain-derived-total` — enable tracing to see EXPLAIN output
- `junit-badge-summary` — XML parsing and normalization
- `order-shipment` — object reshaping and path navigation
- `pipeline-health-gating` — ASSERT/CHECKPOINT with trace
- Standard library coverage: `stdlib-core-keys-values`, `stdlib-core-put-delete`, `stdlib-core-append-prepend`, `stdlib-core-walk`, `stdlib-core-print`, `stdlib-arrays-overview`, `stdlib-agg-overview`, `stdlib-strings-casts`, `stdlib-strings-text`, `stdlib-hof-overview`, `stdlib-debug-explain`, `stdlib-time-now`, `stdlib-shared-await`

## Troubleshooting
- If the embed does not appear, open the new-tab link above.
- XML inputs are parsed into objects; attributes use the `@attr` convention and text nodes use `#text`.
- The playground runs entirely in your browser using the Kotlin/JS interpreter; no data leaves your machine.
