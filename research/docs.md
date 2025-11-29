# Branchline documentation refresh (draft)

This draft lays out the revamped Branchline docs and playground experience, focusing on humans new to Branchline. It is intended to be moved into `docs/` after content is finalized and the playground embed is wired up.

## Goals
- Make first-time setup obvious (install, verify, run first program).
- Explain the language in plain language with runnable, contextual examples.
- Document every stdlib function with interactive examples in the embedded playground.
- Keep all existing playground examples and add coverage-driven ones.

## Who this is for
- Engineers evaluating Branchline for data transformation.
- Folks unfamiliar with Branchline or DSLs who need a guided start.
- Contributors building docs/playground content.

---

## Quickstart (try it now)
1) Open the embedded playground on the docs page (will be inlined on `docs/playground.md`) and load an example.  
2) Toggle tracing to see `EXPLAIN` output.  
3) Paste your own JSON (or XML) into the input pane and tweak the program.  
4) To run locally via CLI: `./gradlew :cli:runBl --args "path/to/program.bl --input sample.json"` (JVM) or `./gradlew :cli:jsNodeProductionRun --args="path/to/program.bl --input sample.json"` (Node).

---

## Installation & first-time setup

### Prerequisites
- JDK (use Gradle toolchains if not installed globally).
- Node.js (required for JS playground/Node CLI).
- Git and the Gradle wrapper (`./gradlew`) — do **not** use system Gradle.

### Clone and verify
```bash
git clone https://github.com/ehlyzov/branchline-public.git
cd branchline-public
./gradlew --version          # verifies toolchain
./gradlew :cli:runBl --help  # confirms CLI wiring
```

### Run your first program (JVM)
Create `hello.bl`:
```branchline
SOURCE msg;
TRANSFORM Hello {
    LET greet = "Hello, " + msg.name;
    OUTPUT { greeting: greet };
}
```
Run:
```bash
./gradlew :cli:runBl --args "hello.bl --input sample.json"
```
Where `sample.json`:
```json
{ "name": "Branchline" }
```
Expected output:
```json
{ "greeting": "Hello, Branchline" }
```

### Run a snippet (Node)
```bash
./gradlew :cli:jsNodeProductionRun --args="hello.bl --input sample.json"
```
Use `--input-format xml` if providing XML.

### Build distributable Node package
```bash
./gradlew :cli:packageJsCli
# outputs cli/build/distributions/branchline-cli-js-<version>.tgz
```

### Troubleshooting checklist
- JDK missing → install or enable toolchain; rerun `./gradlew --version`.
- Node missing (for JS) → install Node 18+.
- Gradle daemon stale → `./gradlew --stop` then retry.
- Playground bundle not loading locally → run `npm install && npm run dev` in `playground/` (for development), or rebuild assets via `npm run build` and copy into `docs/assets`.

---

## Embedded playground plan
- The docs page (`docs/playground.md`) will embed the playground inline by loading `docs/assets/playground.js` and mounting into a `.bl-playground` element. Keep `playground/demo.html` as a direct access fallback.
- All existing examples must remain available:
  - `collection-transforms`
  - `customer-profile`
  - `explain-derived-total` (trace)
  - `junit-badge-summary` (XML)
  - `order-shipment`
  - `pipeline-health-gating` (trace)
- Add new examples for stdlib coverage (IDs listed below). Examples live in `playground/examples/*.json`; docs link to them via anchors/query params to preselect an example in the embed.
- UX tips to surface near the embed:
  - Cmd/Ctrl+Enter runs the current program.
  - Switch input format JSON/XML; XML is parsed into objects.
  - Toggle tracing to expose `EXPLAIN(...)` and provenance JSON.
  - Changing examples resets editors to curated content.

---

## Language tour (human-focused explanations + runnable snippets)

### Syntax & literals
- Numbers, strings, booleans, `NULL`; arrays `[1,2]`; objects `{ key: value }`.
- Truthiness: `null` and `false` are falsey; numbers are false only if zero; strings false if empty.
- Operators: arithmetic `+ - * / %`, logical `&& ||`, equality `== !=`, comparison `< <= > >=`, concatenation `++`, coalesce `??`.

### Bindings and expressions
- `LET name = expr;` introduces variables inside blocks and expression contexts. Shadowing is allowed but should be avoided in long blocks.
- Assignment vs expression: `SET x = ...;` mutates; `LET` binds new.
- `AS` for alias/cast contexts; `CALL` to invoke host functions.

### Control flow
- `IF cond THEN ... ELSE ...` with block or inline expression forms.
- Loops: `FOR x IN items { ... }`, `FOR EACH` synonym, array comprehensions `[FOR (x IN xs) IF cond => expr]`.
- `WHERE` filters inside comprehension/loops.
- `TRY expr CATCH (err) => handler` for trapping runtime errors.
- `ASSERT` and `THROW` for guardrails; `ABORT` to halt pipeline.

### Functions
- `FUNC name(params) = expression` or block-bodied `FUNC name(params) { ... }`.
- Lambdas: `(x, i) -> expr`; higher-order functions consume lambdas.
- `APPLY(fn, ...)` to invoke a function value with dynamic args.

