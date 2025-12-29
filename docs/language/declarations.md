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
block that describes the resulting structure【F:language/src/test/kotlin/v2/ebnf.txt†L26-L31】.

## Transform declarations

```
transformDecl ::= annotation* TRANSFORM IDENTIFIER? transformSig? transformMode? block
transformSig  ::= "(" transformParamList? ")" "->" typeExpr
```

Transforms process data from sources to outputs, optionally annotated or
configured with a buffer mode header. Signatures can document parameter and
result types. When a signature is omitted, both the input and output types
default to `Any`. Use `_`/`_?` as placeholders for `Any`/`Any?` in signatures
and type expressions【F:language/src/test/kotlin/v2/ebnf.txt†L45-L55】.

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
valued【F:language/src/test/kotlin/v2/ebnf.txt†L55-L55】.

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
and either an expression or block body【F:language/src/test/kotlin/v2/ebnf.txt†L61-L63】.

## Type declarations

```
typeDecl ::= TYPE IDENTIFIER "=" typeExpr ;
typeExpr ::= typeTerm ( "|" typeTerm )*
```

Type definitions describe enums, unions, list types, placeholders, and record
schemas with required or optional fields. Lists use `[TypeRef]` syntax, unions
use `A | B`, and `_`/`_?` stand in for `Any`/`Any?`【F:language/src/test/kotlin/v2/ebnf.txt†L65-L75】.

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
