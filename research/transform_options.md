# Transform OPTIONS embedding (plan)

> **Status:** ⏳ Proposed — transforms default to buffer mode with no mode block; `{ stream }` is rejected, and top-level `SOURCE` is ignored in favor of the canonical `input` binding (with `row` as a compat alias).【F:interpreter/src/commonMain/kotlin/Parser.kt†L95-L152】【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L46-L83】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L18-L67】

## Goals
- Make each `TRANSFORM` self-sufficient by housing configuration in an `OPTIONS` block.
- Remove top-level `SOURCE` settings and the standalone `{ stream }`/`{ buffer }` mode block (mode defaults to buffer; stream is unsupported for now).
- Permit multiple independent transforms per file without external wiring (each transform declares its own input alias/mode/adapters).
- Keep a compatibility bridge long enough to migrate existing programs, fixtures, and docs.

## Proposed syntax (draft)
```
TRANSFORM Normalize(input) OPTIONS {
    mode: stream            // default when omitted
    input: { alias: input, adapter: kafka("topic") }  // optional adapter; alias defaults to `in`
    shared: [ cache SINGLE, tokens MANY ]             // optional per-transform shared bindings
    output: { adapter: json() }                       // optional default OUTPUT adapter
} {
    LET name = input.customer.name
    OUTPUT { customer: name }
}
```
- `OPTIONS` is optional; defaults: `mode = stream`, `input.alias = in` (with `row` alias preserved temporarily), no adapters.
- Multiple `TRANSFORM … OPTIONS … { … }` blocks may appear in one file; helpers/registries should accept a map of transforms without requiring a top-level `SOURCE`.
- Back-compat (transition): keep parsing legacy `SOURCE` (ignored) and `{ buffer }` for a short window; reject `{ stream }` until streaming support returns.

## Implementation steps
1) **Grammar & AST**
   - Add `TransformOptions` to the AST and update `TransformDecl` to carry it (mode, input alias + adapter, shared decls, default output adapter).
   - Teach the parser to read an optional `OPTIONS { … }` block after the transform header (name/params) and remove the mandatory `{ STREAM/BUFFER }` block. Keep `{ stream }` as a temporary alternative that populates `options.mode`.
   - Deprecate top-level `SOURCE` parsing; gate it behind a legacy path that lowers into per-transform options or emits a warning.
2) **Semantic analysis**
   - Validate option fields (mode enum, adapter args, unique shared bindings per transform, no duplicate aliases).
   - Default `mode=stream`, `input.alias=in`; preserve `row` alias in the environment during rollout.
   - Reject files that mix legacy `SOURCE` declarations with new `input` options once the bridge is removed; for now, merge them with warnings.
3) **Execution/runtime**
   - Thread `TransformOptions` through `compileStream`, `TransformRegistry`, and runner builders; stop hardcoding `{ stream }`/`row` in the environment bootstrap.【F:vm/src/commonMain/kotlin/v2/ir/StreamCompiler.kt†L46-L84】【F:interpreter/src/commonMain/kotlin/v2/ir/RunnerMP.kt†L26-L39】
   - Update CLI entrypoints and any helper wrappers (test-fixtures runners, JS CLI) to use per-transform options when preparing the environment and selecting engines.
4) **Tests**
   - Parser/sema tests for `OPTIONS` parsing, defaults, invalid fields, and legacy-compat lowering.
   - Runner/registry tests for per-transform options (mode selection, aliasing, adapters) across interpreter + VM paths.
   - Update fixtures/examples to avoid `{ stream }`/`SOURCE`; add mixed legacy/new cases during the transition.
5) **Docs**
   - Language reference and playground examples: show `OPTIONS` syntax, note default `mode`/`input`, and call out deprecation of top-level `SOURCE` and `{ stream }`.
   - CLI/docs pages: update usage samples to the new form; highlight multi-transform files and how to select a transform (if needed).
6) **Migration/interop**
   - Add a feature flag or warning path that surfaces when legacy `SOURCE`/mode blocks are present; document the cutoff timeline.
   - Provide a quick rewrite guide (old → new) for existing scripts and tests.

## Open decisions to confirm
- Exact `OPTIONS` schema: do we need per-transform output adapter defaults and shared declarations, or keep OUTPUT/SHARED in the body?
- Transform selection for multi-transform files: if not explicit, which transform executes by default in CLI/test helpers?
- Deprecation horizon: how long should the legacy parser paths stay enabled before erroring?
