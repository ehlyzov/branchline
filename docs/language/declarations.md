---
title: Declarations
---

# Declarations

Branchline programs are composed of top-level declarations that introduce
sources, outputs, transformations, and reusable definitions.

In small programs you can rely on an implicit `SOURCE` and `OUTPUT` (the playground does this), but production pipelines typically declare them explicitly and wire adapters via `USING`.

## Source declarations

```
sourceDecl ::= SOURCE IDENTIFIER adapterSpec? ;
```

Each `SOURCE` names an input and optionally specifies an adapter
that reads data【F:language/src/test/kotlin/v2/ebnf.txt†L22-L31】.

## Output declarations

```
outputDecl ::= OUTPUT adapterSpec? templateBlock
```

An `OUTPUT` sends data through an optional adapter and a template
block that describes the resulting structure【F:language/src/test/kotlin/v2/ebnf.txt†L26-L31】.

## Transform declarations

```
transformDecl ::= annotation* TRANSFORM IDENTIFIER? transformMode? block
```

Transforms process data from sources to outputs, optionally annotated or
modeled as a stream or buffer【F:language/src/test/kotlin/v2/ebnf.txt†L45-L49】.

## Shared declarations

```
sharedDecl ::= SHARED IDENTIFIER ( SINGLE | MANY )? ;
```

Shared memory exposes mutable storage that can be `SINGLE` or `MANY`
valued【F:language/src/test/kotlin/v2/ebnf.txt†L55-L55】.

## Function declarations

```
funcDecl ::= FUNC IDENTIFIER "(" paramList? ")" funcBody
```

Functions define reusable computations, taking an optional parameter list
and either an expression or block body【F:language/src/test/kotlin/v2/ebnf.txt†L61-L63】.

## Type declarations

```
typeDecl ::= TYPE IDENTIFIER "=" typeExpr ;
typeExpr ::= enum "{" enumVal ("," enumVal)* "}" | simpleType ( "|" simpleType )*
```

Type definitions describe new enums or compositions of built-in types
such as `string`, `number`, `boolean`, `null`, and generic arrays【F:language/src/test/kotlin/v2/ebnf.txt†L65-L70】.
