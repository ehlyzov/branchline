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
- [I/O Contracts Audit](io-contracts.md)
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
- XML namespace declarations are normalized into `@xmlns`, where default namespace is `@xmlns.$` and prefixed declarations use `@xmlns.<prefix>`.
- XML element and attribute names preserve prefixes (for example `x:item` and `@x:id`).

## Output Policies
- JSON output can be emitted in canonical form (`json-canonical`) with deterministic key ordering and normalized numeric formatting.
- Safe JSON output encodes BigInt/BigDec as strings; use `--json-numbers extended` to emit numeric literals.
- JSON output encodes bytes as base64 strings using the standard alphabet with `=` padding and no line breaks.
- Sets serialize as JSON arrays with deterministic ordering (null, boolean, number, string, array, object).
- XML output is available via `--output-format xml|xml-compact`.
- XML output sorts attributes lexicographically by name.
- XML output preserves array order for repeated sibling elements of the same name.
- XML output sorts sibling element names lexicographically unless `@order` is provided.
- XML output accepts `@order` as an explicit sibling name order list, then appends remaining siblings lexicographically.
- XML output validates prefixed element and attribute names against in-scope `@xmlns` declarations in strict mode.
- CLI emits conversion-loss warnings to stderr when known lossy JSON/XML conversions occur.

## Internal Interchange
- Branchline runtime now provides internal CBOR encode/decode helpers for lossless Branchline-to-Branchline interchange.
- CBOR map keys are restricted to text or integer keys; other key types are rejected.
- Extended types are preserved with tags: BigInt (`2`/`3`), BigDecimal (`4`), and Set (`267`).
- CBOR byte strings are used for binary values, so internal interchange does not require base64.
- Deterministic CBOR is available via `CborEncodeOptions(deterministic = true)` and applies canonical ordering for map keys and set elements.

## Standard Library
- [Core](std-core.md)
- [Arrays](std-arrays.md)
- [Aggregation](std-agg.md)
- [Higher-Order Functions](std-hof.md)
- [Strings](std-strings.md)
- [Debug](std-debug.md)
- [Time](std-time.md)
