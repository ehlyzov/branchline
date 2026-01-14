---
title: Aggregation Standard Library
---

# Aggregation

Summaries and aggregates for arrays and collections.

## Functions
- `LENGTH(list)` → number of elements.
- `COUNT(list)` → alias for `LENGTH`.
- `SUM(list)` → sum of numeric values.
- `AVG(list)` → average of numeric values.
- `MIN(list)` → smallest value.
- `MAX(list)` → largest value.

## Example
- [stdlib-agg-overview](../playground.md?example=stdlib-agg-overview)

## Notes
- Most functions expect numeric arrays; non-numeric values may error.

## Related
- [Arrays](std-arrays.md)
- [Higher-Order Functions](std-hof.md)
