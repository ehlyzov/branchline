---
title: Language Overview
---

# Language Overview

Use this section as the formal reference for Branchline syntax and semantics.

## Read this first
- If you are new, start with the [Tour of Branchline](../learn/index.md).
- Keep the [Grammar](grammar.md) nearby for exact syntax.

## Reference sections
- [Lexical Structure](lexical.md)
- [Declarations](declarations.md)
- [Statements](statements.md)
- [Expressions](expressions.md)
- [Numeric Semantics](numeric.md)
- [Grammar](grammar.md)

## Input Policies
- JSON object keys must be unique; duplicates are rejected in CLI and playground input parsing.
- JSON number parsing defaults to safe mode: large integers become BigInt and high-precision decimals become BigDec.
- Use `--json-numbers strict` to reject values outside the safe JSON numeric range.

## Output Policies
- JSON output can be emitted in canonical form (`json-canonical`) with deterministic key ordering and normalized numeric formatting.
- Safe JSON output encodes BigInt/BigDec as strings; use `--json-numbers extended` to emit numeric literals.

## Standard Library
- [Core](std-core.md)
- [Arrays](std-arrays.md)
- [Aggregation](std-agg.md)
- [Higher-Order Functions](std-hof.md)
- [Strings](std-strings.md)
- [Debug](std-debug.md)
- [Time](std-time.md)
