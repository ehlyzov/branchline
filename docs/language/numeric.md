---
title: Numeric Semantics
---

# Numeric Semantics

Branchline exposes a single user-facing type, **Numeric**. Internally, values are tracked as one of four kinds:

- **I**: fast integer (I64 on JVM; JS safe-integer range)
- **F**: float (F64 / double)
- **BI**: BigInt (arbitrary precision integer)
- **BD**: BigDec (arbitrary precision decimal)

## Explicit precision with `DEC(...)`

`DEC(...)` is the **only** entry point into precise arithmetic. No operator will implicitly convert
float values (`F`) into BigDec (`BD`) or BigInt (`BI`).

Rules:

- `DEC("...")`
  - Parses as **BD** if the string contains `.` or an exponent (e.g., `"1.25"`, `"1e3"`).
  - Parses as **BI** otherwise.
- `DEC(number)`
  - `I` → **BI**
  - `BI` → **BI**
  - `BD` → **BD**
  - `F` → **BD** using the platform’s deterministic double→decimal conversion.

> **Tip:** prefer `DEC("0.1")` for human-authored precise values.

## Arithmetic promotion

For `+`, `-`, `*` (same pattern):

1. `I + I → I` (overflow promotes to `BI`)
2. `I + F → F`
3. `F + F → F`
4. `BI + I → BI`
5. `BI + F → F`
6. If either operand is `BD`, result is `BD` **unless** the other operand is `F`
   (mixing `BD` and `F` is rejected; wrap floats in `DEC(...)`).

## Division

- `/` always returns **F** (double) unless a `BD` is involved:
  - `I / I → F`
  - `BI / BI → F`
  - `BD / BD → BD`
  - `BD / I|BI → BD`
  - `I|BI / BD → BD`
- `//` is **integer division**:
  - `I // I → I` (truncates toward zero; overflow promotes to `BI`)
  - `BI // BI → BI`
  - `BD // ...` is rejected

## Rounding (`ROUND`)

`ROUND(x, n)` uses **round half to even**.

- `ROUND(x, 0)` returns an integer kind:
  - `I` / `BI` → unchanged
  - `F` → `I` if within range, otherwise `BI`
  - `BD` → `BI` (or `I` if it fits)
- `ROUND(x, n > 0)` returns:
  - `BD` if `x` is `BD`
  - `F` if `x` is `I`, `BI`, or `F`

## JavaScript notes

- The `I` kind is restricted to the JS safe-integer range (±(2^53 − 1)).
- When integer operations exceed the safe range, results promote to `BI`.
- `BD` uses the platform BigDec implementation (Double-backed in JS); use `DEC("...")` for explicit
  precision and to avoid float rounding surprises.

Try it in the playground: [Numeric precision example](../playground.md?example=stdlib-numeric-precision).
