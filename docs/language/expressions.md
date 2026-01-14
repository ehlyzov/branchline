---
title: Expressions
---

# Expressions

Expressions compute values and support operators, function calls, and data construction.

## Common patterns
- Use `LET` inside expressions to bind temporary values.
- Use `??` to provide fallbacks for missing or null fields.
- Lambdas `(x) -> expr` feed higher-order stdlib functions (`MAP`, `FILTER`, `REDUCE`).

## As {#as}

`AS` provides aliasing or casting when supported by the host.

## Let {#let}

`LET` creates local variable bindings inside expressions.

## Await {#await}

`AWAIT` waits for an asynchronous result (host-dependent).

## Suspend {#suspend}

`SUSPEND` yields control (host-dependent).

## Literals {#literals}

Literals include numbers, strings, booleans, and null.

## Union {#union}

`UNION` creates union type expressions.

## Example
```branchline
LET status = CASE {
    WHEN input.total == 0 THEN "empty"
    WHEN input.total > 0 THEN "ready"
    ELSE "unknown"
};
```

## Operator hierarchy
```
expression  ::= assignment
assignment  ::= coalesce ( "=" assignment )?
coalesce    ::= logicOr ( "??" logicOr )*
logicOr     ::= logicAnd ( "||" logicAnd )*
logicAnd    ::= equality ( "&&" equality )*
equality    ::= comparison ( ( "==" | "!=" ) comparison )*
comparison  ::= term ( ( "<" | ">" | "<=" | ">=" ) term )*
term        ::= factor ( ( "+" | "-" ) factor )*
factor      ::= unary  ( ( "*" | "/" | "//" | "%" ) unary )*
unary       ::= ( "!" | "-" | AWAIT | SUSPEND ) unary | primary
```

## Primary forms
```
primary ::= literal | pathExpr | IDENTIFIER | funCall | arrayLit
          | objectLit | caseExpr | tryExpr | "(" expression ")" | lambdaExpr
```

## Arrays and objects
```
arrayLit  ::= "[" ( expression ("," expression)* )? "]"
            | "[" FOR "(" IDENTIFIER IN expression ")" ( IF expression )? "=>" expression "]"
objectLit ::= "{" fieldPair ("," fieldPair)* "}"
```

### Array comprehensions
```branchline
LET squares = [FOR (n IN input.numbers) => n * n];
LET evens = [FOR (n IN input.numbers) IF n % 2 == 0 => n];
```

## Try/Catch {#try}
`TRY` evaluates an expression and yields the fallback expression if it fails.

```branchline
LET total = TRY SUM(input.items)
CATCH(err) => 0;
```

## Path expressions
```
pathExpr ::= "$" pathSeg* | INPUT pathSeg* | IDENTIFIER pathSeg*
pathSeg  ::= "." "*"? | "." IDENTIFIER | "[" slice "]" | "[" predicate "]"
```

## Related
- [Statements](statements.md)
- [Array Comprehensions](../guides/array-comprehension.md)
- [Higher-Order Functions](std-hof.md)
