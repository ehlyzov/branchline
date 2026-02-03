---
status: Implemented
depends_on: ['language/input-aliases']
blocks: []
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
  - date: 2026-02-01
    change: "Implemented OPTIONS parsing + runtime wiring; removed legacy mode blocks and SOURCE handling."
---
# Transform OPTIONS embedding


## Status (as of 2026-02-01)
- Stage: Implemented.
- `OPTIONS` is parsed into `TransformOptions` and carried on `TransformDecl`.
- Buffer mode is the only supported mode; `mode: stream` is rejected.
- Per-transform shared declarations are supported and wired into runner/registry environment setup.


## Outcomes
- Each `TRANSFORM` is self-sufficient by housing configuration in an `OPTIONS` block.
- Legacy `{ buffer }` mode headers and `SOURCE` declarations are removed.
- Multiple independent transforms per file are supported (each transform declares its own options).


## Syntax
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
- Multiple `TRANSFORM ... OPTIONS ... { ... }` blocks may appear in one file; helpers/registries accept a map of transforms without requiring a top-level `SOURCE`.
- `mode: stream` is rejected.

## Implementation notes
- Parser validates option keys, rejects duplicates, and enforces `mode: buffer` only.
- Shared options are merged with top-level shared declarations when building environments.
- Input/output adapter specs are parsed and carried on the AST for tooling and future adapter wiring.

## Follow-ups
- Wire adapter specs into runtime/CLI when adapters are implemented.
