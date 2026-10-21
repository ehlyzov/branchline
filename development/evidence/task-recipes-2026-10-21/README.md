# Six recipes, task catalog and form usability acceptance

## Scope and source
Three GPT-6.1 Sol High agents implemented recipes, catalog and UI separately. Parent reviewed source, independently tested the published RC CLIs and actual browser, and owns publication.

The six tasks are customer normalization, order shaping, paid-sales summary, incomplete contacts, JSON test summary and pipeline input rejection. All 41 prior effective IDs, executable fixtures and effective AI statuses are preserved; five new fixtures make 46 catalog entries. No language/runtime/package-version change.

## Local acceptance
- Published v1.0.0-rc.1 JVM and Node22 CLI: **88/88** positive/error checks. `verify-cli.py` runs documented base/exercise/edges plus eleven independent business cases in `independent-cases.json`. Errors require nonzero status and no output JSON. Program hashes and outputs: `cli-results.json`.
- `npm --prefix playground test`: pass. Includes 36 recipe JS executions (13 expected rejections), six actual compatible inspections, generated 72-file drift gate, 12 starter/tour runs, catalog/group/filter completeness, exact-edit downloads, XML and wrapper diagnostics.
- Vite production build: pass. Final CSS-only contrast delta rebuilt; ordinary bundle-size warning remains. Actual MkDocs build: pass, no new broken links. Two pre-existing benchmark relative-link informational messages remain.
- `bin/audit_contour.sh`: pass. Verification contour updated because new executable documentation generator/check was added; architecture/topology unchanged.
- Parent browser on built MkDocs: six base programs pass; edited failed pipeline emits expected error; filter retains the edited current example outside results; syntax error displays location and restoration executes successfully; console errors/warnings empty.
- Expanded settings verified at 1280px and390px; mobile page scrollWidth=clientWidth=390, all four select fields left30/right360/width330. Consistent grid and checkbox groups. Nested details padded. Dark palette action text corrected to rgb(191,219,254), visually checked. Correct local-run guide anchor confirmed in real browser.

Raw local screenshots/logs: `output/task-recipes-2026-10-21/`. This is independent engineering UX acceptance, not a study with external participants or a full accessibility audit.

## Publication
Pending exact-commit CI, Pages deployment and public-browser acceptance. These are required before final completion.
