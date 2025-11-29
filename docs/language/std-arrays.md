---
title: Arrays Standard Library
---

# Arrays

Helpers for working with lists.

- `DISTINCT(list)` → removes duplicates, keeps order of first occurrence.
- `FLATTEN(listOfLists)` → removes one level of nesting; non-lists are passed through.
- `SORT(list)` → sorts values; errors on incomparable types.
- `RANGE(n)` or `RANGE(start, end[, step])` → integer sequences (inclusive end). Step must be > 0.
- `ZIP(a, b)` → pairs elements up to the shorter list length.

Run it: [Array helpers example](../playground.md?example=stdlib-arrays-overview).
