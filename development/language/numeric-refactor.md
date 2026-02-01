---
status: Implemented
depends_on: ['language/numeric-semantics']
blocks: []
supersedes: []
superseded_by: []
last_updated: 2026-02-01
changelog:
  - date: 2026-02-01
    change: "Migrated from research/numeric-refactor.md and added YAML front matter."
  - date: 2026-02-01
    change: "Marked implemented and aligned with current interpreter/VM behavior."
---
# Numeric Refactor: DEC, Numeric kinds, and specialization


## Status (as of 2026-02-01)
- Stage: Implemented.
- Numeric kind model, `DEC(...)` precision, and specialization are live in interpreter + VM.
- Specs updated to reflect current behavior (including JS limits and additional BI entry points).

## Goals

- Unify the public numeric surface as a single **Numeric** type.
- Preserve fast paths for integers and floats while enabling explicit precision via `DEC(...)`.
- Provide deterministic, KMP-safe semantics (JVM + JS).
- Keep hot paths in the interpreter and VM specialized with deopt/patching.

## Model

**Internal kinds**

- **I**: fast integer (JVM: 64-bit; JS: safe integer range)
- **F**: float (double)
- **BI**: BigInt
- **BD**: BigDec

**Explicit precision**

`DEC(...)` is the primary explicit entry point into `BI`/`BD`. It parses strings into `BI` or `BD`
depending on whether a decimal/exponent is present, and converts integer kinds to `BI`. Floats
convert to `BD` via deterministic double→decimal conversion.

Additional current BI entry points:
- Integer literals that exceed `Long` parse to `BI`.
- `INT("...")` can return `BI` for large values.

## Semantics

- `+`, `-`, `*` use the promotion rules:
  - `I + I → I` (overflow → `BI`)
  - `I + F → F`
  - `F + F → F`
  - `BI + I → BI`
  - `BI + F → F`
  - `BD` dominates unless paired with `F` (error).
- `/` returns `F` unless a `BD` is involved, in which case it returns `BD`.
- `//` is integer division; `BD` or `F` with `//` is rejected; negatives truncate toward zero.
- `ROUND(x, 0)` returns an integer kind; `ROUND(x, n>0)` returns `BD` for `BD` input and `F` otherwise.
- Comparisons and equality follow the same `BD`/`F` mixing rule (mixed decimal/float is rejected).

## Interpreter specialization

Binary numeric operations start in a generic mode that inspects operand kinds and then patches the
site into a small inline cache (up to four monomorphic variants). If a new kind appears after the
site stabilizes, the site falls back to a megamorphic generic path.

## VM specialization

The VM uses a per-program-counter numeric inline cache to specialize arithmetic sites. After the
first execution, the cached variant handles monomorphic kind pairs without repeated kind branching.

## JS notes

- `I` is constrained to the JS safe-integer range.
- Integer ops promote to `BI` when crossing the safe range, but `BI` on JS is **64-bit** (Long-backed),
  not arbitrary precision.
- `BD` on JS remains Double-backed; use `DEC("...")` to avoid float input.

## Migration notes

- `/` no longer returns integers for exact division; use `//` for integer division.
- Any `BD` + `F` arithmetic now throws unless the `F` operand is wrapped in `DEC(...)`.
- `ROUND` now returns integer kinds for precision `<= 0`, and `F` for non-`BD` inputs when precision `> 0`.

## Optional future work
- True arbitrary-precision `BI` on JS (native `BigInt` or a bignum library) if cross-platform
  numeric parity is a hard requirement.
