---
title: Array Standard Library
---

# Arrays

Helpers for array construction and manipulation.

## Functions
- `DISTINCT(list)` → unique values (preserves order).
- `FLATTEN(list)` → flattens nested arrays by one level.
- `SORT(list)` → sorted list (ascending).
- `RANGE(end[, start, step])` → generate a numeric range.
- `ZIP(listA, listB)` → pairs elements by index.

## Example
- [stdlib-arrays-overview](../playground.md?example=stdlib-arrays-overview)

## Notes
- `ZIP` truncates to the shorter list.
- `RANGE` uses defaults when `start`/`step` are omitted.

## Related
- [Core](std-core.md)
- [Higher-Order Functions](std-hof.md)
