---
status: Proposed
depends_on: ['language/input-aliases']
blocks: ['language/transform-contracts']
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/transform_options.md and added YAML front matter."
  - date: 2026-02-01
    change: "Aligned OPTIONS plan with buffer-only mode and input parameter."
  - date: 2026-02-01
    change: "Aligned plan with removal of SOURCE parsing and updated file references."
---
# Transform OPTIONS embedding (plan)


## Status (as of 2026-02-01)
- Stage: Proposed.
- `OPTIONS` block is not implemented; `SOURCE` is no longer parsed; buffer is the only supported mode; `input` is the standardized source parameter.
- Next: implement `OPTIONS` grammar/IR threading/tests/docs and define a migration timeline.


## Goals
- Make each `TRANSFORM` self-sufficient by housing configuration in an `OPTIONS` block.
- Remove top-level `SOURCE` settings and the standalone `{ buffer }` mode block (buffer is the only supported mode; stream is removed).
- Permit multiple independent transforms per file without external wiring (each transform declares its own input config/adapters).
- Keep a compatibility bridge long enough to migrate existing programs, fixtures, and docs.

## Proposed syntax (draft)
```
TRANSFORM Normalize(input) OPTIONS {
    mode: buffer            // optional; buffer is the only supported mode
    input: { adapter: kafka("topic") }                // input binding is the source parameter
    shared: [ cache SINGLE, tokens MANY ]             // optional per-transform shared bindings
    output: { adapter: json() }                       // optional default OUTPUT adapter
} {
    LET name = input.customer.name
    OUTPUT { customer: name }
}
```
- `OPTIONS` is optional; defaults: `mode = buffer`, input binding is the source parameter (`input`), and no adapters.
- Multiple `TRANSFORM ... OPTIONS ... { ... }` blocks may appear in one file; helpers/registries should accept a map of transforms without requiring a top-level `SOURCE`.
- Back-compat (transition): no `SOURCE` parsing to preserve; `{ buffer }` remains optional; `{ stream }` remains invalid.

## Implementation steps
1) **Grammar & AST**
   - Add `TransformOptions` to the AST and update `TransformDecl` to carry it (mode, input adapter, shared decls, default output adapter).
   - Teach the parser to read an optional `OPTIONS { ... }` block after the transform header (name/params) and remove the mandatory `{ BUFFER }` block.
   - Keep top-level `SOURCE` parsing removed; if reintroduced for migration, lower into per-transform options with warnings.
2) **Semantic analysis**
   - Validate option fields (mode enum, adapter args, unique shared bindings per transform).
   - Default `mode=buffer`, keep `input` as the source parameter; preserve `row` alias in the environment during rollout.
   - Reject files that mix legacy `SOURCE` declarations with new `input` options once the bridge is removed; for now, merge them with warnings.
3) **Execution/runtime**
   - Thread `TransformOptions` through `compileStream`, `TransformRegistry`, and runner builders; stop hardcoding legacy mode blocks/`row` in the environment bootstrap.[F:vm/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/StreamCompiler.kt:L1-L80][F:interpreter/src/commonMain/kotlin/io/github/ehlyzov/branchline/ir/RunnerMP.kt:L1-L80]
   - Update CLI entrypoints and any helper wrappers (test-fixtures runners, JS CLI) to use per-transform options when preparing the environment and selecting engines.
4) **Tests**
   - Parser/sema tests for `OPTIONS` parsing, defaults, invalid fields, and legacy-compat lowering.
   - Runner/registry tests for per-transform options (mode selection, input config, adapters) across interpreter + VM paths.
   - Update fixtures/examples to avoid `{ stream }`/`SOURCE`; add mixed legacy/new cases during the transition.
5) **Docs**
   - Language reference and playground examples: show `OPTIONS` syntax, note buffer-only mode and the `input` source parameter, and call out deprecation of top-level `SOURCE`.
   - CLI/docs pages: update usage samples to the new form; highlight multi-transform files and how to select a transform (if needed).
6) **Migration/interop**
   - Add a feature flag or warning path that surfaces when legacy `SOURCE`/mode blocks are present; document the cutoff timeline.
   - Provide a quick rewrite guide (old -> new) for existing scripts and tests.

## Open decisions to confirm
- Exact `OPTIONS` schema: do we need per-transform output adapter defaults and shared declarations, or keep OUTPUT/SHARED in the body?
- Transform selection for multi-transform files: if not explicit, which transform executes by default in CLI/test helpers?
- Deprecation horizon: how long should the legacy parser paths stay enabled before erroring?
