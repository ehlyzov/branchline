---
title: Numeric Semantics
---

# Numeric Semantics

Branchline exposes a single user-facing type, **Numeric**. Internally, values are tracked as one of four kinds:

- **I**: fast integer (I64 on JVM; JS safe-integer range)
- **F**: float (F64 / double)
- **BI**: BigInt (arbitrary precision on JVM; 64-bit on JS)
- **BD**: BigDec (arbitrary precision on JVM; Double-backed on JS)

## Explicit precision with `DEC(...)`

`DEC(...)` is the primary explicit entry point into precise arithmetic. No operator will implicitly
convert float values (`F`) into BigDec (`BD`) or BigInt (`BI`).

Rules:

- `DEC("...")`
  - Parses as **BD** if the string contains `.` or an exponent (e.g., `"1.25"`, `"1e3"`).
  - Parses as **BI** otherwise.
- `DEC(number)`
  - `I` → **BI**
  - `BI` → **BI**
  - `BD` → **BD**
  - `F` → **BD** using the platform’s deterministic double→decimal conversion.

> **Tip:** on JVM, prefer `DEC("0.1")` for human-authored precise values. JS BigDec remains Double-backed; `DEC` does not recover decimal precision there.

Notes:
- Large integer literals may parse as **BI**.
- `INT("...")` can return **BI** for large values.
- `NUMBER("...")` does **not** produce **BI** or **BD**.

## Arithmetic promotion

For `+`, `-`, `*` (same pattern):

1. `I + I → I` (overflow promotes to `BI`)
2. `I + F → F`
3. `F + F → F`
4. `BI + I → BI`
5. `BI + F → F`
6. If either operand is `BD`, result is `BD` **unless** the other operand is `F`
   (mixing `BD` and `F` is rejected; wrap floats in `DEC(...)`).

This same `BD`/`F` mixing rule applies to comparisons and equality.

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
  - `BD` or `F` with `//` is rejected

## Rounding (`ROUND`)

`ROUND(x, n)` uses **round half to even**.

- `ROUND(x, 0)` returns an integer kind:
  - `I` / `BI` → unchanged
  - `F` → `I` if within range, otherwise `BI`
  - `BD` → `BI` (or `I` if it fits)
- `ROUND(x, n > 0)` returns:
  - `BD` if `x` is `BD`
  - `F` if `x` is `I`, `BI`, or `F`

## Powers and square roots (`POWER`, `SQRT`)

Both functions check arity and return `NULL` when an argument is `NULL`. Unsupported types,
non-finite ordinary numbers, invalid real domains, and floating result overflow are errors.

- With ordinary numbers, `POWER(base, exponent)` and `SQRT(number)` return **F**, including
  integer inputs. Results are approximate Double arithmetic; underflow may produce zero.
- `POWER(0, 0)` is `1`. A zero base with a negative exponent is an error. Negative bases
  require integer exponents. `SQRT` requires a non-negative input.
- If either `POWER` argument is **BI** or **BD**, the exponent must be an integer between
  `-999999999` and `999999999`, inclusive. Float mixing is rejected; opt in with `DEC`.
  JS classifies integral JS numbers as **I**, consistent with the existing arithmetic rules.
- In that explicit domain, an integer base with non-negative exponent returns **BI**;
  a **BD** base returns **BD**. On JVM these positive powers are exact. Negative powers
  return **BD**, using the reciprocal of the exact positive power, rounded to 34 significant
  digits with `HALF_UP`, matching existing decimal division.
- `SQRT(BI|BD)` returns **BD**. On JVM it uses 34 significant digits and `HALF_UP`,
  including irrational roots. It never converts an explicit JVM value to Double first.
- On JS, explicit integer `POWER` rejects results outside signed 64-bit range. Explicit
  decimal powers and roots use Double arithmetic, reject non-finite inputs/results, and
  remain approximate. This differs from JVM exact powers and decimal rounding.

Examples: `POWER(2, -2)` is floating `0.25`; `POWER(DEC(3), 20)` is integer
`3486784401`; `SQRT(DEC("2.25"))` is decimal `1.5`. On JVM,
`SQRT(DEC(2))` is `1.414213562373095048801688724209698`.

Try the [Powers and square roots example](../playground.md?example=numeric-powers-roots)
for values supported on both JVM and JS.

The exponent bound defines supported syntax values, not a memory or execution-time limit.
Very large exact powers can allocate very large numbers. Use trusted transforms or separate
host resource limits.

## JavaScript notes

- The `I` kind is restricted to the JS safe-integer range (±(2^53 − 1)).
- When integer operations exceed the safe range, results promote to `BI`.
- On JS, `BI` is **64-bit** (Long-backed), not arbitrary precision. Parsing outside that range
  fails; existing general integer arithmetic may wrap on Long overflow. The new `POWER`
  integer path explicitly checks overflow.
- JS `BI` retains `DEC("9007199254740993")` exactly, but its conversion to `BD` can round.
- `BD` is Double-backed on JS. `DEC("0.1") + DEC("0.2")` has numeric value
  `0.30000000000000004`; `DEC("9007199254740993.0")` rounds to `9007199254740992`.
  `DEC` chooses the explicit numeric domain; it does not provide arbitrary precision on JS.
- Use JVM for exact decimal or arbitrary-precision workloads. For interoperable money,
  constrain integer cents to supported ranges and validate bounds explicitly.

Try it in the playground: [Numeric precision example](../playground.md?example=stdlib-numeric-precision).

## JSON input/output rules

Branchline treats JSON numeric interoperability separately from in-language arithmetic:

- **Safe mode (default)** parses large integers as `BigInt` and high-precision decimals as `BigDec`, within the runtime storage limits described above.
  When emitting JSON, `BigInt` and `BigDec` are encoded as JSON strings to avoid silent precision loss.
- **Strict mode** rejects numbers outside the safe JSON numeric range.
- **Extended mode** emits large numbers as numeric literals for systems that can handle them.

Control the behavior with `--json-numbers strict|safe|extended` in the CLI (safe is the default).
