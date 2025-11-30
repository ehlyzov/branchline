---
title: FOR EACH Loops
---

# FOR EACH Loops

`FOR EACH` iterates over array-like values and executes a block for every item.

## Basic loop
```branchline
FOR EACH item IN row.items {
    OUTPUT { name: item.name };
}
```

The loop reads `row.items` and outputs each element's `name` field.

## Filtering items
An optional `WHERE` clause keeps only matching elements.

```branchline
FOR EACH item IN row.items WHERE item.qty > 1 {
    OUTPUT { name: item.name, qty: item.qty };
}
```

Only items with quantity greater than 1 reach the body.

## Mutating within a loop
Use `SET` to update accumulators as you iterate:
```branchline
LET totals = { count: 0, sum: 0 };

FOR EACH item IN row.items WHERE item.qty > 0 {
    SET totals.count = totals.count + 1;
    SET totals.sum = totals.sum + item.qty;
}

OUTPUT totals;
```

## Tips
- `FOR` is a synonym of `FOR EACH`.
- `WHERE` is optional; omit it when you don’t need filtering.
- Use `LET` inside the loop for per-item scratch variables; use `SET` to mutate values defined outside the loop.
- For single-expression transformations, consider array comprehensions instead.

## Try it
Load the [collection-transforms](../playground.md?example=collection-transforms) example in the playground and compare the loop to MAP/FILTER/REDUCE alternatives.
