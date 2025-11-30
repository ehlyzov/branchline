# Branchline docs + playground refactor plan

## Current state snapshot
- `docs/` uses MkDocs Material; top-level pages are sparse (e.g., `docs/index.md` and `docs/playground.md` are short, with no embedded playground).
- Language reference pages (`docs/language/*.md`) mostly list grammar snippets with little explanation or runnable samples. Standard library pages omit several functions (e.g., APPEND, PREPEND, COLLECT, PUT, DELETE, WALK, AWAIT_SHARED) and lack examples.
- Guides (`docs/guides/*`) cover only a few topics; no beginner install/first-run flow beyond brief CLI notes in `README.md`.
- Playground React app lives in `playground/` (`src/playground.tsx`, `worker.ts`, `examples/*.json`) and renders into `.bl-playground`. Built assets sit in `docs/assets/playground.js|css` plus Monaco bundles. The docs page links out to `playground/demo.html` instead of embedding.
- Existing curated examples (must be preserved): `collection-transforms`, `customer-profile`, `explain-derived-total` (trace), `junit-badge-summary` (XML), `order-shipment`, `pipeline-health-gating` (trace).

## Objectives (per request)
- Deliver full explanations of language features (syntax, semantics, patterns, pitfalls) with runnable snippets.
- Document every standard library function with interactive examples.
- Embed the playground directly on the docs page while keeping all existing examples.
- Add beginner-friendly install/first-time-use pages so newcomers can quickly try Branchline.

## Documentation architecture (targeting new `research/docs.md` as the drafting ground)
- Front matter: purpose of Branchline, capabilities, when to use it, supported runtimes (JVM/JS), and links to playground/CLI.
- Quickstart + install track:
  - Prereqs (JDK toolchain, Node for JS, Gradle wrapper usage, optional Docker if available).
  - Install options: using provided Gradle tasks (`:cli:runBl`, `:cli:runBlc`, `:cli:runBlvm`, `:cli:jsNodeProductionRun`, `:cli:packageJsCli`), building from source, and downloading packaged artifacts if available.
  - First program walkthrough: write, run, inspect output and EXPLAIN trace; common flags (`--input-format xml`, input file vs stdin).
  - Environment verification and troubleshooting (missing JDK, Node not found, Gradle daemon issues).
- Language tour (expand beyond grammar stubs):
  - Lexical/tokens with examples (identifiers, literals, operators).
  - Data types and literals (numbers, strings, booleans, null, arrays, objects, enums, unions) with semantics (truthiness, coercions).
  - Variables/bindings: `LET`, assignment rules, scoping, shadowing, immutability expectations.
  - Control flow: `IF/ELSE`, `FOR` / `FOR EACH` / `FOREACH`, comprehensions, `WHERE` usage, `TRY/CATCH`, `RETRY` modifiers, `ASSERT/THROW/ABORT`.
  - Functions: `FUNC` forms, parameters, returns, higher-order usage, lambda syntax, `APPLY`, `CALL` for host invocations.
  - Modules/adapters: `SOURCE`, `OUTPUT`, `USING`, transform modes (`STREAM`/`BUFFER`), annotations, `INIT`, `SHARED` with `SINGLE`/`MANY`, `AWAIT_SHARED`.
  - Concurrency/suspension: `SUSPEND`, `AWAIT`, `PARALLEL`/`ONBLOCK`, shared memory considerations.
  - Paths and selectors: `$`/`INPUT`, dot access, wildcards, slices, predicates; edge cases (missing fields, null coalescing, pattern for XML attributes).
  - Error handling and tracing: `TRY/CATCH`, `CHECKPOINT`, `EXPLAIN`, provenance expectations.
  - Each subsection should include: what it is, when to use, rules/pitfalls, and a runnable snippet linked to the playground.
