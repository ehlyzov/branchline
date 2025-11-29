---
title: Array Comprehensions
---

# Array Comprehensions

Array comprehensions build arrays from other collections using a `FOR` clause inside a literal【F:language/src/test/kotlin/v2/ebnf.txt†L122-L124】. They’re handy for concise reshaping without an explicit loop.

## Basic comprehension
```branchline
LET nums = [1, 2, 3];
LET squares = [FOR (n IN nums) => n * n];
OUTPUT { squares: squares };
```
`squares` becomes `[1, 4, 9]`.

## With filtering
```branchline
LET nums = [1, 2, 3, 4];
LET evens = [FOR (n IN nums) IF n % 2 == 0 => n];
OUTPUT { evens: evens };
```
`evens` becomes `[2, 4]`.

## Building objects inline
```branchline
LET items = [
  { name: "pen", qty: 2, price: 9.5 },
  { name: "pad", qty: 1, price: 12.0 },
  { name: "bag", qty: 3, price: 4.75 }
];

LET summaries = [
  FOR (item IN items)
    IF item.qty > 1
    => { name: item.name, subtotal: item.qty * item.price }
];

OUTPUT { summaries: summaries };
```
Result:
```json
{
  "summaries": [
    { "name": "pen", "subtotal": 19 },
    { "name": "bag", "subtotal": 14.25 }
  ]
}
```

## Trailing form
You can trail the `FOR EACH` after the expression:
```branchline
LET nums = [1, 2, 3];
LET doubled = [n * 2 FOR EACH n IN nums];
```
This is equivalent to the basic form above.

## Try it
Paste any of the snippets into the [playground](../playground.md) and run. Start from the `collection-transforms` example to compare comprehensions with MAP/FILTER/REDUCE.
