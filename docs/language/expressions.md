---
title: Expressions
---

# Expressions

Expressions compute values and support operators, function calls, and data
construction.

## Practical notes
- Use `LET` inside expressions to bind temporary values: `LET x = ...; OUTPUT { result: x }`.
- `AWAIT`/`SUSPEND` are for async/suspension-aware hosts; in pure playground scenarios they behave like unary operators and will error if no async host is configured.
- Function calls use `name(args)` for user-defined functions and host-exposed helpers.
- Use `??` to provide fallbacks when fields may be missing or null.
- Lambda syntax `(param[, ...]) -> expression` feeds higher-order stdlib functions (`MAP`, `FILTER`, `REDUCE`, etc).
- Path expressions let you traverse JSON/XML structures quickly; combine slices `[start:end]` and predicates `[expr]` for selective access.

## Constraints
- `CASE` requires an `ELSE` branch.
- `TRY`/`CATCH` is an expression; wrap specific calls instead of large blocks.
- `AWAIT` and `SUSPEND` depend on host support and may error without it.

## Performance tips
- Cache expensive paths or function calls with `LET`.
- Keep lambda bodies small; extract helpers with `FUNC` when reused across transforms.
- Prefer `MAP`/`FILTER`/`REDUCE` for collection processing to keep intent clear.

## As {#as}

The `AS` keyword provides aliasing and casting functionality.

## Let {#let}

The `LET` keyword creates local variable bindings within expressions.

## Await {#await}

The `AWAIT` keyword suspends execution until an asynchronous operation completes.

## Suspend {#suspend}

The `SUSPEND` keyword creates suspension points in execution.

## Function calls {#call}

Function calls use `identifier(...)` syntax and can target user-defined `FUNC`
declarations or host helpers registered as expressions.

## Literals {#literals}

Literals represent constant values like numbers, strings, booleans, and null.

## Union {#union}

The `UNION` keyword creates union type expressions.

## Case {#case}

`CASE` selects the first matching branch from top to bottom. `ELSE` is required.

```branchline
LET status = CASE {
    WHEN totals.tests == 0 THEN "error"
    WHEN failed == 0 THEN "passing"
    ELSE "failing"
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
factor      ::= unary  ( ( "*" | "/" | "%" ) unary )*
unary       ::= ( "!" | "-" | AWAIT | SUSPEND ) unary | primary
```

## Primary forms

```
primary ::= literal | pathExpr | IDENTIFIER | funCall | arrayLit
          | objectLit | caseExpr | tryExpr | "(" expression ")" | lambdaExpr
caseExpr ::= CASE "{" caseWhen+ caseElse "}"
caseWhen ::= WHEN expression THEN expression
caseElse ::= ELSE expression
tryExpr  ::= TRY expression CATCH "(" IDENTIFIER ")" ( RETRY NUMBER TIMES ( BACKOFF STRING )? )? ( "->" | "=>" ) expression
literal ::= NUMBER | STRING | TRUE | FALSE | NULL
```

## Function calls and lambdas

```
funCall    ::= IDENTIFIER "(" argList? ")"
argList    ::= expression ( "," expression )*
lambdaExpr ::= "(" paramList? ")" "->" expression
```

## Arrays and objects

```
arrayLit  ::= "[" ( expression ("," expression)* )? "]"
            | "[" FOR "(" IDENTIFIER IN expression ")" ( IF expression )? "=>" expression "]"
objectLit ::= "{" fieldPair ("," fieldPair)* "}"
fieldPair ::= fieldKey ":" expression
fieldKey  ::= IDENTIFIER ["?"] | STRING
```

### Array comprehensions

Array literals support a comprehension form that builds a new array by
iterating over another collection with an optional filter.

```branchline
LET nums = [1, 2, 3];
LET squares = [FOR (n IN nums) => n * n];
LET evens = [FOR (n IN nums) IF n % 2 == 0 => n];

// Equivalent trailing form
LET squares2 = [n * n FOR EACH n IN nums];
```

See the [Array Comprehensions guide](../guides/array-comprehension.md) for more examples.

## Try/Catch {#try}

`TRY` evaluates an expression and yields the fallback expression if it fails.
The identifier in `CATCH(...)` is bound to an error object with `message` and
`type` fields.

```branchline
LET total = SUM(input.items);
LET guard = TRY ASSERT(total >= 0, "total cannot be negative")
CATCH(err) => { ok: false, error: err.message };
```

## Path expressions

```
pathExpr ::= "$" pathSeg* | INPUT pathSeg* | IDENTIFIER pathSeg*
pathSeg  ::= "." "*"? | "." IDENTIFIER | "[" slice "]" | "[" predicate "]"
slice    ::= [ NUMBER ] ":" [ NUMBER ]
predicate ::= expression
```

These forms allow navigating and slicing data structures within expressions.
