---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/docs-perf.md and added YAML front matter."
---
# Performance Docs + Pages Fix Plan


## Status (as of 2026-01-31)
- Stage: Proposed plan.
- Broken links/assets and formatting issues are identified; fixes are split across Agent A/B/C.
- Next: implement workflow/script changes and validate MkDocs outputs.

## Context snapshot (what is broken in the generated site)

- `file:///Users/eugene/Downloads/artifact/benchmarks/jsonata/releases/snapshot-c502022/index.html`
  - Has `Source data: CSV` linking to `jsonata-summary.csv`, but the file is missing in that directory.
  - `ls /Users/eugene/Downloads/artifact/benchmarks/jsonata/releases/snapshot-c502022` only contains `index.html`.
- `file:///Users/eugene/Downloads/artifact/benchmarks/index.html`
  - Release links point to `releases/<tag>.md` instead of `releases/<tag>/` (MkDocs writes `<tag>/index.html`).
- `file:///Users/eugene/Downloads/artifact/benchmarks/releases/snapshot-c502022/index.html`
  - Contains only the "Runtime and allocation" section, no "Interpreter vs VM ratio (mean)" section.
  - Numeric output has too many digits (example: `2.050769212644806` where `2.051` is enough).

## Page build flow (where to patch)

- Deploy job downloads release assets into `docs/benchmarks/...` and runs MkDocs:
  - `.github/workflows/deploy.yml#L57-L125` (Fetch benchmark summaries).
  - It only downloads `.md` assets and writes links ending in `.md`.
- Local helper script does the same:
  - `.github/scripts/fetch-benchmark-summaries.sh#L37-L105`.
- JMH summary generation (release workflow):
  - `.github/workflows/release-artifacts.yml#L184-L196` uses
    `--shared-key basename` with two `results.json` files.
- JMH report formatting:
  - `.github/scripts/jmh-report.bl#L106-L111` `formatNumber` is `STRING(value)`.
  - Ratio uses `STRING(row.ratio)` at `.github/scripts/jmh-report.bl#L236`.
- JSONata report formatting:
  - `.github/scripts/jsonata-report.bl#L12-L17` `formatNumber` is `STRING(value)`.
- Shared input keys:
  - `--shared-key basename` maps both `.../results.json` to the same key.
  - `cli/src/commonMain/kotlin/io/github/ehlyzov/branchline/cli/SharedIo.kt#L120-L140`
    shows `BASENAME` uses file name only.
  - For `SharedKind.MANY`, keys overwrite in the store (last wins):
    `interpreter/src/jvmMain/kotlin/io/github/ehlyzov/branchline/std/SharedStoreJvm.kt#L15-L41`.

## Plan (split across agents)

### Agent A: Fix release links + CSV downloads (deploy + helper script)

Goal: Ensure MkDocs pages link to the correct HTML paths and CSV assets are present.

Context:
- Link generation is in `.github/workflows/deploy.yml#L94-L119` and
  `.github/scripts/fetch-benchmark-summaries.sh#L72-L99`.
- MkDocs maps `docs/benchmarks/releases/<tag>.md` to `/benchmarks/releases/<tag>/index.html`.
  So links should target `<tag>/` not `<tag>.md`.
- JSONata summary pages expect a file named `jsonata-summary.csv`
  in the same directory as `index.html`.

Tasks:
1) Update release list link targets:
   - In deploy workflow and fetch script, change
     `(${safe_tag}.md)` -> `(${safe_tag}/)`
     and `(releases/${safe_tag}.md)` -> `(releases/${safe_tag}/)`.
2) Download CSV assets in both places and place them in per-release directories:
   - JMH: asset `branchline-jmh-summary-${tag}.csv`.
   - JSONata: asset `branchline-jsonata-summary-${tag}.csv`.
   - Write to:
     - `docs/benchmarks/releases/${safe_tag}/jmh-summary.csv`
     - `docs/benchmarks/jsonata/releases/${safe_tag}/jsonata-summary.csv`
   - Create those directories before writing.
3) Keep the deploy workflow and helper script in sync (same link targets + CSV logic).

Validation:
- After MkDocs build, confirm:
  - `site/benchmarks/releases/<tag>/index.html` and
    `site/benchmarks/releases/<tag>/jmh-summary.csv` exist.
  - `site/benchmarks/jsonata/releases/<tag>/index.html` and
    `site/benchmarks/jsonata/releases/<tag>/jsonata-summary.csv` exist.
  - `benchmarks/index.html` links point to `/benchmarks/releases/<tag>/`.

### Agent B: Restore Interpreter vs VM comparison section

Goal: Ensure JMH summary has both Interpreter + VM rows so the comparison section renders.

Context:
- `release-artifacts.yml#L184-L188` calls
  `jmh-report.bl` with `--shared-key basename` for two files named `results.json`.
- `SharedStore` keeps only the last value for duplicate keys, so one suite drops.
- Comparison section only renders if both suites exist
  (`jmh-report.bl#L167-L203`, `#L229-L239`).

Tasks:
1) Change the JMH report invocation in `.github/workflows/release-artifacts.yml` to
   avoid key collision:
   - Remove `--shared-key basename` or switch to `--shared-key relative`.
2) Update docs/examples that reference the old command:
   - `docs/benchmarks.md#L65-L68`
   - `README.md#L71-L75`
3) Optional hardening:
   - If only one suite is present, add a warning line to the markdown
     so missing data is obvious.

Validation:
- Regenerate a summary locally with two `results.json` files and confirm
  the comparison section appears and includes Interpreter + VM ratios.

### Agent C: Reduce numeric precision in reports

Goal: Round numeric output so tables show a few decimals (e.g., `2.051`).

Context:
- `formatNumber` in `jmh-report.bl` and `jsonata-report.bl` uses `STRING(value)`
  (full precision).
- Ratio uses `STRING(row.ratio)` in `jmh-report.bl#L236`.
- `ROUND(number[, precision])` exists in the std library API but is currently TODO:
  `interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/std/StdNumericModule.kt#L80-L90`.

Tasks:
1) Decide rounding implementation:
   - Option A: Implement `ROUND` (and any helpers) in `StdNumericModule` so it is safe
     to use in `.bl` scripts.
   - Option B: Implement manual rounding inside the `.bl` scripts (scale + floor/ceil),
     if you prefer not to touch the stdlib.
2) Update `formatNumber` in:
   - `.github/scripts/jmh-report.bl#L106-L111` (also use it for ratio at #L236).
   - `.github/scripts/jsonata-report.bl#L12-L17`.
3) Decide whether CSV should also be rounded (usually yes for human-readability).

Validation:
- Rebuild a summary and confirm values like `2.050769212644806` render as `2.051`.

### Agent D: Make deploy workflow runnable via workflow_dispatch

Goal: Manual "Build and Deploy to GitHub Pages" runs should not skip.

Context:
- Workflow definition: `.github/workflows/deploy.yml#L1-L27`.
- Both jobs have `if` conditions tied to `workflow_run` success.
- User reports manual dispatch runs are skipped.

Tasks:
1) Check a skipped run in GitHub Actions UI to see the exact skip reason.
2) If the `if` condition is the cause, simplify to avoid `workflow_run`-only gating:
   - Example: `if: ${{ github.event_name != 'workflow_run' || github.event.workflow_run.conclusion == 'success' }}`.
3) If dispatch needs explicit ref, add `workflow_dispatch` input `ref`
   and update `actions/checkout` to use it.
4) Add a small debug step (echo event name + ref) to confirm dispatch context.

Validation:
- Trigger `workflow_dispatch` and confirm both `build` and `deploy` run.

