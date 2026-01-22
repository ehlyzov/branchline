---
title: Declarations
---

# Declarations

Top-level declarations define transforms, types, shared memory, and functions.

## Output declarations
```
outputDecl ::= OUTPUT adapterSpec? templateBlock
```
`OUTPUT` sends data through an optional adapter and a template block that describes the resulting structure.

## Transform declarations
```
transformDecl ::= annotation* TRANSFORM IDENTIFIER? transformSig? transformMode? block
transformSig  ::= "(" transformParamList? ")" "->" typeExpr
```
Transforms process data from sources to outputs. Signatures document parameter and result types. When omitted, both input and output types default to `Any`. Use `_`/`_?` as placeholders for `Any`/`Any?`.

Example signatures:
```
TRANSFORM X(input: _) -> _ { ... }
TRANSFORM Enrich(user: { id: string, email?: string }) -> { user: string } { ... }
```

## Shared declarations
```
sharedDecl ::= SHARED IDENTIFIER ( SINGLE | MANY )? ;
```
Shared memory exposes mutable storage that can be `SINGLE` or `MANY` valued.

```branchline
SHARED session MANY;
SHARED cache SINGLE;
```

## Function declarations
```
funcDecl ::= FUNC IDENTIFIER "(" paramList? ")" funcBody
```
Functions define reusable computations, taking an optional parameter list and either an expression or block body.

## Type declarations
```
typeDecl ::= TYPE IDENTIFIER "=" typeExpr ;
typeExpr ::= typeTerm ( "|" typeTerm )*
```
Type definitions describe enums, unions, list types, placeholders, and record schemas with required or optional fields.

```
TYPE User = { id: string, name: string, email?: string }
TYPE Tags = [string]
TYPE Status = enum { Active, Suspended, Deleted }
TYPE Flexible = _?
```

## Constraints
- Declarations are top-level; use `LET`/`SET` inside blocks for local state.
- `SHARED` resources must be declared before first use.
- `TYPE` aliases define shapes but do not perform runtime validation unless the host or tests enforce it.

## Versioning and compatibility
When you evolve a schema, prefer explicit versioned `TYPE` names so older contracts can coexist with new definitions.

```
TYPE Order_v1 = { id: string, status: enum { Open, Closed } }
TYPE Order_v2 = { id: string, status: enum { Open, Closed, Cancelled }, note?: string }
```

Use the CLI to compare two type definitions or JSON Schema documents:

```
bl contract-diff schemas/order-v1.json schemas/order-v2.json
bl contract-diff types/order.bl types/order-new.bl --type Order
```

The CLI classifies changes as:
- **Breaking changes**: removed fields, optional fields that become required, type narrowing.
- **Non-breaking changes**: added optional fields, required fields that become optional, type widening.

## Related
- [Statements](statements.md)
- [Expressions](expressions.md)
- [Grammar](grammar.md)
