---
title: Expressions
---

# Expressions

Expressions compute values and support operators, function calls, and data construction.

## Common patterns
- Use `LET` inside expressions to bind temporary values.
- Use `??` to provide fallbacks for missing or null fields.
- Lambdas `(x) -> expr` feed higher-order stdlib functions (`MAP`, `FILTER`, `REDUCE`).

## Coalesce contracts
- Inferred input contracts treat `??` as a fallback chain.
- Example: `input.testsuites ?? input.testsuite` emits a one-of obligation instead of requiring both fields unconditionally.
- In warn/strict contract modes, at least one fallback path in each group must be present with a non-null value.
- Fields read behind fallbacks (for example `suite["@tests"] ?? 0`) remain optional in inferred input nodes because defaults make them non-mandatory.

## Constraints
- `CASE` requires an `ELSE` branch.
- `TRY/CATCH` is an expression and can be used in `LET`, `CASE`, or output templates.
- `AWAIT` and `SUSPEND` require host support and may error without it.
- Comparisons reject mixed `BD`/`F` numeric values; wrap floats with `DEC(...)`.

## As {#as}
`AS` provides aliasing or casting when supported by the host.

## Let {#let}
`LET` creates local variable bindings inside expressions.

## Await {#await}
`AWAIT` waits for an asynchronous result (host-dependent).

## Suspend {#suspend}
`SUSPEND` yields control (host-dependent).

## Function calls {#call}
Function calls use `identifier(...)` syntax and can target user-defined `FUNC` declarations or host helpers.

## Literals {#literals}
Literals include numbers, strings, booleans, and null.

## Union {#union}
`UNION` creates union type expressions.

## Set Types {#set-types}
Use `set<T>` inside type expressions to describe unordered, unique collections.

Sets serialize to JSON arrays with deterministic ordering: null, boolean, number, string, array, object. Arrays and objects are ordered by their canonical JSON encoding. XML output emits repeated elements in the same deterministic order.

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
The identifier in `CATCH(...)` is bound to an error object with `message` and `type` fields.

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
