---
title: Aggregation Standard Library
---

# Aggregation

Summaries for collections.

## Functions
- `LENGTH(x)` / `COUNT(x)` → size of strings, lists, or objects; `0` for `null`.
- `SUM(list)` → sum of numbers; errors on non-numeric entries; `0` when list is empty.
- `AVG(list)` → average or `null` when empty; errors on non-numeric entries.
- `MIN(list)` / `MAX(list)` → smallest/largest value or `null` when empty; errors on incomparable values.

## Example
- [stdlib-agg-overview](../playground.md?example=stdlib-agg-overview)
