---
title: Array Comprehensions
---

# Array Comprehensions

Array comprehensions build a new array from an existing collection in a single expression.

## When to use it
Use a comprehension when you only need a simple mapping or filtering expression and want a concise result.

## Syntax
```branchline
LET squares = [FOR (n IN input.numbers) => n * n];
LET evens = [FOR (n IN input.numbers) IF n % 2 == 0 => n];
```

## Example
```branchline
TRANSFORM Tags {
    LET tags = [FOR (item IN input.items) => item.tag];
    OUTPUT { tags: tags };
}
```

## Pitfalls
- Comprehensions allow only a single expression after `=>`.
- Use `FOR EACH` if you need multiple statements or side effects.

## Try it
Open [collection-transforms](../playground.md?example=collection-transforms){ target="_blank" } to see comprehensions alongside MAP/FILTER/REDUCE.

## Related
- [FOR EACH Loops](for-each.md)
- [Expressions](../language/expressions.md#arrays-and-objects)
