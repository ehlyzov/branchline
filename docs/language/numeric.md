---
title: Numeric Semantics
---

# Numeric Semantics

Branchline exposes a single user-facing type, **Numeric**. Precision depends on the
runtime, operation and conversion path. The profiles below bound what an integration
can rely on; they do not make v1.0.0-rc.1 a stable release.

## Choose a numeric profile

| Profile | Supported exact work | Required limits and exclusions |
| --- | --- | --- |
| Common integer profile (JVM and JS) | Integer parsing, `+`, `-`, `*`, unary `-`, `//`, `%`, equality and ordering | Every input **and intermediate result** is an integer in `-9007199254740991..9007199254740991`. Use integer literals, integer JSON tokens, or validated `INT` conversions. Excludes `/`, decimal/float operands, implicit host conversions, and other numeric functions. |
| JVM explicit profile | `DEC("integer")` arbitrary-precision integers; `DEC("decimal")` exact finite decimal parsing, `+`, `-`, `*`, comparisons; integer `//` and `%` | Keep operands in explicit integer/decimal domains. `/`, decimal `%`, negative powers and roots have separate rounding rules. Memory, scale and execution limits still apply. |
| JS explicit profile | `DEC("integer")`, integer `+`, `-`, `*`, unary `-`, `//`, `%`, comparisons while every result fits signed 64-bit storage | `-9223372036854775808..9223372036854775807`; keep operands integer-only. General overflow can wrap. Decimal storage and conversions to decimal use Double and can lose digits. This profile provides no exact decimal guarantee. |

The common profile is an admission rule for the caller, **not automatic checked
arithmetic**. A value outside its bounds might promote, round, wrap or fail depending
on the operation and runtime. Validate bounds before performing an operation; checking
a result after overflow cannot recover the intended value. Integer cents are suitable
only when every partial sum and product also meets the chosen profile. A final total
inside the bound does not establish that intermediate arithmetic was exact.

Outside the common profile, tests must use a runtime-specific oracle. Equal JVM/JS
outputs or equal interpreter/VM outputs alone do not prove an exact mathematical result.
These profiles cover the interpreter and ordinary VM execution; experimental VM
optimizations do not acquire a numeric support guarantee from this page.

## Numeric kinds

Internally, values are tracked as one of four kinds:

- **I**: ordinary integer (I64 on JVM; JS integer fast arithmetic uses the safe-integer range)
- **F**: float (F64 / double)
- **BI**: BigInt (arbitrary precision on JVM; 64-bit on JS)
- **BD**: BigDec (arbitrary precision on JVM; Double-backed on JS)

## Explicit precision with `DEC(...)`

`DEC(...)` explicitly chooses the BigInt/BigDec domain. Its precision is runtime-specific.
No arithmetic operator implicitly converts a float (`F`) to BigDec (`BD`) or BigInt (`BI`).

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
- Integer literals parse through Int, then Long, then **BI**. Decimal/exponent literals
  parse as Double; write `DEC("0.1")` to avoid that initial conversion on JVM.
- `INT("...")` parses an integer string and can return **BI** on JVM. On JS the string
  must fit signed 64-bit storage. `INT(1.5)` and `INT("1.5")` fail; `INT` does not truncate
  a fractional value. Converting an already rounded number cannot recover lost digits.
- `NUMBER("...")` uses Int/Long for integer text and Double for decimal/exponent text.
  It does not create **BI** or **BD** from strings; integer text outside signed 64-bit
  range fails. JVM host BigInteger/BigDecimal values are also `Number`, so this is not
  a claim that `NUMBER` rejects or converts every host-supplied explicit value.
- JS classifies integral ordinary JS numbers as **I**. As a result, `1.0` can behave
  as an integer there while it remains **F** on JVM. Use integer tokens or explicit
  string conversions when kind-sensitive behavior must agree across runtimes.
  JS integer conversions from ordinary numbers require signed 64-bit storage and can
  already have lost digits in Double; use integer strings to preserve large integers.

## Arithmetic promotion

For `+`, `-`, `*` (same pattern):

1. `I + I → I` (outside the integer fast range, promotes to `BI`; JS `BI` overflow can still wrap)
2. `I + F → F`
3. `F + F → F`
4. `BI + I → BI`
5. `BI + F → F`
6. If either operand is `BD`, result is `BD` **unless** the other operand is `F`
   (mixing `BD` and `F` is rejected; wrap floats in `DEC(...)`).

This same `BD`/`F` mixing rule applies to comparisons and equality. Mixing **BI**
with **F** converts the integer to Double and can lose precision, including in comparisons.
This is not part of either exact profile.

## Division

- `/` always returns **F** (double) unless a `BD` is involved:
  - `I / I → F`
  - `BI / BI → F`
  - `BD / BD → BD`
  - `BD / I|BI → BD`
  - `I|BI / BD → BD`
  - JVM decimal division uses 34 significant digits and `HALF_UP`; it is not exact
    rational arithmetic. For example, `DEC("1.0") / DEC("3.0")` is
    `0.3333333333333333333333333333333333`.
  - JS decimal division uses Double.
- `//` is **integer division**:
  - `I // I → I` (truncates toward zero; overflow promotes to `BI`, subject to runtime storage limits)
  - `BI // BI → BI`
  - `BD` or `F` with `//` is rejected (see the JS integral-number classification above)
