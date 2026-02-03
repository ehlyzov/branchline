---
status: Proposed
depends_on: []
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Proposed CLI option for implicit single-transform execution."
---
# Implicit single-transform mode (CLI proposal)


## Status (as of 2026-02-01)
- Stage: Proposed.
- Scope: CLI-only behavior; no grammar changes.
- Next: agree on flag name + guardrails, then implement + test + document.

## Problem
Small scripts and quick experiments require boilerplate `TRANSFORM` blocks even when the file is
clearly a single transform body. This adds friction for CLI and playground use.

## Proposal
Add an **opt-in** CLI flag that treats a file with **no explicit `TRANSFORM`** declarations as a
single implicit transform.

### CLI flag
- `--implicit-transform` (name TBD) enables the behavior.
- If any `TRANSFORM` is present, the flag is a no-op (normal parsing).

### Implicit wrapper
When enabled and no `TRANSFORM` is found, wrap the file body as:
```
TRANSFORM __Implicit(input) {
    <original file contents>
}
```
- Name defaults to `__Implicit` (internal use only).
- Input parameter defaults to `input` (with existing `row` compatibility alias).

### Guardrails
To avoid surprising errors:
- If the file contains any **top-level declarations** (`FUNC`, `TYPE`, `OPTIONS`,
  or any future declaration tokens), **error** with a message instructing the user to add an
  explicit `TRANSFORM` block.
- Token detection should be done using the **lexer**, not raw text (so comments/strings do not
  trigger false positives).

### Diagnostics
Preserve line/column numbers by using a wrapper that does not shift positions (or by applying a
wrapper offset adjustment in the CLI). Avoid changing error locations relative to the original file.

## Non-goals
- No language syntax change.
- No implicit mode by default.
- No support for multi-transform files in implicit mode.

## Gain/Loss Summary
**Gains**
- Faster CLI iteration for single-transform scripts.
- Lower onboarding friction and smaller examples.

**Losses/Risks**
- Potential ambiguity if a file accidentally omits `TRANSFORM`.
- Conflicts with future top-level syntax if guardrails are too loose.
- Requires careful diagnostics to avoid confusing error locations.

## Implementation sketch
1) In CLI load path, lex the source and detect `TRANSFORM` tokens and disallowed declarations.
2) If `--implicit-transform` and no `TRANSFORM`, wrap and parse.
3) Preserve offsets for diagnostics (prefix-only wrapper + offset map).
4) Execute as normal single-transform runner.

## Tests
- CLI integration: implicit mode succeeds for plain statement bodies.
- CLI integration: implicit mode errors for files containing `FUNC`/`TYPE`/`OPTIONS`.
- CLI integration: explicit `TRANSFORM` remains unchanged when flag is enabled.
- Diagnostics: error line/column matches original source.

## Docs
- Add a note to CLI docs describing the flag and guardrails.
- Add a short example in the CLI guide showing both explicit and implicit usage.

## Open questions
- Flag name: `--implicit-transform` vs `--single-transform` vs `--wrap-transform`.
- Should the implicit transform name be user-visible via `--transform` selection?
- Should implicit mode be allowed for files that include `FUNC` declarations?
