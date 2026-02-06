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
- JSON numeric key conversion is opt-in via `--json-key-mode numeric`; it converts non-negative integer keys without leading zeros (except `0`) for nested objects (top-level input keys remain strings).
- JSON bytes are accepted only when contracts declare `bytes`; they must be base64 strings using the standard alphabet with `=` padding and no line breaks.
- XML input maps attributes to `@name` keys.
- XML pure text content maps to `$`; mixed content text segments map to `$1`, `$2`, ...
- XML repeated sibling elements map to arrays.
- XML empty elements with no attributes or children map to `""`.
- XML reserves `@*`, `$`, and `$n` keys for attributes and text segments.
- XML also provides `#text` as a compatibility alias for `$` on pure text nodes.

## Output Policies
- JSON output can be emitted in canonical form (`json-canonical`) with deterministic key ordering and normalized numeric formatting.
- Safe JSON output encodes BigInt/BigDec as strings; use `--json-numbers extended` to emit numeric literals.
- JSON output encodes bytes as base64 strings using the standard alphabet with `=` padding and no line breaks.
- Sets serialize as JSON arrays with deterministic ordering (null, boolean, number, string, array, object).

## Standard Library
- [Core](std-core.md)
- [Arrays](std-arrays.md)
- [Aggregation](std-agg.md)
- [Higher-Order Functions](std-hof.md)
- [Strings](std-strings.md)
- [Debug](std-debug.md)
- [Time](std-time.md)
