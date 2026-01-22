---
title: FOR EACH Loops
---

# FOR EACH Loops

`FOR EACH` iterates over array-like values and executes a block for every item.

## When to use it
Use `FOR EACH` when you need side effects or multiple statements per item (such as building accumulators).

## Syntax
```branchline
FOR EACH item IN input.items {
    OUTPUT { name: item.name };
}
```

## Filtering items
Add a `WHERE` clause to keep only matching elements.

```branchline
FOR EACH item IN input.items WHERE item.qty > 1 {
    OUTPUT { name: item.name, qty: item.qty };
}
```

## Mutating within a loop
Use `SET` to update accumulators as you iterate.

```branchline
LET totals = { count: 0, sum: 0 };

FOR EACH item IN input.items WHERE item.qty > 0 {
    SET totals.count = totals.count + 1;
    SET totals.sum = totals.sum + item.qty;
}

OUTPUT totals;
```

## Pitfalls
- `FOR` is a synonym of `FOR EACH`, but mixing both in one file can hurt readability.
- `WHERE` is optional; omit it if you do not need filtering.
- For single-expression transforms, prefer array comprehensions.

## Try it
Load [collection-transforms](../playground.md?example=collection-transforms){ target="_blank" } and compare loops to MAP/FILTER/REDUCE alternatives.

## Related
- [Array Comprehensions](array-comprehension.md)
- [Statements](../language/statements.md#for)
