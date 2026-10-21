# Task recipes and expanded-form usability

Status: local acceptance passed; publication in progress. Authorized scope: six practical recipes, task-based example catalog, expanded input/control layout fixes, exact-source Pages publication and independent public acceptance.

## Accepted slice
- Six recipes: normalize a customer, prepare an order (reuse prepare-order), summarize sales, handle incomplete data, summarize a JSON test report, reject invalid pipeline input.
- Each has a complete executable transform, explicit input/result, a small edit with expected result, prerequisites and local-run instructions. Canonical JSON fixtures drive generated docs/downloads and runtime checks.
- Classify all existing examples by user task without changing IDs or existing programs; keep advanced examples discoverable.
- Reproduce and repair expanded settings/input alignment at desktop and mobile widths; preserve keyboard and draft behavior.

## Ownership
- Recipes agent: five new fixtures plus existing starter as sixth, recipe generator/runtime checks and six recipe detail pages/downloads.
- Catalog agent: existing example metadata, catalog helper/tests, recipe and examples landing pages.
- UI agent: playground TSX/CSS, journey helper/tests and global stylesheet only when needed.
- Parent: integration, package scripts, navigation, development records, independent business oracles, browser acceptance, commit/push/deploy.

## Gates
1. Six independent result/exercise/edge oracles pass published JVM and Node CLI; generated documentation is current.
2. Existing example IDs and behavior remain; every example has a meaningful task category; catalog filters work.
3. Fresh browser checks all recipes, changed input/rerun, syntax error/recovery, expanded controls at 1280 and 390 px, no overflow/errors.
4. Playground tests/build and actual MkDocs build pass. Required CI for accepted commit passes; deploy exact SHA.
5. Public metadata matches SHA/run; fresh public browser verifies recipes and expanded UI; save evidence.

No syntax, runtime, numerical guarantees or package version changes are intended. Contour verification instructions will be updated for new executable recipe checks.

## Local result
88/88 published JVM/Node CLI checks;36 JS recipe scenarios;all6 real-browser runs;41 old fixture behaviors preserved. Expanded desktop/mobile controls and dark contrast verified. Evidence: ../evidence/task-recipes-2026-10-21/README.md.