- Standard library catalog with interactive examples:
  - Core: KEYS, VALUES, ENTRIES, PUT, DELETE, WALK, APPEND, PREPEND, COLLECT, PRINT, IS_OBJECT.
  - Arrays: DISTINCT, FLATTEN, SORT, RANGE, ZIP.
  - Aggregation: LENGTH/COUNT, SUM, AVG, MIN, MAX.
  - Strings/numbers/bool: STRING, NUMBER, BOOLEAN, SUBSTRING, CONTAINS, MATCH, REPLACE, SPLIT, JOIN, UPPER, LOWER, TRIM.
  - Higher-order: MAP, FILTER, REDUCE, SOME, EVERY, FIND, APPLY, IS_FUNCTION.
  - Debug: CHECKPOINT, ASSERT, EXPLAIN.
  - Time: NOW.
  - Shared: AWAIT_SHARED (not currently documented).
  - For each function: signature, parameters/returns, type expectations, errors, notes on null/empty handling, performance/edge cases, and an interactive code block that preloads the playground with input+program demonstrating the function.
- Playground section (embedded):
  - Replace external link with an inline embed of `.bl-playground` inside the docs page (either via iframe to `playground/demo.html` or by loading `docs/assets/playground.js` directly).
  - Keep existing examples and surface them as selectable presets; add anchors/deep links so “Try it” buttons in docs select the relevant example.
  - Add a short UX guide: running code, switching input JSON/XML, enabling tracing, resetting examples, keyboard shortcut.
  - Note client-side execution and bundle location; mention that examples live in `playground/examples/*.json` for contributors.
- Guides & FAQ:
  - Add “First time with Branchline” guide (step-by-step from nothing to first output) and “Installation & setup” guide (per-platform).
  - Expand troubleshooting/FAQ: common parser errors, type issues, playground not loading, XML pitfalls, how to read traces.
  - Provide a “learning path” list (intro → language tour → stdlib drills → playground exercises).

## Interactive example + playground integration plan
- Define a reusable example descriptor format (align with `playground/examples/*.json`) that can be embedded in docs via shortcodes or HTML blocks. Ensure the docs can reference an example ID to preselect it when the embedded playground loads.
- Add coverage examples for every stdlib function; group related functions to reduce clutter while ensuring each one has at least one runnable demonstration.
- Verify the worker continues to support both JSON and XML inputs; include at least one XML-driven example (retain `junit-badge-summary`).
- Keep current styling by reusing `docs/assets/playground.css` and the existing React bundle; ensure MkDocs build copies necessary assets or references them relative to `../assets/`.
- Consider a lightweight fallback link to `playground/demo.html` for environments where the embed cannot run.

## Implementation steps (after approval)
1) Create `research/docs.md` as the canonical draft containing the new structure above before syncing into `docs/`.
2) Rewrite `docs/playground.md` (after draft) to embed the playground and describe controls; keep existing example list and add deep links.
3) Expand language reference pages (`docs/language/*.md`) using the draft: add explanations + runnable snippets per feature with “Open in playground” links.
4) Build a stdlib catalog page (may be consolidated) that programmatically pulls or mirrors the example descriptors so docs stay in sync with `playground/examples`.
5) Author new guides: `docs/guides/first-steps.md` (first-time use) and `docs/guides/install.md` (detailed install paths), update `mkdocs.yml` navigation accordingly.
6) Add/augment examples in `playground/examples/*.json` to cover all functions; keep current ones intact and add new IDs for stdlib coverage.
7) Ensure the playground bundle is rebuilt/copied to `docs/assets/` after example updates; verify `playground/demo.html` remains functional for direct access.
8) Validate by running the docs site locally (`mkdocs serve`) to confirm embed works, links resolve, and interactive examples load with correct presets.

## Validation checklist
- All language features explained with at least one runnable snippet and an optional “open in playground” link.
- Each stdlib function has documented behavior (params, null/empty rules, error cases) and an interactive example.
- Embedded playground renders inside the docs page; existing curated examples are still present. Sidebars hidden on this page and layout is full-width.
- New install/first-run guides are discoverable from the nav and walk novices through setup, execution, and troubleshooting.
- `mkdocs build --strict` passes (with Material installed), no missing nav entries or broken links.

## Docs usefulness QA (add to review routine)
- For every page, check it answers: what, when to use, how, pitfalls/edge cases, and “try it now” (playground link or snippet).
- Home page shows “what you can build” with links to curated examples.
- Playground page: verify deep links load examples, “reset example” restores curated content, “open in new tab” present, and tracing toggle works.
- Guides: install instructions include prereqs; first-steps/getting-started include runnable example + expected output.
- Stdlib pages: include null/empty behavior and error conditions.
- Run a smoke tour: open playground, toggle tracing, switch JSON↔XML, edit input, run and see output update.
