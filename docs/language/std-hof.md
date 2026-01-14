---
title: Higher-order Standard Library
---

# Higher-order Functions

Functions that take lambdas to map, filter, and reduce collections.

## Functions
- `MAP(list, fn)` → transform each element.
- `FILTER(list, fn)` → keep elements where predicate is true.
- `REDUCE(list, init, fn)` → fold into a single value.
- `SOME(list, fn)` → true if any element matches.
- `EVERY(list, fn)` → true if all elements match.
- `FIND(list, fn)` → first matching element or null.
- `APPLY(fn, value)` → call a function value.
- `IS_FUNCTION(value)` → true if value is callable.

## Example
- [stdlib-hof-overview](../playground.md?example=stdlib-hof-overview)

## Notes
- Lambdas receive `(value, index, collection)` when the function supports it.

## Related
- [Expressions](expressions.md)
- [Arrays](std-arrays.md)