- `%` is remainder, with the sign of the dividend: `-7 % 2` is `-1`.
  JVM decimal remainder uses the same 34-digit context; a quotient requiring too many
  digits can fail. It is excluded from the exact decimal profile.

Ordinary `/`, `//` and `%`, and explicit integer `//` and `%`, reject a zero divisor.
JVM decimal zero division/remainder also fail. JS decimal arithmetic uses Double:
zero division can produce Infinity/NaN. There is no general arithmetic guarantee that
all overflow or non-finite results are rejected; ordinary floating arithmetic can also
overflow to Infinity. Validate finiteness where floating values enter an integration.

## Rounding (`ROUND`)

`ROUND(x, n)` uses **round half to even**.

- `ROUND(x, 0)` returns an integer kind:
  - `I` / `BI` → same integer value, potentially narrowed to an ordinary integer
  - `F` → a rounded integer, subject to runtime conversion/storage limits
  - `BD` → a rounded integer, potentially narrowed to an ordinary integer
- `ROUND(x, n > 0)` returns:
  - `BD` if `x` is `BD`
  - `F` if `x` is `I`, `BI`, or `F`

Negative precision rounds to tens, hundreds, and so on and returns an integer.
JVM decimal `ROUND` uses exact decimal `HALF_EVEN`; ordinary floats and JS decimals
round their stored Double approximation. Positive precision on **BI** converts through
Double and can lose integer digits. Large precisions can require large intermediates;
they are outside the common integer profile.

`FLOOR` and `CEIL` round down and up respectively. For example,
`FLOOR(DEC("-1.5"))` is `-2` and `CEIL(DEC("1.5"))` is `2`.
On JVM explicit decimal inputs are rounded without converting to Double. JS uses its
stored Double and a signed 64-bit integer result; this can already have lost precision.

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

- The integer fast arithmetic range is ±(2^53 − 1). Long-backed literal/host values can exist outside it.
- When integer operations exceed the safe range, results promote to `BI`.
- On JS, `BI` is **64-bit** (Long-backed), not arbitrary precision. Parsing outside that range
  fails; existing general integer arithmetic may wrap on Long overflow. The new `POWER`
  integer path explicitly checks overflow.
- JS `BI` retains `DEC("9007199254740993")` exactly, but its conversion to `BD` can round.
- `BD` is Double-backed on JS. `DEC("0.1") + DEC("0.2")` has numeric value
  `0.30000000000000004`; `DEC("9007199254740993.0")` rounds to `9007199254740992`.
  `DEC` chooses the explicit numeric domain; it does not provide arbitrary precision on JS.
- Use the JVM explicit profile for exact decimal or arbitrary-precision workloads.
  Apply the common profile only after proving bounds for all intermediates.

Try it in the playground: [Numeric precision example](../playground.md?example=stdlib-numeric-precision).

## JSON input/output rules

Branchline treats JSON numeric interoperability separately from in-language arithmetic:

- **Safe mode (default)** parses integer tokens outside ±(2^53 − 1) as **BI**.
  Decimal tokens with more than 15 significant mantissa digits, or a non-finite Double
  conversion, use **BD**. Runtime storage limits still apply: JS can reject an oversized
  integer or round an accepted decimal. Safe mode does not add precision to JS storage.
- Safe output encodes **every BI/BD**, including `DEC(1)`, as a JSON string. Ordinary
  Long values outside the safe integer range are also strings. Parsing that output again
  produces strings, not automatically restored numeric types; agree a schema and use
  explicit conversions where required.
- **Strict mode** rejects integer input outside the safe integer range, decimal input
  selected for **BD**, and non-finite decimal input. Its decimal check is a digit-count/
  finite-range policy, not a proof of exact decimal representation in Double. Strict
  output rejects every **BI/BD**, even a small one, and unsafe ordinary Long values.
- **Extended mode** uses the same input numeric selection as safe mode and emits
  **BI/BD** and large integers as unquoted numeric literals. A downstream JSON consumer
  can round them; extended output is not an interoperability precision guarantee.

For example, safe JSON input `9007199254740993` retains that integer on both runtimes
and safe output is `"9007199254740993"`. Safe input
`0.1234567890123456789` retains all decimal digits on JVM but rounds on JS before output.
Ordinary Double JSON output rejects NaN and Infinity. Do not rely on serializers to
repair an already rounded or wrapped value, or on extended decimal output as a
non-finite validation layer.

Control the behavior with `--json-numbers strict|safe|extended` in the CLI (safe is the default).
Error categories and acceptance rules here are the contract; exception class, wrapping
and diagnostic wording may differ between the interpreter, VM and platforms.

## Bytecode and snapshot persistence

The v1.0.0-rc.1 artifact v3 separates explicit numbers from ordinary
`i64`/`f64` values using `bi`/`bd` text tags. JVM persistence preserves BigInt value
and kind, and BigDec value, kind and scale; decimal text is never routed through
Double. The same value codec applies inside supported containers and VM snapshots.
Legacy artifacts retain their old interpretation; lost precision cannot be restored.

JS accepts only BigInt values in signed 64-bit storage and finite BigDec values
whose decimal rendering agrees with the encoded mathematical value. Unsupported
precision, overflow and underflow are errors. This checks transport against the
existing JS representation; it does not provide exact decimal arithmetic or retain
JVM scale. See [artifact migration](../guides/migrations.md#compiled-artifact-policy).
