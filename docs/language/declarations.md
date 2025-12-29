---
title: Declarations
---

# Declarations

Branchline programs are composed of top-level declarations that introduce
outputs, transformations, and reusable definitions. The input payload is
available implicitly as `input`/`INPUT`.

## Output declarations

```
outputDecl ::= OUTPUT adapterSpec? templateBlock
```

An `OUTPUT` sends data through an optional adapter and a template
block that describes the resulting structure.

## Transform declarations

```
transformDecl ::= annotation* TRANSFORM IDENTIFIER? transformSig? transformMode? block
transformSig  ::= "(" transformParamList? ")" "->" typeExpr
```

Transforms process data from sources to outputs, optionally annotated or
configured with a buffer mode header. Signatures can document parameter and
result types. When a signature is omitted, both the input and output types
default to `Any`. Use `_`/`_?` as placeholders for `Any`/`Any?` in signatures
and type expressions.

Example signatures:

```
TRANSFORM X(input: _) -> _ { ... }
TRANSFORM Enrich(user: { id: string, email?: string }) -> { user: string } { ... }
```

## Shared declarations

```
sharedDecl ::= SHARED IDENTIFIER ( SINGLE | MANY )? ;
```

Shared memory exposes mutable storage that can be `SINGLE` or `MANY`
valued.

Example:

```branchline
SHARED session MANY;
SHARED cache SINGLE;
```

## Function declarations

```
funcDecl ::= FUNC IDENTIFIER "(" paramList? ")" funcBody
```

Functions define reusable computations, taking an optional parameter list
and either an expression or block body.

## Type declarations

```
typeDecl ::= TYPE IDENTIFIER "=" typeExpr ;
typeExpr ::= typeTerm ( "|" typeTerm )*
```

Type definitions describe enums, unions, list types, placeholders, and record
schemas with required or optional fields. Lists use `[TypeRef]` syntax, unions
use `A | B`, and `_`/`_?` stand in for `Any`/`Any?`.

Examples:

```
TYPE User = { id: string, name: string, email?: string }
TYPE Address = { street: string, location: { lat: number, lon: number } }
TYPE Tags = [string]
TYPE Status = enum { Active, Suspended, Deleted }
TYPE IdOrName = string | number
TYPE Flexible = _?
```

## Constraints

- Declarations are top-level; use `LET`/`SET` inside blocks for local state.
- `SHARED` resources must be declared before first use in the program.
- `TYPE` aliases define shapes but do not perform runtime validation unless the
  host or tests enforce it.

## Versioning and compatibility

When you evolve a schema, prefer explicit versioned TYPE names so older contracts can coexist with new definitions. A common convention is to suffix the name with a version number, for example:

```
TYPE Order_v1 = { id: string, status: enum { Open, Closed } }
TYPE Order_v2 = { id: string, status: enum { Open, Closed, Cancelled }, note?: string }
```

### Contract diff CLI

Use the CLI to compare two TYPE definitions or JSON Schema documents:

```
bl contract-diff schemas/order-v1.json schemas/order-v2.json
bl contract-diff types/order.bl types/order-new.bl --type Order
```

The CLI classifies changes as:

- **Breaking changes**
  - Removed fields.
  - Optional fields that become required.
  - Type narrowing (a value set becomes smaller), including removing union/enum members.
  - Required fields added to a record type.
- **Non-breaking changes**
  - Added optional fields.
  - Required fields that become optional.
  - Type widening (a value set becomes larger), including adding union/enum members.

Use the output to decide when to increment major vs minor versions (e.g., breaking changes for `Order_v3`, non-breaking changes for `Order_v2` updates).