### Sources, outputs, adapters
- `SOURCE name USING adapter(args);` to declare inputs. If absent, `$`/`INPUT` are preloaded.
- `OUTPUT USING adapter(...) { ... }` or `OUTPUT { ... }` for default JSON stream.
- `TRANSFORM Name { stream|buffer } { ... }` to define pipeline stages; `@annotation` supported.

### Paths & selectors
- `$` or `INPUT` refers to the root input; `msg.field`, `items[0]`, slices `[start:end]`, predicates `[expr]`, wildcard `.*`.
- XML attributes appear as `@attr`; text nodes as `#text` (see JUnit example).

### Concurrency & suspension
- `SUSPEND expr;` to yield.
- `AWAIT` on asynchronous operations in expressions.
- `PARALLEL/ONBLOCK` for concurrent blocks.
- `SHARED name [SINGLE|MANY];` with `AWAIT_SHARED(resource, key)` to wait for shared store entries.

### Error handling & tracing
- `TRY/CATCH`, `ASSERT`, `THROW`, `ABORT` for control over failures.
- `CHECKPOINT(label)` to mark milestones.
- `EXPLAIN("varName")` surfaces provenance; ensure tracing is enabled in playground or runtime.

Each subsection in the final docs should include a short runnable snippet linked to the playground (see example IDs below) plus edge-case notes (null handling, out-of-bounds, type errors).

---

## Standard library coverage (function-by-function)

For each function we will document: signature, parameters, returns, error cases, null/empty behavior, notes, and a playground example ID. Below are the planned example IDs to add (grouped by module). Existing examples stay as-is; new ones will be added to `playground/examples` and surfaced in the docs.

### Core
- KEYS, VALUES, ENTRIES — example `stdlib-core-keys-values`
- PUT, DELETE — example `stdlib-core-put-delete`
- WALK, COLLECT — example `stdlib-core-walk`
- APPEND, PREPEND — example `stdlib-core-append-prepend`
- IS_OBJECT — example `stdlib-core-is-object`
- PRINT — example `stdlib-core-print` (note: side-effect only)

### Arrays
- DISTINCT — example `stdlib-arrays-distinct`
- FLATTEN — example `stdlib-arrays-flatten`
- SORT — example `stdlib-arrays-sort`
- RANGE — example `stdlib-arrays-range`
- ZIP — example `stdlib-arrays-zip`

### Aggregation
- LENGTH/COUNT — example `stdlib-agg-length`
- SUM — example `stdlib-agg-sum`
- AVG — example `stdlib-agg-avg`
- MIN/MAX — example `stdlib-agg-min-max`

### Strings / numbers / booleans
- STRING, NUMBER, BOOLEAN — example `stdlib-strings-casts`
- SUBSTRING — example `stdlib-strings-substring`
- CONTAINS — example `stdlib-strings-contains`
- MATCH — example `stdlib-strings-match`
- REPLACE — example `stdlib-strings-replace`
- SPLIT, JOIN — example `stdlib-strings-split-join`
- UPPER, LOWER, TRIM — example `stdlib-strings-upper-lower-trim`

### Higher-order
- MAP — example `stdlib-hof-map`
- FILTER — example `stdlib-hof-filter`
- REDUCE — example `stdlib-hof-reduce`
- SOME, EVERY — example `stdlib-hof-some-every`
- FIND — example `stdlib-hof-find`
- APPLY — example `stdlib-hof-apply`
- IS_FUNCTION — example `stdlib-hof-is-function`

### Debug
- CHECKPOINT — example `stdlib-debug-checkpoint`
- ASSERT — example `stdlib-debug-assert`
- EXPLAIN — example `stdlib-debug-explain`

### Time
- NOW — example `stdlib-time-now`

### Shared
- AWAIT_SHARED — example `stdlib-shared-await-shared`

---

## Learning path for newcomers
1) **Getting started**: follow install steps, run first program locally, then open the playground.  
2) **Language tour**: read the sections above with playground-linked snippets.  
3) **Stdlib drills**: open each stdlib example, tweak inputs, observe outputs.  
4) **Tracing & debugging**: enable tracing, use `EXPLAIN`, add `CHECKPOINT`/`ASSERT` to see runtime feedback.  
5) **XML/structured inputs**: load the JUnit XML example and adapt it.  

---

## Authoring guidelines for the final docs
- Keep explanations concise, prioritize real examples over grammar dumps.
- Every code sample should have a “Run in Playground” link that selects the matching example ID.
- Favor JSON inputs; include at least one XML example per major section to show attribute/text handling.
- Highlight pitfalls (nulls, type errors, out-of-bounds) near the relevant functions.
- Cross-link guides: install → first transform → stdlib catalog → playground.

---

## Validation checklist (post-implementation)
- Playground embeds correctly on `docs/playground.md`; fallback link still works.
- All existing examples are selectable; new stdlib examples appear and run.
- Each stdlib function documented with signature + behavior + interactive example.
- Language feature sections include runnable snippets and practical notes.
- New guides for install/first-time use are discoverable from the nav.
