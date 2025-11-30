---
title: Higher-Order Functions Standard Library
---

# Higher-Order Functions

Functional helpers that take other functions as arguments.

Each helper expects a function value of the form `(value, index, list) -> ...` unless noted.

- `MAP(list, fn)` → transforms each element.
- `FILTER(list, fn)` → keeps elements where `fn` is truthy.
- `REDUCE(list, init, fn)` → accumulates from `init`.
- `SOME(list, fn)` / `EVERY(list, fn)` → booleans for any/all; empty lists return `false`/`true` respectively.
- `FIND(list, fn)` → first matching value or `null`; returns `null` on empty lists.
- `APPLY(fn, ...args)` → calls a function value with arbitrary args.
- `IS_FUNCTION(x)` → tests if a value is callable.

Truthy rules: `null` and `false` are falsey; numbers are falsey only when `0`; strings are falsey only when empty.

Run it: [Higher-order helpers example](../playground.md?example=stdlib-hof-overview).
