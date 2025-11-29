---
title: Aggregation Standard Library
---

# Aggregation

Functions for computing statistics over collections.

- `LENGTH(x)` / `COUNT(x)` → size of strings, lists, or objects; `0` for `null`.
- `SUM(list)` → sum of numbers; errors on non-numeric entries.
- `AVG(list)` → average or `null` when empty; errors on non-numeric entries.
- `MIN(list)` / `MAX(list)` → smallest/largest value or `null` when empty; errors on incomparable values.

Run it: [Aggregation example](../playground.md?example=stdlib-agg-overview).
