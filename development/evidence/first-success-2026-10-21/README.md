# First-success journey acceptance — 2026-10-21

Three GPT-6.1 Sol High agents implemented docs/fixtures, home/navigation and
editor changes. Parent independently checked actual runtime and browser behavior.
A separate read-only cross-review found no remaining P1 in this slice.

## Accepted local facts

- Published v1.0.0-rc.1 CLI: 8/8 independent cases across JVM and Node22,
  default order, edited quantity, empty items and null city. See cli-results.json.
- Two more executions used the actual files downloaded by clicking the changed
  editor UI; both produced BOOK,PEN. See browser-download-results.json.
- Existing GettingStartedExamplesTest: 7 JVM tests; unfiltered conformance Node:
  139 tests; JUnit XML failures/errors zero. Three original marked lessons unchanged.
- npm run build includes all playground tests: generated fixture/download/snippet
  consistency, 12 actual facade executions for starter/tour, catalog/XML/worker
  dependencies, real wrapper diagnostics, exact current-edit export checks.
- Agent also executed corrected tour examples and edge cases with published JVM/
  Node CLI: 16/16; parent independently reviewed code/oracles and durable JS gate.
- MkDocs production build succeeded with installed requirements (Material9.4.8,
  autorefs0.5.0, MkDocs1.6.1). Generated include pages are excluded from site/search.
- bin/audit_contour.sh and git diff --check passed.
- Local browser checks and limits: local-browser.json. Screens, build logs and
  full network evidence: output/first-success-2026-10-21/.

## Browser journey

1. Home — purpose, input/code/output and CTA visible at1280x720; no overflow390px.
2. Example — prepare-order runs to {order_id:A-104,city:London,skus:[BOOK]}.
3. Edit — PEN qty1 produces BOOK,PEN; changing filter >1 produces BOOK.
4. Recovery — malformed JSON and program errors visible; correction reruns.
5. Local execution — downloaded current files execute unchanged on JVM and Node.
6. Compact mode — arrow/Home tab navigation, Run focuses Output, final390px result
   visible without page horizontal overflow. Existing customer-profile still runs.

This is functional/expert UX acceptance, not user research or accessibility
certification. No language runtime behavior, published RC package or version was
changed. Public deployment verification follows the accepted source commit and
must use release-metadata.json plus real browser execution, not HTTP200 alone.
