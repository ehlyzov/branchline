# Advanced CLI plan: replace .github/scripts with Branchline scripts

Goal: replace `.github/scripts/*.mjs` with Branchline scripts executed by the CLI while keeping all filesystem IO in the CLI and using `SHARED` as the only IO abstraction inside `TRANSFORM`.

## 1. What should be added to Branchline
- Implement shared write statements from the grammar (`IDENTIFIER[expr] = expr;`) if still missing, so transforms can emit results into `SHARED` resources without host-side glue.
- Ensure shared-store wiring is available for both interpreter and VM runs that the CLI triggers (pass a configured store into eval/VM so `await shared.key` works).
- Define a shared IO contract for CI scripts: required `SHARED` declarations, key naming rules (relative paths), and manifest layout.
- If new syntax is introduced (shared write or any new grammar), update the canonical grammar in `interpreter/src/jvmTest/resources/v2/ebnf.txt` and add conformance coverage in `conformance-tests/src/commonTest`.

## 2. What should be added to stdlib
- Add `FORMAT(template, args)` with placeholder syntax `{name}` and `{0}`; define stable escaping, and any unknown placeholder passes through verbatim).
- Add docs + tests for new functions and add a playground demo in `playground/examples/` (required for new language features).

## 3. What should be changed in CLI
- Initialize and seed a `SharedStore` for each run, register resources found in `SHARED` declarations, and expose them to both `AWAIT_SHARED` and `await shared.key`.
- Add flags to map local files into shared resources:
  - `--shared-file <resource>=<path>` for single entries.
  - `--shared-glob <resource>=<glob>` / `--shared-dir <resource>=<dir>` for many entries.
  - `--shared-format json|xml|text` to parse before storing.
  - `--shared-key relative|basename|custom` to control key names.
- To get the list of keys in a known `SHARED` slot use existing `KEYS` function, make sure that it works for SHARED too.
- Add `--output-path` (or `--output-field`) to print a value from the JSON output, with `--output-raw` to avoid JSON encoding when needed. This covers the `{ "answer": 42 } -> 42` requirement.
- Add `--output-file <path>` to write the selected output to disk (path can be a file or directory).
- Add `--output-lines <path>` to write JSON Lines output when a transform produces a list (one line per element).
- Add `--write-output-dir` / `--write-output-file` so the CLI writes `OUTPUT.files` entries to disk without paths embedded in transforms (ex: `OUTPUT { files: { "jmh-summary.md": markdown } }`).
- Add `--jobs N` to process per-file transforms in parallel (fan-out in the CLI), then merge results with a summary transform. Cap concurrency for deterministic ordering and memory control.

## 4. Other important things
- Create new Branchline scripts to replace the Node ones:
  - `.github/scripts/jmh-report.bl` to emit markdown + csv strings.
  - `.github/scripts/jmh-gate.bl` to emit pass/fail + failures list.
  - Update `.github/scripts/junit-file-summary.bl` and `.github/scripts/junit-summary.bl` to read inputs from `SHARED` instead of filesystem paths.
- Update CI workflows to call `bl` with the new shared IO flags and remove `.github/scripts/*.mjs`.
- Add CLI tests for shared mapping, output-path extraction, file-writing, and parallel fan-out; add conformance tests for any new language syntax.
- Update docs in `docs/guides/cli.md` and `docs/language/` to explain shared IO, new stdlib functions, and the new CLI options.
- Performance considerations: avoid loading all inputs into memory when not required; allow streaming or batched shared uploads in the CLI for large report sets.
